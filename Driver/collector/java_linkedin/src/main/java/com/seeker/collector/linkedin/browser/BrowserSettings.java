package com.seeker.collector.linkedin.browser;

public record BrowserSettings(
        String channel,
        boolean headless,
        int pageTimeoutSeconds
) {
}
