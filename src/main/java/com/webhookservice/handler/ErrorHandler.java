package com.webhookservice.handler;

import com.webhookservice.model.dto.ErrorResponse;
import com.webhookservice.util.JsonUtil;
import com.webhookservice.validation.ValidationException;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ErrorHandler implements Handler<RoutingContext> {

    private static final Logger log = LoggerFactory.getLogger(ErrorHandler.class);

    @Override
    public void handle(RoutingContext ctx) {
        Throwable failure = ctx.failure();
        if (failure == null) {
            sendError(ctx, ctx.statusCode() > 0 ? ctx.statusCode() : 500, "Unknown error");
            return;
        }

        if (failure instanceof ValidationException ve) {
            sendError(ctx, 400, String.join("; ", ve.errors()));
        } else if (failure instanceof IllegalArgumentException) {
            sendError(ctx, 400, failure.getMessage());
        } else {
            log.error("Unhandled error on {}", ctx.request().path(), failure);
            sendError(ctx, 500, "Internal server error");
        }
    }

    public static void sendError(RoutingContext ctx, int status, String message) {
        ErrorResponse error = ErrorResponse.of(status, message);
        ctx.response()
                .setStatusCode(status)
                .putHeader("Content-Type", "application/json")
                .end(JsonUtil.toJson(error));
    }

    public static void sendNotFound(RoutingContext ctx, String entity) {
        sendError(ctx, 404, entity + " not found");
    }
}
