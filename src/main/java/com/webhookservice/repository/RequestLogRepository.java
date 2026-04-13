package com.webhookservice.repository;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.dto.Page;
import com.webhookservice.model.dto.StatsResponse;

import java.util.Optional;
import java.util.UUID;

public interface RequestLogRepository {

    RequestLog save(RequestLog requestLog);

    Page<RequestLog> findByWebhookId(UUID webhookId, int page, int size);

    Optional<RequestLog> findByWebhookIdAndId(UUID webhookId, UUID requestId);

    long deleteByWebhookId(UUID webhookId);

    StatsResponse getStats(UUID webhookId);

    void trimToMaxCount(UUID webhookId, int maxCount);
}
