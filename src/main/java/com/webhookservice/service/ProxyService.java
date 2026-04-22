package com.webhookservice.service;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.Webhook;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Function;

public class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    private final WebClient webClient;
    private final long timeoutMs;
    private final int maxRetries;

    public ProxyService(Vertx vertx, long timeoutMs, int maxRetries) {
        this.timeoutMs = timeoutMs;
        this.maxRetries = maxRetries;
        int connectTimeout = timeoutMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) timeoutMs;
        int idleTimeout = timeoutMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) timeoutMs;
        this.webClient = WebClient.create(vertx, new WebClientOptions()
                .setConnectTimeout(connectTimeout)
                .setIdleTimeout(idleTimeout)
                .setMaxPoolSize(50)
                .setKeepAlive(true)
                .setDecompressionSupported(true));
    }

    public Future<RequestLog> forward(Webhook webhook, RequestLog requestLog) {
        return forwardWithRetry(webhook, requestLog, 0);
    }

    private Future<RequestLog> forwardWithRetry(Webhook webhook, RequestLog requestLog, int attempt) {
        if (webhook.proxyUrl() == null || webhook.proxyUrl().isBlank()) {
            return Future.succeededFuture(requestLog);
        }

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

        Function<Throwable, Future<RequestLog>> retryHandler = err -> {
            long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            if (attempt < maxRetries) {
                log.warn("Proxy attempt {}/{} failed: webhook={}, error={}, duration={}ms",
                        attempt + 1, maxRetries, webhook.slug(), err.getMessage(), durationMs);
                return forwardWithRetry(webhook, requestLog, attempt + 1);
            }
            String errorMsg = classifyError(err);
            log.warn("Proxy failed after {} attempts: webhook={}, error={}, duration={}ms",
                    maxRetries, webhook.slug(), errorMsg, durationMs);
            return Future.succeededFuture(requestLog.withProxyResult(502, errorMsg, durationMs));
        };

        return responseFuture
                .map(response -> {
                    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                    String responseBody = response.bodyAsString();
                    int status = response.statusCode();
                    log.debug("Proxy response: status={}, duration={}ms, webhook={}, attempt={}",
                            status, durationMs, webhook.slug(), attempt + 1);
                    return requestLog.withProxyResult(status, responseBody, durationMs);
                })
                .recover(retryHandler);
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

    public void close() {
        webClient.close();
    }
}
