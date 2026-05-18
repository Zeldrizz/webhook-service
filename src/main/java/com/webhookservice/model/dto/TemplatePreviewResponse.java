package com.webhookservice.model.dto;

public record TemplatePreviewResponse(
        boolean ok,
        String result,
        String error,
        Integer line,
        Integer column,
        String snippet
) {
    public TemplatePreviewResponse(String result) {
        this(true, result, null, null, null, null);
    }

    public static TemplatePreviewResponse success(String result) {
        return new TemplatePreviewResponse(true, result, null, null, null, null);
    }

    public static TemplatePreviewResponse error(String error, Integer line, Integer column, String snippet) {
        return new TemplatePreviewResponse(false, null, error, line, column, snippet);
    }
}
