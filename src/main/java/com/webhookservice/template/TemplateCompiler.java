package com.webhookservice.template;

import java.util.ArrayList;
import java.util.List;

public class TemplateCompiler {

    public CompiledTemplate compile(String template) {
        String source = template == null ? "" : template;
        ParseResult result = parseUntil(source, 0, null, -1);
        return new CompiledTemplate(result.nodes());
    }

    private ParseResult parseUntil(String source, int start, String expectedClose, int openingOffset) {
        List<CompiledTemplate.TemplateNode> nodes = new ArrayList<>();
        int cursor = start;

        while (cursor < source.length()) {
            Token token = nextToken(source, cursor);
            if (token == null) {
                if (expectedClose != null) {
                    throw error("Unclosed template block '{{#" + expectedClose + "}}'", openingOffset >= 0 ? openingOffset : cursor, source);
                }
                nodes.add(new CompiledTemplate.LiteralNode(source.substring(cursor)));
                return new ParseResult(nodes, source.length());
            }

            if (token.start() > cursor) {
                nodes.add(new CompiledTemplate.LiteralNode(source.substring(cursor, token.start())));
            }

            String expression = token.expression().trim();
            if (expression.isEmpty()) {
                throw error("Empty template expression", token.start(), source);
            }

            if (token.kind() == TokenKind.DOLLAR) {
                if (expression.startsWith("#") || expression.startsWith("/")) {
                    throw error("Block tags must use '{{...}}' syntax", token.start(), source);
                }
                nodes.add(variableNode(expression, token.start(), source));
                cursor = token.end();
                continue;
            }

            TagParts tag = splitTag(expression);
            if ("#if".equals(tag.marker())) {
                requireArgument(tag, token.start(), source);
                ParseResult inner = parseUntil(source, token.end(), "if", token.start());
                nodes.add(new CompiledTemplate.IfNode(tag.argument(), inner.nodes()));
                cursor = inner.end();
                continue;
            }
            if ("#each".equals(tag.marker())) {
                requireArgument(tag, token.start(), source);
                ParseResult inner = parseUntil(source, token.end(), "each", token.start());
                nodes.add(new CompiledTemplate.EachNode(tag.argument(), inner.nodes()));
                cursor = inner.end();
                continue;
            }
            if (tag.marker().startsWith("#")) {
                throw error("Unsupported template block '{{" + tag.marker() + "}}'", token.start(), source);
            }
            if (tag.marker().startsWith("/")) {
                String close = tag.marker().substring(1);
                if (!tag.argument().isBlank()) {
                    throw error("Closing block '{{/" + close + "}}' must not have an expression", token.start(), source);
                }
                if (expectedClose == null) {
                    throw error("Unexpected closing template block '{{/" + close + "}}'", token.start(), source);
                }
                if (!expectedClose.equals(close)) {
                    throw error("Mismatched template block '{{/" + close + "}}'; expected '{{/" + expectedClose + "}}'", token.start(), source);
                }
                return new ParseResult(nodes, token.end());
            }

            nodes.add(variableNode(expression, token.start(), source));
            cursor = token.end();
        }

        if (expectedClose != null) {
            throw error("Unclosed template block '{{#" + expectedClose + "}}'", openingOffset >= 0 ? openingOffset : source.length(), source);
        }
        return new ParseResult(nodes, source.length());
    }

    private CompiledTemplate.VariableNode variableNode(String expression, int offset, String source) {
        VariableSpec spec = parseVariableExpression(expression, offset, source);
        return new CompiledTemplate.VariableNode(spec.path(), spec.fallback());
    }

    private VariableSpec parseVariableExpression(String expression, int offset, String source) {
        int fallbackSeparator = findFallbackSeparator(expression, '|');
        int fallbackLength = 1;
        if (fallbackSeparator < 0) {
            fallbackSeparator = findDoubleQuestionFallbackSeparator(expression);
            fallbackLength = 2;
        }

        if (fallbackSeparator < 0) {
            return new VariableSpec(expression.trim(), null);
        }

        String path = expression.substring(0, fallbackSeparator).trim();
        String fallbackRaw = expression.substring(fallbackSeparator + fallbackLength).trim();
        if (path.isEmpty()) {
            throw error("Fallback expression requires a key before default value", offset, source);
        }
        return new VariableSpec(path, parseQuotedFallback(fallbackRaw, offset, source));
    }

