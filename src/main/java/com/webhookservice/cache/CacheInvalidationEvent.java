package com.webhookservice.cache;

import io.vertx.core.shareddata.Shareable;

import java.util.UUID;

/** EventBus message that triggers cache invalidation across all verticles on webhook mutations. */
public record CacheInvalidationEvent(Kind kind, UUID id, String slug) implements Shareable {

    public enum Kind { UPSERT, DELETE, FLUSH_ALL }

    public static CacheInvalidationEvent upsert(UUID id, String slug) {
        return new CacheInvalidationEvent(Kind.UPSERT, id, slug);
    }

    public static CacheInvalidationEvent delete(UUID id, String slug) {
        return new CacheInvalidationEvent(Kind.DELETE, id, slug);
    }

    public static CacheInvalidationEvent flushAll() {
        return new CacheInvalidationEvent(Kind.FLUSH_ALL, null, null);
    }
}
