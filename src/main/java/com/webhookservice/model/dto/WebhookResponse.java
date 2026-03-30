package com.webhookservice.model.dto;

import com.webhookservice.model.Webhook;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record WebhookResponse(
        UUID id,
        String name,
        String slug,
        String description,
        String endpointUrl,
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

    public static WebhookResponse from(Webhook webhook, String baseUrl) {
        String endpointUrl = baseUrl + "/webhook/" + webhook.slug();
        return new WebhookResponse(
                webhook.id(),
                webhook.name(),
                webhook.slug(),
                webhook.description(),
                endpointUrl,
                webhook.methods(),
                webhook.isActive(),
                webhook.debugMode(),
                webhook.proxyUrl(),
                webhook.proxyHeaders(),
                webhook.requestTemplate(),
                webhook.responseTemplate(),
                webhook.maxLogCount(),
                webhook.createdAt(),
                webhook.updatedAt()
        );
    }
}
