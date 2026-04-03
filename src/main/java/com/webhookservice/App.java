package com.webhookservice;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        VertxOptions options = new VertxOptions()
                .setWorkerPoolSize(20)
                .setEventLoopPoolSize(Runtime.getRuntime().availableProcessors());

        Vertx vertx = Vertx.vertx(options);

        vertx.deployVerticle(new MainVerticle())
                .onSuccess(id -> log.info("MainVerticle deployed: {}", id))
                .onFailure(err -> {
                    log.error("Failed to deploy MainVerticle", err);
                    vertx.close().onComplete(v -> System.exit(1));
                });
    }
}
