package com.webhookservice;

import com.webhookservice.auth.ApiKeyAuthHandler;
import com.webhookservice.cache.CacheManager;
import com.webhookservice.config.AppConfig;
import com.webhookservice.handler.CacheStatsHandler;
import com.webhookservice.handler.ErrorHandler;
import com.webhookservice.handler.RequestLogHandler;
import com.webhookservice.handler.TemplateHandler;
import com.webhookservice.handler.WebhookApiHandler;
import com.webhookservice.handler.WebhookReceiverHandler;
import com.webhookservice.repository.impl.PgRequestLogRepository;
import com.webhookservice.repository.impl.PgWebhookRepository;
import com.webhookservice.service.ProxyService;
import com.webhookservice.service.RequestLogService;
import com.webhookservice.service.RetryPolicy;
import com.webhookservice.service.TemplateService;
import com.webhookservice.service.WebhookService;
import com.webhookservice.util.DatabaseManager;
import com.webhookservice.repository.RequestLogRepository;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

    private DatabaseManager databaseManager;
    private ProxyService proxyService;
    private RequestLogService requestLogService;
    private RequestLogRepository requestLogRepository;
    private WebhookService webhookService;
    private CacheManager cacheManager;
    private long cleanupTimerId;

    @Override
    public void start(Promise<Void> startPromise) {
        AppConfig config = AppConfig.load();

        databaseManager = new DatabaseManager(vertx, config);
        databaseManager.runMigrations();

        var pool = databaseManager.pool();
        var webhookRepository = new PgWebhookRepository(pool);
        var requestLogRepository = new PgRequestLogRepository(pool);

        cacheManager = new CacheManager(vertx, config);
        cacheManager.installEventBusConsumer(vertx.eventBus());

        var webhookService = new WebhookService(webhookRepository, cacheManager);
        requestLogService = new RequestLogService(
                requestLogRepository,
                vertx,
                cacheManager.statsCache(),
                config.requestLogBatchEnabled(),
                config.requestLogBatchMaxSize(),
                config.requestLogBatchFlushMs()
        );
        this.requestLogRepository = requestLogRepository;
        this.webhookService = webhookService;
        var templateService = new TemplateService(cacheManager.compiledTemplateCache());
        RetryPolicy retryPolicy = new RetryPolicy(
                config.proxyMaxRetries(),
                config.proxyRetryBaseDelayMs(),
                config.proxyRetryMaxDelayMs(),
                config.proxyRetryMultiplier(),
                config.proxyRetryJitter(),
                config.proxyRetryOnStatus5xx()
        );
        proxyService = new ProxyService(
                vertx,
                config.proxyTimeoutMs(),
                retryPolicy,
                config.proxyCircuitBreakerEnabled(),
                config.proxyCircuitBreakerMaxFailures(),
                config.proxyCircuitBreakerResetMs(),
                config.proxyCircuitBreakerTimeoutMs()
        );

        String baseUrl = "http://localhost:" + config.serverPort();
        var webhookApiHandler = new WebhookApiHandler(webhookService, baseUrl);
        var requestLogHandler = new RequestLogHandler(webhookService, requestLogService);
        var webhookReceiverHandler = new WebhookReceiverHandler(webhookService, requestLogService, proxyService, templateService);
        var templateHandler = new TemplateHandler(templateService);
        var cacheStatsHandler = new CacheStatsHandler(cacheManager);
        var apiKeyAuth = new ApiKeyAuthHandler(config.authEnabled(), config.adminApiKey());

        Router router = Router.router(vertx);
        BodyHandler bodyHandler = BodyHandler.create().setBodyLimit(1_048_576);
        router.post().handler(bodyHandler);
        router.put().handler(bodyHandler);
        router.patch().handler(bodyHandler);
        router.delete().handler(bodyHandler);
        router.route().failureHandler(new ErrorHandler());

        router.get("/api/health").handler(ctx ->
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end("{\"status\":\"UP\"}"));

        router.route("/api/webhooks*").handler(apiKeyAuth);
        router.route("/api/templates/*").handler(apiKeyAuth);
        router.route("/api/cache/*").handler(apiKeyAuth);
        router.route("/api/auth/*").handler(apiKeyAuth);

        router.post("/api/webhooks").handler(webhookApiHandler::create);
        router.get("/api/webhooks").handler(webhookApiHandler::list);
        router.get("/api/webhooks/slug/:slug").handler(webhookApiHandler::getBySlug);
        router.get("/api/webhooks/:id").handler(webhookApiHandler::getById);
        router.put("/api/webhooks/:id").handler(webhookApiHandler::update);
        router.delete("/api/webhooks/:id").handler(webhookApiHandler::delete);
        router.patch("/api/webhooks/:id/toggle").handler(webhookApiHandler::toggle);

        router.get("/api/webhooks/:id/requests").handler(requestLogHandler::list);
        router.get("/api/webhooks/:id/requests/:requestId").handler(requestLogHandler::getById);
        router.delete("/api/webhooks/:id/requests").handler(requestLogHandler::clear);
        router.get("/api/webhooks/:id/stats").handler(requestLogHandler::stats);

        router.post("/api/templates/preview").handler(templateHandler::preview);
        router.route("/webhook/:slug").handler(webhookReceiverHandler::handle);

        router.get("/api/cache/stats").handler(cacheStatsHandler::stats);
        router.post("/api/cache/flush").handler(cacheStatsHandler::flush);

        router.get("/api/auth/verify").handler(ctx ->
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end("{\"ok\":true}"));

        router.get("/swagger").handler(ctx -> ctx.reroute("/swagger/index.html"));
        router.route("/swagger/*").handler(StaticHandler.create("swagger-ui")
                .setCachingEnabled(false));
        router.route("/docs/*").handler(StaticHandler.create("docs")
                .setCachingEnabled(false));

        router.route("/*").handler(StaticHandler.create("webroot")
                .setCachingEnabled(true)
                .setMaxAgeSeconds(3600));

        HttpServerOptions serverOptions = new HttpServerOptions()
                .setCompressionSupported(true)
                .setTcpNoDelay(true)
                .setReusePort(true);

        HttpServer server = vertx.createHttpServer(serverOptions);
        server.requestHandler(router)
                .listen(config.serverPort())
                .onSuccess(s -> {
                    log.info("Server started on port {} (auth={})", s.actualPort(), config.authEnabled() ? "ON" : "OFF");
                    startCleanupTimer(config);
                    startPromise.complete();
                })
                .onFailure(err -> {
                    log.error("Server start failed", err);
                    startPromise.fail(err);
                });
    }

    @Override
    public void stop(Promise<Void> stopPromise) {
        log.info("Shutting down MainVerticle");
        if (cleanupTimerId > 0) {
            vertx.cancelTimer(cleanupTimerId);
            log.info("Cleanup timer cancelled");
        }
        if (requestLogService != null) requestLogService.close();
        if (proxyService != null) proxyService.close();
        if (cacheManager != null) cacheManager.close();
        if (databaseManager != null) databaseManager.close();
        stopPromise.complete();
    }

    private void startCleanupTimer(AppConfig config) {
        long intervalMs = config.webhookCleanupIntervalHours() * 3600L * 1000L;
        log.info("Starting periodic cleanup timer: {} ms interval", intervalMs);
        cleanupTimerId = vertx.setPeriodic(intervalMs, event -> {
            log.debug("Running periodic log cleanup");
            cleanupOldLogs();
        });
    }

    private void cleanupOldLogs() {
        webhookService.list(0, 1000)
                .onSuccess(page -> {
                    for (var webhook : page.items()) {
                        requestLogRepository.trimToMaxCount(webhook.id(), webhook.maxLogCount())
                                .onFailure(err -> log.warn("Cleanup failed for webhook {}: {}",
                                        webhook.slug(), err.getMessage()));
                    }
                })
                .onFailure(err -> log.warn("Failed to fetch webhooks for cleanup: {}", err.getMessage()));
    }
}
