package com.seeker.collector.linkedin.login;

public record LoginResult(
        String source,
        String status,
        String profile,
        String message
) {
}
