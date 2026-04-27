package com.webhookservice.service;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateServiceTest {
    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new TemplateService();
    }

    @Test
    void render_simpleSubstitution() {
        String template = "Hello, {{name}}!";
        Map data = Map.of("name", "World");

        assertEquals("Hello, World!", templateService.render(template, data));
    }

    @Test
    void render_dollarSyntax() {
        String template = "Hello, ${name}!";
        Map data = Map.of("name", "World");

        assertEquals("Hello, World!", templateService.render(template, data));
    }

    @Test
    void render_dotNotation() {
        Map data = Map.of("user", Map.of("name", "Alice"));
        String result = templateService.render("Hi, {{user.name}}", data);

        assertEquals("Hi, Alice", result);
    }

    @Test
    void render_missingKey_replacesWithEmpty() {
        String template = "Value: {{missing}}";
        Map data = Map.of("other", "value");

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
        Map data = Map.of("count", 42, "active", true);

        String result = templateService.render(template, data);

        assertTrue(result.contains("42"));
        assertTrue(result.contains("true"));
    }

    @Test
    void render_jsonTemplate_exactToken_preservesType() {
        String template = "{\"count\": \"{{count}}\"}";
        Map data = Map.of("count", 42);

        String result = templateService.render(template, data);

        assertTrue(result.contains("42"));
    }

    @Test
    void render_multipleTokens() {
        String template = "{{greeting}}, {{name}}! Today is {{day}}.";
        Map data = Map.of("greeting", "Hello", "name", "Bob", "day", "Monday");

        assertEquals("Hello, Bob! Today is Monday.", templateService.render(template, data));
    }

    @Test
    void render_listAccess() {
        Map data = Map.of("items", List.of("a", "b", "c"));

        assertEquals("b", templateService.render("{{items.1}}", data));
    }

    @Test
    void render_deepDotNotation() {
        Map data = Map.of(
                "request", Map.of("headers", Map.of("Content-Type", "application/json"))
        );

        String result = templateService.render("{{request.headers.Content-Type}}", data);

        assertEquals("application/json", result);
    }

    @Test
    void render_ifBlock_trueCondition_rendersInnerTemplate() {
        Map data = Map.of("request", Map.of("body", Map.of("urgent", true, "message", "Deploy now")));

        String result = templateService.render("{{#if request.body.urgent}}URGENT: {{request.body.message}}{{/if}}", data);

        assertEquals("URGENT: Deploy now", result);
    }

    @Test
    void render_ifBlock_falseCondition_skipsInnerTemplate() {
        Map data = Map.of("urgent", false, "message", "hidden");

        String result = templateService.render("Before{{#if urgent}} {{message}}{{/if}} After", data);

        assertEquals("Before After", result);
    }

    @Test
    void render_ifBlock_emptyString_isFalse() {
        Map data = Map.of("token", "");

        assertEquals("", templateService.render("{{#if token}}token={{token}}{{/if}}", data));
    }

    @Test
    void render_ifBlock_stringFalse_isFalse() {
        Map data = Map.of("enabled", "false");

        assertEquals("disabled", templateService.render("{{#if enabled}}enabled{{/if}}disabled", data));
    }

    @Test
    void render_eachBlock_iteratesList() {
        Map data = Map.of("items", List.of("alpha", "beta", "gamma"));

        String result = templateService.render("{{#each items}}{{@index}}={{this}};{{/each}}", data);

        assertEquals("0=alpha;1=beta;2=gamma;", result);
    }

    @Test
    void render_eachBlock_exposesFirstAndLast() {
        Map data = Map.of("items", List.of("a", "b"));

        String result = templateService.render(
                "{{#each items}}{{#if @first}}first={{this}};{{/if}}{{#if @last}}last={{this}};{{/if}}{{/each}}",
                data
        );

        assertEquals("first=a;last=b;", result);
    }

    @Test
    void render_eachBlock_iteratesMap() {
        Map data = Map.of("headers", Map.of("Content-Type", "application/json"));

        String result = templateService.render("{{#each headers}}{{@key}}={{this}};{{/each}}", data);

        assertEquals("Content-Type=application/json;", result);
    }

    @Test
    void render_eachBlock_flattensMapItem() {
        Map data = Map.of(
                "items", List.of(
                        Map.of("name", "first", "value", 1),
                        Map.of("name", "second", "value", 2)
                )
        );

        String result = templateService.render("{{#each items}}{{name}}:{{value}};{{/each}}", data);

        assertEquals("first:1;second:2;", result);
    }

    @Test
    void render_eachBlock_missingItems_returnsEmpty() {
        assertEquals("prefix suffix", templateService.render("prefix {{#each missing}}{{this}}{{/each}}suffix", Map.of()));
    }

    @Test
    void render_nestedBlocks_insideEach() {
        Map data = Map.of(
                "items", List.of(
                        Map.of("name", "one", "active", true),
                        Map.of("name", "two", "active", false),
                        Map.of("name", "three", "active", true)
                )
        );

        String result = templateService.render("{{#each items}}{{#if active}}{{name}};{{/if}}{{/each}}", data);

        assertEquals("one;three;", result);
    }

    @Test
    void render_jsonTemplate_withIfBlockInsideString() {
        String template = "{\"message\": \"{{#if request.body.urgent}}URGENT: {{request.body.title}}{{/if}}\"}";
        Map data = Map.of("request", Map.of("body", Map.of("urgent", true, "title", "incident")));

        String result = templateService.render(template, data);

        assertTrue(result.contains("URGENT: incident"));
    }

    @Test
    void render_unclosedBlock_throwsHelpfulError() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> templateService.render("{{#if enabled}}enabled", Map.of("enabled", true))
        );

        assertTrue(error.getMessage().contains("Unclosed template block"));
    }

    @Test
    void render_mismatchedBlock_throwsHelpfulError() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> templateService.render("{{#if enabled}}{{#each items}}{{/if}}{{/each}}", Map.of("enabled", true, "items", List.of("a")))
        );

        assertTrue(error.getMessage().contains("Mismatched template block"));
    }

    @Test
    void renderRequestTemplate_nullTemplate_returnsBody() {
        Webhook webhook = createWebhook(null, null);
        RequestLog log = createTestLog("{\"data\":\"test\"}");

        assertEquals("{\"data\":\"test\"}", templateService.renderRequestTemplate(webhook, log));
    }

    @Test
    void renderRequestTemplate_blankTemplate_returnsBody() {
        Webhook webhook = createWebhook(" ", null);
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
    void renderRequestTemplate_withEachBlockFromBody_rendersIt() {
        Webhook webhook = createWebhook("{{#each body.items}}{{name}};{{/each}}", null);
        RequestLog log = createTestLog("{\"items\":[{\"name\":\"first\"},{\"name\":\"second\"}]}");

        String result = templateService.renderRequestTemplate(webhook, log);

        assertEquals("first;second;", result);
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
    void renderResponseTemplate_withIfBlockFromProxy_rendersIt() {
        Webhook webhook = createWebhook(null, "{{#if proxy.status}}status={{proxy.status}}{{/if}}");
        RequestLog log = createTestLog("{\"data\":\"test\"}");

        String result = templateService.renderResponseTemplate(webhook, log);

        assertEquals("status=200", result);
    }

    @Test
    void buildContext_containsExpectedKeys() {
        Webhook webhook = createWebhook(null, null);
        RequestLog log = createTestLog("{\"data\":\"test\"}");

        Map context = templateService.buildContext(webhook, log);

        assertNotNull(context.get("webhook"));
        assertNotNull(context.get("request"));
        assertNotNull(context.get("proxy"));
        assertNotNull(context.get("method"));
        assertNotNull(context.get("url"));
    }

    private Webhook createWebhook(String requestTemplate, String responseTemplate) {
        Instant now = Instant.now();
        return new Webhook(
                UUID.randomUUID(),
                "Test Webhook",
                "test-webhook",
                "desc",
                "GET,POST",
                true,
                true,
                null,
                Map.of(),
                requestTemplate,
                responseTemplate,
                100,
                now,
                now
        );
    }

    private RequestLog createTestLog(String body) {
        return new RequestLog(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                "POST",
                "/webhook/test",
                Map.of("q", "search"),
                Map.of("Content-Type", "application/json"),
                body,
                "application/json",
                "127.0.0.1",
                200,
                "{\"ok\":true}",
                50L
        );
    }
}
