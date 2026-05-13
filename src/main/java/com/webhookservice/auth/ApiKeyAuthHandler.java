package com.webhookservice.auth;

import com.webhookservice.util.JsonUtil;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * Gates admin endpoints behind {@code X-API-Key}. Compares SHA-256 hashes via
 * {@link MessageDigest#isEqual} (constant-time, length-independent).
 * {@code auth.enabled=false} turns the handler into a no-op.
 */
public final class ApiKeyAuthHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthHandler.class);

    public static final String HEADER_NAME = "X-API-Key";

    private final boolean enabled;
    private final byte[] expectedKeyHash;

    public ApiKeyAuthHandler(boolean enabled, String expectedKey) {
        this.enabled = enabled;
        this.expectedKeyHash = enabled ? sha256(expectedKey) : new byte[0];
        if (enabled && (expectedKey == null || expectedKey.isBlank() || "changeme".equals(expectedKey))) {
            log.warn("auth.admin-api-key is unset or left at the default 'changeme' — set ADMIN_API_KEY env var before production");
        }
    }

    @Override
    public void handle(RoutingContext ctx) {
        if (!enabled) {
            ctx.next();
            return;
        }
        String provided = ctx.request().getHeader(HEADER_NAME);
        if (provided == null || provided.isBlank()) {
            sendUnauthorized(ctx, "Missing X-API-Key header");
            return;
        }
        byte[] providedHash = sha256(provided);
        if (!MessageDigest.isEqual(providedHash, expectedKeyHash)) {
            sendUnauthorized(ctx, "Invalid API key");
            return;
        }
        ctx.next();
    }

    private void sendUnauthorized(RoutingContext ctx, String message) {
        ctx.response()
                .setStatusCode(401)
                .putHeader("Content-Type", "application/json")
                .putHeader("WWW-Authenticate", "ApiKey realm=\"webhook-service\"")
                .end(JsonUtil.toJson(Map.of(
                        "status", 401,
                        "message", message
                )));
    }

    private static byte[] sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable on this JRE", e);
        }
    }
}
