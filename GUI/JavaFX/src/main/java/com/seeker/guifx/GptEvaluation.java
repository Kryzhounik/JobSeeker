package com.seeker.guifx;

import java.time.Instant;

/** Executes one evaluation and saves it; launch concurrency belongs to the caller. */
final class GptEvaluation {
    @FunctionalInterface
    interface Evaluator {
        String evaluate(String url) throws Exception;
    }

    @FunctionalInterface
    interface AnswerStore {
        void save(int jobId, String answer, String evaluatedAt) throws Exception;
    }

    static final class SaveException extends Exception {
        private final String answer;

        SaveException(String answer, Exception cause) {
            super("ChatGPT answer received, but saving failed: " + cause.getMessage(), cause);
            this.answer = answer;
        }

        String answer() { return answer; }
    }

    private final Evaluator evaluator;
    private final AnswerStore store;

    GptEvaluation(Evaluator evaluator, AnswerStore store) {
        this.evaluator = evaluator;
        this.store = store;
    }

    JobGptEvaluation run(int jobId, String url) throws Exception {
        String answer = evaluator.evaluate(url);
        String evaluatedAt = Instant.now().toString();
        try {
            store.save(jobId, answer, evaluatedAt);
        } catch (Exception error) {
            throw new SaveException(answer, error);
        }
        return new JobGptEvaluation(jobId, answer, evaluatedAt);
    }
}
