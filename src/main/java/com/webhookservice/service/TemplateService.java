package com.webhookservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.webhookservice.model.RequestLog;
import com.webhookservice.model.Webhook;
import com.webhookservice.util.JsonUtil;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateService {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}|\\$\\{\\s*([^{}]+?)\\s*}");
    private static final Pattern EXACT_TOKEN_PATTERN = Pattern.compile("^\\s*(?:\\{\\{\\s*([^{}]+?)\\s*}}|\\$\\{\\s*([^{}]+?)\\s*})\\s*$");

    public String render(String template, Map<String, ?> data) {
        if (template == null) {
            return null;
        }

        Map<String, ?> safeData = data != null ? data : Map.of();
        JsonNode jsonNode = tryParseJson(template);
        if (jsonNode != null) {
            return JsonUtil.toJson(renderNode(jsonNode, safeData));
        }
        return replaceTokens(template, safeData);
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

    public Map<String, Object> buildContext(Webhook webhook, RequestLog requestLog) {
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

        // Top-level aliases for convenience in templates.
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

    private JsonNode tryParseJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return JsonUtil.mapper().readTree(value);
        } catch (Exception e) {
            return null;
        }
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

    private JsonNode renderNode(JsonNode node, Map<String, ?> data) {
        if (node.isObject()) {
            ObjectNode rendered = JsonUtil.mapper().createObjectNode();
            node.fields().forEachRemaining(entry -> rendered.set(entry.getKey(), renderNode(entry.getValue(), data)));
            return rendered;
        }
        if (node.isArray()) {
            ArrayNode rendered = JsonUtil.mapper().createArrayNode();
            node.forEach(item -> rendered.add(renderNode(item, data)));
            return rendered;
        }
        if (node.isTextual()) {
            return renderTextNode(node.asText(), data);
        }
        return node.deepCopy();
    }

    private JsonNode renderTextNode(String text, Map<String, ?> data) {
        Matcher exactMatcher = EXACT_TOKEN_PATTERN.matcher(text);
        if (exactMatcher.matches()) {
            Object resolved = resolvePath(data, extractExpression(exactMatcher));
            if (resolved == null) {
                return NullNode.getInstance();
            }
            if (resolved instanceof JsonNode jsonNode) {
                return jsonNode;
            }
            return JsonUtil.mapper().valueToTree(resolved);
        }
        return TextNode.valueOf(replaceTokens(text, data));
    }

    private String replaceTokens(String template, Map<String, ?> data) {
        Matcher matcher = TOKEN_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            Object resolved = resolvePath(data, extractExpression(matcher));
            String replacement = stringify(resolved);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String extractExpression(Matcher matcher) {
        return Objects.requireNonNullElseGet(matcher.group(1), () -> matcher.group(2)).trim();
    }

    private Object resolvePath(Map<String, ?> data, String expression) {
        Object current = data;
        for (String segment : expression.split("\\.")) {
            if (segment.isBlank()) {
                return null;
            }
            current = resolveSegment(current, segment.trim());
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private Object resolveSegment(Object current, String segment) {
        if (current instanceof Map<?, ?> map) {
            return map.get(segment);
        }
        if (current instanceof List<?> list) {
            try {
                int index = Integer.parseInt(segment);
                return index >= 0 && index < list.size() ? list.get(index) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode.isValueNode() ? jsonNode.asText() : jsonNode.toString();
        }
        return JsonUtil.toJson(value);
    }
}
