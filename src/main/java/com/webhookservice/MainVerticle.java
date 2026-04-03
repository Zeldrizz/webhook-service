package com.webhookservice;

import com.webhookservice.config.AppConfig;
import com.webhookservice.handler.ErrorHandler;
import com.webhookservice.handler.WebhookApiHandler;
import com.webhookservice.repository.impl.JdbcWebhookRepository;
import com.webhookservice.service.ProxyService;
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

        databaseManager = new DatabaseManager(config);
        databaseManager.runMigrations();

        var webhookRepository = new JdbcWebhookRepository(databaseManager);
        var webhookService = new WebhookService(webhookRepository, vertx);
        proxyService = new ProxyService(vertx, config.proxyTimeoutMs());

        String baseUrl = "http://localhost:" + config.serverPort();
        var webhookApiHandler = new WebhookApiHandler(webhookService, baseUrl);

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create().setBodyLimit(1_048_576));
        router.route().failureHandler(new ErrorHandler());

        router.get("/api/health").handler(ctx ->
                ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end("{\"status\":\"UP\"}"));

        router.post("/api/webhooks").handler(webhookApiHandler::create);
        router.get("/api/webhooks").handler(webhookApiHandler::list);
        router.get("/api/webhooks/:id").handler(webhookApiHandler::getById);
        router.put("/api/webhooks/:id").handler(webhookApiHandler::update);
        router.delete("/api/webhooks/:id").handler(webhookApiHandler::delete);
        router.patch("/api/webhooks/:id/toggle").handler(webhookApiHandler::toggle);

        // TODO (Ксения): RequestLogHandler, WebhookReceiverHandler, TemplateHandler
        // Зависимости: webhookService, proxyService, databaseManager, vertx

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
