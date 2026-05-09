package com.webhookservice.cache;

import com.webhookservice.config.AppConfig;
import com.webhookservice.model.Webhook;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
class CacheManagerTest {

    private static AppConfig defaultConfig(boolean enabled) {
        return new AppConfig(
                8080,
                "jdbc:postgresql://localhost:5432/webhooks",
                "u", "p",
                10, 256, 30000L,
                10000L, 0,
                100L, 5000L, 2.0, true, true,
                false, 5, 3000L, 10000L,
                false, "test-key",
                100, 24,
                enabled,
                100L, 60L, 30L,
                100L, 30L,
                100L, 1800L,
                false, 100, 100L
        );
    }

    @Test
    void snapshot_listsAllRegisteredCaches(Vertx vertx) {
        CacheManager mgr = new CacheManager(vertx, defaultConfig(true));
        CacheMetricsSnapshot snap = mgr.snapshot();
        assertTrue(snap.enabled());
        assertTrue(snap.caches().containsKey(CacheNames.WEBHOOK_BY_SLUG));
        assertTrue(snap.caches().containsKey(CacheNames.WEBHOOK_BY_ID));
        assertTrue(snap.caches().containsKey(CacheNames.NEGATIVE_SLUG));
        assertTrue(snap.caches().containsKey(CacheNames.STATS));
    }

    @Test
    void disabledMode_snapshotReportsZerosAndCachesAreNoOp(Vertx vertx) {
        CacheManager mgr = new CacheManager(vertx, defaultConfig(false));
        Webhook w = sample();
        mgr.webhookCache().putWebhook(w);
        assertFalse(mgr.webhookCache().getBySlug(w.slug()).isPresent());
        CacheMetricsSnapshot snap = mgr.snapshot();
        assertFalse(snap.enabled());
        assertEquals(0L, snap.caches().get(CacheNames.WEBHOOK_BY_SLUG).hitCount());
    }

    @Test
    void applyInvalidation_upsert_clearsAllIndexes(Vertx vertx) {
        CacheManager mgr = new CacheManager(vertx, defaultConfig(true));
        Webhook w = sample();
        mgr.webhookCache().putWebhook(w);
        mgr.webhookCache().putNegativeSlug("other-slug");

        mgr.applyInvalidation(CacheInvalidationEvent.upsert(w.id(), w.slug()));

        assertFalse(mgr.webhookCache().getBySlug(w.slug()).isPresent());
        assertFalse(mgr.webhookCache().getById(w.id()).isPresent());
        // Unrelated negative remains:
        assertTrue(mgr.webhookCache().isKnownNegative("other-slug"));
    }

    @Test
    void applyInvalidation_delete_clearsPositiveButLeavesUnrelatedNegative(Vertx vertx) {
        CacheManager mgr = new CacheManager(vertx, defaultConfig(true));
        Webhook w = sample();
        mgr.webhookCache().putWebhook(w);
        mgr.webhookCache().putNegativeSlug("ghost");

        mgr.applyInvalidation(CacheInvalidationEvent.delete(w.id(), w.slug()));

        assertFalse(mgr.webhookCache().getBySlug(w.slug()).isPresent());
        assertTrue(mgr.webhookCache().isKnownNegative("ghost"));
    }

    @Test
    void applyInvalidation_flushAll_clearsEverything(Vertx vertx) {
        CacheManager mgr = new CacheManager(vertx, defaultConfig(true));
        Webhook w = sample();
        mgr.webhookCache().putWebhook(w);
        mgr.webhookCache().putNegativeSlug("ghost");

        mgr.applyInvalidation(CacheInvalidationEvent.flushAll());

        assertFalse(mgr.webhookCache().getBySlug(w.slug()).isPresent());
        assertFalse(mgr.webhookCache().getById(w.id()).isPresent());
        assertFalse(mgr.webhookCache().isKnownNegative("ghost"));
    }

    @Test
    void installEventBusConsumer_publishedEventReachesConsumer(Vertx vertx, VertxTestContext tc) {
        CacheManager mgr = new CacheManager(vertx, defaultConfig(true));
        mgr.installEventBusConsumer(vertx.eventBus());

        Webhook w = sample();
        mgr.webhookCache().putWebhook(w);
        assertTrue(mgr.webhookCache().getBySlug(w.slug()).isPresent());

        mgr.publishDelete(w.id(), w.slug());

        vertx.setTimer(50, id -> tc.verify(() -> {
            assertFalse(mgr.webhookCache().getBySlug(w.slug()).isPresent());
            tc.completeNow();
        }));
    }

    private Webhook sample() {
        Instant now = Instant.now();
        return new Webhook(UUID.randomUUID(), "Name", "slug-x", "d",
                "GET,POST", true, true, null, Map.of(),
                null, null, 100, now, now);
    }
}
