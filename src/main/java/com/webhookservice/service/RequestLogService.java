package com.webhookservice.service;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.dto.Page;
import com.webhookservice.model.dto.StatsResponse;
import com.webhookservice.repository.RequestLogRepository;
import io.vertx.core.Future;

import java.util.UUID;

public class RequestLogService {

    private final RequestLogRepository requestLogRepository;

    public RequestLogService(RequestLogRepository requestLogRepository) {
        this.requestLogRepository = requestLogRepository;
    }

    public Future<RequestLog> save(RequestLog requestLog, int maxLogCount) {
        return requestLogRepository.save(requestLog)
                .compose(saved -> requestLogRepository.trimToMaxCount(requestLog.webhookId(), maxLogCount)
                        .map(v -> saved));
    }

    public Future<Page<RequestLog>> listByWebhookId(UUID webhookId, int page, int size) {
        return requestLogRepository.findByWebhookId(webhookId, page, size);
    }

    public Future<RequestLog> getByWebhookIdAndId(UUID webhookId, UUID requestId) {
        return requestLogRepository.findByWebhookIdAndId(webhookId, requestId)
                .map(opt -> opt.orElse(null));
    }

    public Future<Long> clearByWebhookId(UUID webhookId) {
        return requestLogRepository.deleteByWebhookId(webhookId);
    }

    public Future<StatsResponse> getStats(UUID webhookId) {
        return requestLogRepository.getStats(webhookId);
    }
}
