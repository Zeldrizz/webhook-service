package com.webhookservice.handler;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.Webhook;
import com.webhookservice.service.ProxyService;
import com.webhookservice.service.RequestLogService;
import com.webhookservice.service.TemplateService;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class WebhookReceiverHandlerTest {

    @Mock WebhookService webhookService;
    @Mock RequestLogService requestLogService;
    @Mock ProxyService proxyService;
    @Mock TemplateService templateService;

    private HttpServer server;
    private int port;

    @BeforeEach
    void setUp(Vertx vertx, VertxTestContext tc) {
        WebhookReceiverHandler handler = new WebhookReceiverHandler(
                webhookService, requestLogService, proxyService, templateService);

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.route("/webhook/:slug").handler(handler::handle);

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
    void handle_webhookNotFound_returns404(Vertx vertx, VertxTestContext tc) throws Exception {
        when(webhookService.getBySlug("unknown")).thenReturn(Future.succeededFuture(null));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/webhook/unknown"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        tc.verify(() -> {
            assertEquals(404, response.statusCode());
            assertTrue(response.body().contains("not found"));
            tc.completeNow();
        });
    }

    @Test
    void handle_inactiveWebhook_returns409(Vertx vertx, VertxTestContext tc) throws Exception {
        Webhook webhook = createWebhook(false, "GET,POST");
        when(webhookService.getBySlug("test-slug")).thenReturn(Future.succeededFuture(webhook));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/webhook/test-slug"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        tc.verify(() -> {
            assertEquals(409, response.statusCode());
            assertTrue(response.body().contains("inactive"));
            tc.completeNow();
        });
    }

    @Test
    void handle_methodNotAllowed_returns405(Vertx vertx, VertxTestContext tc) throws Exception {
        Webhook webhook = createWebhook(true, "GET");
        when(webhookService.getBySlug("test-slug")).thenReturn(Future.succeededFuture(webhook));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/webhook/test-slug"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        tc.verify(() -> {
            assertEquals(405, response.statusCode());
            assertTrue(response.body().contains("Method not allowed"));
            tc.completeNow();
        });
    }

    @Test
    void handle_successfulRequest_returns202(Vertx vertx, VertxTestContext tc) throws Exception {
        Webhook webhook = createWebhook(true, "GET,POST");
        when(webhookService.getBySlug("test-slug")).thenReturn(Future.succeededFuture(webhook));
        when(templateService.renderRequestTemplate(eq(webhook), any())).thenReturn(null);
        when(proxyService.forward(eq(webhook), any())).thenAnswer(inv -> {
            RequestLog log = inv.getArgument(1);
            return Future.succeededFuture(log);
        });
        when(requestLogService.save(any(), eq(100))).thenAnswer(inv -> {
            RequestLog log = inv.getArgument(0);
            return Future.succeededFuture(log);
        });
        when(templateService.renderResponseTemplate(eq(webhook), any())).thenReturn(null);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/webhook/test-slug"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"event\":\"test\"}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        tc.verify(() -> {
            assertEquals(202, response.statusCode());
            assertTrue(response.body().contains("accepted"));
            assertNotNull(response.headers().firstValue("X-Request-Id").orElse(null));
            tc.completeNow();
        });
    }

    @Test
    void handle_withProxyResult_returnsProxyStatus(Vertx vertx, VertxTestContext tc) throws Exception {
        Webhook webhook = createWebhook(true, "POST");
        when(webhookService.getBySlug("test-slug")).thenReturn(Future.succeededFuture(webhook));
        when(templateService.renderRequestTemplate(eq(webhook), any())).thenReturn(null);
        when(proxyService.forward(eq(webhook), any())).thenAnswer(inv -> {
            RequestLog log = inv.getArgument(1);
            return Future.succeededFuture(log.withProxyResult(200, "{\"result\":\"ok\"}", 100L));
        });
        when(requestLogService.save(any(), eq(100))).thenAnswer(inv -> Future.succeededFuture(inv.getArgument(0)));
        when(templateService.renderResponseTemplate(eq(webhook), any())).thenReturn(null);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/webhook/test-slug"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"data\":\"test\"}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        tc.verify(() -> {
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains("result"));
            tc.completeNow();
        });
    }

    @Test
    void handle_withResponseTemplate_usesTemplate(Vertx vertx, VertxTestContext tc) throws Exception {
        Webhook webhook = createWebhook(true, "POST");
        when(webhookService.getBySlug("test-slug")).thenReturn(Future.succeededFuture(webhook));
        when(templateService.renderRequestTemplate(eq(webhook), any())).thenReturn(null);
        when(proxyService.forward(eq(webhook), any())).thenAnswer(inv -> {
            RequestLog log = inv.getArgument(1);
            return Future.succeededFuture(log);
        });
        when(requestLogService.save(any(), eq(100))).thenAnswer(inv -> Future.succeededFuture(inv.getArgument(0)));
        when(templateService.renderResponseTemplate(eq(webhook), any())).thenReturn("{\"custom\":\"response\"}");

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/webhook/test-slug"))
                .POST(HttpRequest.BodyPublishers.ofString("body"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        tc.verify(() -> {
            assertTrue(response.body().contains("custom"));
            assertTrue(response.body().contains("response"));
            tc.completeNow();
        });
    }

    private Webhook createWebhook(boolean isActive, String methods) {
        Instant now = Instant.now();
        return new Webhook(UUID.randomUUID(), "Test Webhook", "test-slug",
                "desc", methods, isActive, true, null, Map.of(),
                null, null, 100, now, now);
    }
}
