package com.seeker.chatevaluator;

import java.time.Instant;

/** One evaluation, with optional persistence; callers manage launch concurrency. */
public final class GptEvaluation {
    @FunctionalInterface
    public interface Evaluator {
        String evaluate(String url) throws Exception;
    }

    public interface Store {
        Integer findJobId(String url) throws Exception;
        void save(int jobId, String answer, String evaluatedAt) throws Exception;
    }

    public record Result(Integer jobId, String answer, String evaluatedAt) { }

    public static final class SaveException extends Exception {
        private final String answer;

        public SaveException(String answer, Exception cause) {
            super("ChatGPT answer received, but saving failed: " + cause.getMessage(), cause);
            this.answer = answer;
        }

        public String answer() { return answer; }
    }

    private final Evaluator evaluator;
    private final Store store;

    public GptEvaluation(Evaluator evaluator, Store store) {
        this.evaluator = evaluator;
        this.store = store;
    }

    public Result run(String url) throws Exception {
        return run(url, store.findJobId(url));
    }

    /** The GUI supplies a consistent URL/ID pair; null means the lookup found no job. */
    public Result run(String url, Integer jobId) throws Exception {
        String answer = evaluator.evaluate(url);
        String evaluatedAt = Instant.now().toString();
        if (jobId != null) {
            try {
                store.save(jobId, answer, evaluatedAt);
            } catch (Exception error) {
                throw new SaveException(answer, error);
            }
        } else {
            // TODO: Fetch and create the missing vacancy without agent evaluations,
            // then save this GPT answer. See GPT-001 in BACKLOG.md.
        }
        return new Result(jobId, answer, evaluatedAt);
    }
}
