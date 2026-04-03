package com.webhookservice.handler;

import com.webhookservice.model.dto.*;
import com.webhookservice.service.WebhookService;
import com.webhookservice.util.JsonUtil;
import io.vertx.ext.web.RoutingContext;

import java.util.List;
import java.util.UUID;

public class WebhookApiHandler {

    private final WebhookService webhookService;
    private final String baseUrl;

    public WebhookApiHandler(WebhookService webhookService, String baseUrl) {
        this.webhookService = webhookService;
        this.baseUrl = baseUrl;
    }

    public void create(RoutingContext ctx) {
        CreateWebhookDto dto = JsonUtil.fromJson(ctx.body().asString(), CreateWebhookDto.class);
        webhookService.create(dto)
                .onSuccess(webhook -> {
                    WebhookResponse response = WebhookResponse.from(webhook, baseUrl);
                    ctx.response()
                            .setStatusCode(201)
                            .putHeader("Content-Type", "application/json")
                            .end(JsonUtil.toJson(response));
                })
                .onFailure(ctx::fail);
    }

    public void getById(RoutingContext ctx) {
        UUID id = parseUUID(ctx, "id");
        if (id == null) return;

        webhookService.getById(id)
                .onSuccess(webhook -> {
                    if (webhook == null) {
                        ErrorHandler.sendNotFound(ctx, "Webhook");
                        return;
                    }
                    ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(JsonUtil.toJson(WebhookResponse.from(webhook, baseUrl)));
                })
                .onFailure(ctx::fail);
    }

    public void getBySlug(RoutingContext ctx) {
        String slug = ctx.pathParam("slug");
        webhookService.getBySlug(slug)
                .onSuccess(webhook -> {
                    if (webhook == null) {
                        ErrorHandler.sendNotFound(ctx, "Webhook");
                        return;
                    }
                    ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(JsonUtil.toJson(WebhookResponse.from(webhook, baseUrl)));
                })
                .onFailure(ctx::fail);
    }

    public void list(RoutingContext ctx) {
        int page = intParam(ctx, "page", 0);
        int size = intParam(ctx, "size", 20);
        size = Math.min(size, 100);

        webhookService.list(page, size)
                .onSuccess(pageResult -> {
                    List<WebhookResponse> items = pageResult.items().stream()
                            .map(w -> WebhookResponse.from(w, baseUrl))
                            .toList();
                    Page<WebhookResponse> responsePages = new Page<>(items, pageResult.page(), pageResult.size(), pageResult.total());
                    ctx.response()
                            .putHeader("Content-Type", "application/json")
                            .end(JsonUtil.toJson(responsePages));
                })
                .onFailure(ctx::fail);
    }

    public void update(RoutingContext ctx) {
        UUID id = parseUUID(ctx, "id");
        if (id == null) return;

        UpdateWebhookDto dto = JsonUtil.fromJson(ctx.body().asString(), UpdateWebhookDto.class);
        webhookService.update(id, dto)
                .onSuccess(webhook -> ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonUtil.toJson(WebhookResponse.from(webhook, baseUrl))))
                .onFailure(ctx::fail);
    }

    public void delete(RoutingContext ctx) {
        UUID id = parseUUID(ctx, "id");
        if (id == null) return;

        webhookService.delete(id)
                .onSuccess(deleted -> {
                    if (!deleted) {
                        ErrorHandler.sendNotFound(ctx, "Webhook");
                        return;
                    }
                    ctx.response().setStatusCode(204).end();
                })
                .onFailure(ctx::fail);
    }

    public void toggle(RoutingContext ctx) {
        UUID id = parseUUID(ctx, "id");
        if (id == null) return;

        webhookService.toggle(id)
                .onSuccess(webhook -> ctx.response()
                        .putHeader("Content-Type", "application/json")
                        .end(JsonUtil.toJson(WebhookResponse.from(webhook, baseUrl))))
                .onFailure(ctx::fail);
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
}
