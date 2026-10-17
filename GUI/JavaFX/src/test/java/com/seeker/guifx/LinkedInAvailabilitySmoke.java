package com.seeker.guifx;

import java.net.URI;

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
        System.out.println("LinkedIn availability response and pacing checks OK");
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
        String actual = LinkedInAvailabilityChecker.classifyResponse(code, url, "text/html", body);
        if (!actual.equals(expected)) throw new AssertionError(actual);
    }

    private static void expectError(int code, URI url, String body) {
        try {
            LinkedInAvailabilityChecker.classifyResponse(code, url, "text/html", body);
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Invalid response accepted: " + code + " " + url);
    }
}
