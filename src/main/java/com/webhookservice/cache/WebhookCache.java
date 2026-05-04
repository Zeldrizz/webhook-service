package com.webhookservice.cache;

import com.webhookservice.model.Webhook;

import java.util.Optional;
import java.util.UUID;

/** Domain wrapper over slug/id positive caches and a negative-slug cache. */
public final class WebhookCache {

    private final CaffeineCache<String, Webhook> bySlug;
    private final CaffeineCache<UUID, Webhook> byId;
    private final CaffeineCache<String, Boolean> negativeSlug;

    public WebhookCache(
            CaffeineCache<String, Webhook> bySlug,
            CaffeineCache<UUID, Webhook> byId,
            CaffeineCache<String, Boolean> negativeSlug
    ) {
        this.bySlug = bySlug;
        this.byId = byId;
        this.negativeSlug = negativeSlug;
    }

    public Optional<Webhook> getBySlug(String slug) {
        return bySlug.getIfPresent(slug);
    }

    public Optional<Webhook> getById(UUID id) {
        return byId.getIfPresent(id);
    }

    /** {@code true} if a recent DB lookup confirmed this slug does not exist. */
    public boolean isKnownNegative(String slug) {
        return slug != null && negativeSlug.getIfPresent(slug).isPresent();
    }

    /** Store positive result in both indexes and clear any negative entry for the same slug. */
    public void putWebhook(Webhook webhook) {
        if (webhook == null) {
            return;
        }
        bySlug.put(webhook.slug(), webhook);
        byId.put(webhook.id(), webhook);
        negativeSlug.invalidate(webhook.slug());
    }

    public void putNegativeSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return;
        }
        negativeSlug.put(slug, Boolean.TRUE);
    }

    /** Drop both positive entries and the negative entry; either parameter may be {@code null}. */
    public void invalidate(UUID id, String slug) {
        if (id != null) {
            byId.invalidate(id);
        }
        if (slug != null) {
            bySlug.invalidate(slug);
            negativeSlug.invalidate(slug);
        }
    }
}
