package com.seeker.collector.linkedin.login;

import com.seeker.collector.linkedin.browser.BrowserSettings;

import java.nio.file.Path;

public record LoginRequest(
        Path profileDirectory,
        BrowserSettings browserSettings,
        int timeoutSeconds
) {
}
