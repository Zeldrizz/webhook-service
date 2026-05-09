package com.webhookservice.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 16-thread stress on {@link CaffeineCache} — no exceptions, no lost counter updates. */
class CacheConcurrencyTest {

    @Test
    void concurrentReadersAndWriters_doNotCorruptCacheOrCounters() throws InterruptedException {
        CaffeineCache<String, String> cache = new CaffeineCache<>("c", 1024, Duration.ofMinutes(1), true);
        int threads = 16;
        int iterations = 2000;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger errors = new AtomicInteger(0);
        AtomicInteger ops = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        String key = "k" + (i % 64);
                        if ((tid + i) % 3 == 0) {
                            cache.put(key, "v" + i);
                        } else if ((tid + i) % 7 == 0) {
                            cache.invalidate(key);
                        } else {
                            cache.getIfPresent(key);
                        }
                        ops.incrementAndGet();
                    }
                } catch (Throwable err) {
                    errors.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS), "concurrent run did not finish in time");
        pool.shutdownNow();

        assertEquals(0, errors.get(), "no operation should throw");
        assertEquals(threads * iterations, ops.get());

        CachePerCacheMetrics snap = cache.snapshot();
        long totalReads = snap.hitCount() + snap.missCount();
        assertTrue(totalReads > 0, "expected some reads to be counted");
    }
}
