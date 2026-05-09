package com.webhookservice.service;

import com.webhookservice.cache.CacheManager;
import com.webhookservice.config.AppConfig;
import com.webhookservice.model.Webhook;
import com.webhookservice.model.dto.UpdateWebhookDto;
import com.webhookservice.repository.WebhookRepository;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Cache-aside read path in {@link WebhookService}: mocked repo + real {@link CacheManager} on EventBus. */
@ExtendWith({VertxExtension.class, MockitoExtension.class})
class WebhookServiceCacheTest {

    @Mock
    WebhookRepository repository;

    private static AppConfig defaultConfig() {
        return new AppConfig(
                8080, "jdbc:postgresql://localhost:5432/webhooks", "u", "p",
                10, 256, 30000L,
                10000L, 0,
                100L, 5000L, 2.0, true, true,
                false, 5, 3000L, 10000L,
                false, "test-key",
                100, 24,
                true,
                100L, 60L, 30L,
                100L, 30L,
                100L, 1800L,
                false, 100, 100L
        );
    }

    @Test
    void getBySlug_secondCallHitsCache(Vertx vertx, VertxTestContext tc) {
        CacheManager cm = new CacheManager(vertx, defaultConfig());
        cm.installEventBusConsumer(vertx.eventBus());
        WebhookService service = new WebhookService(repository, cm);
        Webhook w = sample();

        AtomicInteger dbCalls = new AtomicInteger();
        when(repository.findBySlug("slug-1")).thenAnswer(inv -> {
            dbCalls.incrementAndGet();
            return Future.succeededFuture(Optional.of(w));
        });

        service.getBySlug("slug-1")
                .compose(found -> {
                    assertNotNull(found);
                    return service.getBySlug("slug-1");
                })
                .onComplete(tc.succeeding(found2 -> tc.verify(() -> {
                    assertNotNull(found2);
                    assertEquals(1, dbCalls.get(), "second lookup must be served from cache");
                    tc.completeNow();
                })));
    }

    @Test
    void update_invalidatesCacheForSlug(Vertx vertx, VertxTestContext tc) {
        CacheManager cm = new CacheManager(vertx, defaultConfig());
        cm.installEventBusConsumer(vertx.eventBus());
        WebhookService service = new WebhookService(repository, cm);
        Webhook before = sample();
        Webhook after = new Webhook(before.id(), "Renamed", before.slug(), before.description(),
                before.methods(), before.isActive(), before.debugMode(), before.proxyUrl(),
                before.proxyHeaders(), before.requestTemplate(), before.responseTemplate(),
                before.maxLogCount(), before.createdAt(), Instant.now());

        AtomicInteger dbLookups = new AtomicInteger();
        when(repository.findBySlug("slug-1")).thenAnswer(inv -> {
            dbLookups.incrementAndGet();
            return Future.succeededFuture(Optional.of(before));
        });
        when(repository.update(eq(before.id()), org.mockito.ArgumentMatchers.<UnaryOperator<Webhook>>any()))
                .thenReturn(Future.succeededFuture(after));

        service.getBySlug("slug-1")
                .compose(v -> service.update(before.id(), new UpdateWebhookDto(
                        "Renamed", null, null, null, null, null, null, null, null)))
                // Give the EventBus a tick to deliver the invalidation event.
                .compose(v -> {
                    io.vertx.core.Promise<Void> p = io.vertx.core.Promise.promise();
                    vertx.setTimer(40, id -> p.complete());
                    return p.future();
                })
                .compose(v -> service.getBySlug("slug-1"))
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertEquals(2, dbLookups.get(),
                            "post-update lookup must hit DB after cache invalidation");
                    tc.completeNow();
                })));
    }

    @Test
    void getBySlug_negativeCacheAvoidsDbOnSecondLookup(Vertx vertx, VertxTestContext tc) {
        CacheManager cm = new CacheManager(vertx, defaultConfig());
        cm.installEventBusConsumer(vertx.eventBus());
        WebhookService service = new WebhookService(repository, cm);

        AtomicInteger dbCalls = new AtomicInteger();
        when(repository.findBySlug("ghost")).thenAnswer(inv -> {
            dbCalls.incrementAndGet();
            return Future.succeededFuture(Optional.empty());
        });

        service.getBySlug("ghost")
                .compose(v -> service.getBySlug("ghost"))
                .compose(v -> service.getBySlug("ghost"))
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertEquals(1, dbCalls.get(), "negative cache must absorb subsequent lookups");
                    tc.completeNow();
                })));
    }

    private Webhook sample() {
        Instant now = Instant.now();
        return new Webhook(UUID.randomUUID(), "Name", "slug-1", "d",
                "GET,POST", true, true, null, Map.of(),
                null, null, 100, now, now);
    }
}
