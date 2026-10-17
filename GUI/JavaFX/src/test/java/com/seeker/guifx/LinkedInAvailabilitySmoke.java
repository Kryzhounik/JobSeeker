package com.seeker.guifx;

import com.seeker.tools.LinkedInJobAvailability;

import java.net.URI;
import java.time.Duration;

public final class LinkedInAvailabilitySmoke {
    public static void main(String[] args) {
        URI job = URI.create("https://www.linkedin.com/jobs/view/123/");
        String html = "LinkedIn <div class='show-more-less-html__markup'>" + "a".repeat(1100);
        expect("available", 200, job, html);
        expect("unavailable", 404, job, "Page not found");
        expect("closed", 200, job, "No longer accepting applications");
        for (int code : new int[]{429, 500, 403}) {
            expectError(code, job, html);
        }
        expectError(200, URI.create("https://www.linkedin.com/authwall"), html);
        expectError(200, job, "LinkedIn job " + "a".repeat(1100));
        checkPacing();
        var summary = new LinkedInAvailabilityChecker.Result("Stopped", Duration.ofSeconds(800),
                210, 0, 9, 5, 0, 2, 20, 6);
        if (summary.processed() != 14 || !summary.details().contains("13 min 20 s")
                || !summary.details().contains("Processed: 14 / 210")
                || !summary.details().contains("Unavailable: 9")
                || !summary.details().contains("Closed: 5")
                || !summary.details().contains("Deleted: 0")
                || !summary.details().contains("Skipped: 2")
                || !summary.details().contains("HTTP requests: 20; HTTP 429: 6")) {
            throw new AssertionError(summary.details());
        }
        var empty = new LinkedInAvailabilityChecker.Result("", Duration.ZERO,
                0, 0, 0, 0, 0, 0, 0, 0);
        if (empty.processed() != 0 || !empty.details().contains("0 min 00 s")) {
            throw new AssertionError(empty.details());
        }
        System.out.println("LinkedIn availability response, pacing and summary checks OK");
    }

    private static void checkPacing() {
        var pacing = new LinkedInAvailabilityChecker.RequestPacing();
        expectDelay(pacing, 5000);
        for (int i = 0; i < 20; i++) pacing.onSuccess();
        expectDelay(pacing, 5000);
        for (long delay : new long[]{10000, 20000, 40000, 80000, 120000}) {
            if (!pacing.onRateLimit()) throw new AssertionError("Retry stopped too early");
            expectDelay(pacing, delay);
        }
        if (pacing.onRateLimit()) throw new AssertionError("Retried beyond maximum delay");
        for (long delay : new long[]{60000, 30000, 15000, 7500, 5000}) {
            long previous = pacing.delayMillis();
            for (int i = 0; i < 9; i++) pacing.onSuccess();
            expectDelay(pacing, previous);
            pacing.onSuccess();
            expectDelay(pacing, delay);
        }
        pacing.onRateLimit();
        for (int i = 0; i < 9; i++) pacing.onSuccess();
        pacing.onRateLimit();
        for (int i = 0; i < 9; i++) pacing.onSuccess();
        expectDelay(pacing, 20000);
        pacing.onSuccess();
        expectDelay(pacing, 10000);
    }

    private static void expectDelay(LinkedInAvailabilityChecker.RequestPacing pacing, long expected) {
        if (pacing.delayMillis() != expected) {
            throw new AssertionError("Expected delay " + expected + ", got " + pacing.delayMillis());
        }
    }

    private static void expect(String expected, int code, URI url, String body) {
        String actual = LinkedInJobAvailability.classifyResponse(code, url, "text/html", body);
        if (!actual.equals(expected)) throw new AssertionError(actual);
    }

    private static void expectError(int code, URI url, String body) {
        try {
            LinkedInJobAvailability.classifyResponse(code, url, "text/html", body);
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Invalid response accepted: " + code + " " + url);
    }
}
