package com.webhookservice.repository;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.dto.Page;
import com.webhookservice.model.dto.StatsResponse;
import io.vertx.core.Future;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequestLogRepository {

    Future<RequestLog> save(RequestLog requestLog);

    /**
     * Persist multiple {@link RequestLog} entries in a single round-trip.
     * Implementations should use a multi-row INSERT (or batched prepared
     * statement) to amortize the per-statement overhead. The order of rows
     * is not guaranteed to be preserved across the wire, but each row keeps
     * its own primary key from {@link RequestLog#id()}.
     */
    Future<Void> saveBatch(List<RequestLog> logs);

    Future<Page<RequestLog>> findByWebhookId(UUID webhookId, int page, int size);

    default Future<Page<RequestLog>> findByWebhookId(
            UUID webhookId,
            int page,
            int size,
            String method,
            String statusFilter
    ) {
        boolean noMethodFilter = method == null || method.isBlank();
        boolean noStatusFilter = statusFilter == null || statusFilter.isBlank() || "all".equals(statusFilter);
        if (noMethodFilter && noStatusFilter) {
            return findByWebhookId(webhookId, page, size);
        }
        return Future.failedFuture(new UnsupportedOperationException("Filtered request log lookup is not implemented"));
    }

    Future<Optional<RequestLog>> findByWebhookIdAndId(UUID webhookId, UUID requestId);

    Future<Long> deleteByWebhookId(UUID webhookId);

    Future<StatsResponse> getStats(UUID webhookId);

    Future<Void> trimToMaxCount(UUID webhookId, int maxCount);
}
