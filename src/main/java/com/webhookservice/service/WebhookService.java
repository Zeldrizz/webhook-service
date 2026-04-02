package com.webhookservice.service;

import com.webhookservice.model.Webhook;
import com.webhookservice.model.WebhookFactory;
import com.webhookservice.model.dto.CreateWebhookDto;
import com.webhookservice.model.dto.Page;
import com.webhookservice.model.dto.UpdateWebhookDto;
import com.webhookservice.repository.WebhookRepository;
import com.webhookservice.validation.WebhookValidator;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    private final WebhookRepository webhookRepository;
    private final Vertx vertx;

    public WebhookService(WebhookRepository webhookRepository, Vertx vertx) {
        this.webhookRepository = webhookRepository;
        this.vertx = vertx;
    }

    public Future<Webhook> create(CreateWebhookDto dto) {
        return vertx.executeBlocking(() -> {
            WebhookValidator.validateCreate(dto);
            Webhook webhook = WebhookFactory.create(dto);
            return webhookRepository.save(webhook);
        });
    }

    public Future<Webhook> getById(UUID id) {
        return vertx.executeBlocking(() ->
                webhookRepository.findById(id).orElse(null));
    }

    public Future<Webhook> getBySlug(String slug) {
        return vertx.executeBlocking(() ->
                webhookRepository.findBySlug(slug).orElse(null));
    }

    public Future<Page<Webhook>> list(int page, int size) {
        return vertx.executeBlocking(() -> webhookRepository.findAll(page, size));
    }

    public Future<Webhook> update(UUID id, UpdateWebhookDto dto) {
        return vertx.executeBlocking(() -> {
            WebhookValidator.validateUpdate(dto);
            return webhookRepository.update(id, existing -> WebhookFactory.applyUpdate(existing, dto));
        });
    }

    public Future<Boolean> delete(UUID id) {
        return vertx.executeBlocking(() -> webhookRepository.deleteById(id));
    }

    public Future<Webhook> toggle(UUID id) {
        return vertx.executeBlocking(() -> webhookRepository.toggleActive(id));
    }
}
