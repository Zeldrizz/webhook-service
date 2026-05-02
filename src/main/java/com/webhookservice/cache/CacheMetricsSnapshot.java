package com.webhookservice.cache;

import java.util.Map;

/** Top-level payload for {@code GET /api/cache/stats}. */
public record CacheMetricsSnapshot(
        boolean enabled,
        Map<String, CachePerCacheMetrics> caches
) {
}
