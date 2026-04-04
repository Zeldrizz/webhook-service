package com.webhookservice.validation;

import com.webhookservice.model.dto.CreateWebhookDto;
import com.webhookservice.model.dto.UpdateWebhookDto;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebhookValidatorTest {

    @Test
    void validateCreate_validDto_noException() {
        CreateWebhookDto dto = new CreateWebhookDto(
                "My Webhook", "description", "GET,POST",
                true, "https://example.com/api", Map.of("Authorization", "Bearer token"),
                null, null, 50);
        assertDoesNotThrow(() -> WebhookValidator.validateCreate(dto));
    }

    @Test
    void validateCreate_nullName_throwsValidation() {
        CreateWebhookDto dto = new CreateWebhookDto(
                null, null, null, null, null, null, null, null, null);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> WebhookValidator.validateCreate(dto));
        assertTrue(ex.errors().stream().anyMatch(e -> e.contains("name is required")));
    }

    @Test
    void validateCreate_blankName_throwsValidation() {
        CreateWebhookDto dto = new CreateWebhookDto(
                "   ", null, null, null, null, null, null, null, null);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> WebhookValidator.validateCreate(dto));
        assertTrue(ex.errors().stream().anyMatch(e -> e.contains("name is required")));
    }

    @Test
    void validateCreate_tooLongName_throwsValidation() {
        String longName = "x".repeat(300);
        CreateWebhookDto dto = new CreateWebhookDto(
                longName, null, null, null, null, null, null, null, null);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> WebhookValidator.validateCreate(dto));
        assertTrue(ex.errors().stream().anyMatch(e -> e.contains("255")));
    }

    @Test
    void validateCreate_invalidMethod_throwsValidation() {
        CreateWebhookDto dto = new CreateWebhookDto(
                "Valid Name", null, "GET,INVALID", null, null, null, null, null, null);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> WebhookValidator.validateCreate(dto));
        assertTrue(ex.errors().stream().anyMatch(e -> e.contains("invalid HTTP method")));
    }

    @Test
    void validateCreate_validMethods_noException() {
        CreateWebhookDto dto = new CreateWebhookDto(
                "Valid Name", null, "GET,POST", null, null, null, null, null, null);
        assertDoesNotThrow(() -> WebhookValidator.validateCreate(dto));
    }

    @Test
    void validateCreate_invalidProxyUrl_throwsValidation() {
        CreateWebhookDto dto = new CreateWebhookDto(
                "Valid Name", null, null, null, "not-a-url", null, null, null, null);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> WebhookValidator.validateCreate(dto));
        assertTrue(ex.errors().stream().anyMatch(e -> e.contains("proxy URL")));
    }

    @Test
    void validateCreate_validProxyUrl_noException() {
        CreateWebhookDto dto = new CreateWebhookDto(
                "Valid Name", null, null, null, "https://api.example.com/hook",
                null, null, null, null);
        assertDoesNotThrow(() -> WebhookValidator.validateCreate(dto));
    }

    @Test
    void validateCreate_maxLogCountZero_throwsValidation() {
        CreateWebhookDto dto = new CreateWebhookDto(
                "Valid Name", null, null, null, null, null, null, null, 0);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> WebhookValidator.validateCreate(dto));
        assertTrue(ex.errors().stream().anyMatch(e -> e.contains("maxLogCount")));
    }

    @Test
    void validateCreate_maxLogCountTooHigh_throwsValidation() {
        CreateWebhookDto dto = new CreateWebhookDto(
                "Valid Name", null, null, null, null, null, null, null, 50_000);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> WebhookValidator.validateCreate(dto));
        assertTrue(ex.errors().stream().anyMatch(e -> e.contains("maxLogCount")));
    }

    @Test
    void validateCreate_multipleErrors_collectsAll() {
        CreateWebhookDto dto = new CreateWebhookDto(
                null, null, "INVALID", null, "bad-url", null, null, null, 0);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> WebhookValidator.validateCreate(dto));
        assertTrue(ex.errors().size() >= 3);
    }

    @Test
    void validateUpdate_allNulls_noException() {
        UpdateWebhookDto dto = new UpdateWebhookDto(
                null, null, null, null, null, null, null, null, null);
        assertDoesNotThrow(() -> WebhookValidator.validateUpdate(dto));
    }

    @Test
    void validateUpdate_blankName_throwsValidation() {
        UpdateWebhookDto dto = new UpdateWebhookDto(
                "  ", null, null, null, null, null, null, null, null);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> WebhookValidator.validateUpdate(dto));
        assertTrue(ex.errors().stream().anyMatch(e -> e.contains("name")));
    }

    @Test
    void validateUpdate_validPartialUpdate_noException() {
        UpdateWebhookDto dto = new UpdateWebhookDto(
                "Updated Name", null, null, null, null, null, null, null, null);
        assertDoesNotThrow(() -> WebhookValidator.validateUpdate(dto));
    }
}
