package com.seeker.easyreasoning;

/** Common text-generation API. Requests are independent and failures propagate to the caller. */
public interface EasyReasoningService extends AutoCloseable {
    String generate(String prompt);

    /** Independent comparison request, with the backend's complete-request budget check. */
    default String generateComparison(String prompt) { return generate(prompt); }

    /** Releases resources owned by the configured backend. */
    @Override
    void close();
}
