package com.webhookservice.service;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.dto.Page;
import com.webhookservice.model.dto.StatsResponse;
import com.webhookservice.repository.RequestLogRepository;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestLogServiceTest {
    private RepositoryStub repositoryStub;
    private RequestLogService requestLogService;

    @BeforeEach
    void setUp() {
        repositoryStub = new RepositoryStub();
        requestLogService = new RequestLogService(repositoryStub.repository());
    }

    @Test
    void save_savesAndTrims() {
        RequestLog log = createTestLog();

        Future<RequestLog> future = requestLogService.save(log, 100);

        assertTrue(future.succeeded());
        assertEquals(log.id(), future.result().id());
        assertTrue(repositoryStub.wasCalled("save"));
        assertTrue(repositoryStub.wasCalled("trimToMaxCount"));
        assertEquals(100, repositoryStub.lastCall("trimToMaxCount").args()[1]);
    }

    @Test
    void save_failedRepositorySave_propagatesFailureAndDoesNotTrim() {
        RequestLog log = createTestLog();
        repositoryStub.saveFailure = new IllegalStateException("save failed");

        Future<RequestLog> future = requestLogService.save(log, 100);

        assertTrue(future.failed());
        assertEquals("save failed", future.cause().getMessage());
        assertTrue(repositoryStub.wasCalled("save"));
        assertFalse(repositoryStub.wasCalled("trimToMaxCount"));
    }

    @Test
    void save_failedTrim_propagatesFailure() {
        RequestLog log = createTestLog();
        repositoryStub.trimFailure = new IllegalStateException("trim failed");

        Future<RequestLog> future = requestLogService.save(log, 100);

        assertTrue(future.failed());
        assertEquals("trim failed", future.cause().getMessage());
        assertTrue(repositoryStub.wasCalled("save"));
        assertTrue(repositoryStub.wasCalled("trimToMaxCount"));
    }

    @Test
    void listByWebhookId_returnsPage() {
        UUID webhookId = UUID.randomUUID();
        repositoryStub.page = new Page<>(List.of(createTestLog()), 0, 20, 1L);

        Future<Page<RequestLog>> future = requestLogService.listByWebhookId(webhookId, 0, 20);

        assertTrue(future.succeeded());
        assertEquals(1, future.result().items().size());
        assertEquals(1L, future.result().total());
        assertTrue(repositoryStub.wasCalled("findByWebhookId"));
    }

    @Test
    void listByWebhookId_emptyPage_returnsEmptyItems() {
        UUID webhookId = UUID.randomUUID();
        repositoryStub.page = new Page<>(List.of(), 2, 20, 0L);

        Future<Page<RequestLog>> future = requestLogService.listByWebhookId(webhookId, 2, 20);

        assertTrue(future.succeeded());
        assertEquals(0, future.result().items().size());
        assertEquals(2, future.result().page());
        assertEquals(0L, future.result().total());
        assertTrue(repositoryStub.wasCalled("findByWebhookId"));
    }

    @Test
    void getByWebhookIdAndId_existing_returnsLog() {
        RequestLog log = createTestLog();
        repositoryStub.foundLog = log;

        Future<RequestLog> future = requestLogService.getByWebhookIdAndId(log.webhookId(), log.id());

        assertTrue(future.succeeded());
        assertNotNull(future.result());
        assertEquals(log.id(), future.result().id());
    }

    @Test
    void getByWebhookIdAndId_nonExistent_returnsNull() {
        UUID webhookId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        repositoryStub.foundLog = null;

        Future<RequestLog> future = requestLogService.getByWebhookIdAndId(webhookId, requestId);

        assertTrue(future.succeeded());
        assertNull(future.result());
    }

    @Test
    void clearByWebhookId_returnsDeletedCount() {
        UUID webhookId = UUID.randomUUID();
        repositoryStub.deletedCount = 5L;

        Future<Long> future = requestLogService.clearByWebhookId(webhookId);

        assertTrue(future.succeeded());
        assertEquals(5L, future.result().longValue());
        assertTrue(repositoryStub.wasCalled("deleteByWebhookId"));
    }

    @Test
    void clearByWebhookId_zeroDeleted_returnsZero() {
        UUID webhookId = UUID.randomUUID();
        repositoryStub.deletedCount = 0L;

        Future<Long> future = requestLogService.clearByWebhookId(webhookId);

        assertTrue(future.succeeded());
        assertEquals(0L, future.result().longValue());
        assertTrue(repositoryStub.wasCalled("deleteByWebhookId"));
    }

    @Test
    void getStats_returnsStats() {
        UUID webhookId = UUID.randomUUID();
        repositoryStub.stats = new StatsResponse(10, 3, Map.of("GET", 4L, "POST", 6L), Instant.now());

        Future<StatsResponse> future = requestLogService.getStats(webhookId);

        assertTrue(future.succeeded());
        assertEquals(10, future.result().totalRequests());
        assertEquals(3, future.result().todayRequests());
        assertEquals(2, future.result().methodCounts().size());
    }

    @Test
    void getStats_emptyStats_returnsZeroCounters() {
        UUID webhookId = UUID.randomUUID();
        repositoryStub.stats = new StatsResponse(0, 0, Map.of(), null);

        Future<StatsResponse> future = requestLogService.getStats(webhookId);

        assertTrue(future.succeeded());
        assertEquals(0, future.result().totalRequests());
        assertEquals(0, future.result().todayRequests());
        assertEquals(0, future.result().methodCounts().size());
        assertNull(future.result().lastRequestAt());
    }

    private RequestLog createTestLog() {
        return new RequestLog(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                "POST",
                "/webhook/test",
                Map.of("q", "search"),
                Map.of("Content-Type", "application/json"),
                "{\"test\":true}",
                "application/json",
                "127.0.0.1",
                null,
                null,
                null
        );
    }

    private static final class RepositoryStub implements InvocationHandler {
        private final List<Call> calls = new ArrayList<>();
        private final RequestLogRepository repository;

        private Page<RequestLog> page = new Page<>(List.of(), 0, 20, 0L);
        private RequestLog foundLog;
        private long deletedCount;
        private StatsResponse stats = new StatsResponse(0, 0, Map.of(), null);
        private RuntimeException saveFailure;
        private RuntimeException trimFailure;

        private RepositoryStub() {
            repository = (RequestLogRepository) Proxy.newProxyInstance(
                    RequestLogRepository.class.getClassLoader(),
                    new Class<?>[]{RequestLogRepository.class},
                    this
            );
        }

        private RequestLogRepository repository() {
            return repository;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }

            calls.add(new Call(method.getName(), args == null ? new Object[0] : Arrays.copyOf(args, args.length)));

            Object value = switch (method.getName()) {
                case "save" -> {
                    if (saveFailure != null) {
                        throw saveFailure;
                    }
                    yield args[0];
                }
                case "trimToMaxCount" -> {
                    if (trimFailure != null) {
                        throw trimFailure;
                    }
                    yield null;
                }
                case "findByWebhookId" -> page;
                case "findByWebhookIdAndId" -> foundLog;
                case "deleteByWebhookId" -> deletedCount;
                case "getStats" -> stats;
                default -> throw new UnsupportedOperationException("Unexpected repository method: " + method.getName());
            };

            return adaptReturnValue(method, value);
        }

        private Object adaptReturnValue(Method method, Object value) {
            Class<?> returnType = method.getReturnType();
            if (returnType == Void.TYPE) {
                return null;
            }
            if (Future.class.isAssignableFrom(returnType)) {
                Object futureValue = value;
                if ("findByWebhookIdAndId".equals(method.getName())
                        && method.getGenericReturnType().getTypeName().contains("Optional")) {
                    futureValue = Optional.ofNullable(value);
                }
                return Future.succeededFuture(futureValue);
            }
            if (Optional.class.isAssignableFrom(returnType)) {
                return Optional.ofNullable(value);
            }
            if (returnType == Long.TYPE) {
                return value == null ? 0L : value;
            }
            if (returnType == Integer.TYPE) {
                return value == null ? 0 : value;
            }
            if (returnType == Boolean.TYPE) {
                return value != null && (Boolean) value;
            }
            return value;
        }

        private boolean wasCalled(String methodName) {
            return calls.stream().anyMatch(call -> call.methodName().equals(methodName));
        }

        private Call lastCall(String methodName) {
            return calls.stream()
                    .filter(call -> call.methodName().equals(methodName))
                    .reduce((first, second) -> second)
                    .orElseThrow();
        }
    }

    private record Call(String methodName, Object[] args) {
    }
}
