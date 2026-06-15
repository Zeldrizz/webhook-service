package com.webhookservice.handler;

import com.webhookservice.model.dto.Page;
import com.webhookservice.model.dto.RequestLogResponse;
import com.webhookservice.model.dto.StatsResponse;
import com.webhookservice.service.RequestLogService;
import com.webhookservice.service.WebhookService;
import com.webhookservice.util.JsonUtil;
import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class RequestLogHandler {

    private final WebhookService webhookService;
    private final RequestLogService requestLogService;

    public RequestLogHandler(WebhookService webhookService, RequestLogService requestLogService) {
        this.webhookService = webhookService;
        this.requestLogService = requestLogService;
    }

    public void list(RoutingContext ctx) {
        UUID webhookId = parseUUID(ctx, "id");
        if (webhookId == null) return;

        int page = Math.max(0, intParam(ctx, "page", 0));
        int size = Math.min(Math.max(1, intParam(ctx, "size", 20)), 100);
        String method = normalizeMethodParam(ctx.request().getParam("method"));
        String statusFilter = normalizeStatusParam(ctx.request().getParam("status"));
        if ("__invalid__".equals(method)) {
            ErrorHandler.sendError(ctx, 400, "Invalid method filter");
            return;
        }
        if ("__invalid__".equals(statusFilter)) {
            ErrorHandler.sendError(ctx, 400, "Invalid status filter");
            return;
        }

        webhookService.getById(webhookId)
                .onSuccess(webhook -> {
                    if (webhook == null) {
                        ErrorHandler.sendNotFound(ctx, "Webhook");
                        return;
                    }

                    requestLogService.listByWebhookId(webhookId, page, size, method, statusFilter)
                            .onSuccess(result -> {
                                List<RequestLogResponse> items = result.items().stream()
                                        .map(RequestLogResponse::from)
                                        .toList();
                                Page<RequestLogResponse> response = new Page<>(items, result.page(), result.size(), result.total());
                                ctx.response()
                                        .putHeader("Content-Type", "application/json")
                                        .end(JsonUtil.toJson(response));
                            })
                            .onFailure(ctx::fail);
                })
                .onFailure(ctx::fail);
    }

    public void getById(RoutingContext ctx) {
        UUID webhookId = parseUUID(ctx, "id");
        UUID requestId = parseUUID(ctx, "requestId");
        if (webhookId == null || requestId == null) return;

        webhookService.getById(webhookId)
                .onSuccess(webhook -> {
                    if (webhook == null) {
                        ErrorHandler.sendNotFound(ctx, "Webhook");
                        return;
                    }

                    requestLogService.getByWebhookIdAndId(webhookId, requestId)
                            .onSuccess(requestLog -> {
                                if (requestLog == null) {
                                    ErrorHandler.sendNotFound(ctx, "Request log");
                                    return;
                                }
                                ctx.response()
                                        .putHeader("Content-Type", "application/json")
                                        .end(JsonUtil.toJson(RequestLogResponse.from(requestLog)));
                            })
                            .onFailure(ctx::fail);
                })
                .onFailure(ctx::fail);
    }

    public void clear(RoutingContext ctx) {
        UUID webhookId = parseUUID(ctx, "id");
        if (webhookId == null) return;

        webhookService.getById(webhookId)
                .onSuccess(webhook -> {
                    if (webhook == null) {
                        ErrorHandler.sendNotFound(ctx, "Webhook");
                        return;
                    }

                    requestLogService.clearByWebhookId(webhookId)
                            .onSuccess(deleted -> ctx.response()
                                    .putHeader("X-Deleted-Count", String.valueOf(deleted))
                                    .setStatusCode(204)
                                    .end())
                            .onFailure(ctx::fail);
                })
                .onFailure(ctx::fail);
    }

    public void stats(RoutingContext ctx) {
        UUID webhookId = parseUUID(ctx, "id");
        if (webhookId == null) return;

        webhookService.getById(webhookId)
                .onSuccess(webhook -> {
                    if (webhook == null) {
                        ErrorHandler.sendNotFound(ctx, "Webhook");
                        return;
                    }

                    requestLogService.getStats(webhookId)
                            .onSuccess(stats -> sendStats(ctx, stats))
                            .onFailure(ctx::fail);
                })
                .onFailure(ctx::fail);
    }

    private void sendStats(RoutingContext ctx, StatsResponse stats) {
        ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonUtil.toJson(stats));
    }

    private UUID parseUUID(RoutingContext ctx, String param) {
        String value = ctx.pathParam(param);
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            ErrorHandler.sendError(ctx, 400, "Invalid UUID: " + value);
            return null;
        }
    }

    private int intParam(RoutingContext ctx, String name, int defaultValue) {
        String value = ctx.request().getParam(name);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String normalizeMethodParam(String value) {
        if (value == null || value.isBlank() || "all".equalsIgnoreCase(value)) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "GET", "POST", "PUT", "PATCH", "DELETE" -> normalized;
            default -> "__invalid__";
        };
    }

    private String normalizeStatusParam(String value) {
        if (value == null || value.isBlank() || "all".equalsIgnoreCase(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("none".equals(normalized)) {
            return normalized;
        }
        if (normalized.matches("[1-5]xx")) {
            return normalized;
        }
        try {
            int status = Integer.parseInt(normalized);
            return status >= 100 && status <= 599 ? normalized : "__invalid__";
        } catch (NumberFormatException e) {
            return "__invalid__";
        }
    }
}
