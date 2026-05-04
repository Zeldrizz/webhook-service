package com.webhookservice.cache;

import com.webhookservice.model.dto.StatsResponse;
import io.vertx.core.Future;

import java.util.UUID;
import java.util.function.Supplier;

/** Short-TTL cache for {@code GET /api/webhooks/:id/stats} responses. Invalidated on save/clear/update. */
public final class StatsCache {

    private final CaffeineCache<UUID, StatsResponse> cache;

    public StatsCache(CaffeineCache<UUID, StatsResponse> cache) {
        this.cache = cache;
    }

    /** Cache-aside read; failed Futures from {@code loader} are not cached. */
    public Future<StatsResponse> getOrLoad(UUID webhookId, Supplier<Future<StatsResponse>> loader) {
        if (webhookId == null) {
            return loader.get();
        }
        return cache.getIfPresent(webhookId)
                .map(Future::succeededFuture)
                .orElseGet(() -> loader.get().onSuccess(stats -> {
                    if (stats != null) {
                        cache.put(webhookId, stats);
                    }
                }));
    }

    public void invalidate(UUID webhookId) {
        cache.invalidate(webhookId);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }
}
