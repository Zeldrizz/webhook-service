package com.webhookservice.service;

import com.webhookservice.cache.CacheManager;
import com.webhookservice.cache.WebhookCache;
import com.webhookservice.model.Webhook;
import com.webhookservice.model.WebhookFactory;
import com.webhookservice.model.dto.CreateWebhookDto;
import com.webhookservice.model.dto.Page;
import com.webhookservice.model.dto.UpdateWebhookDto;
import com.webhookservice.repository.WebhookRepository;
import com.webhookservice.validation.ValidationException;
import com.webhookservice.validation.WebhookValidator;
import io.vertx.core.Future;

import java.util.Optional;
import java.util.UUID;

/**
 * Business-layer facade for the {@code webhooks} aggregate.
 *
 * <p>The service is the only owner of the {@link CacheManager}-backed
 * {@link WebhookCache}: lookups go through positive/negative caches before
 * falling through to the repository, and every mutation publishes a
 * {@link com.webhookservice.cache.CacheInvalidationEvent} so the cached copies
 * in <em>every</em> verticle are evicted.
 *
 * <p>The {@code CacheManager} dependency is optional. Tests that work with raw
 * mocks of the repository can construct the service without a cache manager
 * via the legacy constructor — in that case all cache calls collapse to
 * straight repository pass-through.
 */
public class WebhookService {

    private final WebhookRepository webhookRepository;
    private final CacheManager cacheManager;
    private final WebhookCache cache;

    public WebhookService(WebhookRepository webhookRepository) {
        this(webhookRepository, null);
    }

    public WebhookService(WebhookRepository webhookRepository, CacheManager cacheManager) {
        this.webhookRepository = webhookRepository;
        this.cacheManager = cacheManager;
        this.cache = cacheManager != null ? cacheManager.webhookCache() : null;
    }

    public Future<Webhook> create(CreateWebhookDto dto) {
        try {
            WebhookValidator.validateCreate(dto);
        } catch (ValidationException e) {
            return Future.failedFuture(e);
        }
        Webhook webhook = WebhookFactory.create(dto);
        return webhookRepository.save(webhook)
                .onSuccess(this::onAfterCreate);
    }

    public Future<Webhook> getById(UUID id) {
        if (cache != null) {
            Optional<Webhook> hit = cache.getById(id);
            if (hit.isPresent()) {
                return Future.succeededFuture(hit.get());
            }
        }
        return webhookRepository.findById(id)
                .map(opt -> opt.orElse(null))
                .onSuccess(this::cachePositiveIfNotNull);
    }

    public Future<Webhook> getBySlug(String slug) {
        if (cache != null) {
            Optional<Webhook> hit = cache.getBySlug(slug);
            if (hit.isPresent()) {
                return Future.succeededFuture(hit.get());
            }
            if (cache.isKnownNegative(slug)) {
                return Future.succeededFuture(null);
            }
        }
        return webhookRepository.findBySlug(slug)
                .map(opt -> opt.orElse(null))
                .onSuccess(found -> {
                    if (found != null) {
                        cachePositiveIfNotNull(found);
                    } else if (cache != null) {
                        cache.putNegativeSlug(slug);
                    }
                });
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
        return webhookRepository.update(id, existing -> WebhookFactory.applyUpdate(existing, dto))
                .onSuccess(this::onUpsert);
    }

    public Future<Boolean> delete(UUID id) {
        // Look up first to know the slug for negative-cache eviction; the lookup
        // can come from cache itself, which is free.
        return getById(id)
                .compose(existing -> webhookRepository.deleteById(id)
                        .onSuccess(removed -> {
                            if (Boolean.TRUE.equals(removed)) {
                                onDelete(existing != null ? existing.id() : id,
                                        existing != null ? existing.slug() : null);
                            }
                        }));
    }

    public Future<Webhook> toggle(UUID id) {
        return webhookRepository.toggleActive(id)
                .onSuccess(this::onUpsert);
    }

    private void cachePositiveIfNotNull(Webhook webhook) {
        if (cache != null && webhook != null) {
            cache.putWebhook(webhook);
        }
    }

    private void onAfterCreate(Webhook webhook) {
        // On create: clear only the local negative-slug entry (if any was cached before this slug existed).
        // No EventBus publish needed — no other verticle can have a positive cache entry for a brand-new slug,
        // and publishing UPSERT would race with the first getBySlug call populating the cache.
        if (cache != null && webhook != null) {
            cache.invalidate(webhook.id(), webhook.slug());
        }
    }

    private void onUpsert(Webhook webhook) {
        if (webhook == null) {
            return;
        }
        if (cache != null) {
            cache.invalidate(webhook.id(), webhook.slug());
        }
        if (cacheManager != null) {
            cacheManager.publishUpsert(webhook.id(), webhook.slug());
        }
    }

    private void onDelete(UUID id, String slug) {
        if (cache != null) {
            cache.invalidate(id, slug);
        }
        if (cacheManager != null) {
            cacheManager.publishDelete(id, slug);
        }
    }
}
