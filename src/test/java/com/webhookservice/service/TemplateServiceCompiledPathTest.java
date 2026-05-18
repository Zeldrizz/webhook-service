package com.webhookservice.service;

import com.webhookservice.template.TemplateParseException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateServiceCompiledPathTest {

    @Test
    void render_usesCompiledPathWithFallback() {
        TemplateService service = new TemplateService();

        String result = service.render("Hello, {{body.user.name | \"Anonymous\"}}", Map.of("body", Map.of()));

        assertEquals("Hello, Anonymous", result);
    }

    @Test
    void render_parseErrorContainsLineAndColumn() {
        TemplateService service = new TemplateService();

        TemplateParseException error = assertThrows(
                TemplateParseException.class,
                () -> service.render("ok\n{{#each items}}", Map.of())
        );

        assertTrue(error.line() >= 2);
        assertTrue(error.column() >= 1);
        assertTrue(error.snippet().contains("#each"));
    }
}
