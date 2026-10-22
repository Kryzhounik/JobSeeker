package com.seeker.codex;

import java.time.Instant;

/** Codex usage is exhausted; the caller decides when and how to resume. */
public final class UsageLimitExceededException extends Exception {
    private final Instant retryAt;

    public UsageLimitExceededException(String message, Instant retryAt) {
        super(message);
        this.retryAt = retryAt;
    }

    /** Returns the reset time, or null when Codex did not provide a recognized time. */
    public Instant getRetryAt() {
        return retryAt;
    }
}
