package com.webhookservice.config;

import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public record AppConfig(
        int serverPort,
        String databaseUrl,
        String databaseUser,
        String databasePassword,
        int databasePoolSize,
        long databaseConnectionTimeout,
        long proxyTimeoutMs,
        int proxyMaxRetries,
        int webhookMaxLogCountDefault,
        int webhookCleanupIntervalHours
) {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    public static AppConfig load() {
        JsonObject config = loadYamlAsJson();
        return new AppConfig(
                config.getInteger("server.port", 8080),
                resolveEnv(config.getString("database.url", "jdbc:postgresql://localhost:5432/webhooks")),
                resolveEnv(config.getString("database.user", "webhook_user")),
                resolveEnv(config.getString("database.password", "webhook_pass")),
                config.getInteger("database.pool-size", 5),
                config.getLong("database.connection-timeout", 30000L),
                config.getLong("proxy.timeout-ms", 10000L),
                config.getInteger("proxy.max-retries", 0),
                config.getInteger("webhook.max-log-count-default", 100),
                config.getInteger("webhook.cleanup-interval-hours", 24)
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
        String[] prefixStack = new String[10];
        int[] indentStack = new int[10];
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

            // Adjust depth based on indentation
            while (depth > 0 && indent <= indentStack[depth - 1]) {
                depth--;
            }

            if (valuePart.isEmpty()) {
                // This is a parent key
                prefixStack[depth] = key;
                indentStack[depth] = indent;
                depth++;
            } else {
                // This is a leaf key: value
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
        return value;
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
