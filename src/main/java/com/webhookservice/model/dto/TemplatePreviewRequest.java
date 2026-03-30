package com.webhookservice.model.dto;

import java.util.Map;

public record TemplatePreviewRequest(
        String template,
        Map<String, Object> data
) {}
