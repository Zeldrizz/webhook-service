package com.webhookservice.cache;

/** Per-cache metrics surfaced by {@code GET /api/cache/stats}. */
public record CachePerCacheMetrics(
        long size,
        long hitCount,
        long missCount,
        double hitRatio,
        long evictionCount
) {
    public static CachePerCacheMetrics empty() {
        return new CachePerCacheMetrics(0L, 0L, 0L, 0d, 0L);
    }
}
