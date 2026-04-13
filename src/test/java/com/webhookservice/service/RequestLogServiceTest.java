package com.webhookservice.service;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.dto.Page;
import com.webhookservice.model.dto.StatsResponse;
import com.webhookservice.repository.RequestLogRepository;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class RequestLogServiceTest {

    @Mock
    private RequestLogRepository requestLogRepository;

    private RequestLogService requestLogService;

    @BeforeEach
    void setUp(Vertx vertx) {
        requestLogService = new RequestLogService(requestLogRepository, vertx);
    }

    @Test
    void save_savesAndTrims(Vertx vertx, VertxTestContext tc) {
        RequestLog log = createTestLog();
        when(requestLogRepository.save(log)).thenReturn(log);

        requestLogService.save(log, 100)
                .onComplete(tc.succeeding(saved -> tc.verify(() -> {
                    assertEquals(log.id(), saved.id());
                    verify(requestLogRepository).save(log);
                    verify(requestLogRepository).trimToMaxCount(log.webhookId(), 100);
                    tc.completeNow();
                })));
    }

    @Test
    void listByWebhookId_returnsPage(Vertx vertx, VertxTestContext tc) {
        UUID webhookId = UUID.randomUUID();
        Page<RequestLog> page = new Page<>(List.of(createTestLog()), 0, 20, 1L);
        when(requestLogRepository.findByWebhookId(webhookId, 0, 20)).thenReturn(page);

        requestLogService.listByWebhookId(webhookId, 0, 20)
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertEquals(1, result.items().size());
                    assertEquals(1L, result.total());
                    tc.completeNow();
                })));
    }

    @Test
    void getByWebhookIdAndId_existing_returnsLog(Vertx vertx, VertxTestContext tc) {
        RequestLog log = createTestLog();
        when(requestLogRepository.findByWebhookIdAndId(log.webhookId(), log.id()))
                .thenReturn(Optional.of(log));

        requestLogService.getByWebhookIdAndId(log.webhookId(), log.id())
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertNotNull(result);
                    assertEquals(log.id(), result.id());
                    tc.completeNow();
                })));
    }

    @Test
    void getByWebhookIdAndId_nonExistent_returnsNull(Vertx vertx, VertxTestContext tc) {
        UUID webhookId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        when(requestLogRepository.findByWebhookIdAndId(webhookId, requestId))
                .thenReturn(Optional.empty());

        requestLogService.getByWebhookIdAndId(webhookId, requestId)
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertNull(result);
                    tc.completeNow();
                })));
    }

    @Test
    void clearByWebhookId_returnsDeletedCount(Vertx vertx, VertxTestContext tc) {
        UUID webhookId = UUID.randomUUID();
        when(requestLogRepository.deleteByWebhookId(webhookId)).thenReturn(5L);

        requestLogService.clearByWebhookId(webhookId)
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertEquals(5L, result);
                    verify(requestLogRepository).deleteByWebhookId(webhookId);
                    tc.completeNow();
                })));
    }

    @Test
    void getStats_returnsStats(Vertx vertx, VertxTestContext tc) {
        UUID webhookId = UUID.randomUUID();
        StatsResponse stats = new StatsResponse(10, 3, Map.of("GET", 4L, "POST", 6L), Instant.now());
        when(requestLogRepository.getStats(webhookId)).thenReturn(stats);

        requestLogService.getStats(webhookId)
                .onComplete(tc.succeeding(result -> tc.verify(() -> {
                    assertEquals(10, result.totalRequests());
                    assertEquals(3, result.todayRequests());
                    assertEquals(2, result.methodCounts().size());
                    tc.completeNow();
                })));
    }

    private RequestLog createTestLog() {
        return new RequestLog(
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                "POST", "/webhook/test",
                Map.of("q", "search"), Map.of("Content-Type", "application/json"),
                "{\"test\":true}", "application/json", "127.0.0.1",
                null, null, null);
    }
}
