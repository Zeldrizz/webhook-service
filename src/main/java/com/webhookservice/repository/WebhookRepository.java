package com.webhookservice.repository;

import com.webhookservice.model.Webhook;
import com.webhookservice.model.dto.Page;
import io.vertx.core.Future;

import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public interface WebhookRepository {

    Future<Webhook> save(Webhook webhook);

    Future<Optional<Webhook>> findById(UUID id);

    Future<Optional<Webhook>> findBySlug(String slug);

    Future<Page<Webhook>> findAll(int page, int size);

    Future<Webhook> update(UUID id, UnaryOperator<Webhook> updater);

    Future<Webhook> toggleActive(UUID id);

    Future<Boolean> deleteById(UUID id);
}
