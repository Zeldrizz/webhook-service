package com.webhookservice.handler;

import com.webhookservice.model.Webhook;
import com.webhookservice.model.dto.Page;
import com.webhookservice.service.WebhookService;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class WebhookApiHandlerTest {

    @Mock WebhookService webhookService;

    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext tc) {
        WebhookApiHandler handler = new WebhookApiHandler(webhookService, "http://localhost:0");

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.route().failureHandler(new ErrorHandler());
        router.post("/api/webhooks").handler(handler::create);
        router.get("/api/webhooks").handler(handler::list);
        router.get("/api/webhooks/:id").handler(handler::getById);
        router.put("/api/webhooks/:id").handler(handler::update);
        router.delete("/api/webhooks/:id").handler(handler::delete);
        router.patch("/api/webhooks/:id/toggle").handler(handler::toggle);

        server = vertx.createHttpServer();
        server.requestHandler(router)
                .listen(0)
                .onSuccess(s -> {
                    port = s.actualPort();
                    tc.completeNow();
                })
                .onFailure(tc::failNow);
    }

    @AfterEach
    void tearDown(VertxTestContext tc) {
        if (server != null) {
            server.close().onComplete(tc.succeeding(v -> tc.completeNow()));
        }
    }

    @Test
    void create_returns201(Vertx vertx, VertxTestContext tc) throws Exception {
        Webhook webhook = sampleWebhook();
        when(webhookService.create(any())).thenReturn(Future.succeededFuture(webhook));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/webhooks"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"name\":\"Test\",\"methods\":\"GET,POST\"}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        tc.verify(() -> {
            assertEquals(201, response.statusCode());
            assertTrue(response.body().contains("Test Webhook"));
            tc.completeNow();
        });
    }

    @Test
    void list_returns200(Vertx vertx, VertxTestContext tc) throws Exception {
        when(webhookService.list(anyInt(), anyInt())).thenReturn(
                Future.succeededFuture(new Page<>(List.of(sampleWebhook()), 0, 20, 1)));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/webhooks"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        tc.verify(() -> {
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("items"));
            tc.completeNow();
        });
    }

    @Test
    void getById_notFound_returns404(Vertx vertx, VertxTestContext tc) throws Exception {
        UUID id = UUID.randomUUID();
        when(webhookService.getById(id)).thenReturn(Future.succeededFuture(null));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/webhooks/" + id))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        tc.verify(() -> {
            assertEquals(404, response.statusCode());
            tc.completeNow();
        });
    }

    @Test
    void getById_invalidUUID_returns400(Vertx vertx, VertxTestContext tc) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/webhooks/invalid-uuid"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        tc.verify(() -> {
            assertEquals(400, response.statusCode());
            assertTrue(response.body().contains("Invalid UUID"));
            tc.completeNow();
        });
    }

    @Test
    void delete_returns204(Vertx vertx, VertxTestContext tc) throws Exception {
        UUID id = UUID.randomUUID();
        when(webhookService.delete(id)).thenReturn(Future.succeededFuture(true));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/webhooks/" + id))
                .DELETE()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        tc.verify(() -> {
            assertEquals(204, response.statusCode());
            tc.completeNow();
        });
    }

    @Test
    void toggle_returns200(Vertx vertx, VertxTestContext tc) throws Exception {
        UUID id = UUID.randomUUID();
        Webhook toggled = sampleWebhook();
        when(webhookService.toggle(id)).thenReturn(Future.succeededFuture(toggled));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/webhooks/" + id + "/toggle"))
                .method("PATCH", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        tc.verify(() -> {
            assertEquals(200, response.statusCode());
            tc.completeNow();
        });
    }

    private Webhook sampleWebhook() {
        Instant now = Instant.now();
        return new Webhook(UUID.randomUUID(), "Test Webhook", "test-webhook",
                "desc", "GET,POST", true, true, null, Map.of(),
                null, null, 100, now, now);
    }
}
