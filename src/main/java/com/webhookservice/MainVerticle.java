package com.webhookservice;

import com.webhookservice.config.AppConfig;
import com.webhookservice.handler.ErrorHandler;
import com.webhookservice.handler.RequestLogHandler;
import com.webhookservice.handler.TemplateHandler;
import com.webhookservice.handler.WebhookApiHandler;
import com.webhookservice.handler.WebhookReceiverHandler;
import com.webhookservice.repository.impl.PgRequestLogRepository;
import com.webhookservice.repository.impl.PgWebhookRepository;
import com.webhookservice.service.ProxyService;
import com.webhookservice.service.RequestLogService;
import com.webhookservice.service.TemplateService;
import com.webhookservice.service.WebhookService;
import com.webhookservice.util.DatabaseManager;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

    private static final Logger log = LoggerFactory.getLogger(MainVerticle.class);

    private DatabaseManager databaseManager;
    private ProxyService proxyService;

    @Override
    public void start(Promise<Void> startPromise) {
        AppConfig config = AppConfig.load();

        databaseManager = new DatabaseManager(vertx, config);
        databaseManager.runMigrations();

        var pool = databaseManager.pool();
        var webhookRepository = new PgWebhookRepository(pool);
        var requestLogRepository = new PgRequestLogRepository(pool);

        var webhookService = new WebhookService(webhookRepository);
        var requestLogService = new RequestLogService(requestLogRepository);
        var templateService = new TemplateService();
        proxyService = new ProxyService(vertx, config.proxyTimeoutMs());

        String baseUrl = "http://localhost:" + config.serverPort();
        var webhookApiHandler = new WebhookApiHandler(webhookService, baseUrl);
        var requestLogHandler = new RequestLogHandler(webhookService, requestLogService);
        var webhookReceiverHandler = new WebhookReceiverHandler(webhookService, requestLogService, proxyService, templateService);
        var templateHandler = new TemplateHandler(templateService);

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create().setBodyLimit(1_048_576));
        router.route().failureHandler(new ErrorHandler());

        router.get("/api/health").handler(ctx ->
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end("{\"status\":\"UP\"}"));

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

        router.get("/swagger").handler(ctx -> ctx.reroute("/swagger/index.html"));
        router.route("/swagger/*").handler(StaticHandler.create("swagger-ui")
                .setCachingEnabled(false));
        router.route("/docs/*").handler(StaticHandler.create("docs")
                .setCachingEnabled(false));

        router.route("/*").handler(StaticHandler.create("webroot")
                .setCachingEnabled(true)
                .setMaxAgeSeconds(3600));

        HttpServer server = vertx.createHttpServer();
        server.requestHandler(router)
                .listen(config.serverPort())
                .onSuccess(s -> {
                    log.info("Server started on port {}", s.actualPort());
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
        if (proxyService != null) proxyService.close();
        if (databaseManager != null) databaseManager.close();
        stopPromise.complete();
    }
}
