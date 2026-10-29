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
        Exception loadFailure;
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

        @Override public GptEvaluation.Result load(int id) throws Exception {
            events.add("load:" + id);
            if (loadFailure != null) throw loadFailure;
            return saved;
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
        assertEquals(List.of("find", "load:42", "evaluate", "save"), store.events);
        assertEquals(store.saved, result);
        assertEquals(ANSWER, result.answer());
        assertEquals(42, result.jobId());
        Instant.parse(result.evaluatedAt());
    }

    @Test void explicitGuiIdSkipsLookupAndTrustsSuppliedPair() throws Exception {
        Store store = new Store();
        GptEvaluation.Result result = operation(store).run(URL, 71);
        assertEquals(List.of("load:71", "evaluate", "save"), store.events);
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

    @Test void savingFailureRetainsObtainedAnswerAndNeverRepeatsEvaluation() {
        Store store = new Store();
        store.saveFailure = new IOException("disk full");
        GptEvaluation.SaveException error = assertThrows(GptEvaluation.SaveException.class,
                () -> operation(store).run(URL));
        assertSame(store.saveFailure, error.getCause());
        assertEquals(ANSWER, error.answer());
        assertTrue(error.getMessage().contains("saving failed"));
        assertNull(store.saved);
        assertEquals(List.of("find", "load:42", "evaluate", "save"), store.events);
    }

    @Test void evaluationFailureDoesNotWriteAnAnswer() {
        Store store = new Store();
        IOException failure = new IOException("evaluation failed");
        GptEvaluation evaluation = new GptEvaluation(url -> { throw failure; }, store);
        assertSame(failure, assertThrows(IOException.class, () -> evaluation.run(URL)));
        assertEquals(List.of("find", "load:42"), store.events);
        assertNull(store.saved);
    }

    @Test void urlOnlyReturnsStoredAnswerWithoutEvaluationOrUpdatingTimestamp() throws Exception {
        Store store = new Store();
        store.saved = new GptEvaluation.Result(42, ANSWER, "2026-10-22T14:00:00Z");
        GptEvaluation evaluation = new GptEvaluation(url -> {
            throw new AssertionError("Stored answer must bypass HTTP and browser evaluation");
        }, store);
        assertSame(store.saved, evaluation.run(URL));
        assertEquals(List.of("find", "load:42"), store.events);
        assertEquals("2026-10-22T14:00:00Z", store.saved.evaluatedAt());
    }

    @Test void guiIdReturnsStoredAnswerWithoutLookupOrEvaluation() throws Exception {
        Store store = new Store();
        store.saved = new GptEvaluation.Result(71, ANSWER, "before");
        assertSame(store.saved, operation(store).run(URL, 71));
        assertEquals(List.of("load:71"), store.events);
    }

    @Test void repeatedCallReadsFirstSavedAnswerInsteadOfSendingAgain() throws Exception {
        Store store = new Store();
        GptEvaluation evaluation = operation(store);
        GptEvaluation.Result first = evaluation.run(URL);
        assertEquals(first, evaluation.run(URL));
        assertEquals(List.of("find", "load:42", "evaluate", "save", "find", "load:42"), store.events);
    }

    @Test void readingFailureIsNotTreatedAsAnAbsentAnswer() {
        Store store = new Store();
        store.loadFailure = new IOException("read failed");
        assertSame(store.loadFailure, assertThrows(IOException.class, () -> operation(store).run(URL)));
        assertEquals(List.of("find", "load:42"), store.events);
    }
}
