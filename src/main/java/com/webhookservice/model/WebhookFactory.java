package com.webhookservice.model;

import com.webhookservice.model.dto.CreateWebhookDto;
import com.webhookservice.model.dto.UpdateWebhookDto;
import com.webhookservice.util.IdGenerator;
import com.webhookservice.util.SlugGenerator;

import java.time.Instant;
import java.util.Map;

public final class WebhookFactory {

    private WebhookFactory() {}

    public static Webhook create(CreateWebhookDto dto) {
        Instant now = Instant.now();
        return new Webhook(
                IdGenerator.generate(),
                dto.name().trim(),
                SlugGenerator.generate(dto.name()),
                dto.description(),
                dto.methods() != null ? dto.methods().toUpperCase() : "GET,POST",
                true,
                dto.debugMode() != null ? dto.debugMode() : true,
                dto.proxyUrl(),
                dto.proxyHeaders() != null ? Map.copyOf(dto.proxyHeaders()) : Map.of(),
                dto.requestTemplate(),
                dto.responseTemplate(),
                dto.maxLogCount() != null ? dto.maxLogCount() : 100,
                now,
                now
        );
    }

    public static Webhook applyUpdate(Webhook existing, UpdateWebhookDto dto) {
        Instant now = Instant.now();
        return new Webhook(
                existing.id(),
                dto.name() != null ? dto.name().trim() : existing.name(),
                dto.name() != null ? SlugGenerator.generate(dto.name()) : existing.slug(),
                dto.description() != null ? dto.description() : existing.description(),
                dto.methods() != null ? dto.methods().toUpperCase() : existing.methods(),
                existing.isActive(),
                dto.debugMode() != null ? dto.debugMode() : existing.debugMode(),
                dto.proxyUrl() != null ? dto.proxyUrl() : existing.proxyUrl(),
                dto.proxyHeaders() != null ? Map.copyOf(dto.proxyHeaders()) : existing.proxyHeaders(),
                dto.requestTemplate() != null ? dto.requestTemplate() : existing.requestTemplate(),
                dto.responseTemplate() != null ? dto.responseTemplate() : existing.responseTemplate(),
                dto.maxLogCount() != null ? dto.maxLogCount() : existing.maxLogCount(),
                existing.createdAt(),
                now
        );
    }
}
