package com.webhookservice.model.dto;

import java.util.Map;

public record UpdateWebhookDto(
        String name,
        String description,
        String methods,
        Boolean debugMode,
        String proxyUrl,
        Map<String, String> proxyHeaders,
        String requestTemplate,
        String responseTemplate,
        Integer maxLogCount
) {}
