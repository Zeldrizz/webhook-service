package com.webhookservice.metrics;

import com.webhookservice.cache.CacheManager;
import com.webhookservice.cache.CacheNames;
import com.webhookservice.cache.CachePerCacheMetrics;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.vertx.circuitbreaker.CircuitBreaker;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Business and infrastructure metrics for the webhook service.
 *
 * Registered metric names (Prometheus format after Micrometer naming conversion):
 *   webhook_requests_total{slug, method, status}
 *   webhook_request_duration_seconds{slug, method, status}
 *   webhook_proxy_requests_total{slug, upstream_status}
 *   webhook_proxy_duration_seconds{slug}
 *   webhook_proxy_retries_total{slug}
 *   webhook_circuit_breaker_state{name}          0=CLOSED 1=OPEN 2=HALF_OPEN
 *   webhook_batch_queue_size
 *   webhook_batch_flush_total{result}
 *   webhook_batch_flush_duration_seconds
 *   webhook_batch_size_{count,sum}
 *   webhook_cache_hits_total{cache}
 *   webhook_cache_misses_total{cache}
 *   webhook_cache_evictions_total{cache}
 *   webhook_cache_size{cache}
 */
public class WebhookMetrics {

    private final MeterRegistry registry;

    public WebhookMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Record one completed webhook receive request (all exit paths). */
    public void recordReceive(String slug, String method, int statusCode, long durationMs) {
        Tags tags = Tags.of("slug", slug, "method", method, "status", String.valueOf(statusCode));
        registry.counter("webhook.requests", tags).increment();
        registry.timer("webhook.request.duration", tags).record(durationMs, TimeUnit.MILLISECONDS);
    }

    /** Record one completed proxy call (called from ProxyService.forward). */
    public void recordProxy(String slug, int upstreamStatus, long durationMs) {
        registry.counter("webhook.proxy.requests",
                Tags.of("slug", slug, "upstream_status", String.valueOf(upstreamStatus))).increment();
        registry.timer("webhook.proxy.duration",
                Tags.of("slug", slug)).record(durationMs, TimeUnit.MILLISECONDS);
    }

    /** Increment retry counter for a single retry attempt. */
    public void recordProxyRetry(String slug) {
        registry.counter("webhook.proxy.retries", Tags.of("slug", slug)).increment();
    }

    /**
     * Register a Gauge tracking the circuit breaker state.
     * Value: 0=CLOSED (healthy), 1=OPEN (tripping), 2=HALF_OPEN (probing).
     * Call once after circuit breaker is created.
     */
    public void registerCircuitBreaker(CircuitBreaker cb) {
        Gauge.builder("webhook.circuit.breaker.state", cb, breaker -> switch (breaker.state()) {
                    case CLOSED -> 0.0;
                    case OPEN -> 1.0;
                    case HALF_OPEN -> 2.0;
                })
                .tag("name", cb.name())
                .description("Circuit breaker state: 0=CLOSED, 1=OPEN, 2=HALF_OPEN")
                .register(registry);
    }

    /**
     * Register a Gauge that tracks the pending batch queue depth.
     * Call once during service initialisation; registry holds a weak reference.
     */
    public void registerBatchQueue(AtomicInteger pendingSize) {
        Gauge.builder("webhook.batch.queue.size", pendingSize, AtomicInteger::get)
                .description("Pending request logs waiting to be flushed to the database")
                .register(registry);
    }

    /** Record one batch flush operation (success or failure). */
    public void recordBatchFlush(int batchSize, long durationMs, boolean success) {
        registry.counter("webhook.batch.flush",
                Tags.of("result", success ? "success" : "failure")).increment();
        if (batchSize > 0) {
            registry.summary("webhook.batch.size").record(batchSize);
        }
        if (success) {
            registry.timer("webhook.batch.flush.duration").record(durationMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Register FunctionCounters + Gauges polling Caffeine stats for all 5 caches.
     * Micrometer tracks the monotonically-increasing Caffeine counters and exposes
     * per-scrape deltas as Prometheus rates.
     */
    public void registerCacheMetrics(CacheManager cacheManager) {
        String[] names = {
                CacheNames.WEBHOOK_BY_SLUG,
                CacheNames.WEBHOOK_BY_ID,
                CacheNames.NEGATIVE_SLUG,
                CacheNames.STATS,
                CacheNames.COMPILED_TEMPLATE
        };

        for (String cacheName : names) {
            Tags tags = Tags.of("cache", cacheName);

            FunctionCounter.builder("webhook.cache.hits", cacheManager,
                            cm -> safeMetric(cm, cacheName, CachePerCacheMetrics::hitCount))
                    .tags(tags)
                    .description("Caffeine cache hit count (cumulative)")
                    .register(registry);

            FunctionCounter.builder("webhook.cache.misses", cacheManager,
                            cm -> safeMetric(cm, cacheName, CachePerCacheMetrics::missCount))
                    .tags(tags)
                    .description("Caffeine cache miss count (cumulative)")
                    .register(registry);

            FunctionCounter.builder("webhook.cache.evictions", cacheManager,
                            cm -> safeMetric(cm, cacheName, CachePerCacheMetrics::evictionCount))
                    .tags(tags)
                    .description("Caffeine cache eviction count (cumulative)")
                    .register(registry);

            Gauge.builder("webhook.cache.size", cacheManager,
                            cm -> safeMetric(cm, cacheName, CachePerCacheMetrics::size))
                    .tags(tags)
                    .description("Caffeine cache estimated current size")
                    .register(registry);
        }
    }

    private static double safeMetric(CacheManager cm, String name,
                                     java.util.function.ToLongFunction<CachePerCacheMetrics> fn) {
        CachePerCacheMetrics m = cm.snapshot().caches().get(name);
        return m != null ? (double) fn.applyAsLong(m) : 0d;
    }
}
