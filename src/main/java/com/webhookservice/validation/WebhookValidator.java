package com.webhookservice.validation;

import com.webhookservice.model.dto.CreateWebhookDto;
import com.webhookservice.model.dto.UpdateWebhookDto;
import com.webhookservice.model.enums.HttpMethodType;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public final class WebhookValidator {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_DESCRIPTION_LENGTH = 2000;

    private WebhookValidator() {}

    public static void validateCreate(CreateWebhookDto dto) {
        List<String> errors = new ArrayList<>();

        if (dto.name() == null || dto.name().isBlank()) {
            errors.add("name is required");
        } else if (dto.name().trim().length() > MAX_NAME_LENGTH) {
            errors.add("name must not exceed " + MAX_NAME_LENGTH + " characters");
        }

        if (dto.description() != null && dto.description().length() > MAX_DESCRIPTION_LENGTH) {
            errors.add("description must not exceed " + MAX_DESCRIPTION_LENGTH + " characters");
        }

        validateMethods(dto.methods(), errors);
        validateProxyUrl(dto.proxyUrl(), errors);
        validateMaxLogCount(dto.maxLogCount(), errors);

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    public static void validateUpdate(UpdateWebhookDto dto) {
        List<String> errors = new ArrayList<>();

        if (dto.name() != null) {
            if (dto.name().isBlank()) {
                errors.add("name must not be blank");
            } else if (dto.name().trim().length() > MAX_NAME_LENGTH) {
                errors.add("name must not exceed " + MAX_NAME_LENGTH + " characters");
            }
        }

        if (dto.description() != null && dto.description().length() > MAX_DESCRIPTION_LENGTH) {
            errors.add("description must not exceed " + MAX_DESCRIPTION_LENGTH + " characters");
        }

        validateMethods(dto.methods(), errors);
        validateProxyUrl(dto.proxyUrl(), errors);
        validateMaxLogCount(dto.maxLogCount(), errors);

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static void validateMethods(String methods, List<String> errors) {
        if (methods == null) return;
        String[] parts = methods.split(",");
        for (String part : parts) {
            if (!HttpMethodType.isValid(part.trim())) {
                errors.add("invalid HTTP method: " + part.trim());
            }
        }
    }

    private static void validateProxyUrl(String proxyUrl, List<String> errors) {
        if (proxyUrl == null || proxyUrl.isBlank()) return;
        try {
            URI uri = new URI(proxyUrl);
            uri.toURL(); // validates scheme
            if (uri.getHost() == null) {
                errors.add("proxy URL must have a host");
            }
        } catch (URISyntaxException | MalformedURLException | IllegalArgumentException e) {
            errors.add("proxy URL is not valid: " + proxyUrl);
        }
    }

    private static void validateMaxLogCount(Integer maxLogCount, List<String> errors) {
        if (maxLogCount == null) return;
        if (maxLogCount < 1) {
            errors.add("maxLogCount must be at least 1");
        }
        if (maxLogCount > 10_000) {
            errors.add("maxLogCount must not exceed 10000");
        }
    }
}
