package com.webhookservice.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetryPolicyTest {

    @Test
    void shouldRetryStatus_5xxAllowed_returnsTrueFor500And599() {
        RetryPolicy p = new RetryPolicy(3, 100, 5000, 2.0, false, true);
        assertTrue(p.shouldRetryStatus(500));
        assertTrue(p.shouldRetryStatus(503));
        assertTrue(p.shouldRetryStatus(599));
    }

    @Test
    void shouldRetryStatus_5xxDisabled_alwaysFalse() {
        RetryPolicy p = new RetryPolicy(3, 100, 5000, 2.0, false, false);
        assertFalse(p.shouldRetryStatus(500));
        assertFalse(p.shouldRetryStatus(503));
    }

    @Test
    void shouldRetryStatus_neverRetries4xx() {
        RetryPolicy p = new RetryPolicy(3, 100, 5000, 2.0, false, true);
        assertFalse(p.shouldRetryStatus(400));
        assertFalse(p.shouldRetryStatus(404));
        assertFalse(p.shouldRetryStatus(429));
    }

    @Test
    void delayForAttempt_noJitter_growsExponentially() {
        RetryPolicy p = new RetryPolicy(5, 100, 10_000, 2.0, false, true);
        assertEquals(100L, p.delayForAttempt(0));
        assertEquals(200L, p.delayForAttempt(1));
        assertEquals(400L, p.delayForAttempt(2));
        assertEquals(800L, p.delayForAttempt(3));
        assertEquals(1600L, p.delayForAttempt(4));
    }

    @Test
    void delayForAttempt_cappedByMaxDelay() {
        RetryPolicy p = new RetryPolicy(10, 100, 500, 2.0, false, true);
        assertEquals(100L, p.delayForAttempt(0));
        assertEquals(200L, p.delayForAttempt(1));
        assertEquals(400L, p.delayForAttempt(2));
        assertEquals(500L, p.delayForAttempt(3));      // capped
        assertEquals(500L, p.delayForAttempt(10));     // still capped
    }

    @Test
    void delayForAttempt_withJitter_staysWithinHalfToOneAndHalfRange() {
        RetryPolicy p = new RetryPolicy(5, 200, 10_000, 2.0, true, true);
        long expected = 400; // attempt=1 -> 200 * 2^1
        for (int i = 0; i < 200; i++) {
            long delay = p.delayForAttempt(1);
            assertTrue(delay >= expected / 2, "delay below jitter floor: " + delay);
            assertTrue(delay <= (long) (expected * 1.5), "delay above jitter ceiling: " + delay);
        }
    }

    @Test
    void delayForAttempt_negativeAttempt_clampedToZero() {
        RetryPolicy p = new RetryPolicy(5, 100, 5000, 2.0, false, true);
        assertEquals(100L, p.delayForAttempt(-1));
        assertEquals(100L, p.delayForAttempt(-100));
    }

    @Test
    void disabledPolicy_zeroRetriesAndZeroDelay() {
        RetryPolicy p = RetryPolicy.disabled();
        assertEquals(0, p.maxRetries());
        assertEquals(0L, p.delayForAttempt(0));
        assertFalse(p.shouldRetryStatus(500));
    }
}
