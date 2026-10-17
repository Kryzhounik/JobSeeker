package com.seeker.chatevaluator;

import com.seeker.chatevaluator.ChatEvaluatorRunner.Turn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatEvaluatorRunnerTest {
    private static final String URL = "https://www.linkedin.com/jobs/view/123/";
    private static final Turn OLD = new Turn("old", "user-old", URL, "answer-old", "Previous answer", true);
    private static final Turn SENT = new Turn("new", "user-new", URL, "", "", false);

    @Test
    void lateHistoryBeforeAnchorIsNotSubmissionEvenForSameUrl() {
        Turn late = new Turn("late", "user-late", URL, "answer-late", "Historical answer", true);
        assertNull(ChatEvaluatorRunner.submittedTurn(List.of(OLD), List.of(late, OLD), URL, true));
    }

    @Test
    void repeatingUrlRequiresNewUserAndTurnIds() {
        assertNull(ChatEvaluatorRunner.submittedTurn(List.of(OLD), List.of(OLD), URL, true));
        assertEquals(SENT, ChatEvaluatorRunner.submittedTurn(List.of(OLD), List.of(OLD, SENT), URL, true));
    }

    @Test
    void filledComposerDoesNotAcknowledgeSubmission() {
        assertNull(ChatEvaluatorRunner.submittedTurn(List.of(OLD), List.of(OLD, SENT), URL, false));
    }

    @Test
    void oldUserInDifferentTurnIsRejected() {
        Turn reused = new Turn("new", OLD.userId(), URL, "answer-new", "Wrong answer", true);
        assertNull(ChatEvaluatorRunner.submittedTurn(List.of(OLD), List.of(OLD, reused), URL, true));
    }

    @Test
    void anotherUrlDoesNotAcknowledgeOurSubmission() {
        Turn other = new Turn("new", "user-new", "https://example.invalid/other", "", "", false);
        assertNull(ChatEvaluatorRunner.submittedTurn(List.of(OLD), List.of(OLD, other), URL, true));
    }

    @Test
    void lostHistoryAnchorIsRejected() {
        assertNull(ChatEvaluatorRunner.submittedTurn(List.of(OLD), List.of(SENT), URL, true));
    }

    @Test
    void missingUserIdentityIsRejected() {
        Turn noId = new Turn("new", "", URL, "", "", false);
        assertNull(ChatEvaluatorRunner.submittedTurn(List.of(OLD), List.of(OLD, noId), URL, true));
    }

    @Test
    void historicalCompleteDoesNotFinishNewGeneration() {
        Turn generating = new Turn("new", "user-new", URL, "answer-new", "Partial", false);
        assertEquals("", ChatEvaluatorRunner.completedResponse(SENT, List.of(OLD), List.of(OLD, generating)));
    }

    @Test
    void noResponseIsReturnedBeforeAcknowledgement() {
        assertEquals("", ChatEvaluatorRunner.completedResponse(null, List.of(OLD), List.of(OLD)));
    }

    @Test
    void completeFromAnotherTurnDoesNotFinishSubmittedTurn() {
        Turn other = new Turn("other", "user-other", URL, "answer-other", "Other answer", true);
        assertEquals("", ChatEvaluatorRunner.completedResponse(SENT, List.of(OLD), List.of(OLD, SENT, other)));
    }

    @Test
    void oldAssistantIdentityIsRejectedEvenInsideNewTurn() {
        Turn reused = new Turn("new", "user-new", URL, OLD.assistantId(), "Previous answer", true);
        assertEquals("", ChatEvaluatorRunner.completedResponse(SENT, List.of(OLD), List.of(OLD, reused)));
    }

    @Test
    void responseRequiresSameUserIdentityAndText() {
        Turn other = new Turn("new", "user-other", URL, "answer-new", "Answer", true);
        assertEquals("", ChatEvaluatorRunner.completedResponse(SENT, List.of(OLD), List.of(OLD, other)));
    }

    @Test
    void completedResponseIsReturnedInFull() {
        String answer = "APPLY — новая вакансия\n\nПервый абзац.\n\nПоследний абзац.";
        Turn complete = new Turn("new", "user-new", URL, "answer-new", answer, true);
        assertEquals(answer, ChatEvaluatorRunner.completedResponse(SENT, List.of(OLD), List.of(OLD, complete)));
    }

    @Test
    void emptyOrUnidentifiedAssistantIsNotComplete() {
        Turn empty = new Turn("new", "user-new", URL, "answer-new", "", true);
        Turn noId = new Turn("new", "user-new", URL, "", "Answer", true);
        assertEquals("", ChatEvaluatorRunner.completedResponse(SENT, List.of(OLD), List.of(OLD, empty)));
        assertEquals("", ChatEvaluatorRunner.completedResponse(SENT, List.of(OLD), List.of(OLD, noId)));
    }
}
