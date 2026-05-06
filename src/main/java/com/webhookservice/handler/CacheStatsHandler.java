package com.webhookservice.handler;

import com.webhookservice.cache.CacheManager;
import com.webhookservice.cache.CacheMetricsSnapshot;
import com.webhookservice.util.JsonUtil;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Admin endpoints over {@link CacheManager}: {@code GET /api/cache/stats}, {@code POST /api/cache/flush}. */
public final class CacheStatsHandler {

    private static final Logger log = LoggerFactory.getLogger(CacheStatsHandler.class);

    private final CacheManager cacheManager;

    public CacheStatsHandler(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void stats(RoutingContext ctx) {
        CacheMetricsSnapshot snapshot = cacheManager.snapshot();
        ctx.response()
                .putHeader("Content-Type", "application/json")
                .putHeader("Cache-Control", "no-cache")
                .end(JsonUtil.toJson(snapshot));
    }

    public void flush(RoutingContext ctx) {
        cacheManager.flushAllCluster();
        log.info("Cache flushed cluster-wide via admin endpoint");
        ctx.response().setStatusCode(204).end();
    }
}
