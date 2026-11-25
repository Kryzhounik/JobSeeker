package com.seeker.easyreasoning;

/** A comparison was rejected locally, before inference. */
public final class RequestTooLargeException extends RuntimeException {
    private final int requestTokens;
    private final int contextLimit;
    private final int responseTokens;

    public RequestTooLargeException(int requestTokens, int contextLimit, int responseTokens) {
        super("Comparison request exceeds context: request_tokens=" + requestTokens
                + " response_tokens=" + responseTokens + " context_limit=" + contextLimit);
        this.requestTokens = requestTokens;
        this.contextLimit = contextLimit;
        this.responseTokens = responseTokens;
    }

    public int requestTokens() { return requestTokens; }
    public int contextLimit() { return contextLimit; }
    public int responseTokens() { return responseTokens; }
}
