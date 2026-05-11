package com.webhookservice.service;

import com.webhookservice.cache.CaffeineCache;
import com.webhookservice.cache.StatsCache;
import com.webhookservice.model.RequestLog;
import com.webhookservice.model.dto.Page;
import com.webhookservice.model.dto.StatsResponse;
import com.webhookservice.repository.RequestLogRepository;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Batching contract of {@link RequestLogService}: size/timer flush, no loss on close, disabled = direct save. */
@ExtendWith(VertxExtension.class)
class RequestLogServiceBatchTest {

    @Test
    void sizeThreshold_triggersImmediateFlush(Vertx vertx, VertxTestContext tc) {
        RecordingRepo repo = new RecordingRepo();
        StatsCache statsCache = new StatsCache(new CaffeineCache<>("stats", 100, Duration.ofSeconds(30), true));
        RequestLogService svc = new RequestLogService(repo, vertx, statsCache, true, 5, 10_000L);

        UUID webhookId = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            svc.save(sample(webhookId), 100);
        }

        vertx.setTimer(150, id -> tc.verify(() -> {
            assertTrue(repo.batchCalls.get() >= 1, "expected at least one batch flush");
            assertEquals(5, repo.totalSaved.get());
            svc.close();
            tc.completeNow();
        }));
    }

    @Test
    void timerThreshold_triggersFlushEvenBelowSize(Vertx vertx, VertxTestContext tc) {
        RecordingRepo repo = new RecordingRepo();
        StatsCache statsCache = new StatsCache(new CaffeineCache<>("stats", 100, Duration.ofSeconds(30), true));
        RequestLogService svc = new RequestLogService(repo, vertx, statsCache, true, 100, 50L);

        UUID webhookId = UUID.randomUUID();
        svc.save(sample(webhookId), 100);
        svc.save(sample(webhookId), 100);

        vertx.setTimer(250, id -> tc.verify(() -> {
            assertTrue(repo.batchCalls.get() >= 1, "expected flush by timer");
            assertEquals(2, repo.totalSaved.get());
            svc.close();
            tc.completeNow();
        }));
    }

    @Test
    void close_persistsPendingLogs(Vertx vertx, VertxTestContext tc) {
        RecordingRepo repo = new RecordingRepo();
        StatsCache statsCache = new StatsCache(new CaffeineCache<>("stats", 100, Duration.ofSeconds(30), true));
        RequestLogService svc = new RequestLogService(repo, vertx, statsCache, true, 1000, 10_000L);

        UUID webhookId = UUID.randomUUID();
        svc.save(sample(webhookId), 100);
        svc.save(sample(webhookId), 100);
        svc.save(sample(webhookId), 100);

        svc.flushPending().onComplete(tc.succeeding(v -> tc.verify(() -> {
            assertEquals(3, repo.totalSaved.get());
            svc.close();
            tc.completeNow();
        })));
    }

    @Test
    void batchDisabled_callsSaveImmediately(Vertx vertx, VertxTestContext tc) {
        RecordingRepo repo = new RecordingRepo();
        StatsCache statsCache = new StatsCache(new CaffeineCache<>("stats", 100, Duration.ofSeconds(30), true));
        RequestLogService svc = new RequestLogService(repo, vertx, statsCache, false, 0, 0L);

        UUID webhookId = UUID.randomUUID();
        svc.save(sample(webhookId), 100)
                .onComplete(tc.succeeding(v -> tc.verify(() -> {
                    assertEquals(1, repo.singleSaveCalls.get());
                    assertEquals(0, repo.batchCalls.get());
                    tc.completeNow();
                })));
    }

    private RequestLog sample(UUID webhookId) {
        return new RequestLog(
                UUID.randomUUID(), webhookId, Instant.now(),
                "POST", "/webhook/x", Map.of(), Map.of(),
                "{}", "application/json", "127.0.0.1",
                null, null, null);
    }

    private static final class RecordingRepo implements RequestLogRepository {
        final AtomicInteger singleSaveCalls = new AtomicInteger();
        final AtomicInteger batchCalls = new AtomicInteger();
        final AtomicInteger totalSaved = new AtomicInteger();
        final ConcurrentLinkedQueue<RequestLog> stored = new ConcurrentLinkedQueue<>();

        @Override
        public Future<RequestLog> save(RequestLog requestLog) {
            singleSaveCalls.incrementAndGet();
            totalSaved.incrementAndGet();
            stored.add(requestLog);
            return Future.succeededFuture(requestLog);
        }

        @Override
        public Future<Void> saveBatch(List<RequestLog> logs) {
            batchCalls.incrementAndGet();
            totalSaved.addAndGet(logs.size());
            stored.addAll(logs);
            return Future.succeededFuture();
        }

        @Override
        public Future<Page<RequestLog>> findByWebhookId(UUID webhookId, int page, int size) {
            return Future.succeededFuture(new Page<>(List.of(), page, size, 0L));
        }

        @Override
        public Future<Optional<RequestLog>> findByWebhookIdAndId(UUID webhookId, UUID requestId) {
            return Future.succeededFuture(Optional.empty());
        }

        @Override
        public Future<Long> deleteByWebhookId(UUID webhookId) {
            return Future.succeededFuture(0L);
        }

        @Override
        public Future<StatsResponse> getStats(UUID webhookId) {
            return Future.succeededFuture(new StatsResponse(0, 0, Map.of(), null));
        }

        @Override
        public Future<Void> trimToMaxCount(UUID webhookId, int maxCount) {
            return Future.succeededFuture();
        }
    }
}
