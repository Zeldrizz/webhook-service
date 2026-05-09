package com.webhookservice.handler;

import com.webhookservice.cache.CacheManager;
import com.webhookservice.cache.CacheNames;
import com.webhookservice.config.AppConfig;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HTTP-level tests for {@code /api/cache/stats} and {@code /api/cache/flush}. */
@ExtendWith(VertxExtension.class)
class CacheStatsHandlerTest {

    private HttpServer server;
    private WebClient client;
    private int port;
    private CacheManager cacheManager;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext tc) {
        cacheManager = new CacheManager(vertx, defaultConfig());
        CacheStatsHandler handler = new CacheStatsHandler(cacheManager);

        Router router = Router.router(vertx);
        router.get("/api/cache/stats").handler(handler::stats);
        router.post("/api/cache/flush").handler(handler::flush);

        server = vertx.createHttpServer();
        server.requestHandler(router).listen(0)
                .onSuccess(s -> {
                    port = s.actualPort();
                    client = WebClient.create(vertx);
                    tc.completeNow();
                })
                .onFailure(tc::failNow);
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.close();
    }

    @Test
    void statsEndpoint_returnsSnapshotJson(Vertx vertx, VertxTestContext tc) {
        client.get(port, "127.0.0.1", "/api/cache/stats")
                .send()
                .onComplete(tc.succeeding(res -> tc.verify(() -> {
                    assertEquals(200, res.statusCode());
                    Buffer body = res.body();
                    assertNotNull(body);
                    JsonObject json = body.toJsonObject();
                    assertEquals(true, json.getBoolean("enabled"));
                    JsonObject caches = json.getJsonObject("caches");
                    assertTrue(caches.containsKey(CacheNames.WEBHOOK_BY_SLUG));
                    assertTrue(caches.containsKey(CacheNames.WEBHOOK_BY_ID));
                    assertTrue(caches.containsKey(CacheNames.NEGATIVE_SLUG));
                    assertTrue(caches.containsKey(CacheNames.STATS));
                    tc.completeNow();
                })));
    }

    @Test
    void flushEndpoint_returns204AndEvictsLocalEntries(Vertx vertx, VertxTestContext tc) {
        AtomicInteger receivedFlush = new AtomicInteger();
        vertx.eventBus().consumer(CacheNames.EVENT_BUS_INVALIDATE_ADDRESS, message -> receivedFlush.incrementAndGet());

        cacheManager.installEventBusConsumer(vertx.eventBus());

        client.post(port, "127.0.0.1", "/api/cache/flush")
                .send()
                .onComplete(tc.succeeding(res -> tc.verify(() -> {
                    assertEquals(204, res.statusCode());
                    // Allow EventBus delivery to land before asserting.
                    vertx.setTimer(50, id -> tc.verify(() -> {
                        assertTrue(receivedFlush.get() >= 1, "flush event must propagate");
                        tc.completeNow();
                    }));
                })));
    }

    private AppConfig defaultConfig() {
        return new AppConfig(
                8080, "jdbc:postgresql://localhost:5432/webhooks", "u", "p",
                10, 256, 30000L,
                10000L, 0,
                100L, 5000L, 2.0, true, true,
                false, 5, 3000L, 10000L,
                false, "test-key",
                100, 24,
                true,
                100L, 60L, 30L,
                100L, 30L,
                100L, 1800L,
                false, 100, 100L
        );
    }
}
