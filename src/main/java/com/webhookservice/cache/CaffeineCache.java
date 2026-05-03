package com.webhookservice.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Caffeine wrapper with a global enabled-toggle (no-op when off) and a uniform
 * {@link #snapshot()} for {@code /api/cache/stats}.
 */
public final class CaffeineCache<K, V> {

    private final String name;
    private final boolean enabled;
    private final Cache<K, V> cache;

    public CaffeineCache(String name, long maxSize, Duration ttl, boolean enabled) {
        this.name = name;
        this.enabled = enabled;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttl)
                .recordStats()
                .build();
    }

    public String name() {
        return name;
    }

    public boolean enabled() {
        return enabled;
    }

    public Optional<V> getIfPresent(K key) {
        if (!enabled || key == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    /** Atomic get-or-compute. When disabled, delegates to {@code loader} without caching. */
    public V getOrLoad(K key, Supplier<V> loader) {
        if (!enabled || key == null) {
            return loader.get();
        }
        return cache.get(key, k -> loader.get());
    }

    public void put(K key, V value) {
        if (!enabled || key == null || value == null) {
            return;
        }
        cache.put(key, value);
    }

    public void invalidate(K key) {
        if (!enabled || key == null) {
            return;
        }
        cache.invalidate(key);
    }

    public void invalidateAll() {
        if (!enabled) {
            return;
        }
        cache.invalidateAll();
    }

    public long size() {
        return enabled ? cache.estimatedSize() : 0L;
    }

    public CachePerCacheMetrics snapshot() {
        if (!enabled) {
            return CachePerCacheMetrics.empty();
        }
        CacheStats stats = cache.stats();
        long hits = stats.hitCount();
        long misses = stats.missCount();
        long total = hits + misses;
        double ratio = total == 0 ? 0d : (double) hits / (double) total;
        return new CachePerCacheMetrics(
                cache.estimatedSize(),
                hits,
                misses,
                ratio,
                stats.evictionCount()
        );
    }
}
