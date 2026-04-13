package com.webhookservice.handler;

import com.webhookservice.model.dto.TemplatePreviewRequest;
import com.webhookservice.model.dto.TemplatePreviewResponse;
import com.webhookservice.service.TemplateService;
import com.webhookservice.util.JsonUtil;
import io.vertx.ext.web.RoutingContext;

public class TemplateHandler {

    private final TemplateService templateService;

    public TemplateHandler(TemplateService templateService) {
        this.templateService = templateService;
    }

    public void preview(RoutingContext ctx) {
        TemplatePreviewRequest request = JsonUtil.fromJson(ctx.body().asString(), TemplatePreviewRequest.class);
        if (request.template() == null) {
            throw new IllegalArgumentException("template is required");
        }

        String result = templateService.render(request.template(), request.data());
        ctx.response()
                .putHeader("Content-Type", "application/json")
                .end(JsonUtil.toJson(new TemplatePreviewResponse(result)));
    }
}
