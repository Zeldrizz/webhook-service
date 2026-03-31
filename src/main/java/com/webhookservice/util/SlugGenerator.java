package com.webhookservice.util;

import java.text.Normalizer;
import java.util.regex.Pattern;

public final class SlugGenerator {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9\\-]");
    private static final Pattern MULTIPLE_HYPHENS = Pattern.compile("-{2,}");

    private SlugGenerator() {}

    public static String generate(String name) {
        if (name == null || name.isBlank()) {
            return "webhook-" + IdGenerator.generate().toString().substring(0, 8);
        }
        String normalized = Normalizer.normalize(name.trim(), Normalizer.Form.NFD);
        String slug = normalized.toLowerCase()
                .replace(' ', '-');
        slug = NON_ALPHANUMERIC.matcher(slug).replaceAll("");
        slug = MULTIPLE_HYPHENS.matcher(slug).replaceAll("-");
        slug = slug.replaceAll("^-|-$", "");
        if (slug.isEmpty()) {
            return "webhook-" + IdGenerator.generate().toString().substring(0, 8);
        }
        return slug;
    }
}
