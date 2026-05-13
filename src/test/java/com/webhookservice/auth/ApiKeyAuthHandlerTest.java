package com.webhookservice.auth;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(VertxExtension.class)
class ApiKeyAuthHandlerTest {

    private HttpServer server;
    private WebClient client;
    private int port;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext tc) {
        Router router = Router.router(vertx);
        ApiKeyAuthHandler auth = new ApiKeyAuthHandler(true, "secret-key-1234");

        router.route("/api/*").handler(auth);
        router.get("/api/protected").handler(ctx -> ctx.response().setStatusCode(200).end("ok"));
        router.get("/public").handler(ctx -> ctx.response().setStatusCode(200).end("public"));

        server = vertx.createHttpServer();
        server.requestHandler(router).listen(0)
                .onSuccess(s -> {
                    port = s.actualPort();
                    client = WebClient.create(vertx);
                    tc.completeNow();
                })
                .onFailure(tc::failNow);
    }

    @AfterEach
    void tearDown() {
        if (client != null) client.close();
        if (server != null) server.close();
    }

    @Test
    void missingHeader_returns401(VertxTestContext tc) {
        client.get(port, "127.0.0.1", "/api/protected")
                .send()
                .onComplete(tc.succeeding(res -> tc.verify(() -> {
                    assertEquals(401, res.statusCode());
                    tc.completeNow();
                })));
    }

    @Test
    void wrongHeader_returns401(VertxTestContext tc) {
        client.get(port, "127.0.0.1", "/api/protected")
                .putHeader("X-API-Key", "wrong-key")
                .send()
                .onComplete(tc.succeeding(res -> tc.verify(() -> {
                    assertEquals(401, res.statusCode());
                    tc.completeNow();
                })));
    }

    @Test
    void correctHeader_passesThrough(VertxTestContext tc) {
        client.get(port, "127.0.0.1", "/api/protected")
                .putHeader("X-API-Key", "secret-key-1234")
                .send()
                .onComplete(tc.succeeding(res -> tc.verify(() -> {
                    assertEquals(200, res.statusCode());
                    tc.completeNow();
                })));
    }

    @Test
    void blankHeader_returns401(VertxTestContext tc) {
        client.get(port, "127.0.0.1", "/api/protected")
                .putHeader("X-API-Key", "   ")
                .send()
                .onComplete(tc.succeeding(res -> tc.verify(() -> {
                    assertEquals(401, res.statusCode());
                    tc.completeNow();
                })));
    }

    @Test
    void publicRoute_doesNotRequireKey(VertxTestContext tc) {
        client.get(port, "127.0.0.1", "/public")
                .send()
                .onComplete(tc.succeeding(res -> tc.verify(() -> {
                    assertEquals(200, res.statusCode());
                    tc.completeNow();
                })));
    }

    @Test
    void disabledHandler_passesEverythingThrough(Vertx vertx, VertxTestContext tc) {
        Router router = Router.router(vertx);
        ApiKeyAuthHandler disabled = new ApiKeyAuthHandler(false, "ignored");
        router.route("/api/*").handler(disabled);
        router.get("/api/x").handler(ctx -> ctx.response().setStatusCode(200).end("ok"));

        vertx.createHttpServer().requestHandler(router).listen(0)
                .onSuccess(s -> {
                    WebClient c = WebClient.create(vertx);
                    c.get(s.actualPort(), "127.0.0.1", "/api/x")
                            .send()
                            .onComplete(tc.succeeding(res -> tc.verify(() -> {
                                assertEquals(200, res.statusCode());
                                c.close();
                                s.close();
                                tc.completeNow();
                            })));
                })
                .onFailure(tc::failNow);
    }

    @Test
    void wrongKeyDifferentLength_stillReturns401(VertxTestContext tc) {
        // Constant-time comparison via SHA-256 → length differences should not
        // produce a different timing or status code than equal-length wrong keys.
        client.get(port, "127.0.0.1", "/api/protected")
                .putHeader("X-API-Key", "x")
                .send()
                .onComplete(tc.succeeding(res -> tc.verify(() -> {
                    assertEquals(401, res.statusCode());
                    tc.completeNow();
                })));
    }
}
