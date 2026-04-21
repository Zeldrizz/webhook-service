package com.webhookservice.service;

import com.webhookservice.model.Webhook;
import com.webhookservice.model.dto.CreateWebhookDto;
import com.webhookservice.model.dto.Page;
import com.webhookservice.model.dto.UpdateWebhookDto;
import com.webhookservice.repository.WebhookRepository;
import com.webhookservice.validation.ValidationException;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class WebhookServiceTest {

    @Mock
    private WebhookRepository webhookRepository;

    private WebhookService webhookService;

    @BeforeEach
    void setUp() {
        webhookService = new WebhookService(webhookRepository);
    }

    @Test
    void create_withValidDto_savesAndReturnsWebhook(Vertx vertx, VertxTestContext tc) {
        CreateWebhookDto dto = new CreateWebhookDto(
                "Test Webhook", "A test webhook", "GET,POST",
                true, null, null, null, null, null);

        when(webhookRepository.save(any(Webhook.class)))
                .thenAnswer(inv -> Future.succeededFuture(inv.getArgument(0)));

        webhookService.create(dto)
                .onComplete(tc.succeeding(webhook -> tc.verify(() -> {
                    assertNotNull(webhook.id());
                    assertEquals("Test Webhook", webhook.name());
                    assertNotNull(webhook.slug());
                    assertEquals("GET,POST", webhook.methods());
                    assertTrue(webhook.isActive());
                    verify(webhookRepository).save(any(Webhook.class));
                    tc.completeNow();
                })));
    }

    @Test
    void create_withBlankName_failsWithValidationException(Vertx vertx, VertxTestContext tc) {
        CreateWebhookDto dto = new CreateWebhookDto(
                "", null, null, null, null, null, null, null, null);

        webhookService.create(dto)
                .onComplete(tc.failing(err -> tc.verify(() -> {
                    assertInstanceOf(ValidationException.class, err);
                    tc.completeNow();
                })));
    }

    @Test
    void create_withNullName_failsWithValidationException(Vertx vertx, VertxTestContext tc) {
        CreateWebhookDto dto = new CreateWebhookDto(
                null, null, null, null, null, null, null, null, null);

        webhookService.create(dto)
                .onComplete(tc.failing(err -> tc.verify(() -> {
                    assertInstanceOf(ValidationException.class, err);
                    tc.completeNow();
                })));
    }

    @Test
    void getById_existing_returnsWebhook(Vertx vertx, VertxTestContext tc) {
        UUID id = UUID.randomUUID();
        Webhook webhook = createSampleWebhook(id);
        when(webhookRepository.findById(id)).thenReturn(Future.succeededFuture(Optional.of(webhook)));

        webhookService.getById(id)
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertNotNull(result);
                    assertEquals(id, result.id());
                    tc.completeNow();
                })));
    }

    @Test
    void getById_nonExisting_returnsNull(Vertx vertx, VertxTestContext tc) {
        UUID id = UUID.randomUUID();
        when(webhookRepository.findById(id)).thenReturn(Future.succeededFuture(Optional.empty()));

        webhookService.getById(id)
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertNull(result);
                    tc.completeNow();
                })));
    }

    @Test
    void getBySlug_existing_returnsWebhook(Vertx vertx, VertxTestContext tc) {
        Webhook webhook = createSampleWebhook(UUID.randomUUID());
        when(webhookRepository.findBySlug("test-slug"))
                .thenReturn(Future.succeededFuture(Optional.of(webhook)));

        webhookService.getBySlug("test-slug")
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertNotNull(result);
                    tc.completeNow();
                })));
    }

    @Test
    void list_returnsPagedResults(Vertx vertx, VertxTestContext tc) {
        List<Webhook> webhooks = List.of(createSampleWebhook(UUID.randomUUID()));
        Page<Webhook> page = new Page<>(webhooks, 0, 20, 1L);
        when(webhookRepository.findAll(0, 20)).thenReturn(Future.succeededFuture(page));

        webhookService.list(0, 20)
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertEquals(1, result.items().size());
                    assertEquals(0, result.page());
                    assertEquals(20, result.size());
                    assertEquals(1L, result.total());
                    tc.completeNow();
                })));
    }

    @Test
    void update_existing_returnsUpdated(Vertx vertx, VertxTestContext tc) {
        UUID id = UUID.randomUUID();
        Webhook existing = createSampleWebhook(id);
        Webhook updated = new Webhook(id, "Updated Name", existing.slug(), existing.description(),
                existing.methods(), existing.isActive(), existing.debugMode(), existing.proxyUrl(),
                existing.proxyHeaders(), existing.requestTemplate(), existing.responseTemplate(),
                existing.maxLogCount(), existing.createdAt(), Instant.now());
        when(webhookRepository.update(eq(id), any())).thenReturn(Future.succeededFuture(updated));

        UpdateWebhookDto dto = new UpdateWebhookDto(
                "Updated Name", null, null, null, null, null, null, null, null);

        webhookService.update(id, dto)
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertEquals("Updated Name", result.name());
                    verify(webhookRepository).update(eq(id), any());
                    tc.completeNow();
                })));
    }

    @Test
    void delete_existing_returnsTrue(Vertx vertx, VertxTestContext tc) {
        UUID id = UUID.randomUUID();
        when(webhookRepository.deleteById(id)).thenReturn(Future.succeededFuture(true));

        webhookService.delete(id)
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertTrue(result);
                    tc.completeNow();
                })));
    }

    @Test
    void toggle_existing_togglesIsActive(Vertx vertx, VertxTestContext tc) {
        UUID id = UUID.randomUUID();
        Webhook toggled = new Webhook(id, "Test Webhook", "test-slug", "desc",
                "GET,POST", false, true, null, Map.of(),
                null, null, 100, Instant.now(), Instant.now());
        when(webhookRepository.toggleActive(id)).thenReturn(Future.succeededFuture(toggled));

        webhookService.toggle(id)
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertFalse(result.isActive());
                    verify(webhookRepository).toggleActive(id);
                    tc.completeNow();
                })));
    }

    private Webhook createSampleWebhook(UUID id) {
        Instant now = Instant.now();
        return new Webhook(id, "Test Webhook", "test-slug", "desc",
                "GET,POST", true, true, null, Map.of(),
                null, null, 100, now, now);
    }
}
