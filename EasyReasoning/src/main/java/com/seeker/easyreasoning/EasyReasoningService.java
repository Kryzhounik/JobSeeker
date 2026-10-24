package com.seeker.easyreasoning;

/** Common text-generation API. Requests are independent and failures propagate to the caller. */
public interface EasyReasoningService extends AutoCloseable {
    String generate(String prompt);

    /** Releases resources owned by the configured backend. */
    @Override
    void close();
}
