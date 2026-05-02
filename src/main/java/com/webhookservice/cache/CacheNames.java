package com.webhookservice.cache;

/** Cache name constants. Used as registry keys and surfaced in {@code /api/cache/stats}. */
public final class CacheNames {

    public static final String WEBHOOK_BY_SLUG = "webhookBySlug";
    public static final String WEBHOOK_BY_ID = "webhookById";
    public static final String NEGATIVE_SLUG = "negativeSlug";
    public static final String STATS = "stats";
    public static final String COMPILED_TEMPLATE = "compiledTemplate";

    public static final String EVENT_BUS_INVALIDATE_ADDRESS = "cache.webhook.invalidate";

    private CacheNames() {
    }
}
