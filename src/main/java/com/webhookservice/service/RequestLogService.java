package com.webhookservice.service;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.dto.Page;
import com.webhookservice.model.dto.StatsResponse;
import com.webhookservice.repository.RequestLogRepository;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class RequestLogService {
    private final RequestLogRepository requestLogRepository;
    private final Vertx vertx;
    public RequestLogService(RequestLogRepository requestLogRepository) {
        this(requestLogRepository, null);
    }

    public RequestLogService(RequestLogRepository requestLogRepository, Vertx vertx) {
        this.requestLogRepository = Objects.requireNonNull(requestLogRepository, "requestLogRepository");
        this.vertx = vertx;
    }
    public Future<RequestLog> save(RequestLog requestLog, int maxLogCount) {
        return callRepository("save", new Class<?>[]{RequestLog.class}, requestLog)
                .compose(result -> asRequestLogFuture(result))
                .compose(saved -> callRepository("trimToMaxCount", new Class<?>[]{UUID.class, int.class}, requestLog.webhookId(), maxLogCount)
                        .compose(this::asVoidFuture)
                        .map(ignored -> saved));
    }

    public Future<Page<RequestLog>> listByWebhookId(UUID webhookId, int page, int size) {
        return callRepository("findByWebhookId", new Class<?>[]{UUID.class, int.class, int.class}, webhookId, page, size)
                .compose(result -> asFuture(result, Page.class))
                .map(result -> (Page<RequestLog>) result);
    }

    public Future<RequestLog> getByWebhookIdAndId(UUID webhookId, UUID requestId) {
        return callRepository("findByWebhookIdAndId", new Class<?>[]{UUID.class, UUID.class}, webhookId, requestId)
                .compose(this::asOptionalOrDirectRequestLogFuture);
    }

    public Future<Long> clearByWebhookId(UUID webhookId) {
        return callRepository("deleteByWebhookId", new Class<?>[]{UUID.class}, webhookId)
                .compose(this::asLongFuture);
    }

    public Future<StatsResponse> getStats(UUID webhookId) {
        return callRepository("getStats", new Class<?>[]{UUID.class}, webhookId)
                .compose(result -> asFuture(result, StatsResponse.class));
    }

    private Future<Object> callRepository(String methodName, Class<?>[] parameterTypes, Object... args) {
        if (vertx == null) {
            try {
                return Future.succeededFuture(invokeRepository(methodName, parameterTypes, args));
            } catch (Throwable error) {
                return Future.failedFuture(error);
            }
        }

        return vertx.<Object>executeBlocking(promise -> {
            try {
                promise.complete(invokeRepository(methodName, parameterTypes, args));
            } catch (Throwable error) {
                promise.fail(error);
            }
        }, false);
    }

    private Object invokeRepository(String methodName, Class<?>[] parameterTypes, Object... args) throws Throwable {
        try {
            Method method = RequestLogRepository.class.getMethod(methodName, parameterTypes);
            return method.invoke(requestLogRepository, args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> Future<T> asFuture(Object result, Class<T> resultType) {
        if (result instanceof Future<?> future) {
            return future.map(value -> value == null ? null : resultType.cast(value));
        }
        return Future.succeededFuture(result == null ? null : resultType.cast(result));
    }

    private Future<RequestLog> asRequestLogFuture(Object result) {
        return asFuture(result, RequestLog.class);
    }

    private Future<RequestLog> asOptionalOrDirectRequestLogFuture(Object result) {
        if (result instanceof Future<?> future) {
            return future.compose(this::asOptionalOrDirectRequestLogFuture);
        }
        if (result instanceof Optional<?> optional) {
            Object value = optional.orElse(null);
            return Future.succeededFuture(value == null ? null : RequestLog.class.cast(value));
        }
        return Future.succeededFuture(result == null ? null : RequestLog.class.cast(result));
    }

    private Future<Long> asLongFuture(Object result) {
        if (result instanceof Future<?> future) {
            return future.compose(this::asLongFuture);
        }
        if (result == null) {
            return Future.succeededFuture(0L);
        }
        if (result instanceof Number number) {
            return Future.succeededFuture(number.longValue());
        }
        return Future.succeededFuture(Long.class.cast(result));
    }

    private Future<Void> asVoidFuture(Object result) {
        if (result instanceof Future<?> future) {
            return future.map(ignored -> null);
        }
        return Future.succeededFuture();
    }
}
