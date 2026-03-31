package com.webhookservice.util;

import com.webhookservice.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseManager {

    private static final Logger log = LoggerFactory.getLogger(DatabaseManager.class);

    private final HikariDataSource dataSource;

    public DatabaseManager(AppConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.databaseUrl());
        hikari.setUsername(config.databaseUser());
        hikari.setPassword(config.databasePassword());
        hikari.setMaximumPoolSize(config.databasePoolSize());
        hikari.setConnectionTimeout(config.databaseConnectionTimeout());
        hikari.setMinimumIdle(Math.max(1, config.databasePoolSize() / 2));
        hikari.setIdleTimeout(600_000);
        hikari.setMaxLifetime(1_800_000);
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikari.addDataSourceProperty("useServerPrepStmts", "true");
        hikari.setPoolName("webhook-pool");

        this.dataSource = new HikariDataSource(hikari);
        log.info("HikariCP pool initialized: url={}, poolSize={}", config.databaseUrl(), config.databasePoolSize());
    }

    public void runMigrations() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();
        int applied = flyway.migrate().migrationsExecuted;
        log.info("Flyway migrations applied: {}", applied);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("HikariCP pool closed");
        }
    }
}
