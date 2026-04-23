package com.webhookservice.repository;

import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.junit.jupiter.api.Assumptions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

public abstract class AbstractPgRepositoryIT {

    protected static PostgreSQLContainer<?> postgres;
    protected static Vertx vertx;
    protected static Pool pool;

    protected static void initPool() {
        boolean dockerAvailable;
        try {
            dockerAvailable = DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            dockerAvailable = false;
        }
        Assumptions.assumeTrue(dockerAvailable,
                "Skipping repository integration tests: Docker is not available");

        @SuppressWarnings("resource")
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("webhooks")
                .withUsername("test_user")
                .withPassword("test_pass");
        if (postgres == null) {
            postgres = container;
            postgres.start();
        }

        if (vertx == null) {
            vertx = Vertx.vertx();
            PgConnectOptions connect = new PgConnectOptions()
                    .setHost(postgres.getHost())
                    .setPort(postgres.getFirstMappedPort())
                    .setDatabase(postgres.getDatabaseName())
                    .setUser(postgres.getUsername())
                    .setPassword(postgres.getPassword())
                    .setCachePreparedStatements(true);
            pool = Pool.pool(vertx, connect, new PoolOptions().setMaxSize(4));
        }
    }

    protected static void shutdownPool() {
        if (pool != null) {
            pool.close();
            pool = null;
        }
        if (vertx != null) {
            vertx.close();
            vertx = null;
        }
        if (postgres != null) {
            postgres.stop();
            postgres = null;
        }
    }
}
