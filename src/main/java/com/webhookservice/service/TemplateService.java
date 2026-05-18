package com.webhookservice.service;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.Webhook;
import com.webhookservice.template.CompiledTemplateCache;
import com.webhookservice.template.TemplateCompiler;
import com.webhookservice.util.JsonUtil;

import java.util.LinkedHashMap;
import java.util.Map;

public class TemplateService {

    private static final long DEFAULT_TEMPLATE_CACHE_MAX_SIZE = 1000L;
    private static final long DEFAULT_TEMPLATE_CACHE_TTL_SECONDS = 1800L;

    private final TemplateCompiler compiler;
    private final CompiledTemplateCache compiledTemplateCache;

    public TemplateService() {
        this(new CompiledTemplateCache(
                DEFAULT_TEMPLATE_CACHE_MAX_SIZE,
                DEFAULT_TEMPLATE_CACHE_TTL_SECONDS,
                true
        ));
    }

    public TemplateService(CompiledTemplateCache compiledTemplateCache) {
        this(new TemplateCompiler(), compiledTemplateCache);
    }

    public TemplateService(TemplateCompiler compiler, CompiledTemplateCache compiledTemplateCache) {
        this.compiler = compiler == null ? new TemplateCompiler() : compiler;
        this.compiledTemplateCache = compiledTemplateCache == null
                ? new CompiledTemplateCache(DEFAULT_TEMPLATE_CACHE_MAX_SIZE, DEFAULT_TEMPLATE_CACHE_TTL_SECONDS, true)
                : compiledTemplateCache;
    }

    public String render(String template, Map data) {
        if (template == null) {
            return null;
        }
        Map<String, Object> safeData = normalizeData(data);
        return compiledTemplateCache.getOrCompile(template, compiler).render(safeData);
    }

    public String renderRequestTemplate(Webhook webhook, RequestLog requestLog) {
        if (webhook.requestTemplate() == null || webhook.requestTemplate().isBlank()) {
            return requestLog.body();
        }
        return render(webhook.requestTemplate(), buildContext(webhook, requestLog));
    }

    public String renderResponseTemplate(Webhook webhook, RequestLog requestLog) {
        if (webhook.responseTemplate() == null || webhook.responseTemplate().isBlank()) {
            return null;
        }
        return render(webhook.responseTemplate(), buildContext(webhook, requestLog));
    }

    public Map buildContext(Webhook webhook, RequestLog requestLog) {
        Map<String, Object> webhookData = new LinkedHashMap<>();
        webhookData.put("id", webhook.id());
        webhookData.put("name", webhook.name());
        webhookData.put("slug", webhook.slug());
        webhookData.put("description", webhook.description());
        webhookData.put("methods", webhook.methods());
        webhookData.put("isActive", webhook.isActive());
        webhookData.put("debugMode", webhook.debugMode());
        webhookData.put("proxyUrl", webhook.proxyUrl());
        webhookData.put("proxyHeaders", webhook.proxyHeaders());
        webhookData.put("requestTemplate", webhook.requestTemplate());
        webhookData.put("responseTemplate", webhook.responseTemplate());
        webhookData.put("maxLogCount", webhook.maxLogCount());
        webhookData.put("createdAt", webhook.createdAt());
        webhookData.put("updatedAt", webhook.updatedAt());

        Map<String, Object> requestData = new LinkedHashMap<>();
        requestData.put("id", requestLog.id());
        requestData.put("webhookId", requestLog.webhookId());
        requestData.put("receivedAt", requestLog.receivedAt());
        requestData.put("method", requestLog.method());
        requestData.put("url", requestLog.url());
        requestData.put("queryParams", requestLog.queryParams());
        requestData.put("headers", requestLog.headers());
        requestData.put("body", parseJsonOrRaw(requestLog.body()));
        requestData.put("rawBody", requestLog.body());
        requestData.put("contentType", requestLog.contentType());
        requestData.put("sourceIp", requestLog.sourceIp());

        Map<String, Object> proxyData = new LinkedHashMap<>();
        proxyData.put("status", requestLog.responseStatus());
        proxyData.put("response", parseJsonOrRaw(requestLog.proxyResponse()));
        proxyData.put("rawResponse", requestLog.proxyResponse());
        proxyData.put("durationMs", requestLog.proxyDurationMs());

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("webhook", webhookData);
        context.put("request", requestData);
        context.put("proxy", proxyData);

        context.put("id", requestLog.id());
        context.put("webhookId", requestLog.webhookId());
        context.put("receivedAt", requestLog.receivedAt());
        context.put("method", requestLog.method());
        context.put("url", requestLog.url());
        context.put("queryParams", requestLog.queryParams());
        context.put("headers", requestLog.headers());
        context.put("body", requestData.get("body"));
        context.put("rawBody", requestLog.body());
        context.put("contentType", requestLog.contentType());
        context.put("sourceIp", requestLog.sourceIp());
        context.put("responseStatus", requestLog.responseStatus());
        context.put("proxyResponse", proxyData.get("response"));
        context.put("rawProxyResponse", requestLog.proxyResponse());
        context.put("proxyDurationMs", requestLog.proxyDurationMs());
        return context;
    }

    private Object parseJsonOrRaw(String value) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            return value;
        }
        try {
            return JsonUtil.mapper().readValue(value, Object.class);
        } catch (Exception e) {
            return value;
        }
    }

    private Map<String, Object> normalizeData(Map data) {
        if (data == null || data.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        data.forEach((key, value) -> {
            if (key != null) {
                result.put(String.valueOf(key), value);
            }
        });
        return result;
    }
}