    private int findFallbackSeparator(String expression, char separator) {
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (quoted && c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"' || c == '\'') {
                if (!quoted) {
                    quoted = true;
                    quote = c;
                } else if (quote == c) {
                    quoted = false;
                }
                continue;
            }
            if (!quoted && c == separator) {
                return i;
            }
        }
        return -1;
    }

    private int findDoubleQuestionFallbackSeparator(String expression) {
        boolean quoted = false;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < expression.length() - 1; i++) {
            char c = expression.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (quoted && c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"' || c == '\'') {
                if (!quoted) {
                    quoted = true;
                    quote = c;
                } else if (quote == c) {
                    quoted = false;
                }
                continue;
            }
            if (!quoted && c == '?' && expression.charAt(i + 1) == '?') {
                return i;
            }
        }
        return -1;
    }

    private String parseQuotedFallback(String raw, int offset, String source) {
        if (raw.length() < 2) {
            throw error("Fallback default value must be quoted", offset, source);
        }
        char quote = raw.charAt(0);
        if ((quote != '"' && quote != '\'') || raw.charAt(raw.length() - 1) != quote) {
            throw error("Fallback default value must be quoted", offset, source);
        }

        String body = raw.substring(1, raw.length() - 1);
        StringBuilder result = new StringBuilder();
        boolean escaped = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (!escaped && c == '\\') {
                escaped = true;
                continue;
            }
            if (escaped) {
                result.append(switch (c) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '\\' -> '\\';
                    case '"' -> '"';
                    case '\'' -> '\'';
                    default -> c;
                });
                escaped = false;
            } else {
                result.append(c);
            }
        }
        if (escaped) {
            result.append('\\');
        }
        return result.toString();
    }

    private void requireArgument(TagParts tag, int offset, String source) {
        if (tag.argument().isBlank()) {
            throw error("Template block '{{" + tag.marker() + "}}' requires an expression", offset, source);
        }
    }

    private TagParts splitTag(String expression) {
        String trimmed = expression.trim();
        int firstWhitespace = -1;
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isWhitespace(trimmed.charAt(i))) {
                firstWhitespace = i;
                break;
            }
        }
        if (firstWhitespace < 0) {
            return new TagParts(trimmed, "");
        }
        return new TagParts(trimmed.substring(0, firstWhitespace), trimmed.substring(firstWhitespace + 1).trim());
    }

    private Token nextToken(String source, int from) {
        int curlyStart = source.indexOf("{{", from);
        int dollarStart = source.indexOf("${", from);
        if (curlyStart < 0 && dollarStart < 0) {
            return null;
        }
        if (dollarStart >= 0 && (curlyStart < 0 || dollarStart < curlyStart)) {
            int end = source.indexOf('}', dollarStart + 2);
            if (end < 0) {
                throw error("Unclosed template token '${'", dollarStart, source);
            }
            return new Token(TokenKind.DOLLAR, dollarStart, end + 1, source.substring(dollarStart + 2, end));
        }

        int end = source.indexOf("}}", curlyStart + 2);
        if (end < 0) {
            throw error("Unclosed template token '{{'", curlyStart, source);
        }
        return new Token(TokenKind.CURLY, curlyStart, end + 2, source.substring(curlyStart + 2, end));
    }

    private TemplateParseException error(String message, int offset, String source) {
        int safeOffset = Math.max(0, Math.min(offset, source.length()));
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < safeOffset; i++) {
            if (source.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        int lineEnd = source.indexOf('\n', lineStart);
        if (lineEnd < 0) {
            lineEnd = source.length();
        }
        int column = safeOffset - lineStart + 1;
        String snippet = source.substring(lineStart, lineEnd);
        return new TemplateParseException(message, line, column, snippet);
    }

    private enum TokenKind {
        CURLY, DOLLAR
    }

    private record Token(TokenKind kind, int start, int end, String expression) {
    }

    private record ParseResult(List<CompiledTemplate.TemplateNode> nodes, int end) {
    }

    private record TagParts(String marker, String argument) {
    }

    private record VariableSpec(String path, String fallback) {
    }
}
