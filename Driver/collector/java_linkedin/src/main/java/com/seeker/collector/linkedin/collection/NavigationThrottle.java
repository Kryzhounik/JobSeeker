package com.seeker.collector.linkedin.collection;

import com.microsoft.playwright.Page;

final class NavigationThrottle {
    private final long delayNanos;
    private long previousActionStarted;

    NavigationThrottle(double delaySeconds) {
        this.delayNanos = Math.max(0L, (long) (delaySeconds * 1_000_000_000L));
    }

    void beforeAction(Page page) {
        long now = System.nanoTime();
        long remaining = previousActionStarted + delayNanos - now;
        if (remaining > 0) {
            page.waitForTimeout(remaining / 1_000_000.0);
        }
        previousActionStarted = System.nanoTime();
    }
}
