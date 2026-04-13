package com.webhookservice.service;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.dto.Page;
import com.webhookservice.model.dto.StatsResponse;
import com.webhookservice.repository.RequestLogRepository;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

import java.util.UUID;

public class RequestLogService {

    private final RequestLogRepository requestLogRepository;
    private final Vertx vertx;

    public RequestLogService(RequestLogRepository requestLogRepository, Vertx vertx) {
        this.requestLogRepository = requestLogRepository;
        this.vertx = vertx;
    }

    public Future<RequestLog> save(RequestLog requestLog, int maxLogCount) {
        return vertx.executeBlocking(() -> {
            RequestLog saved = requestLogRepository.save(requestLog);
            requestLogRepository.trimToMaxCount(requestLog.webhookId(), maxLogCount);
            return saved;
        });
    }

    public Future<Page<RequestLog>> listByWebhookId(UUID webhookId, int page, int size) {
        return vertx.executeBlocking(() -> requestLogRepository.findByWebhookId(webhookId, page, size));
    }

    public Future<RequestLog> getByWebhookIdAndId(UUID webhookId, UUID requestId) {
        return vertx.executeBlocking(() -> requestLogRepository.findByWebhookIdAndId(webhookId, requestId).orElse(null));
    }

    public Future<Long> clearByWebhookId(UUID webhookId) {
        return vertx.executeBlocking(() -> requestLogRepository.deleteByWebhookId(webhookId));
    }

    public Future<StatsResponse> getStats(UUID webhookId) {
        return vertx.executeBlocking(() -> requestLogRepository.getStats(webhookId));
    }
}
