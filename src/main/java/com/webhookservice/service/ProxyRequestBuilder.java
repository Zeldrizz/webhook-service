package com.webhookservice.service;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;

import java.util.Map;

public class ProxyRequestBuilder {

    private final WebClient webClient;
    private String method = "POST";
    private String absoluteUrl;
    private final MultiMap headers = MultiMap.caseInsensitiveMultiMap();

    public ProxyRequestBuilder(WebClient webClient) {
        this.webClient = webClient;
    }

    public ProxyRequestBuilder method(String method) {
        this.method = method.toUpperCase();
        return this;
    }

    public ProxyRequestBuilder url(String url) {
        this.absoluteUrl = url;
        return this;
    }

    public ProxyRequestBuilder headers(Map<String, String> headers) {
        if (headers != null) {
            headers.forEach(this.headers::set);
        }
        return this;
    }

    public ProxyRequestBuilder header(String name, String value) {
        this.headers.set(name, value);
        return this;
    }

    public HttpRequest<Buffer> build() {
        if (absoluteUrl == null || absoluteUrl.isBlank()) {
            throw new IllegalStateException("URL is required for proxy request");
        }

        HttpRequest<Buffer> request = switch (method) {
            case "GET" -> webClient.getAbs(absoluteUrl);
            case "POST" -> webClient.postAbs(absoluteUrl);
            case "PUT" -> webClient.putAbs(absoluteUrl);
            case "DELETE" -> webClient.deleteAbs(absoluteUrl);
            case "PATCH" -> webClient.patchAbs(absoluteUrl);
            default -> webClient.postAbs(absoluteUrl);
        };

        request.putHeaders(headers);
        return request;
    }
}
