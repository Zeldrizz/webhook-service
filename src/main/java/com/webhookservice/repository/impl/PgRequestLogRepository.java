package com.webhookservice.repository.impl;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.dto.Page;
import com.webhookservice.model.dto.StatsResponse;
import com.webhookservice.repository.RequestLogRepository;
import com.webhookservice.util.JsonUtil;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Reactive PostgreSQL implementation io.vertx.pgclient.PgPool
 * All operations return Future and never block the calling event loop.
 */
public class PgRequestLogRepository implements RequestLogRepository {

    private static final String INSERT_SQL = """
            INSERT INTO request_logs (
                id, webhook_id, received_at, method, url, query_params, headers,
                body, content_type, source_ip, response_status, proxy_response, proxy_duration_ms
            )
            VALUES ($1, $2, $3, $4, $5, $6::jsonb, $7::jsonb, $8, $9, $10, $11, $12, $13)
            """;

    private static final String SELECT_PAGE_SQL = """
            SELECT id, webhook_id, received_at, method, url, query_params, headers,
                   body, content_type, source_ip, response_status, proxy_response, proxy_duration_ms,
                   COUNT(*) OVER() AS total_count
            FROM request_logs
            WHERE webhook_id = $1
            ORDER BY received_at DESC, id DESC
            LIMIT $2 OFFSET $3
            """;

    private static final String SELECT_BY_ID_SQL = """
            SELECT id, webhook_id, received_at, method, url, query_params, headers,
                   body, content_type, source_ip, response_status, proxy_response, proxy_duration_ms
            FROM request_logs
            WHERE webhook_id = $1 AND id = $2
            """;

    private static final String DELETE_BY_WEBHOOK_SQL = "DELETE FROM request_logs WHERE webhook_id = $1";

    private static final String TOTAL_COUNT_SQL = "SELECT COUNT(*) AS c FROM request_logs WHERE webhook_id = $1";

    private static final String TODAY_COUNT_SQL = """
            SELECT COUNT(*) AS c FROM request_logs
            WHERE webhook_id = $1 AND received_at >= $2
            """;

    private static final String LAST_REQUEST_SQL = """
            SELECT MAX(received_at) AS last_at FROM request_logs WHERE webhook_id = $1
            """;

    private static final String METHOD_COUNTS_SQL = """
            SELECT method, COUNT(*) AS total FROM request_logs
            WHERE webhook_id = $1
            GROUP BY method
            ORDER BY method
            """;

    private static final String TRIM_SQL = """
            DELETE FROM request_logs
            WHERE id IN (
                SELECT id FROM request_logs
                WHERE webhook_id = $1
                ORDER BY received_at DESC, id DESC
                OFFSET $2
            )
            """;

    private final Pool pool;

    public PgRequestLogRepository(Pool pool) {
        this.pool = pool;
    }

    @Override
    public Future<RequestLog> save(RequestLog r) {
        return pool.preparedQuery(INSERT_SQL)
                .execute(insertTuple(r))
                .map(rs -> r);
    }

    @Override
    public Future<Page<RequestLog>> findByWebhookId(UUID webhookId, int page, int size) {
        int offset = page * size;
        return pool.preparedQuery(SELECT_PAGE_SQL)
                .execute(Tuple.of(webhookId, size, offset))
                .map(rs -> {
                    List<RequestLog> items = new ArrayList<>(rs.size());
                    long total = 0;
                    for (Row row : rs) {
                        items.add(mapRow(row));
                        total = row.getLong("total_count");
                    }
                    return new Page<>(items, page, size, total);
                });
    }

    @Override
    public Future<Optional<RequestLog>> findByWebhookIdAndId(UUID webhookId, UUID requestId) {
        return pool.preparedQuery(SELECT_BY_ID_SQL)
                .execute(Tuple.of(webhookId, requestId))
                .map(rs -> rs.size() == 0
                        ? Optional.<RequestLog>empty()
                        : Optional.of(mapRow(rs.iterator().next())));
    }

    @Override
    public Future<Long> deleteByWebhookId(UUID webhookId) {
        return pool.preparedQuery(DELETE_BY_WEBHOOK_SQL)
                .execute(Tuple.of(webhookId))
                .map(rs -> (long) rs.rowCount());
    }

    @Override
    public Future<StatsResponse> getStats(UUID webhookId) {
        Instant startOfDay = LocalDate.now(ZoneId.systemDefault())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();
        LocalDateTime startOfDayLdt = LocalDateTime.ofInstant(startOfDay, ZoneOffset.UTC);

        Future<Long> totalF = pool.preparedQuery(TOTAL_COUNT_SQL)
                .execute(Tuple.of(webhookId))
                .map(rs -> rs.iterator().next().getLong("c"));

        Future<Long> todayF = pool.preparedQuery(TODAY_COUNT_SQL)
                .execute(Tuple.of(webhookId, startOfDayLdt))
                .map(rs -> rs.iterator().next().getLong("c"));

        Future<Instant> lastF = pool.preparedQuery(LAST_REQUEST_SQL)
                .execute(Tuple.of(webhookId))
                .map(rs -> {
                    LocalDateTime ldt = rs.iterator().next().getLocalDateTime("last_at");
                    return ldt == null ? null : ldt.toInstant(ZoneOffset.UTC);
                });

        Future<Map<String, Long>> methodsF = pool.preparedQuery(METHOD_COUNTS_SQL)
                .execute(Tuple.of(webhookId))
                .map(rs -> {
                    Map<String, Long> m = new LinkedHashMap<>();
                    for (Row row : rs) {
                        m.put(row.getString("method"), row.getLong("total"));
                    }
                    return m;
                });

        return CompositeFuture.all(totalF, todayF, lastF, methodsF)
                .map(cf -> new StatsResponse(
                        totalF.result(),
                        todayF.result(),
                        methodsF.result(),
                        lastF.result()
                ));
    }

    @Override
    public Future<Void> trimToMaxCount(UUID webhookId, int maxCount) {
        if (maxCount < 1) {
            return Future.failedFuture(new IllegalArgumentException("maxCount must be positive"));
        }
        return pool.preparedQuery(TRIM_SQL)
                .execute(Tuple.of(webhookId, maxCount))
                .mapEmpty();
    }

    private Tuple insertTuple(RequestLog r) {
        return Tuple.of(
                r.id(),
                r.webhookId(),
                LocalDateTime.ofInstant(r.receivedAt(), ZoneOffset.UTC),
                r.method(),
                r.url(),
                JsonUtil.mapToJson(r.queryParams()),
                JsonUtil.mapToJson(r.headers()),
                r.body(),
                r.contentType(),
                r.sourceIp()
        ).addValue(r.responseStatus())
         .addValue(r.proxyResponse())
         .addValue(r.proxyDurationMs());
    }

    private RequestLog mapRow(Row row) {
        Object qp = row.getValue("query_params");
        Object hd = row.getValue("headers");
        LocalDateTime receivedAt = row.getLocalDateTime("received_at");
        return new RequestLog(
                row.getUUID("id"),
                row.getUUID("webhook_id"),
                receivedAt == null ? null : receivedAt.toInstant(ZoneOffset.UTC),
                row.getString("method"),
                row.getString("url"),
                JsonUtil.jsonToStringMap(qp == null ? null : qp.toString()),
                JsonUtil.jsonToStringMap(hd == null ? null : hd.toString()),
                row.getString("body"),
                row.getString("content_type"),
                row.getString("source_ip"),
                row.getInteger("response_status"),
                row.getString("proxy_response"),
                row.getLong("proxy_duration_ms")
        );
    }
}
