package com.webhookservice.template;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemplateCompilerTest {

    private final TemplateCompiler compiler = new TemplateCompiler();

    @Test
    void compile_simpleTemplate() {
        assertEquals("A=1", compiler.compile("A={{value}}").render(Map.of("value", 1)));
    }

    @Test
    void compile_nestedIfEach() {
        Map<String, Object> data = Map.of(
                "user", Map.of("enabled", true),
                "items", List.of(Map.of("name", "one", "active", true), Map.of("name", "two", "active", false))
        );
        String template = "{{#if user.enabled}}{{#each items}}{{#if active}}{{name}};{{/if}}{{/each}}{{/if}}";
        assertEquals("one;", compiler.compile(template).render(data));
    }

    @Test
    void compile_fallback() {
        assertEquals("Anonymous", compiler.compile("{{body.user.name | \"Anonymous\"}}").render(Map.of("body", Map.of())));
    }

    @Test
    void compile_questionFallbackForCompatibility() {
        assertEquals("Anonymous", compiler.compile("{{body.user.name ?? \"Anonymous\"}}").render(Map.of("body", Map.of())));
    }

    @Test
    void compile_unclosedBlockReportsLocation() {
        TemplateParseException error = assertThrows(
                TemplateParseException.class,
                () -> compiler.compile("line1\n{{#if user}}hello")
        );
        assertTrue(error.getMessage().contains("Unclosed template block"));
        assertEquals(2, error.line());
        assertTrue(error.column() >= 1);
        assertTrue(error.snippet().contains("#if user"));
    }
}
