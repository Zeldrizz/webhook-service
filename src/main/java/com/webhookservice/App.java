package com.webhookservice;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.micrometer.MicrometerMetricsOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        int cpus = Runtime.getRuntime().availableProcessors();

        PrometheusMeterRegistry prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        prometheusRegistry.config().meterFilter(new MeterFilter() {
            @Override
            public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
                if (id.getType() == Meter.Type.TIMER) {
                    return DistributionStatisticConfig.builder()
                            .percentilesHistogram(true)
                            .build()
                            .merge(config);
                }
                return config;
            }
        });
        new JvmMemoryMetrics().bindTo(prometheusRegistry);
        JvmGcMetrics gcMetrics = new JvmGcMetrics();
        gcMetrics.bindTo(prometheusRegistry);
        new JvmThreadMetrics().bindTo(prometheusRegistry);
        new ClassLoaderMetrics().bindTo(prometheusRegistry);
        new ProcessorMetrics().bindTo(prometheusRegistry);

        Runtime.getRuntime().addShutdownHook(new Thread(gcMetrics::close));

        VertxOptions options = new VertxOptions()
                .setEventLoopPoolSize(cpus)
                .setWorkerPoolSize(cpus * 2)
                .setMetricsOptions(new MicrometerMetricsOptions()
                        .setMicrometerRegistry(prometheusRegistry)
                        .setEnabled(true));

        Vertx vertx = Vertx.vertx(options);

        int instances = Integer.parseInt(
                System.getenv().getOrDefault("VERTICLE_INSTANCES", String.valueOf(cpus)));
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
