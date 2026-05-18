package com.webhookservice.template;

import com.webhookservice.cache.CacheNames;
import com.webhookservice.cache.CachePerCacheMetrics;
import com.webhookservice.cache.CaffeineCache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

public class CompiledTemplateCache {

    private final CaffeineCache<String, CompiledTemplate> inner;

    public CompiledTemplateCache(long maxSize, long ttlSeconds, boolean enabled) {
        this(new CaffeineCache<>(
                CacheNames.COMPILED_TEMPLATE,
                maxSize,
                Duration.ofSeconds(ttlSeconds),
                enabled
        ));
    }

    public CompiledTemplateCache(CaffeineCache<String, CompiledTemplate> inner) {
        this.inner = inner;
    }

    public CompiledTemplate getOrCompile(String template, TemplateCompiler compiler) {
        String normalizedTemplate = template == null ? "" : template;
        String key = sha256(normalizedTemplate);
        return inner.getOrLoad(key, () -> compiler.compile(normalizedTemplate));
    }

    public CachePerCacheMetrics snapshot() {
        return inner.snapshot();
    }

    public void invalidateAll() {
        inner.invalidateAll();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
