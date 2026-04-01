package com.webhookservice.repository.impl;

import com.webhookservice.model.Webhook;
import com.webhookservice.model.dto.Page;
import com.webhookservice.repository.WebhookRepository;
import com.webhookservice.util.DatabaseManager;
import com.webhookservice.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.UnaryOperator;

public class JdbcWebhookRepository implements WebhookRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcWebhookRepository.class);

    private static final String INSERT_SQL = """
            INSERT INTO webhooks (id, name, slug, description, methods, is_active, debug_mode,
                proxy_url, proxy_headers, request_template, response_template, max_log_count,
                created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_ID_SQL = """
            SELECT id, name, slug, description, methods, is_active, debug_mode,
                proxy_url, proxy_headers, request_template, response_template, max_log_count,
                created_at, updated_at
            FROM webhooks WHERE id = ?
            """;

    private static final String SELECT_BY_SLUG_SQL = """
            SELECT id, name, slug, description, methods, is_active, debug_mode,
                proxy_url, proxy_headers, request_template, response_template, max_log_count,
                created_at, updated_at
            FROM webhooks WHERE slug = ?
            """;

    private static final String SELECT_ALL_SQL = """
            SELECT id, name, slug, description, methods, is_active, debug_mode,
                proxy_url, proxy_headers, request_template, response_template, max_log_count,
                created_at, updated_at, COUNT(*) OVER() AS total_count
            FROM webhooks ORDER BY created_at DESC LIMIT ? OFFSET ?
            """;

    private static final String UPDATE_SQL = """
            UPDATE webhooks SET name = ?, slug = ?, description = ?, methods = ?,
                is_active = ?, debug_mode = ?, proxy_url = ?, proxy_headers = ?::jsonb,
                request_template = ?, response_template = ?, max_log_count = ?, updated_at = ?
            WHERE id = ?
            """;

    private static final String TOGGLE_SQL = """
            UPDATE webhooks SET is_active = NOT is_active, updated_at = ?
            WHERE id = ?
            """;

    private static final String DELETE_SQL = "DELETE FROM webhooks WHERE id = ?";

    private final DatabaseManager db;

    public JdbcWebhookRepository(DatabaseManager db) {
        this.db = db;
    }

    @Override
    public Webhook save(Webhook webhook) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(INSERT_SQL)) {
            setWebhookParams(ps, webhook);
            ps.executeUpdate();
            return webhook;
        } catch (SQLException e) {
            log.error("save failed: {}", webhook.id(), e);
            throw new RuntimeException("save failed", e);
        }
    }

    @Override
    public Optional<Webhook> findById(UUID id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID_SQL)) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findById failed: {}", id, e);
            throw new RuntimeException("findById failed", e);
        }
    }

    @Override
    public Optional<Webhook> findBySlug(String slug) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_BY_SLUG_SQL)) {
            ps.setString(1, slug);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            log.error("findBySlug failed: {}", slug, e);
            throw new RuntimeException("findBySlug failed", e);
        }
    }

    @Override
    public Page<Webhook> findAll(int page, int size) {
        int offset = page * size;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ALL_SQL)) {
            ps.setInt(1, size);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                List<Webhook> result = new ArrayList<>();
                long total = 0;
                while (rs.next()) {
                    result.add(mapRow(rs));
                    total = rs.getLong("total_count");
                }
                return new Page<>(result, page, size, total);
            }
        } catch (SQLException e) {
            log.error("findAll failed", e);
            throw new RuntimeException("findAll failed", e);
        }
    }

    @Override
    public Webhook update(UUID id, UnaryOperator<Webhook> updater) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Webhook existing;
                try (PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID_SQL + " FOR UPDATE")) {
                    ps.setObject(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new RuntimeException("Webhook not found: " + id);
                        existing = mapRow(rs);
                    }
                }
                Webhook updated = updater.apply(existing);
                try (PreparedStatement ps = conn.prepareStatement(UPDATE_SQL)) {
                    setUpdateParams(ps, updated);
                    ps.executeUpdate();
                }
                conn.commit();
                return updated;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("update failed: {}", id, e);
            throw new RuntimeException("update failed", e);
        }
    }

    @Override
    public Webhook toggleActive(UUID id) {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(TOGGLE_SQL)) {
                    ps.setTimestamp(1, Timestamp.from(Instant.now()));
                    ps.setObject(2, id);
                    if (ps.executeUpdate() == 0) throw new RuntimeException("Webhook not found: " + id);
                }
                try (PreparedStatement ps = conn.prepareStatement(SELECT_BY_ID_SQL)) {
                    ps.setObject(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        Webhook result = mapRow(rs);
                        conn.commit();
                        return result;
                    }
                }
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            log.error("toggleActive failed: {}", id, e);
            throw new RuntimeException("toggleActive failed", e);
        }
    }

    @Override
    public boolean deleteById(UUID id) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(DELETE_SQL)) {
            ps.setObject(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.error("delete failed: {}", id, e);
            throw new RuntimeException("delete failed", e);
        }
    }

    private void setWebhookParams(PreparedStatement ps, Webhook w) throws SQLException {
        ps.setObject(1, w.id());
        ps.setString(2, w.name());
        ps.setString(3, w.slug());
        ps.setString(4, w.description());
        ps.setString(5, w.methods());
        ps.setBoolean(6, w.isActive());
        ps.setBoolean(7, w.debugMode());
        ps.setString(8, w.proxyUrl());
        ps.setString(9, JsonUtil.mapToJson(w.proxyHeaders()));
        ps.setString(10, w.requestTemplate());
        ps.setString(11, w.responseTemplate());
        ps.setInt(12, w.maxLogCount());
        ps.setTimestamp(13, Timestamp.from(w.createdAt()));
        ps.setTimestamp(14, Timestamp.from(w.updatedAt()));
    }

    private void setUpdateParams(PreparedStatement ps, Webhook w) throws SQLException {
        ps.setString(1, w.name());
        ps.setString(2, w.slug());
        ps.setString(3, w.description());
        ps.setString(4, w.methods());
        ps.setBoolean(5, w.isActive());
        ps.setBoolean(6, w.debugMode());
        ps.setString(7, w.proxyUrl());
        ps.setString(8, JsonUtil.mapToJson(w.proxyHeaders()));
        ps.setString(9, w.requestTemplate());
        ps.setString(10, w.responseTemplate());
        ps.setInt(11, w.maxLogCount());
        ps.setTimestamp(12, Timestamp.from(w.updatedAt()));
        ps.setObject(13, w.id());
    }

    private Webhook mapRow(ResultSet rs) throws SQLException {
        return new Webhook(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("description"),
                rs.getString("methods"),
                rs.getBoolean("is_active"),
                rs.getBoolean("debug_mode"),
                rs.getString("proxy_url"),
                JsonUtil.jsonToStringMap(rs.getString("proxy_headers")),
                rs.getString("request_template"),
                rs.getString("response_template"),
                rs.getInt("max_log_count"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
