package com.webhookservice.service;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.Webhook;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end retry tests against a real Vert.x HTTP upstream. */
@ExtendWith(VertxExtension.class)
class ProxyServiceRetryTest {

    @Test
    void serverReturning500_isRetriedUpToLimit_thenSurfaces500(Vertx vertx, VertxTestContext tc) {
        AtomicInteger hits = new AtomicInteger();
        startUpstream(vertx, router -> router.post("/")
                .handler(ctx -> {
                    hits.incrementAndGet();
                    ctx.response().setStatusCode(500).end("nope");
                }), port -> {
            RetryPolicy policy = new RetryPolicy(2, 10, 100, 2.0, false, true);
            ProxyService proxy = new ProxyService(vertx, 2000, policy, false, 5, 3000L, 2000L);

            Webhook webhook = webhookWithProxy("http://127.0.0.1:" + port + "/");
            proxy.forward(webhook, sampleLog(webhook.id()))
                    .onComplete(tc.succeeding(result -> tc.verify(() -> {
                        assertEquals(500, result.responseStatus(), "final response surfaces upstream status");
                        assertEquals(3, hits.get(), "1 initial + 2 retries = 3 upstream hits");
                        proxy.close();
                        tc.completeNow();
                    })));
        });
    }

    @Test
    void serverReturning400_isNotRetried(Vertx vertx, VertxTestContext tc) {
        AtomicInteger hits = new AtomicInteger();
        startUpstream(vertx, router -> router.post("/")
                .handler(ctx -> {
                    hits.incrementAndGet();
                    ctx.response().setStatusCode(400).end("bad request");
                }), port -> {
            RetryPolicy policy = new RetryPolicy(3, 10, 100, 2.0, false, true);
            ProxyService proxy = new ProxyService(vertx, 2000, policy, false, 5, 3000L, 2000L);

            Webhook webhook = webhookWithProxy("http://127.0.0.1:" + port + "/");
            proxy.forward(webhook, sampleLog(webhook.id()))
                    .onComplete(tc.succeeding(result -> tc.verify(() -> {
                        assertEquals(400, result.responseStatus());
                        assertEquals(1, hits.get(), "4xx must not trigger retries");
                        proxy.close();
                        tc.completeNow();
                    })));
        });
    }

    @Test
    void serverEventuallyReturns200_succeedsAfterRetries(Vertx vertx, VertxTestContext tc) {
        AtomicInteger hits = new AtomicInteger();
        startUpstream(vertx, router -> router.post("/")
                .handler(ctx -> {
                    int n = hits.incrementAndGet();
                    if (n < 3) {
                        ctx.response().setStatusCode(503).end("not yet");
                    } else {
                        ctx.response().setStatusCode(200).end("ok");
                    }
                }), port -> {
            RetryPolicy policy = new RetryPolicy(5, 10, 100, 2.0, false, true);
            ProxyService proxy = new ProxyService(vertx, 2000, policy, false, 5, 3000L, 2000L);

            Webhook webhook = webhookWithProxy("http://127.0.0.1:" + port + "/");
            proxy.forward(webhook, sampleLog(webhook.id()))
                    .onComplete(tc.succeeding(result -> tc.verify(() -> {
                        assertEquals(200, result.responseStatus());
                        assertEquals(3, hits.get(), "should retry twice then succeed");
                        proxy.close();
                        tc.completeNow();
                    })));
        });
    }

    @Test
    void networkFailure_alwaysRetried_thenSurfaces502(Vertx vertx, VertxTestContext tc) {
        // Closed port: every attempt fails with Connection refused.
        RetryPolicy policy = new RetryPolicy(2, 10, 100, 2.0, false, true);
        ProxyService proxy = new ProxyService(vertx, 500, policy, false, 5, 3000L, 500L);

        Webhook webhook = webhookWithProxy("http://127.0.0.1:1/");
        proxy.forward(webhook, sampleLog(webhook.id()))
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertEquals(502, result.responseStatus());
                    assertTrue(result.proxyResponse() != null && !result.proxyResponse().isBlank());
                    proxy.close();
                    tc.completeNow();
                })));
    }

    private void startUpstream(Vertx vertx, java.util.function.Consumer<Router> configurator, java.util.function.IntConsumer onReady) {
        Router router = Router.router(vertx);
        configurator.accept(router);
        HttpServer server = vertx.createHttpServer();
        server.requestHandler(router).listen(0).onSuccess(s -> onReady.accept(s.actualPort()));
    }

    private Webhook webhookWithProxy(String url) {
        Instant now = Instant.now();
        return new Webhook(UUID.randomUUID(), "Test", "test", null, "POST",
                true, true, url, Map.of(), null, null, 100, now, now);
    }

    private RequestLog sampleLog(UUID webhookId) {
        return new RequestLog(
                UUID.randomUUID(), webhookId, Instant.now(),
                "POST", "/webhook/test", Map.of(), Map.of(),
                "{}", "application/json", "127.0.0.1",
                null, null, null);
    }
}
