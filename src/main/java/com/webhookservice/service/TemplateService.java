package com.webhookservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.webhookservice.model.RequestLog;
import com.webhookservice.model.Webhook;
import com.webhookservice.util.JsonUtil;

import java.lang.reflect.Array;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateService {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}|\\$\\{\\s*([^{}]+?)\\s*}");
    private static final Pattern EXACT_TOKEN_PATTERN = Pattern.compile("^\\s*(?:\\{\\{\\s*([^{}]+?)\\s*}}|\\$\\{\\s*([^{}]+?)\\s*})\\s*$");
    private static final Pattern SECTION_TAG_PATTERN = Pattern.compile("\\{\\{\\s*(#if|#each|/if|/each)(?:\\s+([^{}]+?))?\\s*}}");

    public String render(String template, Map data) {
        if (template == null) {
            return null;
        }

        Map<String, Object> safeData = normalizeData(data);
        JsonNode jsonNode = tryParseJson(template);
        if (jsonNode != null) {
            return JsonUtil.toJson(renderNode(jsonNode, safeData));
        }

        return renderSections(template, safeData);
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

    private JsonNode renderNode(JsonNode node, Map<String, Object> data) {
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

    private JsonNode renderTextNode(String text, Map<String, Object> data) {
        if (!containsSectionTag(text)) {
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
        return TextNode.valueOf(renderSections(text, data));
    }

    private String renderSections(String template, Map<String, Object> data) {
        Matcher matcher = SECTION_TAG_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        int cursor = 0;

        while (matcher.find(cursor)) {
            String marker = matcher.group(1);
            if (marker.startsWith("/")) {
                throw new IllegalArgumentException("Unexpected closing template block '{{" + marker + "}}'");
            }

            String expression = matcher.group(2);
            if (expression == null || expression.isBlank()) {
                throw new IllegalArgumentException("Template block '{{" + marker + "}}' requires an expression");
            }

            result.append(replaceTokens(template.substring(cursor, matcher.start()), data));

            String blockType = marker.substring(1);
            SectionMatch section = findMatchingSection(template, matcher.end(), blockType);
            String innerTemplate = template.substring(matcher.end(), section.start());
            result.append(renderSection(blockType, expression.trim(), innerTemplate, data));
            cursor = section.end();
        }

        result.append(replaceTokens(template.substring(cursor), data));
        return result.toString();
    }

    private SectionMatch findMatchingSection(String template, int searchStart, String openType) {
        Matcher matcher = SECTION_TAG_PATTERN.matcher(template);
        Deque<String> stack = new ArrayDeque<>();
        stack.push(openType);
        int cursor = searchStart;

        while (matcher.find(cursor)) {
            String marker = matcher.group(1);
            if (marker.startsWith("#")) {
                String expression = matcher.group(2);
                if (expression == null || expression.isBlank()) {
                    throw new IllegalArgumentException("Template block '{{" + marker + "}}' requires an expression");
                }
                stack.push(marker.substring(1));
            } else {
                String closeType = marker.substring(1);
                if (stack.isEmpty() || !stack.peek().equals(closeType)) {
                    String expected = stack.isEmpty() ? openType : stack.peek();
                    throw new IllegalArgumentException(
                            "Mismatched template block '{{" + marker + "}}'; expected '{{/" + expected + "}}'"
                    );
                }
                stack.pop();
                if (stack.isEmpty()) {
                    return new SectionMatch(matcher.start(), matcher.end());
                }
            }
            cursor = matcher.end();
        }

        throw new IllegalArgumentException("Unclosed template block '{{#" + openType + "}}'");
    }

    private String renderSection(String blockType, String expression, String innerTemplate, Map<String, Object> data) {
        Object value = resolvePath(data, expression);
        return switch (blockType) {
            case "if" -> isTruthy(value) ? renderSections(innerTemplate, data) : "";
            case "each" -> renderEach(value, innerTemplate, data);
            default -> throw new IllegalArgumentException("Unsupported template block '{{#" + blockType + "}}'");
        };
    }

    private String renderEach(Object value, String innerTemplate, Map<String, Object> parentData) {
        List<IterationItem> items = iterationItems(value);
        if (items.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            IterationItem item = items.get(i);
            Map<String, Object> childData = iterationContext(parentData, item, i, items.size());
            result.append(renderSections(innerTemplate, childData));
        }
        return result.toString();
    }

    private List<IterationItem> iterationItems(Object value) {
        List<IterationItem> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        if (value instanceof JsonNode jsonNode) {
            if (jsonNode.isArray()) {
                jsonNode.forEach(item -> result.add(new IterationItem(null, item)));
            } else if (jsonNode.isObject()) {
                jsonNode.fields().forEachRemaining(entry -> result.add(new IterationItem(entry.getKey(), entry.getValue())));
            }
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, itemValue) -> result.add(new IterationItem(key, itemValue)));
            return result;
        }
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> result.add(new IterationItem(null, item)));
            return result;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                result.add(new IterationItem(null, Array.get(value, i)));
            }
        }
        return result;
    }

    private Map<String, Object> iterationContext(
            Map<String, Object> parentData,
            IterationItem item,
            int index,
            int total
    ) {
        Map<String, Object> childData = new LinkedHashMap<>(parentData);
        Object value = item.value();

        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nestedValue) -> {
                if (key != null) {
                    childData.put(String.valueOf(key), nestedValue);
                }
            });
        } else if (value instanceof JsonNode jsonNode && jsonNode.isObject()) {
            jsonNode.fields().forEachRemaining(entry -> childData.put(entry.getKey(), entry.getValue()));
        }

        childData.put("this", value);
        childData.put("@key", item.key());
        childData.put("@index", index);
        childData.put("@first", index == 0);
        childData.put("@last", index == total - 1);
        return childData;
    }

    private String replaceTokens(String template, Map<String, Object> data) {
        Matcher matcher = TOKEN_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String expression = extractExpression(matcher);
            if (expression.startsWith("#") || expression.startsWith("/")) {
                throw new IllegalArgumentException("Unexpected template block token '{{" + expression + "}}'");
            }
            Object resolved = resolvePath(data, expression);
            String replacement = stringify(resolved);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String extractExpression(Matcher matcher) {
        return Objects.requireNonNullElseGet(matcher.group(1), () -> matcher.group(2)).trim();
    }

    private Object resolvePath(Map<String, Object> data, String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }

        String trimmedExpression = expression.trim();
        if (".".equals(trimmedExpression)) {
            return data.get("this");
        }

        Object current = data;
        for (String segment : trimmedExpression.split("\\.")) {
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
            if (map.containsKey(segment)) {
                return map.get(segment);
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null && segment.equals(String.valueOf(entry.getKey()))) {
                    return entry.getValue();
                }
            }
            return null;
        }
        if (current instanceof List<?> list) {
            Integer index = parseIndex(segment);
            return index != null && index >= 0 && index < list.size() ? list.get(index) : null;
        }
        if (current instanceof JsonNode jsonNode) {
            if (jsonNode.isObject()) {
                JsonNode child = jsonNode.get(segment);
                return child == null || child.isMissingNode() || child.isNull() ? null : child;
            }
            if (jsonNode.isArray()) {
                Integer index = parseIndex(segment);
                return index != null && index >= 0 && index < jsonNode.size() ? jsonNode.get(index) : null;
            }
            return null;
        }
        if (current instanceof Map.Entry<?, ?> entry) {
            return switch (segment) {
                case "key" -> entry.getKey();
                case "value" -> entry.getValue();
                default -> null;
            };
        }
        return null;
    }

    private Integer parseIndex(String segment) {
        try {
            return Integer.parseInt(segment);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof JsonNode jsonNode) {
            if (jsonNode.isMissingNode() || jsonNode.isNull()) {
                return false;
            }
            if (jsonNode.isBoolean()) {
                return jsonNode.asBoolean();
            }
            if (jsonNode.isNumber()) {
                return jsonNode.asDouble() != 0d;
            }
            if (jsonNode.isTextual()) {
                return isTruthyString(jsonNode.asText());
            }
            return jsonNode.size() > 0;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0d;
        }
        if (value instanceof CharSequence sequence) {
            return isTruthyString(sequence.toString());
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        if (value instanceof Iterable<?> iterable) {
            return iterable.iterator().hasNext();
        }
        if (value.getClass().isArray()) {
            return Array.getLength(value) > 0;
        }
        return true;
    }

    private boolean isTruthyString(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return !("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized) || "off".equals(normalized));
    }

    private boolean containsSectionTag(String text) {
        return SECTION_TAG_PATTERN.matcher(text).find();
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
            if (jsonNode.isNull() || jsonNode.isMissingNode()) {
                return "";
            }
            return jsonNode.isValueNode() ? jsonNode.asText() : jsonNode.toString();
        }
        return JsonUtil.toJson(value);
    }

    private record SectionMatch(int start, int end) {
    }

    private record IterationItem(Object key, Object value) {
    }
}
