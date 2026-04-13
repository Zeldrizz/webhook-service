package com.webhookservice.service;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TemplateServiceTest {

    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new TemplateService();
    }

    @Test
    void render_simpleSubstitution() {
        String template = "Hello, {{name}}!";
        Map<String, Object> data = Map.of("name", "World");
        assertEquals("Hello, World!", templateService.render(template, data));
    }

    @Test
    void render_dollarSyntax() {
        String template = "Hello, ${name}!";
        Map<String, Object> data = Map.of("name", "World");
        assertEquals("Hello, World!", templateService.render(template, data));
    }

    @Test
    void render_dotNotation() {
        Map<String, Object> data = Map.of("user", Map.of("name", "Alice"));
        String result = templateService.render("Hi, {{user.name}}", data);
        assertEquals("Hi, Alice", result);
    }

    @Test
    void render_missingKey_replacesWithEmpty() {
        String template = "Value: {{missing}}";
        Map<String, Object> data = Map.of("other", "value");
        assertEquals("Value: ", templateService.render(template, data));
    }

    @Test
    void render_nullTemplate_returnsNull() {
        assertNull(templateService.render(null, Map.of("key", "value")));
    }

    @Test
    void render_nullData_treatsAsEmpty() {
        assertEquals("Value: ", templateService.render("Value: {{key}}", null));
    }

    @Test
    void render_noTokens_returnsOriginal() {
        assertEquals("plain text", templateService.render("plain text", Map.of()));
    }

    @Test
    void render_jsonTemplate_preservesTypes() {
        String template = "{\"count\": \"{{count}}\", \"active\": \"{{active}}\"}";
        Map<String, Object> data = Map.of("count", 42, "active", true);
        String result = templateService.render(template, data);
        assertTrue(result.contains("42"));
        assertTrue(result.contains("true"));
    }

    @Test
    void render_jsonTemplate_exactToken_preservesType() {
        String template = "{\"count\": \"{{count}}\"}";
        Map<String, Object> data = Map.of("count", 42);
        String result = templateService.render(template, data);
        assertTrue(result.contains("42"));
    }

    @Test
    void render_multipleTokens() {
        String template = "{{greeting}}, {{name}}! Today is {{day}}.";
        Map<String, Object> data = Map.of("greeting", "Hello", "name", "Bob", "day", "Monday");
        assertEquals("Hello, Bob! Today is Monday.", templateService.render(template, data));
    }

    @Test
    void render_listAccess() {
        Map<String, Object> data = Map.of("items", java.util.List.of("a", "b", "c"));
        assertEquals("b", templateService.render("{{items.1}}", data));
    }

    @Test
    void render_deepDotNotation() {
        Map<String, Object> data = Map.of(
                "request", Map.of("headers", Map.of("Content-Type", "application/json")));
        String result = templateService.render("{{request.headers.Content-Type}}", data);
        assertEquals("application/json", result);
    }

    @Test
    void renderRequestTemplate_nullTemplate_returnsBody() {
        Webhook webhook = createWebhook(null, null);
        RequestLog log = createTestLog("{\"data\":\"test\"}");
        assertEquals("{\"data\":\"test\"}", templateService.renderRequestTemplate(webhook, log));
    }

    @Test
    void renderRequestTemplate_blankTemplate_returnsBody() {
        Webhook webhook = createWebhook("   ", null);
        RequestLog log = createTestLog("{\"data\":\"test\"}");
        assertEquals("{\"data\":\"test\"}", templateService.renderRequestTemplate(webhook, log));
    }

    @Test
    void renderRequestTemplate_withTemplate_rendersIt() {
        Webhook webhook = createWebhook("{\"method\": \"{{method}}\"}", null);
        RequestLog log = createTestLog("{\"data\":\"test\"}");
        String result = templateService.renderRequestTemplate(webhook, log);
        assertTrue(result.contains("POST"));
    }

    @Test
    void renderResponseTemplate_nullTemplate_returnsNull() {
        Webhook webhook = createWebhook(null, null);
        RequestLog log = createTestLog(null);
        assertNull(templateService.renderResponseTemplate(webhook, log));
    }

    @Test
    void renderResponseTemplate_withTemplate_rendersIt() {
        Webhook webhook = createWebhook(null, "{\"received\": \"{{method}}\"}");
        RequestLog log = createTestLog(null);
        String result = templateService.renderResponseTemplate(webhook, log);
        assertNotNull(result);
        assertTrue(result.contains("POST"));
    }

    @Test
    void buildContext_containsExpectedKeys() {
        Webhook webhook = createWebhook(null, null);
        RequestLog log = createTestLog("{\"data\":\"test\"}");
        Map<String, Object> context = templateService.buildContext(webhook, log);

        assertNotNull(context.get("webhook"));
        assertNotNull(context.get("request"));
        assertNotNull(context.get("proxy"));
        assertNotNull(context.get("method"));
        assertNotNull(context.get("url"));
    }

    private Webhook createWebhook(String requestTemplate, String responseTemplate) {
        Instant now = Instant.now();
        return new Webhook(UUID.randomUUID(), "Test Webhook", "test-webhook",
                "desc", "GET,POST", true, true, null, Map.of(),
                requestTemplate, responseTemplate, 100, now, now);
    }

    private RequestLog createTestLog(String body) {
        return new RequestLog(
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(),
                "POST", "/webhook/test",
                Map.of("q", "search"), Map.of("Content-Type", "application/json"),
                body, "application/json", "127.0.0.1",
                200, "{\"ok\":true}", 50L);
    }
}
