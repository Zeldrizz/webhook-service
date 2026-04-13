package com.webhookservice.repository;

import com.webhookservice.config.AppConfig;
import com.webhookservice.model.RequestLog;
import com.webhookservice.model.Webhook;
import com.webhookservice.model.WebhookFactory;
import com.webhookservice.model.dto.CreateWebhookDto;
import com.webhookservice.model.dto.Page;
import com.webhookservice.model.dto.StatsResponse;
import com.webhookservice.repository.impl.JdbcRequestLogRepository;
import com.webhookservice.repository.impl.JdbcWebhookRepository;
import com.webhookservice.util.DatabaseManager;
import org.junit.jupiter.api.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JdbcRequestLogRepositoryTest {

    private static DatabaseManager databaseManager;
    private static JdbcRequestLogRepository repository;
    private static JdbcWebhookRepository webhookRepository;
    private static UUID testWebhookId;

    @BeforeAll
    static void setUp() {
        AppConfig config = new AppConfig(
                8080,
                "jdbc:h2:mem:logtestdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
                "sa", "", 2, 5000, 10000, 0, 100, 24);
        databaseManager = new DatabaseManager(config);

        try (var conn = databaseManager.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS webhooks (
                        id UUID PRIMARY KEY,
                        name VARCHAR(255) NOT NULL,
                        slug VARCHAR(255) NOT NULL UNIQUE,
                        description TEXT,
                        methods VARCHAR(50) NOT NULL DEFAULT 'GET,POST',
                        is_active BOOLEAN NOT NULL DEFAULT true,
                        debug_mode BOOLEAN NOT NULL DEFAULT true,
                        proxy_url TEXT,
                        proxy_headers TEXT,
                        request_template TEXT,
                        response_template TEXT,
                        max_log_count INTEGER NOT NULL DEFAULT 100,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS request_logs (
                        id UUID PRIMARY KEY,
                        webhook_id UUID NOT NULL REFERENCES webhooks(id) ON DELETE CASCADE,
                        received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        method VARCHAR(10) NOT NULL,
                        url TEXT NOT NULL,
                        query_params JSON,
                        headers JSON,
                        body TEXT,
                        content_type VARCHAR(255),
                        source_ip VARCHAR(45),
                        response_status INTEGER,
                        proxy_response TEXT,
                        proxy_duration_ms BIGINT
                    )
                    """);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test tables", e);
        }

        webhookRepository = new JdbcWebhookRepository(databaseManager);
        repository = new JdbcRequestLogRepository(databaseManager);
    }

    @AfterAll
    static void tearDown() {
        if (databaseManager != null) databaseManager.close();
    }

    @BeforeEach
    void cleanTables() throws Exception {
        try (var conn = databaseManager.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM request_logs");
            stmt.execute("DELETE FROM webhooks");
        }

        Webhook webhook = WebhookFactory.create(new CreateWebhookDto(
                "Test Webhook", "test description", "GET,POST",
                true, null, null, null, null, null));
        webhookRepository.save(webhook);
        testWebhookId = webhook.id();
    }

    @Test
    void save_and_findByWebhookIdAndId() {
        RequestLog log = createTestLog(testWebhookId);
        repository.save(log);

        Optional<RequestLog> found = repository.findByWebhookIdAndId(testWebhookId, log.id());
        assertTrue(found.isPresent());
        assertEquals(log.id(), found.get().id());
        assertEquals(testWebhookId, found.get().webhookId());
        assertEquals("POST", found.get().method());
        assertEquals("/webhook/test", found.get().url());
        assertEquals("127.0.0.1", found.get().sourceIp());
        assertEquals("{\"test\":true}", found.get().body());
        assertEquals("application/json", found.get().contentType());
    }

    @Test
    void findByWebhookIdAndId_nonExistent_returnsEmpty() {
        Optional<RequestLog> found = repository.findByWebhookIdAndId(testWebhookId, UUID.randomUUID());
        assertTrue(found.isEmpty());
    }

    @Test
    void findByWebhookId_withPagination() {
        for (int i = 0; i < 5; i++) {
            repository.save(createTestLog(testWebhookId));
        }

        Page<RequestLog> page1 = repository.findByWebhookId(testWebhookId, 0, 3);
        assertEquals(3, page1.items().size());
        assertEquals(5L, page1.total());
        assertEquals(0, page1.page());
        assertEquals(3, page1.size());

        Page<RequestLog> page2 = repository.findByWebhookId(testWebhookId, 1, 3);
        assertEquals(2, page2.items().size());
        assertEquals(5L, page2.total());
    }

    @Test
    void findByWebhookId_empty_returnsEmptyPage() {
        Page<RequestLog> page = repository.findByWebhookId(testWebhookId, 0, 20);
        assertEquals(0, page.items().size());
        assertEquals(0L, page.total());
    }

    @Test
    void deleteByWebhookId_deletesAllLogs() {
        repository.save(createTestLog(testWebhookId));
        repository.save(createTestLog(testWebhookId));
        repository.save(createTestLog(testWebhookId));

        long deleted = repository.deleteByWebhookId(testWebhookId);
        assertEquals(3, deleted);

        Page<RequestLog> page = repository.findByWebhookId(testWebhookId, 0, 20);
        assertEquals(0, page.items().size());
    }

    @Test
    void deleteByWebhookId_noLogs_returnsZero() {
        long deleted = repository.deleteByWebhookId(testWebhookId);
        assertEquals(0, deleted);
    }

    @Test
    void getStats_returnsCorrectCounts() {
        repository.save(createTestLog(testWebhookId, "GET"));
        repository.save(createTestLog(testWebhookId, "POST"));
        repository.save(createTestLog(testWebhookId, "POST"));

        StatsResponse stats = repository.getStats(testWebhookId);
        assertEquals(3, stats.totalRequests());
        assertTrue(stats.todayRequests() >= 3);
        assertNotNull(stats.lastRequestAt());
        assertEquals(1L, stats.methodCounts().get("GET"));
        assertEquals(2L, stats.methodCounts().get("POST"));
    }

    @Test
    void getStats_noLogs_returnsZeros() {
        StatsResponse stats = repository.getStats(testWebhookId);
        assertEquals(0, stats.totalRequests());
        assertEquals(0, stats.todayRequests());
        assertNull(stats.lastRequestAt());
        assertTrue(stats.methodCounts().isEmpty());
    }

    @Test
    void trimToMaxCount_removesOldest() {
        for (int i = 0; i < 5; i++) {
            repository.save(createTestLog(testWebhookId));
        }

        repository.trimToMaxCount(testWebhookId, 3);

        Page<RequestLog> page = repository.findByWebhookId(testWebhookId, 0, 20);
        assertEquals(3, page.items().size());
    }

    @Test
    void trimToMaxCount_withInvalidMaxCount_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> repository.trimToMaxCount(testWebhookId, 0));
        assertThrows(IllegalArgumentException.class, () -> repository.trimToMaxCount(testWebhookId, -1));
    }

    @Test
    void save_preservesProxyFields() {
        RequestLog log = new RequestLog(
                UUID.randomUUID(), testWebhookId, Instant.now(),
                "POST", "/webhook/test",
                Map.of(), Map.of(),
                "{\"data\":\"test\"}", "application/json", "127.0.0.1",
                200, "{\"result\":\"ok\"}", 150L);
        repository.save(log);

        Optional<RequestLog> found = repository.findByWebhookIdAndId(testWebhookId, log.id());
        assertTrue(found.isPresent());
        assertEquals(200, found.get().responseStatus());
        assertEquals("{\"result\":\"ok\"}", found.get().proxyResponse());
        assertEquals(150L, found.get().proxyDurationMs());
    }

    @Test
    void save_handlesNullOptionalFields() {
        RequestLog log = new RequestLog(
                UUID.randomUUID(), testWebhookId, Instant.now(),
                "GET", "/webhook/test",
                Map.of(), Map.of(),
                null, null, null,
                null, null, null);
        repository.save(log);

        Optional<RequestLog> found = repository.findByWebhookIdAndId(testWebhookId, log.id());
        assertTrue(found.isPresent());
        assertNull(found.get().body());
        assertNull(found.get().responseStatus());
        assertNull(found.get().proxyResponse());
        assertNull(found.get().proxyDurationMs());
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
