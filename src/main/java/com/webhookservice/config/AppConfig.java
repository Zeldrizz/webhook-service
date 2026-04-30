package com.webhookservice.config;

import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public record AppConfig(
        int serverPort,
        String databaseUrl,
        String databaseUser,
        String databasePassword,
        int databasePoolSize,
        int databasePipeliningLimit,
        long databaseConnectionTimeout,
        long proxyTimeoutMs,
        int proxyMaxRetries,
        long proxyRetryBaseDelayMs,
        long proxyRetryMaxDelayMs,
        double proxyRetryMultiplier,
        boolean proxyRetryJitter,
        boolean proxyRetryOnStatus5xx,
        boolean proxyCircuitBreakerEnabled,
        int proxyCircuitBreakerMaxFailures,
        long proxyCircuitBreakerResetMs,
        long proxyCircuitBreakerTimeoutMs,
        boolean authEnabled,
        String adminApiKey,
        int webhookMaxLogCountDefault,
        int webhookCleanupIntervalHours,
        boolean cacheEnabled,
        long cacheWebhookMaxSize,
        long cacheWebhookTtlSeconds,
        long cacheNegativeTtlSeconds,
        long cacheStatsMaxSize,
        long cacheStatsTtlSeconds,
        long cacheTemplateMaxSize,
        long cacheTemplateTtlSeconds,
        boolean requestLogBatchEnabled,
        int requestLogBatchMaxSize,
        long requestLogBatchFlushMs
) {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    public PgConnectOptions pgConnectOptions() {
        String resolvedUrl = resolveEnv(databaseUrl);
        String uri = stripJdbcPrefix(resolvedUrl);
        PgConnectOptions options = PgConnectOptions.fromUri(uri)
                .setUser(databaseUser())
                .setPassword(databasePassword())
                .setCachePreparedStatements(true)
                .setPipeliningLimit(databasePipeliningLimit)
                .setReconnectAttempts(2)
                .setReconnectInterval(1000);
        return options;
    }

    private static String stripJdbcPrefix(String url) {
        if (url != null && url.startsWith("jdbc:")) {
            return url.substring(5);
        }
        return url;
    }

    public static AppConfig load() {
        JsonObject config = loadYamlAsJson();
        return new AppConfig(
                config.getInteger("server.port", 8080),
                config.getString("database.url", "jdbc:postgresql://localhost:5432/webhooks"),
                config.getString("database.user", "webhook_user"),
                config.getString("database.password", "webhook_pass"),
                config.getInteger("database.pool-size", 32),
                config.getInteger("database.pipelining-limit", 256),
                config.getLong("database.connection-timeout", 30000L),
                config.getLong("proxy.timeout-ms", 10000L),
                config.getInteger("proxy.max-retries", 3),
                config.getLong("proxy.retry.base-delay-ms", 100L),
                config.getLong("proxy.retry.max-delay-ms", 5000L),
                getDoubleValue(config, "proxy.retry.multiplier", 2.0),
                config.getBoolean("proxy.retry.jitter", true),
                config.getBoolean("proxy.retry.retry-on-status-5xx", true),
                config.getBoolean("proxy.circuit-breaker.enabled", true),
                config.getInteger("proxy.circuit-breaker.max-failures", 5),
                config.getLong("proxy.circuit-breaker.reset-ms", 3000L),
                config.getLong("proxy.circuit-breaker.timeout-ms", 10000L),
                config.getBoolean("auth.enabled", true),
                config.getString("auth.admin-api-key", "changeme"),
                config.getInteger("webhook.max-log-count-default", 100),
                config.getInteger("webhook.cleanup-interval-hours", 24),
                config.getBoolean("cache.enabled", true),
                config.getLong("cache.webhook.max-size", 10000L),
                config.getLong("cache.webhook.ttl-seconds", 300L),
                config.getLong("cache.webhook.negative-ttl-seconds", 30L),
                config.getLong("cache.stats.max-size", 1000L),
                config.getLong("cache.stats.ttl-seconds", 30L),
                config.getLong("cache.template.max-size", 1000L),
                config.getLong("cache.template.ttl-seconds", 1800L),
                config.getBoolean("request-log.batch.enabled", true),
                config.getInteger("request-log.batch.max-size", 100),
                config.getLong("request-log.batch.flush-ms", 100L)
        );
    }

    private static JsonObject loadYamlAsJson() {
        JsonObject result = new JsonObject();
        try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream("application.yaml")) {
            if (is == null) {
                log.warn("application.yaml not found, using defaults");
                return result;
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            parseSimpleYaml(content, result);
        } catch (IOException e) {
            log.error("Failed to read application.yaml", e);
        }
        return result;
    }

    private static void parseSimpleYaml(String content, JsonObject result) {
        String[] lines = content.split("\n");
        String[] prefixStack = new String[16];
        int[] indentStack = new int[16];
        int depth = 0;

        for (String rawLine : lines) {
            if (rawLine.trim().isEmpty() || rawLine.trim().startsWith("#")) continue;

            int indent = 0;
            while (indent < rawLine.length() && rawLine.charAt(indent) == ' ') indent++;
            String line = rawLine.trim();

            int colonIdx = line.indexOf(':');
            if (colonIdx < 0) continue;

            String key = line.substring(0, colonIdx).trim();
            String valuePart = line.substring(colonIdx + 1).trim();

            while (depth > 0 && indent <= indentStack[depth - 1]) {
                depth--;
            }

            if (valuePart.isEmpty()) {
                prefixStack[depth] = key;
                indentStack[depth] = indent;
                depth++;
            } else {
                StringBuilder fullKey = new StringBuilder();
                for (int i = 0; i < depth; i++) {
                    fullKey.append(prefixStack[i]).append('.');
                }
                fullKey.append(key);

                String resolved = resolveEnv(valuePart);
                result.put(fullKey.toString(), parseValue(resolved));
            }
        }
    }

    private static Object parseValue(String value) {
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) {}
        try { return Long.parseLong(value); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(value); } catch (NumberFormatException ignored) {}
        return value;
    }

    /**
     * Lenient double getter that also accepts a stringified number (the simple
     * YAML parser in {@link #parseSimpleYaml} stores values verbatim until a
     * specific type is requested).
     */
    private static double getDoubleValue(JsonObject config, String key, double defaultValue) {
        Object raw = config.getValue(key);
        if (raw == null) return defaultValue;
        if (raw instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(raw.toString()); } catch (NumberFormatException e) { return defaultValue; }
    }

    private static String resolveEnv(String value) {
        if (value == null || !value.contains("${")) return value;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            int start = value.indexOf("${", i);
            if (start < 0) {
                sb.append(value, i, value.length());
                break;
            }
            sb.append(value, i, start);
            int end = value.indexOf('}', start);
            if (end < 0) {
                sb.append(value, start, value.length());
                break;
            }
            String placeholder = value.substring(start + 2, end);
            int colonIdx = placeholder.indexOf(':');
            String envName, defaultVal;
            if (colonIdx >= 0) {
                envName = placeholder.substring(0, colonIdx);
                defaultVal = placeholder.substring(colonIdx + 1);
            } else {
                envName = placeholder;
                defaultVal = "";
            }
            String envVal = System.getenv(envName);
            sb.append(envVal != null ? envVal : defaultVal);
            i = end + 1;
        }
        return sb.toString();
    }
}
