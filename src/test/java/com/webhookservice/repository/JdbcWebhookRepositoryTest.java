package com.webhookservice.repository;

import com.webhookservice.config.AppConfig;
import com.webhookservice.model.Webhook;
import com.webhookservice.model.WebhookFactory;
import com.webhookservice.model.dto.CreateWebhookDto;
import com.webhookservice.model.dto.UpdateWebhookDto;
import com.webhookservice.repository.impl.JdbcWebhookRepository;
import com.webhookservice.util.DatabaseManager;
import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JdbcWebhookRepositoryTest {

    private static DatabaseManager databaseManager;
    private static JdbcWebhookRepository repository;

    @BeforeAll
    static void setUp() {
        AppConfig config = new AppConfig(
                8080,
                "jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE",
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
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test tables", e);
        }

        repository = new JdbcWebhookRepository(databaseManager);
    }

    @AfterAll
    static void tearDown() {
        if (databaseManager != null) databaseManager.close();
    }

    @BeforeEach
    void cleanTable() throws Exception {
        try (var conn = databaseManager.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM webhooks");
        }
    }

    @Test
    void save_and_findById() {
        Webhook webhook = createTestWebhook("Test Save");
        repository.save(webhook);

        Optional<Webhook> found = repository.findById(webhook.id());
        assertTrue(found.isPresent());
        assertEquals("Test Save", found.get().name());
        assertEquals(webhook.slug(), found.get().slug());
    }

    @Test
    void findBySlug() {
        Webhook webhook = createTestWebhook("Slug Test");
        repository.save(webhook);

        Optional<Webhook> found = repository.findBySlug(webhook.slug());
        assertTrue(found.isPresent());
        assertEquals(webhook.id(), found.get().id());
    }

    @Test
    void findById_nonExistent_returnsEmpty() {
        Optional<Webhook> found = repository.findById(UUID.randomUUID());
        assertTrue(found.isEmpty());
    }

    @Test
    void findAll_withPagination() {
        for (int i = 0; i < 5; i++) {
            repository.save(createTestWebhook("Webhook " + i));
        }

        var page1 = repository.findAll(0, 3);
        assertEquals(3, page1.items().size());
        assertEquals(5L, page1.total());

        var page2 = repository.findAll(1, 3);
        assertEquals(2, page2.items().size());
        assertEquals(5L, page2.total());
    }

    @Test
    void findAll_empty_returnsZeroTotal() {
        var page = repository.findAll(0, 10);
        assertTrue(page.items().isEmpty());
        assertEquals(0L, page.total());
    }

    @Test
    void update() {
        Webhook webhook = createTestWebhook("Before Update");
        repository.save(webhook);

        UpdateWebhookDto dto = new UpdateWebhookDto(
                "After Update", null, null, null, null, null, null, null, null);
        Webhook updated = repository.update(webhook.id(), w -> WebhookFactory.applyUpdate(w, dto));

        assertEquals("After Update", updated.name());
        Optional<Webhook> found = repository.findById(webhook.id());
        assertTrue(found.isPresent());
        assertEquals("After Update", found.get().name());
    }

    @Test
    void toggleActive() {
        Webhook webhook = createTestWebhook("Toggle Test");
        repository.save(webhook);
        assertTrue(webhook.isActive());

        Webhook toggled = repository.toggleActive(webhook.id());
        assertFalse(toggled.isActive());

        Webhook toggledBack = repository.toggleActive(webhook.id());
        assertTrue(toggledBack.isActive());
    }

    @Test
    void deleteById() {
        Webhook webhook = createTestWebhook("To Delete");
        repository.save(webhook);
        assertTrue(repository.deleteById(webhook.id()));
        assertTrue(repository.findById(webhook.id()).isEmpty());
    }

    @Test
    void deleteById_nonExistent_returnsFalse() {
        assertFalse(repository.deleteById(UUID.randomUUID()));
    }

    private Webhook createTestWebhook(String name) {
        return WebhookFactory.create(new CreateWebhookDto(
                name, "desc", "GET,POST", true, null,
                Map.of(), null, null, 100));
    }
}
