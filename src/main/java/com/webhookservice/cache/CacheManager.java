package com.webhookservice.cache;

import com.webhookservice.config.AppConfig;
import com.webhookservice.model.Webhook;
import com.webhookservice.model.dto.StatsResponse;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Facade for per-verticle Caffeine caches; uses Vert.x EventBus pub/sub for cross-verticle invalidation. */
public final class CacheManager {

    private static final Logger log = LoggerFactory.getLogger(CacheManager.class);

    private final boolean enabled;
    private final Vertx vertx;
    private final CaffeineCache<String, Webhook> webhookBySlug;
    private final CaffeineCache<UUID, Webhook> webhookById;
    private final CaffeineCache<String, Boolean> negativeSlug;
    private final CaffeineCache<UUID, StatsResponse> stats;
    private final WebhookCache webhookCache;
    private final StatsCache statsCache;

    private io.vertx.core.eventbus.MessageConsumer<CacheInvalidationEvent> consumer;

    public CacheManager(Vertx vertx, AppConfig config) {
        this.vertx = vertx;
        this.enabled = config.cacheEnabled();
        this.webhookBySlug = new CaffeineCache<>(
                CacheNames.WEBHOOK_BY_SLUG,
                config.cacheWebhookMaxSize(),
                Duration.ofSeconds(config.cacheWebhookTtlSeconds()),
                enabled
        );
        this.webhookById = new CaffeineCache<>(
                CacheNames.WEBHOOK_BY_ID,
                config.cacheWebhookMaxSize(),
                Duration.ofSeconds(config.cacheWebhookTtlSeconds()),
                enabled
        );
        this.negativeSlug = new CaffeineCache<>(
                CacheNames.NEGATIVE_SLUG,
                config.cacheWebhookMaxSize(),
                Duration.ofSeconds(config.cacheNegativeTtlSeconds()),
                enabled
        );
        this.stats = new CaffeineCache<>(
                CacheNames.STATS,
                config.cacheStatsMaxSize(),
                Duration.ofSeconds(config.cacheStatsTtlSeconds()),
                enabled
        );
        this.webhookCache = new WebhookCache(webhookBySlug, webhookById, negativeSlug);
        this.statsCache = new StatsCache(stats);
        log.info("CacheManager initialized: enabled={}, webhookMaxSize={}, ttl={}s, negativeTtl={}s, statsTtl={}s",
                enabled, config.cacheWebhookMaxSize(), config.cacheWebhookTtlSeconds(),
                config.cacheNegativeTtlSeconds(), config.cacheStatsTtlSeconds());
    }

    /** Register codec and subscribe to invalidation events. Call once per verticle. */
    public void installEventBusConsumer(EventBus eventBus) {
        registerCodecIfAbsent(eventBus);
        this.consumer = eventBus.consumer(CacheNames.EVENT_BUS_INVALIDATE_ADDRESS, this::onInvalidationMessage);
        log.debug("CacheManager consumer registered on '{}'", CacheNames.EVENT_BUS_INVALIDATE_ADDRESS);
    }

    private void registerCodecIfAbsent(EventBus eventBus) {
        try {
            eventBus.registerDefaultCodec(CacheInvalidationEvent.class, new CacheInvalidationCodec());
        } catch (IllegalStateException alreadyRegistered) {
            // Codec already registered for this Vertx instance — fine, ignore.
        }
    }

    private void onInvalidationMessage(Message<CacheInvalidationEvent> message) {
        CacheInvalidationEvent event = message.body();
        if (event == null) {
            return;
        }
        applyInvalidation(event);
    }

    /** Apply event to local caches; exposed so tests can skip EventBus setup. */
    public void applyInvalidation(CacheInvalidationEvent event) {
        switch (event.kind()) {
            case UPSERT -> {
                if (event.id() != null) {
                    webhookById.invalidate(event.id());
                    stats.invalidate(event.id());
                }
                if (event.slug() != null) {
                    webhookBySlug.invalidate(event.slug());
                    negativeSlug.invalidate(event.slug());
                }
            }
            case DELETE -> {
                if (event.id() != null) {
                    webhookById.invalidate(event.id());
                    stats.invalidate(event.id());
                }
                if (event.slug() != null) {
                    webhookBySlug.invalidate(event.slug());
                }
            }
            case FLUSH_ALL -> flushLocal();
        }
    }

    public void publishUpsert(UUID id, String slug) {
        publish(CacheInvalidationEvent.upsert(id, slug));
    }

    public void publishDelete(UUID id, String slug) {
        publish(CacheInvalidationEvent.delete(id, slug));
    }

    /** Flush all caches everywhere — admin endpoint {@code POST /api/cache/flush}. */
    public void flushAllCluster() {
        publish(CacheInvalidationEvent.flushAll());
        flushLocal();
    }

    private void publish(CacheInvalidationEvent event) {
        if (vertx == null) {
            applyInvalidation(event);
            return;
        }
        vertx.eventBus().publish(
                CacheNames.EVENT_BUS_INVALIDATE_ADDRESS,
                event,
                new DeliveryOptions().setCodecName(CacheInvalidationCodec.NAME)
        );
    }

    private void flushLocal() {
        webhookBySlug.invalidateAll();
        webhookById.invalidateAll();
        negativeSlug.invalidateAll();
        stats.invalidateAll();
    }

    public WebhookCache webhookCache() {
        return webhookCache;
    }

    public StatsCache statsCache() {
        return statsCache;
    }

    public boolean enabled() {
        return enabled;
    }

    public CacheMetricsSnapshot snapshot() {
        Map<String, CachePerCacheMetrics> caches = new LinkedHashMap<>();
        caches.put(CacheNames.WEBHOOK_BY_SLUG, webhookBySlug.snapshot());
        caches.put(CacheNames.WEBHOOK_BY_ID, webhookById.snapshot());
        caches.put(CacheNames.NEGATIVE_SLUG, negativeSlug.snapshot());
        caches.put(CacheNames.STATS, stats.snapshot());
        return new CacheMetricsSnapshot(enabled, caches);
    }

    public void close() {
        if (consumer != null) {
            consumer.unregister();
            consumer = null;
        }
    }
}
