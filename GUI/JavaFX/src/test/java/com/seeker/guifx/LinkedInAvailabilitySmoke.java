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
        System.out.println("LinkedIn availability response checks OK");
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
