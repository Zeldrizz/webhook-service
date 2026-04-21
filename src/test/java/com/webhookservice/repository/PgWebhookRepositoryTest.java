package com.webhookservice.repository;

import com.webhookservice.model.Webhook;
import com.webhookservice.model.WebhookFactory;
import com.webhookservice.model.dto.CreateWebhookDto;
import com.webhookservice.model.dto.UpdateWebhookDto;
import com.webhookservice.repository.impl.PgWebhookRepository;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(VertxExtension.class)
class PgWebhookRepositoryTest extends AbstractPgRepositoryIT {

    private static PgWebhookRepository repository;

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
                """).execute().onComplete(tc.succeedingThenComplete());
        assertTrue(tc.awaitCompletion(30, TimeUnit.SECONDS));
        if (tc.failed()) throw new RuntimeException(tc.causeOfFailure());
        repository = new PgWebhookRepository(pool);
    }

    @AfterAll
    static void tearDown() {
        shutdownPool();
    }

    @BeforeEach
    void cleanTable(VertxTestContext tc) {
        pool.query("DELETE FROM webhooks").execute()
                .onComplete(tc.succeedingThenComplete());
    }

    @Test
    void save_and_findById(VertxTestContext tc) {
        Webhook webhook = createTestWebhook("Test Save");
        repository.save(webhook)
                .compose(saved -> repository.findById(webhook.id()))
                .onComplete(tc.succeeding(found -> tc.verify(() -> {
                    assertTrue(found.isPresent());
                    assertEquals("Test Save", found.get().name());
                    assertEquals(webhook.slug(), found.get().slug());
                    tc.completeNow();
                })));
    }

    @Test
    void findBySlug(VertxTestContext tc) {
        Webhook webhook = createTestWebhook("Slug Test");
        repository.save(webhook)
                .compose(saved -> repository.findBySlug(webhook.slug()))
                .onComplete(tc.succeeding(found -> tc.verify(() -> {
                    assertTrue(found.isPresent());
                    assertEquals(webhook.id(), found.get().id());
                    tc.completeNow();
                })));
    }

    @Test
    void findById_nonExistent_returnsEmpty(VertxTestContext tc) {
        repository.findById(UUID.randomUUID())
                .onComplete(tc.succeeding(found -> tc.verify(() -> {
                    assertTrue(found.isEmpty());
                    tc.completeNow();
                })));
    }

    @Test
    void findAll_withPagination(VertxTestContext tc) {
        var futures = new io.vertx.core.Future[5];
        for (int i = 0; i < 5; i++) {
            futures[i] = repository.save(createTestWebhook("Webhook " + i));
        }
        io.vertx.core.CompositeFuture.all(java.util.Arrays.asList(futures))
                .compose(v -> repository.findAll(0, 3))
                .compose(page1 -> {
                    assertEquals(3, page1.items().size());
                    assertEquals(5L, page1.total());
                    return repository.findAll(1, 3);
                })
                .onComplete(tc.succeeding(page2 -> tc.verify(() -> {
                    assertEquals(2, page2.items().size());
                    assertEquals(5L, page2.total());
                    tc.completeNow();
                })));
    }

    @Test
    void findAll_empty_returnsZeroTotal(VertxTestContext tc) {
        repository.findAll(0, 10)
                .onComplete(tc.succeeding(page -> tc.verify(() -> {
                    assertTrue(page.items().isEmpty());
                    assertEquals(0L, page.total());
                    tc.completeNow();
                })));
    }

    @Test
    void update(VertxTestContext tc) {
        Webhook webhook = createTestWebhook("Before Update");
        UpdateWebhookDto dto = new UpdateWebhookDto(
                "After Update", null, null, null, null, null, null, null, null);

        repository.save(webhook)
                .compose(saved -> repository.update(webhook.id(),
                        w -> WebhookFactory.applyUpdate(w, dto)))
                .compose(updated -> {
                    assertEquals("After Update", updated.name());
                    return repository.findById(webhook.id());
                })
                .onComplete(tc.succeeding(found -> tc.verify(() -> {
                    assertTrue(found.isPresent());
                    assertEquals("After Update", found.get().name());
                    tc.completeNow();
                })));
    }

    @Test
    void toggleActive(VertxTestContext tc) {
        Webhook webhook = createTestWebhook("Toggle Test");
        assertTrue(webhook.isActive());

        repository.save(webhook)
                .compose(saved -> repository.toggleActive(webhook.id()))
                .compose(toggled -> {
                    assertFalse(toggled.isActive());
                    return repository.toggleActive(webhook.id());
                })
                .onComplete(tc.succeeding(toggledBack -> tc.verify(() -> {
                    assertTrue(toggledBack.isActive());
                    tc.completeNow();
                })));
    }

    @Test
    void deleteById(VertxTestContext tc) {
        Webhook webhook = createTestWebhook("To Delete");
        repository.save(webhook)
                .compose(saved -> repository.deleteById(webhook.id()))
                .compose(deleted -> {
                    assertTrue(deleted);
                    return repository.findById(webhook.id());
                })
                .onComplete(tc.succeeding(found -> tc.verify(() -> {
                    assertTrue(found.isEmpty());
                    tc.completeNow();
                })));
    }

    @Test
    void deleteById_nonExistent_returnsFalse(VertxTestContext tc) {
        repository.deleteById(UUID.randomUUID())
                .onComplete(tc.succeeding(deleted -> tc.verify(() -> {
                    assertFalse(deleted);
                    tc.completeNow();
                })));
    }

    private Webhook createTestWebhook(String name) {
        return WebhookFactory.create(new CreateWebhookDto(
                name, "desc", "GET,POST", true, null,
                Map.of(), null, null, 100));
    }
}
