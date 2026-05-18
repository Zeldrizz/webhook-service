package com.webhookservice.template;

public class TemplateParseException extends IllegalArgumentException {

    private final int line;
    private final int column;
    private final String snippet;

    public TemplateParseException(String message, int line, int column, String snippet) {
        super(message);
        this.line = line;
        this.column = column;
        this.snippet = snippet;
    }

    public int line() {
        return line;
    }

    public int column() {
        return column;
    }

    public String snippet() {
        return snippet;
    }
}
