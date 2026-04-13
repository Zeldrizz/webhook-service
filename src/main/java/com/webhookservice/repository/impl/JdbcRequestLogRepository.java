package com.webhookservice.repository.impl;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.dto.Page;
import com.webhookservice.model.dto.StatsResponse;
import com.webhookservice.repository.RequestLogRepository;
import com.webhookservice.util.DatabaseManager;
import com.webhookservice.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class JdbcRequestLogRepository implements RequestLogRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcRequestLogRepository.class);

    private static final String INSERT_SQL = """
            INSERT INTO request_logs (
                id, webhook_id, received_at, method, url, query_params, headers,
                body, content_type, source_ip, response_status, proxy_response, proxy_duration_ms
            )
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_PAGE_SQL = """
            SELECT id, webhook_id, received_at, method, url, query_params, headers,
                   body, content_type, source_ip, response_status, proxy_response, proxy_duration_ms,
                   COUNT(*) OVER() AS total_count
            FROM request_logs
            WHERE webhook_id = ?
            ORDER BY received_at DESC, id DESC
            LIMIT ? OFFSET ?
            """;

    private static final String SELECT_BY_ID_SQL = """
            SELECT id, webhook_id, received_at, method, url, query_params, headers,
                   body, content_type, source_ip, response_status, proxy_response, proxy_duration_ms
            FROM request_logs
            WHERE webhook_id = ? AND id = ?
            """;

    private static final String DELETE_BY_WEBHOOK_SQL = """
            DELETE FROM request_logs
            WHERE webhook_id = ?
            """;

    private static final String TOTAL_COUNT_SQL = """
            SELECT COUNT(*)
            FROM request_logs
            WHERE webhook_id = ?
            """;

    private static final String TODAY_COUNT_SQL = """
            SELECT COUNT(*)
            FROM request_logs
            WHERE webhook_id = ? AND received_at >= ?
            """;

    private static final String LAST_REQUEST_SQL = """
            SELECT MAX(received_at)
            FROM request_logs
            WHERE webhook_id = ?
            """;

    private static final String METHOD_COUNTS_SQL = """
            SELECT method, COUNT(*) AS total
            FROM request_logs
            WHERE webhook_id = ?
            GROUP BY method
            ORDER BY method
            """;

    private static final String TRIM_SQL = """
            DELETE FROM request_logs
            WHERE id IN (
                SELECT id
                FROM request_logs
                WHERE webhook_id = ?
                ORDER BY received_at DESC, id DESC
                OFFSET ?
            )
            """;

    private final DatabaseManager db;

    public JdbcRequestLogRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public RequestLog save(RequestLog requestLog) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            setParams(ps, requestLog);
            ps.executeUpdate();
            return requestLog;
        } catch (SQLException e) {
            log.error("save failed: {}", requestLog.id(), e);
            throw new RuntimeException("save failed", e);
        }
    }

    @Override
    public Page<RequestLog> findByWebhookId(UUID webhookId, int page, int size) {
        int offset = page * size;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_PAGE_SQL)) {
            ps.setObject(1, webhookId);
            ps.setInt(2, size);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                List<RequestLog> items = new ArrayList<>();
                long total = 0;
                while (rs.next()) {
                    items.add(mapRow(rs));
                    total = rs.getLong("total_count");
                }
                return new Page<>(items, page, size, total);
            }
        } catch (SQLException e) {
            log.error("findByWebhookId failed: {}", webhookId, e);
            throw new RuntimeException("findByWebhookId failed", e);
        }
    }

    @Override
    public Optional<RequestLog> findByWebhookIdAndId(UUID webhookId, UUID requestId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID_SQL)) {
            ps.setObject(1, webhookId);
            ps.setObject(2, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findByWebhookIdAndId failed: webhookId={}, requestId={}", webhookId, requestId, e);
            throw new RuntimeException("findByWebhookIdAndId failed", e);
        }
    }

    @Override
    public long deleteByWebhookId(UUID webhookId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_BY_WEBHOOK_SQL)) {
            ps.setObject(1, webhookId);
            return ps.executeUpdate();
        } catch (SQLException e) {
            log.error("deleteByWebhookId failed: {}", webhookId, e);
            throw new RuntimeException("deleteByWebhookId failed", e);
        }
    }

    @Override
    public StatsResponse getStats(UUID webhookId) {
        try (Connection conn = db.getConnection()) {
            long totalRequests = queryLong(conn, TOTAL_COUNT_SQL, webhookId, null);
            Instant startOfDay = LocalDate.now(ZoneId.systemDefault())
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant();
            long todayRequests = queryLong(conn, TODAY_COUNT_SQL, webhookId, Timestamp.from(startOfDay));
            Instant lastRequestAt = queryLastRequestAt(conn, webhookId);
            Map<String, Long> methodCounts = queryMethodCounts(conn, webhookId);
            return new StatsResponse(totalRequests, todayRequests, methodCounts, lastRequestAt);
        } catch (SQLException e) {
            log.error("getStats failed: {}", webhookId, e);
            throw new RuntimeException("getStats failed", e);
        }
    }

    @Override
    public void trimToMaxCount(UUID webhookId, int maxCount) {
        if (maxCount < 1) {
            throw new IllegalArgumentException("maxCount must be positive");
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(TRIM_SQL)) {
            ps.setObject(1, webhookId);
            ps.setInt(2, maxCount);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                log.debug("trimmed request logs: webhookId={}, deleted={}", webhookId, deleted);
            }
        } catch (SQLException e) {
            log.error("trimToMaxCount failed: webhookId={}, maxCount={}", webhookId, maxCount, e);
            throw new RuntimeException("trimToMaxCount failed", e);
        }
    }

    private void setParams(PreparedStatement ps, RequestLog requestLog) throws SQLException {
        ps.setObject(1, requestLog.id());
        ps.setObject(2, requestLog.webhookId());
        ps.setTimestamp(3, Timestamp.from(requestLog.receivedAt()));
        ps.setString(4, requestLog.method());
        ps.setString(5, requestLog.url());
        ps.setString(6, JsonUtil.mapToJson(requestLog.queryParams()));
        ps.setString(7, JsonUtil.mapToJson(requestLog.headers()));
        ps.setString(8, requestLog.body());
        ps.setString(9, requestLog.contentType());
        ps.setString(10, requestLog.sourceIp());
        if (requestLog.responseStatus() != null) {
            ps.setInt(11, requestLog.responseStatus());
        } else {
            ps.setObject(11, null);
        }
        ps.setString(12, requestLog.proxyResponse());
        if (requestLog.proxyDurationMs() != null) {
            ps.setLong(13, requestLog.proxyDurationMs());
        } else {
            ps.setObject(13, null);
        }
    }

    private long queryLong(Connection conn, String sql, UUID webhookId, Timestamp timestamp) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, webhookId);
            if (timestamp != null) {
                ps.setTimestamp(2, timestamp);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private Instant queryLastRequestAt(Connection conn, UUID webhookId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(LAST_REQUEST_SQL)) {
            ps.setObject(1, webhookId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                Timestamp ts = rs.getTimestamp(1);
                return ts != null ? ts.toInstant() : null;
            }
        }
    }

    private Map<String, Long> queryMethodCounts(Connection conn, UUID webhookId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(METHOD_COUNTS_SQL)) {
            ps.setObject(1, webhookId);
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, Long> methodCounts = new LinkedHashMap<>();
                while (rs.next()) {
                    methodCounts.put(rs.getString("method"), rs.getLong("total"));
                }
                return methodCounts;
            }
        }
    }

    private RequestLog mapRow(ResultSet rs) throws SQLException {
        Timestamp receivedAt = rs.getTimestamp("received_at");
        return new RequestLog(
                rs.getObject("id", UUID.class),
                rs.getObject("webhook_id", UUID.class),
                receivedAt != null ? receivedAt.toInstant() : null,
                rs.getString("method"),
                rs.getString("url"),
                JsonUtil.jsonToStringMap(rs.getString("query_params")),
                JsonUtil.jsonToStringMap(rs.getString("headers")),
                rs.getString("body"),
                rs.getString("content_type"),
                rs.getString("source_ip"),
                (Integer) rs.getObject("response_status"),
                rs.getString("proxy_response"),
                (Long) rs.getObject("proxy_duration_ms")
        );
    }
}
