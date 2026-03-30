package com.webhookservice.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Webhook(
        UUID id,
        String name,
        String slug,
        String description,
        String methods,
        boolean isActive,
        boolean debugMode,
        String proxyUrl,
        Map<String, String> proxyHeaders,
        String requestTemplate,
        String responseTemplate,
        int maxLogCount,
        Instant createdAt,
        Instant updatedAt
) {

    public Webhook withActive(boolean active) {
        return new Webhook(id, name, slug, description, methods, active, debugMode,
                proxyUrl, proxyHeaders, requestTemplate, responseTemplate,
                maxLogCount, createdAt, Instant.now());
    }

    public Webhook withUpdatedAt(Instant updatedAt) {
        return new Webhook(id, name, slug, description, methods, isActive, debugMode,
                proxyUrl, proxyHeaders, requestTemplate, responseTemplate,
                maxLogCount, createdAt, updatedAt);
    }
}
