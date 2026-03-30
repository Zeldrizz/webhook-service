package com.webhookservice.model.dto;

import com.webhookservice.model.RequestLog;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RequestLogResponse(
        UUID id,
        UUID webhookId,
        Instant receivedAt,
        String method,
        String url,
        Map<String, String> queryParams,
        Map<String, String> headers,
        String body,
        String contentType,
        String sourceIp,
        Integer responseStatus,
        String proxyResponse,
        Long proxyDurationMs
) {

    public static RequestLogResponse from(RequestLog log) {
        return new RequestLogResponse(
                log.id(),
                log.webhookId(),
                log.receivedAt(),
                log.method(),
                log.url(),
                log.queryParams(),
                log.headers(),
                log.body(),
                log.contentType(),
                log.sourceIp(),
                log.responseStatus(),
                log.proxyResponse(),
                log.proxyDurationMs()
        );
    }
}
