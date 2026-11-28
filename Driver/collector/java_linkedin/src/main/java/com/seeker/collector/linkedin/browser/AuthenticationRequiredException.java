package com.seeker.collector.linkedin.browser;

import com.seeker.logging.LogExceptionCreation;
import org.slf4j.event.Level;

@LogExceptionCreation(level = Level.WARN)
public final class AuthenticationRequiredException extends RuntimeException {
    public AuthenticationRequiredException(String message) {
        super(message);
    }
}
