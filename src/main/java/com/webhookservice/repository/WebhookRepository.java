package com.webhookservice.repository;

import com.webhookservice.model.Webhook;
import com.webhookservice.model.dto.Page;

import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

public interface WebhookRepository {

    Webhook save(Webhook webhook);

    Optional<Webhook> findById(UUID id);

    Optional<Webhook> findBySlug(String slug);

    Page<Webhook> findAll(int page, int size);

    Webhook update(UUID id, UnaryOperator<Webhook> updater);

    Webhook toggleActive(UUID id);

    boolean deleteById(UUID id);
}
