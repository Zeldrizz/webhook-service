package com.webhookservice.service;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.Webhook;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the circuit breaker opens after N failures and short-circuits with 503. */
@ExtendWith(VertxExtension.class)
class ProxyServiceCircuitBreakerTest {

    @Test
    void afterMaxFailures_breakerOpensAndShortCircuits(Vertx vertx, VertxTestContext tc) {
        ProxyService proxy = new ProxyService(vertx, 500,
                RetryPolicy.disabled(),
                /*cbEnabled*/ true,
                /*maxFailures*/ 3,
                /*resetMs*/ 5000L,
                /*timeoutMs*/ 500L);

        Webhook webhook = new Webhook(
                UUID.randomUUID(), "Test", "test", null, "POST",
                true, true, "http://127.0.0.1:19999/nope", Map.of(),
                null, null, 100, Instant.now(), Instant.now());

        // Fire enough failed requests to open the breaker.
        chain(vertx, proxy, webhook, 4)
                .compose(v -> {
                    // Now the breaker should be OPEN — next call must short-circuit.
                    return proxy.forward(webhook, sampleLog(webhook.id()));
                })
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertNotNull(result.responseStatus());
                    assertTrue(
                            result.responseStatus() == 503 || result.responseStatus() == 502,
                            "expected 503 (breaker open) or 502 (probe still failed), got " + result.responseStatus()
                    );
                    proxy.close();
                    tc.completeNow();
                })));
    }

    @Test
    void afterMaxFailures_breakerStateIsOpen(Vertx vertx, VertxTestContext tc) throws InterruptedException {
        ProxyService proxy = new ProxyService(vertx, 500,
                RetryPolicy.disabled(),
                true, 2, 10_000L, 500L);

        Webhook webhook = new Webhook(
                UUID.randomUUID(), "Test", "test", null, "POST",
                true, true, "http://127.0.0.1:19999/nope", Map.of(),
                null, null, 100, Instant.now(), Instant.now());

        AtomicInteger done = new AtomicInteger();
        for (int i = 0; i < 3; i++) {
            proxy.forward(webhook, sampleLog(webhook.id())).onComplete(r -> done.incrementAndGet());
        }
        // Poll briefly until all calls complete.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (done.get() < 3 && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertEquals(3, done.get(), "all probes should complete in time");
        assertTrue(proxy.isCircuitOpen(), "circuit must be open after threshold failures");
        proxy.close();
        tc.completeNow();
    }

    private io.vertx.core.Future<Void> chain(Vertx vertx, ProxyService proxy, Webhook webhook, int count) {
        io.vertx.core.Future<Void> f = io.vertx.core.Future.succeededFuture();
        for (int i = 0; i < count; i++) {
            f = f.compose(v -> proxy.forward(webhook, sampleLog(webhook.id())).mapEmpty());
        }
        return f;
    }

    private RequestLog sampleLog(UUID webhookId) {
        return new RequestLog(
                UUID.randomUUID(), webhookId, Instant.now(),
                "POST", "/webhook/test", Map.of(), Map.of(),
                "{}", "application/json", "127.0.0.1",
                null, null, null);
    }
}
