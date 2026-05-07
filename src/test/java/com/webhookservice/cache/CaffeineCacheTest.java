package com.webhookservice.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaffeineCacheTest {

    @Test
    void enabledCache_putThenGet_returnsValue() {
        CaffeineCache<String, String> cache = new CaffeineCache<>("t", 10, Duration.ofMinutes(1), true);
        cache.put("k", "v");
        Optional<String> result = cache.getIfPresent("k");
        assertTrue(result.isPresent());
        assertEquals("v", result.get());
    }

    @Test
    void enabledCache_missingKey_returnsEmpty() {
        CaffeineCache<String, String> cache = new CaffeineCache<>("t", 10, Duration.ofMinutes(1), true);
        assertTrue(cache.getIfPresent("absent").isEmpty());
    }

    @Test
    void enabledCache_invalidate_removesEntry() {
        CaffeineCache<String, String> cache = new CaffeineCache<>("t", 10, Duration.ofMinutes(1), true);
        cache.put("k", "v");
        cache.invalidate("k");
        assertTrue(cache.getIfPresent("k").isEmpty());
    }

    @Test
    void enabledCache_invalidateAll_emptiesCache() {
        CaffeineCache<String, String> cache = new CaffeineCache<>("t", 10, Duration.ofMinutes(1), true);
        cache.put("k1", "v1");
        cache.put("k2", "v2");
        cache.invalidateAll();
        assertEquals(0, cache.size());
    }

    @Test
    void disabledCache_putGet_isNoOp() {
        CaffeineCache<String, String> cache = new CaffeineCache<>("t", 10, Duration.ofMinutes(1), false);
        cache.put("k", "v");
        assertTrue(cache.getIfPresent("k").isEmpty());
        assertEquals(0, cache.size());
        // Snapshot must still produce a valid (zero) record so /api/cache/stats works in disabled mode.
        assertNotNull(cache.snapshot());
        assertFalse(cache.enabled());
    }

    @Test
    void enabledCache_getOrLoad_callsLoaderOnceForSameKey() {
        CaffeineCache<String, String> cache = new CaffeineCache<>("t", 10, Duration.ofMinutes(1), true);
        int[] loaderCalls = {0};
        String first = cache.getOrLoad("k", () -> { loaderCalls[0]++; return "computed"; });
        String second = cache.getOrLoad("k", () -> { loaderCalls[0]++; return "computed"; });
        assertEquals("computed", first);
        assertSame(first, second);
        assertEquals(1, loaderCalls[0]);
    }

    @Test
    void disabledCache_getOrLoad_callsLoaderEveryTime() {
        CaffeineCache<String, String> cache = new CaffeineCache<>("t", 10, Duration.ofMinutes(1), false);
        int[] loaderCalls = {0};
        cache.getOrLoad("k", () -> { loaderCalls[0]++; return "computed"; });
        cache.getOrLoad("k", () -> { loaderCalls[0]++; return "computed"; });
        assertEquals(2, loaderCalls[0]);
    }

    @Test
    void snapshot_recordsHitsAndMisses() {
        CaffeineCache<String, String> cache = new CaffeineCache<>("t", 10, Duration.ofMinutes(1), true);
        cache.getIfPresent("missing"); // miss
        cache.put("k", "v");
        cache.getIfPresent("k"); // hit
        cache.getIfPresent("k"); // hit
        CachePerCacheMetrics snapshot = cache.snapshot();
        assertEquals(2, snapshot.hitCount());
        assertEquals(1, snapshot.missCount());
        assertTrue(snapshot.hitRatio() > 0.6);
    }
}
