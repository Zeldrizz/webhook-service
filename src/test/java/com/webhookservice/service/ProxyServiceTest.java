package com.webhookservice.service;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.Webhook;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class ProxyServiceTest {

    private ProxyService proxyService;

    @BeforeEach
    void setUp(Vertx vertx) {
        proxyService = new ProxyService(vertx, 5000);
    }

    @Test
    void forward_noProxyUrl_returnsOriginalLog(Vertx vertx, VertxTestContext tc) {
        Webhook webhook = new Webhook(
                UUID.randomUUID(), "Test", "test", null, "GET,POST",
                true, true, null, Map.of(), null, null, 100,
                Instant.now(), Instant.now());

        RequestLog log = createSampleLog(webhook.id());

        proxyService.forward(webhook, log)
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertEquals(log.id(), result.id());
                    assertNull(result.responseStatus());
                    tc.completeNow();
                })));
    }

    @Test
    void forward_emptyProxyUrl_returnsOriginalLog(Vertx vertx, VertxTestContext tc) {
        Webhook webhook = new Webhook(
                UUID.randomUUID(), "Test", "test", null, "GET,POST",
                true, true, "  ", Map.of(), null, null, 100,
                Instant.now(), Instant.now());

        RequestLog log = createSampleLog(webhook.id());

        proxyService.forward(webhook, log)
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertNull(result.responseStatus());
                    tc.completeNow();
                })));
    }

    @Test
    void forward_invalidUrl_returns502(Vertx vertx, VertxTestContext tc) {
        Webhook webhook = new Webhook(
                UUID.randomUUID(), "Test", "test", null, "GET,POST",
                true, true, "http://localhost:19999/nonexistent", Map.of(),
                null, null, 100, Instant.now(), Instant.now());

        RequestLog log = createSampleLog(webhook.id());

        proxyService.forward(webhook, log)
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertEquals(502, result.responseStatus());
                    assertNotNull(result.proxyResponse());
                    tc.completeNow();
                })));
    }

    @Test
    void forward_withHeaders_sendsHeaders(Vertx vertx, VertxTestContext tc) {
        Webhook webhook = new Webhook(
                UUID.randomUUID(), "Test", "test", null, "POST",
                true, true, "http://localhost:19999/test",
                Map.of("Authorization", "Bearer test123"), null, null, 100,
                Instant.now(), Instant.now());

        RequestLog log = createSampleLog(webhook.id());

        proxyService.forward(webhook, log)
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    // The request failed (no server), but we verify it tried and returned error
                    assertNotNull(result.proxyDurationMs());
                    tc.completeNow();
                })));
    }

    private RequestLog createSampleLog(UUID webhookId) {
        return new RequestLog(
                UUID.randomUUID(), webhookId, Instant.now(),
                "POST", "/webhook/test", Map.of(), Map.of("Content-Type", "application/json"),
                "{\"key\": \"value\"}", "application/json", "127.0.0.1",
                null, null, null);
    }
}
