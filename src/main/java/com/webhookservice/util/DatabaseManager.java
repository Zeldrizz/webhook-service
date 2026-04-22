package com.webhookservice.util;

import com.webhookservice.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.vertx.core.Vertx;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private final AppConfig config;
    private final Pool pgPool;

    public DatabaseManager(Vertx vertx, AppConfig config) {
        this.config = config;

        PgConnectOptions connectOptions = config.pgConnectOptions();

        long connectionTimeoutSec = config.databaseConnectionTimeout() / 1000;
        int poolTimeoutSec = connectionTimeoutSec > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) connectionTimeoutSec;

        PoolOptions poolOptions = new PoolOptions()
                .setMaxSize(config.databasePoolSize())
                .setConnectionTimeout(poolTimeoutSec)
                .setName("webhook-pg-pool");

        this.pgPool = Pool.pool(vertx, connectOptions, poolOptions);
        log.info("Vert.x PgPool initialized: db={}, maxSize={}, timeout={}s",
                connectOptions.getDatabase(), config.databasePoolSize(), poolTimeoutSec);
    }

    public void runMigrations() {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.databaseUrl());
        hikari.setUsername(config.databaseUser());
        hikari.setPassword(config.databasePassword());
        hikari.setMaximumPoolSize(2);
        hikari.setMinimumIdle(1);
        hikari.setConnectionTimeout(config.databaseConnectionTimeout());
        hikari.setPoolName("flyway-bootstrap-pool");

        try (HikariDataSource ds = new HikariDataSource(hikari)) {
            Flyway flyway = Flyway.configure()
                    .dataSource(ds)
                    .locations("classpath:db/migration")
                    .load();
            int applied = flyway.migrate().migrationsExecuted;
            log.info("Flyway migrations applied: {}", applied);
        }
    }

    public Pool pool() {
        return pgPool;
    }

    public void close() {
        if (pgPool != null) {
            pgPool.close();
            log.info("Vert.x PgPool closed");
        }
    }
}
