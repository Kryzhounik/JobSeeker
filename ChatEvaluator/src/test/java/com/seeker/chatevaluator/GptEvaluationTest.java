package com.seeker.chatevaluator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GptEvaluationTest {
    private static final String URL = "https://example.com/job";
    private static final String ANSWER = "Полный ответ\n\n" + "Java — подходит.\n".repeat(1000);

    static class Store implements GptEvaluation.Store {
        Integer jobId = 42;
        GptEvaluation.Result saved;
        Exception saveFailure;
        final List<String> events = new ArrayList<>();

        @Override public Integer findJobId(String url) {
            assertEquals(URL, url);
            events.add("find");
            return jobId;
        }

        @Override public void save(int id, String answer, String time) throws Exception {
            events.add("save");
            if (saveFailure != null) throw saveFailure;
            saved = new GptEvaluation.Result(id, answer, time);
        }
    }

    private GptEvaluation operation(Store store) {
        return new GptEvaluation(url -> {
            assertEquals(URL, url);
            store.events.add("evaluate");
            return ANSWER;
        }, store);
    }

    @Test void urlOnlyResolvesIdThenEvaluatesAndSavesFullAnswer() throws Exception {
        Store store = new Store();
        GptEvaluation.Result result = operation(store).run(URL);
        assertEquals(List.of("find", "evaluate", "save"), store.events);
        assertEquals(store.saved, result);
        assertEquals(ANSWER, result.answer());
        assertEquals(42, result.jobId());
        Instant.parse(result.evaluatedAt());
    }

    @Test void explicitGuiIdSkipsLookupAndTrustsSuppliedPair() throws Exception {
        Store store = new Store();
        GptEvaluation.Result result = operation(store).run(URL, 71);
        assertEquals(List.of("evaluate", "save"), store.events);
        assertEquals(71, result.jobId());
        assertEquals(store.saved, result);
    }

    @Test void unknownUrlReturnsAnswerNormallyWithoutSavingOrCreatingJob() throws Exception {
        Store store = new Store();
        store.jobId = null;
        GptEvaluation.Result result = operation(store).run(URL);
        assertEquals(List.of("find", "evaluate"), store.events);
        assertNull(result.jobId());
        assertNull(store.saved);
        assertEquals(ANSWER, result.answer());
    }

    @Test void savingFailurePreservesPreviousAnswerAndNeverRepeatsEvaluation() {
        Store store = new Store();
        store.saved = new GptEvaluation.Result(42, "previous", "before");
        store.saveFailure = new IOException("disk full");
        GptEvaluation.SaveException error = assertThrows(GptEvaluation.SaveException.class,
                () -> operation(store).run(URL));
        assertSame(store.saveFailure, error.getCause());
        assertEquals(ANSWER, error.answer());
        assertTrue(error.getMessage().contains("saving failed"));
        assertEquals("previous", store.saved.answer());
        assertEquals(List.of("find", "evaluate", "save"), store.events);
    }

    @Test void evaluationFailureDoesNotTouchSavedAnswer() {
        Store store = new Store();
        store.saved = new GptEvaluation.Result(42, "previous", "before");
        IOException failure = new IOException("evaluation failed");
        GptEvaluation evaluation = new GptEvaluation(url -> { throw failure; }, store);
        assertSame(failure, assertThrows(IOException.class, () -> evaluation.run(URL)));
        assertEquals(List.of("find"), store.events);
        assertEquals("previous", store.saved.answer());
    }
}
