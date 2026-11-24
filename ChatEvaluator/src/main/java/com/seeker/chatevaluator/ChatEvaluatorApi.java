package com.seeker.chatevaluator;

import java.net.URI;
import java.nio.file.Path;

/** Reusable evaluation API; construction does not access the browser or database. */
public final class ChatEvaluatorApi {
    private final GptEvaluation evaluation;

    public ChatEvaluatorApi(Path projectRoot) {
        Path root = projectRoot.toAbsolutePath().normalize();
        evaluation = new GptEvaluation(url -> ChatEvaluatorRunner.evaluateInBrowser(root, url),
                new PythonEvaluationStore(root));
    }

    public String evaluate(String url) throws Exception {
        validateUrl(url);
        return evaluation.run(url).answer();
    }

    public GptEvaluation.Result evaluate(String url, int jobId) throws Exception {
        validateUrl(url);
        return evaluation.run(url, jobId);
    }

    public static final class InvalidUrlException extends IllegalArgumentException {
        private InvalidUrlException() { super("Expected one HTTP(S) vacancy URL."); }
    }

    private static void validateUrl(String url) {
        if (url == null || url.isBlank()) throw new InvalidUrlException();
        URI uri;
        try { uri = URI.create(url); }
        catch (IllegalArgumentException error) { throw new InvalidUrlException(); }
        if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))) throw new InvalidUrlException();
    }
}
