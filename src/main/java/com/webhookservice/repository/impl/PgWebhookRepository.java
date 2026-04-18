package com.webhookservice.repository.impl;

import com.webhookservice.model.Webhook;
import com.webhookservice.model.dto.Page;
import com.webhookservice.repository.WebhookRepository;
import com.webhookservice.util.JsonUtil;
import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Reactive PostgreSQL implementation io.vertx.pgclient.PgPool
 * All operations return Future and never block the calling event loop.
 */
public class PgWebhookRepository implements WebhookRepository {

    private static final String INSERT_SQL = """
            INSERT INTO webhooks (id, name, slug, description, methods, is_active, debug_mode,
                proxy_url, proxy_headers, request_template, response_template, max_log_count,
                created_at, updated_at)
            VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9::jsonb, $10, $11, $12, $13, $14)
            """;

    private static final String SELECT_BY_ID_SQL = """
            SELECT id, name, slug, description, methods, is_active, debug_mode,
                proxy_url, proxy_headers, request_template, response_template, max_log_count,
                created_at, updated_at
            FROM webhooks WHERE id = $1
            """;

    private static final String SELECT_BY_ID_FOR_UPDATE_SQL = SELECT_BY_ID_SQL + " FOR UPDATE";

    private static final String SELECT_BY_SLUG_SQL = """
            SELECT id, name, slug, description, methods, is_active, debug_mode,
                proxy_url, proxy_headers, request_template, response_template, max_log_count,
                created_at, updated_at
            FROM webhooks WHERE slug = $1
            """;

    private static final String SELECT_ALL_SQL = """
            SELECT id, name, slug, description, methods, is_active, debug_mode,
                proxy_url, proxy_headers, request_template, response_template, max_log_count,
                created_at, updated_at, COUNT(*) OVER() AS total_count
            FROM webhooks ORDER BY created_at DESC LIMIT $1 OFFSET $2
            """;

    private static final String UPDATE_SQL = """
            UPDATE webhooks SET name = $1, slug = $2, description = $3, methods = $4,
                is_active = $5, debug_mode = $6, proxy_url = $7, proxy_headers = $8::jsonb,
                request_template = $9, response_template = $10, max_log_count = $11, updated_at = $12
            WHERE id = $13
            """;

    private static final String TOGGLE_SQL = """
            UPDATE webhooks SET is_active = NOT is_active, updated_at = $1
            WHERE id = $2
            """;

    private static final String DELETE_SQL = "DELETE FROM webhooks WHERE id = $1";

    private final Pool pool;

    public PgWebhookRepository(Pool pool) {
        this.pool = pool;
    }

    @Override
    public Future<Webhook> save(Webhook w) {
        return pool.preparedQuery(INSERT_SQL)
                .execute(insertTuple(w))
                .map(rs -> w);
    }

    @Override
    public Future<Optional<Webhook>> findById(UUID id) {
        return pool.preparedQuery(SELECT_BY_ID_SQL)
                .execute(Tuple.of(id))
                .map(this::firstWebhook);
    }

    @Override
    public Future<Optional<Webhook>> findBySlug(String slug) {
        return pool.preparedQuery(SELECT_BY_SLUG_SQL)
                .execute(Tuple.of(slug))
                .map(this::firstWebhook);
    }

    @Override
    public Future<Page<Webhook>> findAll(int page, int size) {
        int offset = page * size;
        return pool.preparedQuery(SELECT_ALL_SQL)
                .execute(Tuple.of(size, offset))
                .map(rs -> {
                    List<Webhook> items = new ArrayList<>(rs.size());
                    long total = 0;
                    for (Row row : rs) {
                        items.add(mapRow(row));
                        total = row.getLong("total_count");
                    }
                    return new Page<>(items, page, size, total);
                });
    }

    @Override
    public Future<Webhook> update(UUID id, UnaryOperator<Webhook> updater) {
        return pool.withTransaction(client ->
                client.preparedQuery(SELECT_BY_ID_FOR_UPDATE_SQL)
                        .execute(Tuple.of(id))
                        .compose(rs -> {
                            if (rs.size() == 0) {
                                return Future.failedFuture(new RuntimeException("Webhook not found: " + id));
                            }
                            Webhook existing = mapRow(rs.iterator().next());
                            Webhook updated = updater.apply(existing);
                            return client.preparedQuery(UPDATE_SQL)
                                    .execute(updateTuple(updated))
                                    .map(updated);
                        }));
    }

    @Override
    public Future<Webhook> toggleActive(UUID id) {
        return pool.withTransaction(client ->
                client.preparedQuery(TOGGLE_SQL)
                        .execute(Tuple.of(LocalDateTime.now(ZoneOffset.UTC), id))
                        .compose(rs -> {
                            if (rs.rowCount() == 0) {
                                return Future.failedFuture(new RuntimeException("Webhook not found: " + id));
                            }
                            return client.preparedQuery(SELECT_BY_ID_SQL)
                                    .execute(Tuple.of(id))
                                    .map(sel -> mapRow(sel.iterator().next()));
                        }));
    }

    @Override
    public Future<Boolean> deleteById(UUID id) {
        return pool.preparedQuery(DELETE_SQL)
                .execute(Tuple.of(id))
                .map(rs -> rs.rowCount() > 0);
    }

    private Optional<Webhook> firstWebhook(RowSet<Row> rs) {
        return rs.size() == 0 ? Optional.empty() : Optional.of(mapRow(rs.iterator().next()));
    }

    private Tuple insertTuple(Webhook w) {
        return Tuple.of(
                w.id(),
                w.name(),
                w.slug(),
                w.description(),
                w.methods(),
                w.isActive(),
                w.debugMode(),
                w.proxyUrl(),
                JsonUtil.mapToJson(w.proxyHeaders()),
                w.requestTemplate()
        ).addValue(w.responseTemplate())
         .addValue(w.maxLogCount())
         .addValue(LocalDateTime.ofInstant(w.createdAt(), ZoneOffset.UTC))
         .addValue(LocalDateTime.ofInstant(w.updatedAt(), ZoneOffset.UTC));
    }

    private Tuple updateTuple(Webhook w) {
        return Tuple.of(
                w.name(),
                w.slug(),
                w.description(),
                w.methods(),
                w.isActive(),
                w.debugMode(),
                w.proxyUrl(),
                JsonUtil.mapToJson(w.proxyHeaders()),
                w.requestTemplate(),
                w.responseTemplate()
        ).addValue(w.maxLogCount())
         .addValue(LocalDateTime.ofInstant(w.updatedAt(), ZoneOffset.UTC))
         .addValue(w.id());
    }

    private Webhook mapRow(Row row) {
        Object headers = row.getValue("proxy_headers");
        String headersJson = headers == null ? null : headers.toString();
        return new Webhook(
                row.getUUID("id"),
                row.getString("name"),
                row.getString("slug"),
                row.getString("description"),
                row.getString("methods"),
                row.getBoolean("is_active"),
                row.getBoolean("debug_mode"),
                row.getString("proxy_url"),
                JsonUtil.jsonToStringMap(headersJson),
                row.getString("request_template"),
                row.getString("response_template"),
                row.getInteger("max_log_count"),
                row.getLocalDateTime("created_at").toInstant(ZoneOffset.UTC),
                row.getLocalDateTime("updated_at").toInstant(ZoneOffset.UTC)
        );
    }
}
