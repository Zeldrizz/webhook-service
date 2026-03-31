package com.webhookservice.validation;

import java.util.List;

public class ValidationException extends RuntimeException {

    private final List<String> errors;

    public ValidationException(List<String> errors) {
        super(String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public ValidationException(String error) {
        this(List.of(error));
    }

    public List<String> errors() {
        return errors;
    }
}
