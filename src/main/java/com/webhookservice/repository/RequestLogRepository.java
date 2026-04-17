package com.webhookservice.repository;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.dto.Page;
import com.webhookservice.model.dto.StatsResponse;
import io.vertx.core.Future;

import java.util.Optional;
import java.util.UUID;

public interface RequestLogRepository {

    Future<RequestLog> save(RequestLog requestLog);

    Future<Page<RequestLog>> findByWebhookId(UUID webhookId, int page, int size);

    Future<Optional<RequestLog>> findByWebhookIdAndId(UUID webhookId, UUID requestId);

    Future<Long> deleteByWebhookId(UUID webhookId);

    Future<StatsResponse> getStats(UUID webhookId);

    Future<Void> trimToMaxCount(UUID webhookId, int maxCount);
}
