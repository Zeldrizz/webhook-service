package com.webhookservice.cache;

import com.webhookservice.model.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookCacheTest {

    private CaffeineCache<String, Webhook> bySlug;
    private CaffeineCache<UUID, Webhook> byId;
    private CaffeineCache<String, Boolean> negative;
    private WebhookCache cache;

    @BeforeEach
    void setUp() {
        bySlug = new CaffeineCache<>("bySlug", 100, Duration.ofMinutes(5), true);
        byId = new CaffeineCache<>("byId", 100, Duration.ofMinutes(5), true);
        negative = new CaffeineCache<>("negative", 100, Duration.ofSeconds(30), true);
        cache = new WebhookCache(bySlug, byId, negative);
    }

    @Test
    void putWebhook_populatesBothPositiveIndexes() {
        Webhook w = sample();
        cache.putWebhook(w);
        assertTrue(cache.getBySlug(w.slug()).isPresent());
        assertTrue(cache.getById(w.id()).isPresent());
    }

    @Test
    void putWebhook_clearsNegativeEntryForSameSlug() {
        Webhook w = sample();
        cache.putNegativeSlug(w.slug());
        assertTrue(cache.isKnownNegative(w.slug()));
        cache.putWebhook(w);
        assertFalse(cache.isKnownNegative(w.slug()));
    }

    @Test
    void putNegativeSlug_isKnownNegativeReturnsTrue() {
        cache.putNegativeSlug("ghost");
        assertTrue(cache.isKnownNegative("ghost"));
    }

    @Test
    void invalidate_clearsBothIndexesAndNegative() {
        Webhook w = sample();
        cache.putWebhook(w);
        cache.putNegativeSlug("other");
        cache.invalidate(w.id(), w.slug());
        assertFalse(cache.getBySlug(w.slug()).isPresent());
        assertFalse(cache.getById(w.id()).isPresent());
        assertTrue(cache.isKnownNegative("other"), "unrelated negative entry stays");
    }

    @Test
    void getBySlug_nullSlug_returnsEmpty() {
        assertFalse(cache.getBySlug(null).isPresent());
        assertFalse(cache.isKnownNegative(null));
    }

    @Test
    void putWebhook_nullSafe() {
        cache.putWebhook(null);
        assertEquals(0, bySlug.size());
    }

    private Webhook sample() {
        Instant now = Instant.now();
        return new Webhook(UUID.randomUUID(), "Name", "slug-1", "d",
                "GET,POST", true, true, null, Map.of(),
                null, null, 100, now, now);
    }
}
