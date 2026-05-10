package com.webhookservice.service;

import com.webhookservice.cache.StatsCache;
import com.webhookservice.model.RequestLog;
import com.webhookservice.model.dto.Page;
import com.webhookservice.model.dto.StatsResponse;
import com.webhookservice.repository.RequestLogRepository;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service facade for {@code request_logs}: direct typed repository calls
 * (no reflection), optional batched inserts ({@link #save} queues + periodic
 * flush via {@link RequestLogRepository#saveBatch}), short-TTL {@link StatsCache}.
 */
public class RequestLogService {

    private static final Logger log = LoggerFactory.getLogger(RequestLogService.class);

    private final RequestLogRepository requestLogRepository;
    private final Vertx vertx;
    private final StatsCache statsCache;
    private final boolean batchEnabled;
    private final int batchMaxSize;
    private final long batchFlushMs;

    private final Queue<RequestLog> pending;
    private final Map<UUID, Integer> pendingMaxLogCount;
    private final AtomicInteger pendingSize;
    private final Object trimLock = new Object();
    private volatile long flushTimerId = -1L;

    /** Legacy ctor: batching + caching off. */
    public RequestLogService(RequestLogRepository requestLogRepository) {
        this(requestLogRepository, null, null, false, 0, 0L);
    }

    public RequestLogService(
            RequestLogRepository requestLogRepository,
            Vertx vertx,
            StatsCache statsCache,
            boolean batchEnabled,
            int batchMaxSize,
            long batchFlushMs
    ) {
        this.requestLogRepository = Objects.requireNonNull(requestLogRepository, "requestLogRepository");
        this.vertx = vertx;
        this.statsCache = statsCache;
        this.batchEnabled = batchEnabled && vertx != null && batchMaxSize > 0 && batchFlushMs > 0;
        this.batchMaxSize = batchMaxSize;
        this.batchFlushMs = batchFlushMs;
        this.pending = new ConcurrentLinkedQueue<>();
        this.pendingMaxLogCount = new HashMap<>();
        this.pendingSize = new AtomicInteger(0);
        if (this.batchEnabled) {
            startFlushTimer();
        }
    }

    /**
     * Save a log. With batching enabled the Future completes on enqueue, not on persist
     * — the actual INSERT happens on the next periodic flush. Stats cache is invalidated immediately.
     */
    public Future<RequestLog> save(RequestLog requestLog, int maxLogCount) {
        if (requestLog == null) {
            return Future.failedFuture(new IllegalArgumentException("requestLog is null"));
        }
        invalidateStats(requestLog.webhookId());
        if (batchEnabled) {
            pending.offer(requestLog);
            recordMaxLogCount(requestLog.webhookId(), maxLogCount);
            int currentSize = pendingSize.incrementAndGet();
            if (currentSize >= batchMaxSize) {
                triggerFlush();
            }
            return Future.succeededFuture(requestLog);
        }
        return requestLogRepository.save(requestLog)
                .compose(saved -> requestLogRepository.trimToMaxCount(requestLog.webhookId(), maxLogCount)
                        .map(v -> saved));
    }

    public Future<Page<RequestLog>> listByWebhookId(UUID webhookId, int page, int size) {
        return requestLogRepository.findByWebhookId(webhookId, page, size);
    }

    public Future<RequestLog> getByWebhookIdAndId(UUID webhookId, UUID requestId) {
        return requestLogRepository.findByWebhookIdAndId(webhookId, requestId)
                .map(opt -> opt.orElse(null));
    }

    public Future<Long> clearByWebhookId(UUID webhookId) {
        return requestLogRepository.deleteByWebhookId(webhookId)
                .onSuccess(v -> invalidateStats(webhookId));
    }

    public Future<StatsResponse> getStats(UUID webhookId) {
        if (statsCache != null) {
            return statsCache.getOrLoad(webhookId, () -> requestLogRepository.getStats(webhookId));
        }
        return requestLogRepository.getStats(webhookId);
    }

    /** Manually flush the pending queue. Used by tests; production relies on the timer/size trigger. */
    public Future<Void> flushPending() {
        return drainAndPersist();
    }

    /** Stop the periodic flush timer and persist any remaining logs. */
    public void close() {
        if (flushTimerId > 0 && vertx != null) {
            vertx.cancelTimer(flushTimerId);
            flushTimerId = -1L;
        }
        if (batchEnabled && !pending.isEmpty()) {
            drainAndPersist();
        }
    }

    private void startFlushTimer() {
        flushTimerId = vertx.setPeriodic(batchFlushMs, id -> {
            if (!pending.isEmpty()) {
                drainAndPersist();
            }
        });
    }

    private void triggerFlush() {
        if (vertx != null) {
            vertx.runOnContext(v -> drainAndPersist());
        } else {
            drainAndPersist();
        }
    }

    private void recordMaxLogCount(UUID webhookId, int maxLogCount) {
        synchronized (trimLock) {
            Integer existing = pendingMaxLogCount.get(webhookId);
            if (existing == null || maxLogCount < existing) {
                pendingMaxLogCount.put(webhookId, maxLogCount);
            }
        }
    }

    private Future<Void> drainAndPersist() {
        List<RequestLog> batch = drainAll();
        if (batch.isEmpty()) {
            return Future.succeededFuture();
        }
        Map<UUID, Integer> trimDirectives = snapshotTrimDirectives();

        return requestLogRepository.saveBatch(batch)
                .compose(ignored -> trimAffected(trimDirectives))
                .onSuccess(v -> log.debug("Flushed {} pending request logs", batch.size()))
                .onFailure(err -> log.warn("Batch flush of {} logs failed: {}", batch.size(), err.getMessage()));
    }

    private List<RequestLog> drainAll() {
        List<RequestLog> batch = new ArrayList<>();
        RequestLog item;
        while ((item = pending.poll()) != null) {
            batch.add(item);
        }
        pendingSize.addAndGet(-batch.size());
        return batch;
    }

    private Map<UUID, Integer> snapshotTrimDirectives() {
        synchronized (trimLock) {
            Map<UUID, Integer> copy = new HashMap<>(pendingMaxLogCount);
            pendingMaxLogCount.clear();
            return copy;
        }
    }

    private Future<Void> trimAffected(Map<UUID, Integer> trimDirectives) {
        if (trimDirectives.isEmpty()) {
            return Future.succeededFuture();
        }
        Set<UUID> seen = new HashSet<>();
        Future<Void> chain = Future.succeededFuture();
        for (Map.Entry<UUID, Integer> entry : trimDirectives.entrySet()) {
            UUID webhookId = entry.getKey();
            int maxLogCount = entry.getValue();
            if (!seen.add(webhookId)) {
                continue;
            }
            chain = chain.compose(v -> requestLogRepository.trimToMaxCount(webhookId, maxLogCount));
        }
        return chain;
    }

    private void invalidateStats(UUID webhookId) {
        if (statsCache != null && webhookId != null) {
            statsCache.invalidate(webhookId);
        }
    }
}
