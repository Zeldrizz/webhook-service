package com.webhookservice.model.dto;

import java.time.Instant;
import java.util.Map;

public record StatsResponse(
        long totalRequests,
        long todayRequests,
        Map<String, Long> methodCounts,
        Instant lastRequestAt
) {}
