package com.webhookservice.service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential-backoff retry policy with optional full-jitter.
 * Delay: {@code min(maxDelay, base × multiplier^attempt) × [0.5, 1.5)} (if jitter).
 * 5xx retried when {@code retryOnStatus5xx}, 4xx never. Jitter uses {@link ThreadLocalRandom} (lock-free).
 */
public record RetryPolicy(
        int maxRetries,
        long baseDelayMs,
        long maxDelayMs,
        double multiplier,
        boolean jitter,
        boolean retryOnStatus5xx
) {

    public boolean shouldRetryStatus(int status) {
        return retryOnStatus5xx && status >= 500 && status < 600;
    }

    /** Delay before retry {@code attempt} (0-based). Non-negative, capped at {@link #maxDelayMs}. */
    public long delayForAttempt(int attempt) {
        if (attempt < 0) attempt = 0;
        double exp = baseDelayMs * Math.pow(multiplier, attempt);
        long capped = (long) Math.min((double) maxDelayMs, exp);
        if (capped <= 0) return 0L;
        if (!jitter) return capped;
        double scale = 0.5 + ThreadLocalRandom.current().nextDouble();
        long jittered = (long) (capped * scale);
        return Math.max(0L, Math.min(maxDelayMs, jittered));
    }

    /** Policy that disables retries entirely. */
    public static RetryPolicy disabled() {
        return new RetryPolicy(0, 0L, 0L, 1.0, false, false);
    }
}
