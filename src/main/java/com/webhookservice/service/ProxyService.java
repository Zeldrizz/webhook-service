package com.webhookservice.service;

import com.webhookservice.metrics.WebhookMetrics;
import com.webhookservice.model.RequestLog;
import com.webhookservice.model.Webhook;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.circuitbreaker.CircuitBreakerState;
import io.vertx.circuitbreaker.OpenCircuitException;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Async HTTP proxy: exponential-backoff retry (via {@link RetryPolicy}) inside a
 * per-webhook {@link CircuitBreaker}. Each proxy-enabled webhook gets its own breaker,
 * so a failing upstream only affects that webhook, not others.
 *
 * {@link #forward} always succeeds with a {@link RequestLog} —
 * failure modes surface as synthetic 502/503 entries, never as failed Futures.
 */
public class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    private final Vertx vertx;
    private final WebClient webClient;
    private final long timeoutMs;
    private final RetryPolicy retryPolicy;
    private final WebhookMetrics metrics;

    private final boolean cbEnabled;
    private final int cbMaxFailures;
    private final long cbResetMs;
    private final long cbTimeoutMs;

    private final ConcurrentHashMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();

    /** Legacy constructor for tests without retries, breaker, or metrics. */
    public ProxyService(Vertx vertx, long timeoutMs, int maxRetries) {
        this(vertx, timeoutMs,
                new RetryPolicy(maxRetries, 100L, 5000L, 2.0, true, true),
                false, 5, 3000L, timeoutMs, null);
    }

    /** Backward-compatible constructor for tests (no metrics). */
    public ProxyService(
            Vertx vertx,
            long timeoutMs,
            RetryPolicy retryPolicy,
            boolean circuitBreakerEnabled,
            int cbMaxFailures,
            long cbResetMs,
            long cbTimeoutMs
    ) {
        this(vertx, timeoutMs, retryPolicy, circuitBreakerEnabled,
                cbMaxFailures, cbResetMs, cbTimeoutMs, null);
    }

    public ProxyService(
            Vertx vertx,
            long timeoutMs,
            RetryPolicy retryPolicy,
            boolean circuitBreakerEnabled,
            int cbMaxFailures,
            long cbResetMs,
            long cbTimeoutMs,
            WebhookMetrics metrics
    ) {
        this.vertx = vertx;
        this.timeoutMs = timeoutMs;
        this.retryPolicy = retryPolicy;
        this.metrics = metrics;
        this.cbEnabled = circuitBreakerEnabled;
        this.cbMaxFailures = cbMaxFailures;
        this.cbResetMs = cbResetMs;
        this.cbTimeoutMs = cbTimeoutMs;

        int connectTimeout = timeoutMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) timeoutMs;
        int idleTimeout = timeoutMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) timeoutMs;
        this.webClient = WebClient.create(vertx, new WebClientOptions()
                .setConnectTimeout(connectTimeout)
                .setIdleTimeout(idleTimeout)
                .setMaxPoolSize(50)
                .setKeepAlive(true)
                .setDecompressionSupported(true));
    }

    private CircuitBreaker getOrCreateBreaker(Webhook webhook) {
        return breakers.computeIfAbsent(webhook.slug(), slug -> {
            CircuitBreaker cb = CircuitBreaker.create(slug, vertx,
                    new CircuitBreakerOptions()
                            .setMaxFailures(cbMaxFailures)
                            .setTimeout(cbTimeoutMs)
                            .setResetTimeout(cbResetMs)
                            .setFallbackOnFailure(false));
            cb.openHandler(v -> log.warn("Circuit OPENED: webhook={}", slug));
            cb.halfOpenHandler(v -> log.info("Circuit HALF-OPEN: webhook={}", slug));
            cb.closeHandler(v -> log.info("Circuit CLOSED: webhook={}", slug));
            if (metrics != null) metrics.registerCircuitBreaker(cb);
            return cb;
        });
    }

    public Future<RequestLog> forward(Webhook webhook, RequestLog requestLog) {
        if (webhook.proxyUrl() == null || webhook.proxyUrl().isBlank()) {
            return Future.succeededFuture(requestLog);
        }
        if (!cbEnabled) {
            return forwardWithRetry(webhook, requestLog, 0)
                    .recover(err -> recoverFromFailure(err, requestLog))
                    .map(result -> { recordProxyResult(webhook.slug(), result); return result; });
        }
        CircuitBreaker cb = getOrCreateBreaker(webhook);
        return cb.<RequestLog>execute(promise ->
                forwardWithRetry(webhook, requestLog, 0).onComplete(promise)
        ).recover(err -> {
            if (err instanceof OpenCircuitException) {
                log.warn("Proxy circuit OPEN — short-circuit for webhook={}", webhook.slug());
                return Future.succeededFuture(requestLog.withProxyResult(503, "Proxy circuit open", 0L));
            }
            return recoverFromFailure(err, requestLog);
        }).map(result -> { recordProxyResult(webhook.slug(), result); return result; });
    }

    /** Remove the circuit breaker for a deleted webhook. */
    public void removeBreaker(String slug) {
        CircuitBreaker cb = breakers.remove(slug);
        if (cb != null) cb.close();
    }

    public boolean isCircuitOpen(String slug) {
        CircuitBreaker cb = breakers.get(slug);
        return cb != null && cb.state() == CircuitBreakerState.OPEN;
    }

    private void recordProxyResult(String slug, RequestLog result) {
        if (metrics != null) {
            int status = result.responseStatus() != null ? result.responseStatus() : 0;
            long dur = result.proxyDurationMs() != null ? result.proxyDurationMs() : 0L;
            metrics.recordProxy(slug, status, dur);
        }
    }

    private Future<RequestLog> recoverFromFailure(Throwable err, RequestLog requestLog) {
        if (err instanceof ProxyFailure failure) {
            return Future.succeededFuture(requestLog.withProxyResult(502, failure.getMessage(), failure.durationMs));
        }
        return Future.succeededFuture(requestLog.withProxyResult(502, "Proxy error: " + err.getMessage(), 0L));
    }

    private Future<RequestLog> forwardWithRetry(Webhook webhook, RequestLog requestLog, int attempt) {
        long startTime = System.nanoTime();

        ProxyRequestBuilder requestBuilder = new ProxyRequestBuilder(webClient)
                .method(requestLog.method())
                .url(webhook.proxyUrl())
                .headers(webhook.proxyHeaders())
                .header("X-Webhook-Id", webhook.id().toString())
                .header("X-Webhook-Name", webhook.name());

        if (requestLog.contentType() != null && !requestLog.contentType().isBlank()
                && !hasHeader(webhook.proxyHeaders(), "Content-Type")) {
            requestBuilder.header("Content-Type", requestLog.contentType());
        }

        if (requestLog.sourceIp() != null && !requestLog.sourceIp().isBlank()) {
            requestBuilder.header("X-Forwarded-For", requestLog.sourceIp());
        }

        HttpRequest<Buffer> proxyRequest = requestBuilder.build();
        proxyRequest.timeout(timeoutMs);

        Future<HttpResponse<Buffer>> responseFuture;
        Buffer body = requestLog.body() != null ? Buffer.buffer(requestLog.body()) : null;
        if (body != null) {
            responseFuture = proxyRequest.sendBuffer(body);
        } else {
            responseFuture = proxyRequest.send();
        }

        return responseFuture
                .compose(response -> {
                    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                    int status = response.statusCode();
                    String responseBody = response.bodyAsString();
                    if (retryPolicy.shouldRetryStatus(status) && attempt < retryPolicy.maxRetries()) {
                        log.warn("Proxy attempt {}/{} got retry-worthy status {}: webhook={}, duration={}ms",
                                attempt + 1, retryPolicy.maxRetries() + 1, status, webhook.slug(), durationMs);
                        if (metrics != null) metrics.recordProxyRetry(webhook.slug());
                        return delayThen(retryPolicy.delayForAttempt(attempt),
                                () -> forwardWithRetry(webhook, requestLog, attempt + 1));
                    }
                    if (status >= 200) {
                        log.debug("Proxy response: status={}, duration={}ms, webhook={}, attempt={}",
                                status, durationMs, webhook.slug(), attempt + 1);
                    }
                    return Future.succeededFuture(requestLog.withProxyResult(status, responseBody, durationMs));
                })
                .recover(err -> {
                    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                    if (attempt < retryPolicy.maxRetries()) {
                        log.warn("Proxy attempt {}/{} failed: webhook={}, error={}, duration={}ms",
                                attempt + 1, retryPolicy.maxRetries() + 1, webhook.slug(), err.getMessage(), durationMs);
                        if (metrics != null) metrics.recordProxyRetry(webhook.slug());
                        return delayThen(retryPolicy.delayForAttempt(attempt),
                                () -> forwardWithRetry(webhook, requestLog, attempt + 1));
                    }
                    String errorMsg = classifyError(err);
                    log.warn("Proxy exhausted retries: webhook={}, error={}, duration={}ms",
                            webhook.slug(), errorMsg, durationMs);
                    return Future.failedFuture(new ProxyFailure(errorMsg, durationMs, requestLog));
                });
    }

    private <T> Future<T> delayThen(long delayMs, java.util.function.Supplier<Future<T>> task) {
        if (delayMs <= 0 || vertx == null) {
            return task.get();
        }
        Promise<T> promise = Promise.promise();
        vertx.setTimer(delayMs, id -> task.get().onComplete(promise));
        return promise.future();
    }

    private boolean hasHeader(java.util.Map<String, String> headers, String name) {
        if (headers == null || headers.isEmpty()) return false;
        return headers.keySet().stream().anyMatch(key -> key.equalsIgnoreCase(name));
    }

    private String classifyError(Throwable err) {
        String msg = err.getMessage();
        if (msg == null) msg = err.getClass().getSimpleName();
        if (err instanceof java.util.concurrent.TimeoutException || msg.contains("timeout")) {
            return "Proxy timeout: " + msg;
        }
        if (err instanceof java.net.ConnectException || msg.contains("Connection refused")) {
            return "Connection refused: " + msg;
        }
        return "Proxy error: " + msg;
    }

    /** Carries 502 details (message + total duration) through the circuit breaker. */
    private static final class ProxyFailure extends RuntimeException {
        private final long durationMs;

        ProxyFailure(String message, long durationMs, RequestLog ignored) {
            super(message);
            this.durationMs = durationMs;
        }
    }

    public void close() {
        webClient.close();
        breakers.values().forEach(CircuitBreaker::close);
        breakers.clear();
    }
}
