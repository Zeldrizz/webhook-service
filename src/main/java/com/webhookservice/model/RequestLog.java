package com.webhookservice.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record RequestLog(
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

    public RequestLog withBody(String body) {
        return new RequestLog(id, webhookId, receivedAt, method, url, queryParams, headers,
                body, contentType, sourceIp, responseStatus, proxyResponse, proxyDurationMs);
    }

    public RequestLog withProxyResult(Integer responseStatus, String proxyResponse, Long proxyDurationMs) {
        return new RequestLog(id, webhookId, receivedAt, method, url, queryParams, headers,
                body, contentType, sourceIp, responseStatus, proxyResponse, proxyDurationMs);
    }
}
