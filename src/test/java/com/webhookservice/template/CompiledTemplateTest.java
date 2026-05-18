package com.webhookservice.template;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompiledTemplateTest {

    private final TemplateCompiler compiler = new TemplateCompiler();

    @Test
    void render_literalOnly() {
        assertEquals("plain", compiler.compile("plain").render(Map.of()));
    }

    @Test
    void render_variable() {
        assertEquals("Hello, Ksenia", compiler.compile("Hello, {{name}}").render(Map.of("name", "Ksenia")));
    }

    @Test
    void render_dollarVariable() {
        assertEquals("Hello, Ksenia", compiler.compile("Hello, ${name}").render(Map.of("name", "Ksenia")));
    }

    @Test
    void render_nestedIf() {
        Map<String, Object> data = Map.of("user", Map.of("premium", true, "name", "Ksenia"));
        assertEquals("Premium Ksenia", compiler.compile("{{#if user}}{{#if user.premium}}Premium {{user.name}}{{/if}}{{/if}}").render(data));
    }

    @Test
    void render_each() {
        Map<String, Object> data = Map.of("items", List.of("a", "b"));
        assertEquals("0:a;1:b;", compiler.compile("{{#each items}}{{@index}}:{{this}};{{/each}}").render(data));
    }

    @Test
    void render_fallbackWhenMissing() {
        assertEquals("Привет, Гость", compiler.compile("Привет, {{user.name | \"Гость\"}}").render(Map.of()));
    }

    @Test
    void render_emptyTemplate() {
        assertEquals("", compiler.compile("").render(Map.of()));
    }

    @Test
    void render_singleCharacterTemplate() {
        assertEquals("x", compiler.compile("x").render(Map.of()));
    }
}
