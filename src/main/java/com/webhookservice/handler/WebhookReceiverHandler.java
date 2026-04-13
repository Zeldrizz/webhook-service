package com.webhookservice.handler;

import com.webhookservice.model.RequestLog;
import com.webhookservice.model.Webhook;
import com.webhookservice.service.ProxyService;
import com.webhookservice.service.RequestLogService;
import com.webhookservice.service.TemplateService;
import com.webhookservice.service.WebhookService;
import com.webhookservice.util.IdGenerator;
import com.webhookservice.util.JsonUtil;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class WebhookReceiverHandler {

    private static final Logger log = LoggerFactory.getLogger(WebhookReceiverHandler.class);

    private final WebhookService webhookService;
    private final RequestLogService requestLogService;
    private final ProxyService proxyService;
    private final TemplateService templateService;

    public WebhookReceiverHandler(
            WebhookService webhookService,
            RequestLogService requestLogService,
            ProxyService proxyService,
            TemplateService templateService
    ) {
        this.webhookService = webhookService;
        this.requestLogService = requestLogService;
        this.proxyService = proxyService;
        this.templateService = templateService;
    }

    public void handle(RoutingContext ctx) {
        String slug = ctx.pathParam("slug");

        webhookService.getBySlug(slug)
                .onSuccess(webhook -> {
                    if (webhook == null) {
                        ErrorHandler.sendNotFound(ctx, "Webhook");
                        return;
                    }
                    if (!webhook.isActive()) {
                        ErrorHandler.sendError(ctx, 409, "Webhook is inactive");
                        return;
                    }

                    String requestMethod = ctx.request().method().name();
                    if (!isMethodAllowed(webhook, requestMethod)) {
                        ctx.response()
                                .setStatusCode(405)
                                .putHeader("Allow", normalizeMethods(webhook.methods()))
                                .putHeader("Content-Type", "application/json")
                                .end(JsonUtil.toJson(Map.of(
                                        "status", 405,
                                        "message", "Method not allowed",
                                        "allowedMethods", normalizeMethods(webhook.methods())
                                )));
                        return;
                    }

                    RequestLog originalLog = buildRequestLog(ctx, webhook.id());
                    String proxyBody = templateService.renderRequestTemplate(webhook, originalLog);
                    RequestLog proxyRequestLog = proxyBody != null ? originalLog.withBody(proxyBody) : originalLog;

                    proxyService.forward(webhook, proxyRequestLog)
                            .map(proxyLog -> originalLog.withProxyResult(
                                    proxyLog.responseStatus(),
                                    proxyLog.proxyResponse(),
                                    proxyLog.proxyDurationMs()))
                            .compose(finalLog -> requestLogService.save(finalLog, webhook.maxLogCount()))
                            .onSuccess(savedLog -> {
                                log.info("Webhook received: slug={}, requestId={}, method={}", webhook.slug(), savedLog.id(), requestMethod);
                                sendReceiverResponse(ctx, webhook, savedLog);
                            })
                            .onFailure(ctx::fail);
                })
                .onFailure(ctx::fail);
    }

    private RequestLog buildRequestLog(RoutingContext ctx, UUID webhookId) {
        return new RequestLog(
                IdGenerator.generate(),
                webhookId,
                Instant.now(),
                ctx.request().method().name(),
                ctx.request().uri(),
                toSingleValueMap(ctx.queryParams()),
                toSingleValueMap(ctx.request().headers()),
                ctx.body() != null ? ctx.body().asString() : null,
                ctx.request().getHeader("Content-Type"),
                extractSourceIp(ctx),
                null,
                null,
                null
        );
    }

    private void sendReceiverResponse(RoutingContext ctx, Webhook webhook, RequestLog savedLog) {
        String body;
        int status;
        String contentType;

        String responseTemplate = templateService.renderResponseTemplate(webhook, savedLog);
        if (responseTemplate != null) {
            body = responseTemplate;
            status = savedLog.responseStatus() != null ? savedLog.responseStatus() : 200;
            contentType = looksLikeJson(body) ? "application/json" : "text/plain; charset=UTF-8";
        } else if (savedLog.responseStatus() != null || savedLog.proxyResponse() != null) {
            body = savedLog.proxyResponse() != null ? savedLog.proxyResponse() : "";
            status = savedLog.responseStatus() != null ? savedLog.responseStatus() : 200;
            contentType = looksLikeJson(body) ? "application/json" : "text/plain; charset=UTF-8";
        } else {
            body = JsonUtil.toJson(Map.of(
                    "status", "accepted",
                    "requestId", savedLog.id(),
                    "webhookId", savedLog.webhookId(),
                    "receivedAt", savedLog.receivedAt()
            ));
            status = 202;
            contentType = "application/json";
        }

        ctx.response()
                .setStatusCode(status)
                .putHeader("X-Request-Id", savedLog.id().toString())
                .putHeader("Content-Type", contentType)
                .end(body);
    }

    private String normalizeMethods(String methods) {
        if (methods == null || methods.isBlank()) {
            return "GET, POST";
        }
        return java.util.Arrays.stream(methods.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElse("GET, POST");
    }

    private boolean isMethodAllowed(Webhook webhook, String requestMethod) {
        if (webhook.methods() == null || webhook.methods().isBlank()) {
            return true;
        }
        String normalizedMethod = requestMethod.toUpperCase(Locale.ROOT);
        return java.util.Arrays.stream(webhook.methods().split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s.toUpperCase(Locale.ROOT))
                .anyMatch(normalizedMethod::equals);
    }

    private Map<String, String> toSingleValueMap(MultiMap multiMap) {
        Map<String, String> result = new LinkedHashMap<>();
        if (multiMap == null) {
            return result;
        }
        for (String name : multiMap.names()) {
            result.put(name, String.join(", ", multiMap.getAll(name)));
        }
        return result;
    }

    private String extractSourceIp(RoutingContext ctx) {
        String forwardedFor = ctx.request().getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int comma = forwardedFor.indexOf(',');
            return comma >= 0 ? forwardedFor.substring(0, comma).trim() : forwardedFor.trim();
        }
        if (ctx.request().remoteAddress() != null) {
            return ctx.request().remoteAddress().host();
        }
        return null;
    }

    private boolean looksLikeJson(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            JsonUtil.mapper().readTree(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
