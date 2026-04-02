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

public class ProxyService {

    private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

    private final WebClient webClient;
    private final long timeoutMs;

    public ProxyService(Vertx vertx, long timeoutMs) {
        this.timeoutMs = timeoutMs;
        this.webClient = WebClient.create(vertx, new WebClientOptions()
                .setConnectTimeout((int) timeoutMs)
                .setIdleTimeout((int) timeoutMs)
                .setMaxPoolSize(50)
                .setKeepAlive(true)
                .setDecompressionSupported(true));
    }

    public Future<RequestLog> forward(Webhook webhook, RequestLog requestLog) {
        if (webhook.proxyUrl() == null || webhook.proxyUrl().isBlank()) {
            return Future.succeededFuture(requestLog);
        }

        long startTime = System.nanoTime();

        HttpRequest<Buffer> proxyRequest = new ProxyRequestBuilder(webClient)
                .method(requestLog.method())
                .url(webhook.proxyUrl())
                .headers(webhook.proxyHeaders())
                .header("X-Webhook-Id", webhook.id().toString())
                .header("X-Webhook-Name", webhook.name())
                .build();

        proxyRequest.timeout(timeoutMs);

        Future<HttpResponse<Buffer>> responseFuture;
        Buffer body = requestLog.body() != null ? Buffer.buffer(requestLog.body()) : null;
        if (body != null) {
            responseFuture = proxyRequest.sendBuffer(body);
        } else {
            responseFuture = proxyRequest.send();
        }

        return responseFuture
                .map(response -> {
                    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                    String responseBody = response.bodyAsString();
                    int status = response.statusCode();
                    log.debug("Proxy response: status={}, duration={}ms, webhook={}", status, durationMs, webhook.slug());
                    return requestLog.withProxyResult(status, responseBody, durationMs);
                })
                .recover(err -> {
                    long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                    String errorMsg = classifyError(err);
                    log.warn("Proxy failed: webhook={}, error={}, duration={}ms", webhook.slug(), errorMsg, durationMs);
                    return Future.succeededFuture(
                            requestLog.withProxyResult(502, errorMsg, durationMs));
                });
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
