package com.webhookservice.cache;

import com.webhookservice.model.dto.StatsResponse;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class StatsCacheTest {

    private CaffeineCache<UUID, StatsResponse> backing;
    private StatsCache cache;

    @BeforeEach
    void setUp() {
        backing = new CaffeineCache<>("stats", 100, Duration.ofSeconds(30), true);
        cache = new StatsCache(backing);
    }

    @Test
    void getOrLoad_missThenHit_callsLoaderOnce() {
        UUID id = UUID.randomUUID();
        int[] loads = {0};
        StatsResponse expected = new StatsResponse(5, 1, Map.of("GET", 5L), Instant.now());

        Future<StatsResponse> first = cache.getOrLoad(id, () -> {
            loads[0]++;
            return Future.succeededFuture(expected);
        });
        assertSame(expected, first.result());

        Future<StatsResponse> second = cache.getOrLoad(id, () -> {
            loads[0]++;
            return Future.succeededFuture(expected);
        });
        assertSame(expected, second.result());
        assertEquals(1, loads[0]);
    }

    @Test
    void getOrLoad_failedFuture_doesNotCache() {
        UUID id = UUID.randomUUID();
        Future<StatsResponse> failed = cache.getOrLoad(id, () -> Future.failedFuture(new IllegalStateException("boom")));
        assertEquals(true, failed.failed());
        // Next call should retry, not return cached failure.
        int[] retries = {0};
        cache.getOrLoad(id, () -> {
            retries[0]++;
            return Future.succeededFuture(new StatsResponse(0, 0, Map.of(), null));
        });
        assertEquals(1, retries[0]);
    }

    @Test
    void invalidate_dropsCachedEntry() {
        UUID id = UUID.randomUUID();
        StatsResponse v = new StatsResponse(1, 1, Map.of(), null);
        cache.getOrLoad(id, () -> Future.succeededFuture(v));

        cache.invalidate(id);

        int[] loads = {0};
        cache.getOrLoad(id, () -> {
            loads[0]++;
            return Future.succeededFuture(v);
        });
        assertEquals(1, loads[0]);
    }
}
