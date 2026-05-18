package com.webhookservice.template;

import com.fasterxml.jackson.databind.JsonNode;
import com.webhookservice.util.JsonUtil;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CompiledTemplate(List<TemplateNode> nodes) {

    public CompiledTemplate {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
    }

    public String render(Map<String, Object> context) {
        StringBuilder result = new StringBuilder();
        Map<String, Object> safeContext = context == null ? Map.of() : context;
        renderNodes(nodes, result, safeContext);
        return result.toString();
    }

    static void renderNodes(List<TemplateNode> nodes, StringBuilder result, Map<String, Object> context) {
        for (TemplateNode node : nodes) {
            node.render(result, context);
        }
    }

    public interface TemplateNode {
        void render(StringBuilder result, Map<String, Object> context);
    }

    public record LiteralNode(String text) implements TemplateNode {
        @Override
        public void render(StringBuilder result, Map<String, Object> context) {
            result.append(text == null ? "" : text);
        }
    }

    public record VariableNode(String path, String fallback) implements TemplateNode {
        public VariableNode {
            path = path == null ? "" : path.trim();
        }

        @Override
        public void render(StringBuilder result, Map<String, Object> context) {
            Object value = resolvePath(context, path);
            if (value == null && fallback != null) {
                result.append(fallback);
                return;
            }
            result.append(stringify(value));
        }
    }

    public record IfNode(String path, List<TemplateNode> children) implements TemplateNode {
        public IfNode {
            path = path == null ? "" : path.trim();
            children = children == null ? List.of() : List.copyOf(children);
        }

        @Override
        public void render(StringBuilder result, Map<String, Object> context) {
            if (isTruthy(resolvePath(context, path))) {
                renderNodes(children, result, context);
            }
        }
    }

    public record EachNode(String path, List<TemplateNode> children) implements TemplateNode {
        public EachNode {
            path = path == null ? "" : path.trim();
            children = children == null ? List.of() : List.copyOf(children);
        }

        @Override
        public void render(StringBuilder result, Map<String, Object> context) {
            List<IterationItem> items = iterationItems(resolvePath(context, path));
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> childContext = iterationContext(context, items.get(i), i, items.size());
                renderNodes(children, result, childContext);
            }
        }
    }

    static Object resolvePath(Map<String, Object> data, String expression) {
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

    private static Object resolveSegment(Object current, String segment) {
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
        if (current != null && current.getClass().isArray()) {
            Integer index = parseIndex(segment);
            int length = Array.getLength(current);
            return index != null && index >= 0 && index < length ? Array.get(current, index) : null;
        }
        return null;
    }

    private static Integer parseIndex(String segment) {
        try {
            return Integer.parseInt(segment);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isTruthy(Object value) {
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

    private static boolean isTruthyString(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return !("false".equals(normalized) || "0".equals(normalized) || "no".equals(normalized) || "off".equals(normalized));
    }

    private static List<IterationItem> iterationItems(Object value) {
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

    private static Map<String, Object> iterationContext(
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

    private static String stringify(Object value) {
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

    private record IterationItem(Object key, Object value) {
    }
}
