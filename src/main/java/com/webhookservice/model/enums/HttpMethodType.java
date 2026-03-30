package com.webhookservice.model.enums;

public enum HttpMethodType {
    GET,
    POST,
    PUT,
    DELETE,
    PATCH;

    public static boolean isValid(String method) {
        if (method == null) return false;
        try {
            HttpMethodType.valueOf(method.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
