package com.webhookservice.service;

import com.webhookservice.model.Webhook;
import com.webhookservice.model.WebhookFactory;
import com.webhookservice.model.dto.CreateWebhookDto;
import com.webhookservice.model.dto.Page;
import com.webhookservice.model.dto.UpdateWebhookDto;
import com.webhookservice.repository.WebhookRepository;
import com.webhookservice.validation.ValidationException;
import com.webhookservice.validation.WebhookValidator;
import io.vertx.core.Future;

import java.util.UUID;

public class WebhookService {

    private final WebhookRepository webhookRepository;

    public WebhookService(WebhookRepository webhookRepository) {
        this.webhookRepository = webhookRepository;
    }

    public Future<Webhook> create(CreateWebhookDto dto) {
        try {
            WebhookValidator.validateCreate(dto);
        } catch (ValidationException e) {
            return Future.failedFuture(e);
        }
        Webhook webhook = WebhookFactory.create(dto);
        return webhookRepository.save(webhook);
    }

    public Future<Webhook> getById(UUID id) {
        return webhookRepository.findById(id).map(opt -> opt.orElse(null));
    }

    public Future<Webhook> getBySlug(String slug) {
        return webhookRepository.findBySlug(slug).map(opt -> opt.orElse(null));
    }

    public Future<Page<Webhook>> list(int page, int size) {
        return webhookRepository.findAll(page, size);
    }

    public Future<Webhook> update(UUID id, UpdateWebhookDto dto) {
        try {
            WebhookValidator.validateUpdate(dto);
        } catch (ValidationException e) {
            return Future.failedFuture(e);
        }
        return webhookRepository.update(id, existing -> WebhookFactory.applyUpdate(existing, dto));
    }

    public Future<Boolean> delete(UUID id) {
        return webhookRepository.deleteById(id);
    }

    public Future<Webhook> toggle(UUID id) {
        return webhookRepository.toggleActive(id);
    }
}
