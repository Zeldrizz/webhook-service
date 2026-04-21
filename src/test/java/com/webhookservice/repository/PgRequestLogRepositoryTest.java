package com.webhookservice.repository;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.Webhook;
import com.webhookservice.model.WebhookFactory;
import com.webhookservice.model.dto.CreateWebhookDto;
import com.webhookservice.repository.impl.PgRequestLogRepository;
import com.webhookservice.repository.impl.PgWebhookRepository;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class PgRequestLogRepositoryTest extends AbstractPgRepositoryIT {

    private static PgRequestLogRepository repository;
    private static PgWebhookRepository webhookRepository;
    private static UUID testWebhookId;

    @BeforeAll
    static void setUp() throws Exception {
        initPool();
        VertxTestContext tc = new VertxTestContext();
        pool.query("""
                CREATE TABLE IF NOT EXISTS webhooks (
                    id UUID PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    slug VARCHAR(255) NOT NULL UNIQUE,
                    description TEXT,
                    methods VARCHAR(50) NOT NULL DEFAULT 'GET,POST',
                    is_active BOOLEAN NOT NULL DEFAULT true,
                    debug_mode BOOLEAN NOT NULL DEFAULT true,
                    proxy_url TEXT,
                    proxy_headers JSONB,
                    request_template TEXT,
                    response_template TEXT,
                    max_log_count INTEGER NOT NULL DEFAULT 100,
                    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
                )
                """).execute()
                .compose(v -> pool.query("""
                        CREATE TABLE IF NOT EXISTS request_logs (
                            id UUID PRIMARY KEY,
                            webhook_id UUID NOT NULL REFERENCES webhooks(id) ON DELETE CASCADE,
                            received_at TIMESTAMP NOT NULL DEFAULT NOW(),
                            method VARCHAR(10) NOT NULL,
                            url TEXT NOT NULL,
                            query_params JSONB,
                            headers JSONB,
                            body TEXT,
                            content_type VARCHAR(255),
                            source_ip VARCHAR(45),
                            response_status INTEGER,
                            proxy_response TEXT,
                            proxy_duration_ms BIGINT
                        )
                        """).execute())
                .onComplete(tc.succeedingThenComplete());
        assertTrue(tc.awaitCompletion(30, TimeUnit.SECONDS));
        if (tc.failed()) throw new RuntimeException(tc.causeOfFailure());

        webhookRepository = new PgWebhookRepository(pool);
        repository = new PgRequestLogRepository(pool);
    }

    @AfterAll
    static void tearDown() {
        shutdownPool();
    }

    @BeforeEach
    void cleanTables(VertxTestContext tc) {
        pool.query("DELETE FROM request_logs").execute()
                .compose(v -> pool.query("DELETE FROM webhooks").execute())
                .compose(v -> {
                    Webhook webhook = WebhookFactory.create(new CreateWebhookDto(
                            "Test Webhook", "test description", "GET,POST",
                            true, null, null, null, null, null));
                    testWebhookId = webhook.id();
                    return webhookRepository.save(webhook);
                })
                .onComplete(tc.succeedingThenComplete());
    }

    @Test
    void save_and_findByWebhookIdAndId(VertxTestContext tc) {
        RequestLog log = createTestLog(testWebhookId);
        repository.save(log)
                .compose(saved -> repository.findByWebhookIdAndId(testWebhookId, log.id()))
                .onComplete(tc.succeeding(found -> tc.verify(() -> {
                    assertTrue(found.isPresent());
                    assertEquals(log.id(), found.get().id());
                    assertEquals(testWebhookId, found.get().webhookId());
                    assertEquals("POST", found.get().method());
                    assertEquals("/webhook/test", found.get().url());
                    assertEquals("127.0.0.1", found.get().sourceIp());
                    assertEquals("{\"test\":true}", found.get().body());
                    assertEquals("application/json", found.get().contentType());
                    tc.completeNow();
                })));
    }

    @Test
    void findByWebhookIdAndId_nonExistent_returnsEmpty(VertxTestContext tc) {
        repository.findByWebhookIdAndId(testWebhookId, UUID.randomUUID())
                .onComplete(tc.succeeding(found -> tc.verify(() -> {
                    assertTrue(found.isEmpty());
                    tc.completeNow();
                })));
    }

    @Test
    void findByWebhookId_withPagination(VertxTestContext tc) {
        Future<?>[] saves = new Future[5];
        for (int i = 0; i < 5; i++) {
            saves[i] = repository.save(createTestLog(testWebhookId));
        }
        CompositeFuture.all(Arrays.asList(saves))
                .compose(v -> repository.findByWebhookId(testWebhookId, 0, 3))
                .compose(page1 -> {
                    assertEquals(3, page1.items().size());
                    assertEquals(5L, page1.total());
                    assertEquals(0, page1.page());
                    assertEquals(3, page1.size());
                    return repository.findByWebhookId(testWebhookId, 1, 3);
                })
                .onComplete(tc.succeeding(page2 -> tc.verify(() -> {
                    assertEquals(2, page2.items().size());
                    assertEquals(5L, page2.total());
                    tc.completeNow();
                })));
    }

    @Test
    void findByWebhookId_empty_returnsEmptyPage(VertxTestContext tc) {
        repository.findByWebhookId(testWebhookId, 0, 20)
                .onComplete(tc.succeeding(page -> tc.verify(() -> {
                    assertEquals(0, page.items().size());
                    assertEquals(0L, page.total());
                    tc.completeNow();
                })));
    }

    @Test
    void deleteByWebhookId_deletesAllLogs(VertxTestContext tc) {
        Future<?>[] saves = new Future[3];
        for (int i = 0; i < 3; i++) {
            saves[i] = repository.save(createTestLog(testWebhookId));
        }
        CompositeFuture.all(Arrays.asList(saves))
                .compose(v -> repository.deleteByWebhookId(testWebhookId))
                .compose(deleted -> {
                    assertEquals(3L, deleted);
                    return repository.findByWebhookId(testWebhookId, 0, 20);
                })
                .onComplete(tc.succeeding(page -> tc.verify(() -> {
                    assertEquals(0, page.items().size());
                    tc.completeNow();
                })));
    }

    @Test
    void deleteByWebhookId_noLogs_returnsZero(VertxTestContext tc) {
        repository.deleteByWebhookId(testWebhookId)
                .onComplete(tc.succeeding(deleted -> tc.verify(() -> {
                    assertEquals(0L, deleted);
                    tc.completeNow();
                })));
    }

    @Test
    void getStats_returnsCorrectCounts(VertxTestContext tc) {
        CompositeFuture.all(
                        repository.save(createTestLog(testWebhookId, "GET")),
                        repository.save(createTestLog(testWebhookId, "POST")),
                        repository.save(createTestLog(testWebhookId, "POST")))
                .compose(v -> repository.getStats(testWebhookId))
                .onComplete(tc.succeeding(stats -> tc.verify(() -> {
                    assertEquals(3, stats.totalRequests());
                    assertTrue(stats.todayRequests() >= 3);
                    assertNotNull(stats.lastRequestAt());
                    assertEquals(1L, stats.methodCounts().get("GET"));
                    assertEquals(2L, stats.methodCounts().get("POST"));
                    tc.completeNow();
                })));
    }

    @Test
    void getStats_noLogs_returnsZeros(VertxTestContext tc) {
        repository.getStats(testWebhookId)
                .onComplete(tc.succeeding(stats -> tc.verify(() -> {
                    assertEquals(0, stats.totalRequests());
                    assertEquals(0, stats.todayRequests());
                    assertNull(stats.lastRequestAt());
                    assertTrue(stats.methodCounts().isEmpty());
                    tc.completeNow();
                })));
    }

    @Test
    void trimToMaxCount_removesOldest(VertxTestContext tc) {
        Future<?>[] saves = new Future[5];
        for (int i = 0; i < 5; i++) {
            saves[i] = repository.save(createTestLog(testWebhookId));
        }
        CompositeFuture.all(Arrays.asList(saves))
                .compose(v -> repository.trimToMaxCount(testWebhookId, 3))
                .compose(v -> repository.findByWebhookId(testWebhookId, 0, 20))
                .onComplete(tc.succeeding(page -> tc.verify(() -> {
                    assertEquals(3, page.items().size());
                    tc.completeNow();
                })));
    }

    @Test
    void trimToMaxCount_withInvalidMaxCount_failsFuture(VertxTestContext tc) {
        repository.trimToMaxCount(testWebhookId, 0)
                .onComplete(tc.failing(err -> tc.verify(() -> {
                    assertInstanceOf(IllegalArgumentException.class, err);
                    tc.completeNow();
                })));
    }

    @Test
    void save_preservesProxyFields(VertxTestContext tc) {
        RequestLog log = new RequestLog(
                UUID.randomUUID(), testWebhookId, Instant.now(),
                "POST", "/webhook/test",
                Map.of(), Map.of(),
                "{\"data\":\"test\"}", "application/json", "127.0.0.1",
                200, "{\"result\":\"ok\"}", 150L);
        repository.save(log)
                .compose(saved -> repository.findByWebhookIdAndId(testWebhookId, log.id()))
                .onComplete(tc.succeeding(found -> tc.verify(() -> {
                    assertTrue(found.isPresent());
                    assertEquals(200, found.get().responseStatus());
                    assertEquals("{\"result\":\"ok\"}", found.get().proxyResponse());
                    assertEquals(150L, found.get().proxyDurationMs());
                    tc.completeNow();
                })));
    }

    @Test
    void save_handlesNullOptionalFields(VertxTestContext tc) {
        RequestLog log = new RequestLog(
                UUID.randomUUID(), testWebhookId, Instant.now(),
                "GET", "/webhook/test",
                Map.of(), Map.of(),
                null, null, null,
                null, null, null);
        repository.save(log)
                .compose(saved -> repository.findByWebhookIdAndId(testWebhookId, log.id()))
                .onComplete(tc.succeeding(found -> tc.verify(() -> {
                    assertTrue(found.isPresent());
                    assertNull(found.get().body());
                    assertNull(found.get().responseStatus());
                    assertNull(found.get().proxyResponse());
                    assertNull(found.get().proxyDurationMs());
                    tc.completeNow();
                })));
    }

    private RequestLog createTestLog(UUID webhookId) {
        return createTestLog(webhookId, "POST");
    }

    private RequestLog createTestLog(UUID webhookId, String method) {
        return new RequestLog(
                UUID.randomUUID(), webhookId, Instant.now(),
                method, "/webhook/test",
                Map.of(), Map.of(),
                "{\"test\":true}", "application/json", "127.0.0.1",
                null, null, null);
    }
}
