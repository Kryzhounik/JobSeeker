package com.seeker.mailer;

import java.io.IOException;
import com.seeker.logging.LogExceptionCreation;

/** An operational or interpretation failure; never an empty successful scan. */
@LogExceptionCreation(stackTrace = false)
public class MailException extends IOException {
    public MailException(String message) {
        super(message);
    }

    public MailException(String message, Throwable cause) {
        super(message, cause);
    }
}
