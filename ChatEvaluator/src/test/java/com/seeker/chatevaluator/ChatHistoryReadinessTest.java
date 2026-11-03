package com.seeker.chatevaluator;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Local HTML only; no managed profile, network or ChatGPT session. */
class ChatHistoryReadinessTest {
    private static Playwright playwright;
    private static Browser browser;
    private static final ChatEvaluatorRunner.Turn STOPPED = new ChatEvaluatorRunner.Turn(
            "stopped", "user-stopped", "Previous question", "", "", false);

    @BeforeAll
    static void openBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Test
    void stoppedHistoryBecomesReadyWithoutCompletedAssistant() throws Exception {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            Page page = context.newPage();
            page.setContent("""
                <div data-turn-key="stopped">
                  <div data-chatgpt-search-unit-key="stopped:user"
                       data-chatgpt-search-message-ids="user-stopped">Previous question</div>
                  <div data-talvt-turn-state="cancelled">Thinking stopped</div>
                </div>
                <div id="prompt-textarea" contenteditable="true"></div>
                """);
            ChatEvaluatorRunner.awaitReadyHistory(page, page.url());
            assertTrue(ChatEvaluatorRunner.historyReadyForMessage(page, List.of(STOPPED)));
        }
    }

    @Test
    void activeGenerationBlocksUntilStopButtonDisappears() throws Exception {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            Page page = context.newPage();
            page.setContent("""
                <div data-turn-key="stopped">
                  <div data-chatgpt-search-unit-key="stopped:user"
                       data-chatgpt-search-message-ids="user-stopped">Previous question</div>
                </div>
                <div id="prompt-textarea" contenteditable="true"></div>
                <button data-testid="stop-button">Stop</button>
                """);
            assertFalse(ChatEvaluatorRunner.historyReadyForMessage(page, List.of(STOPPED)));
            page.evaluate("setTimeout(() => document.querySelector('button').remove(), 500)");
            ChatEvaluatorRunner.awaitReadyHistory(page, page.url());
            assertEquals(0, page.locator("button").count());
        }
    }

    @Test
    void missingHistoryOrUnavailableComposerIsNotReady() {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            Page page = context.newPage();
            page.setContent("<div id='prompt-textarea' contenteditable='true'></div>");
            assertFalse(ChatEvaluatorRunner.historyReadyForMessage(page, List.of()));
            assertFalse(ChatEvaluatorRunner.historyReadyForMessage(page, List.of(
                    new ChatEvaluatorRunner.Turn("stopped", "", "", "", "", false))));
            page.locator("#prompt-textarea").evaluate("node => node.contentEditable = 'false'");
            assertFalse(ChatEvaluatorRunner.historyReadyForMessage(page, List.of(STOPPED)));
            page.setContent("<div id='prompt-textarea' contenteditable='true' aria-disabled='true'></div>");
            assertFalse(ChatEvaluatorRunner.historyReadyForMessage(page, List.of(STOPPED)));
            page.setContent("<div id='prompt-textarea' contenteditable='true' hidden></div>");
            assertFalse(ChatEvaluatorRunner.historyReadyForMessage(page, List.of(STOPPED)));
        }
    }
}
