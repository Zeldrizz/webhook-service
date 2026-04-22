package com.webhookservice;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        int cpus = Runtime.getRuntime().availableProcessors();

        VertxOptions options = new VertxOptions()
                .setEventLoopPoolSize(cpus)
                .setWorkerPoolSize(cpus * 2);

        Vertx vertx = Vertx.vertx(options);

        int instances = cpus;
        DeploymentOptions deploymentOptions = new DeploymentOptions()
                .setInstances(instances)
                .setHa(false);

        vertx.deployVerticle("com.webhookservice.MainVerticle", deploymentOptions)
                .onSuccess(id -> log.info("MainVerticle deployed: {} with {} instances", id, instances))
                .onFailure(err -> {
                    log.error("Failed to deploy MainVerticle", err);
                    vertx.close().onComplete(v -> System.exit(1));
                });
    }
}
