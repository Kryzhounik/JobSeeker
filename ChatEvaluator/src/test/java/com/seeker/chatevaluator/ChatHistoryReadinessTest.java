package com.seeker.chatevaluator;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/** Local HTML only; no managed profile, network or ChatGPT session. */
class ChatHistoryReadinessTest {
    private static Playwright playwright;
    private static Browser browser;
    private static final ChatEvaluatorRunner.Turn STOPPED = new ChatEvaluatorRunner.Turn(
            "stopped", "user-stopped", "Previous question", "", "", false);
    private static final String READY_PAGE = """
        <div data-turn-key="stopped"><div data-chatgpt-search-unit-key="stopped:user"
             data-chatgpt-search-message-ids="user-stopped">Previous question</div></div>
        <form><div role="textbox" contenteditable="true"></div></form>
        """;

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
    void conversationCanBeReadyBeforeDomContentLoaded() throws Exception {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            context.route("https://chat.test/**", route -> {
                if (route.request().url().endsWith("blocked.js")) return;
                route.fulfill(new Route.FulfillOptions().setContentType("text/html").setBody("""
                    <div data-turn-key="stopped">
                      <div data-chatgpt-search-unit-key="stopped:user"
                           data-chatgpt-search-message-ids="user-stopped">Previous question</div>
                    </div>
                    <form><div role="textbox" contenteditable="true"></div></form>
                    <script defer src="/blocked.js"></script>
                    """));
            });
            Page page = context.newPage();
            String chatUrl = "https://chat.test/c/existing";
            var composer = ChatEvaluatorRunner.prepareConversation(page, chatUrl, true);
            assertEquals(1, composer.count());
            assertNotEquals("complete", page.evaluate("document.readyState"));
            assertEquals(0, ((Number) page.evaluate(
                    "performance.getEntriesByType('navigation')[0].domContentLoadedEventStart")).intValue());
            assertTrue(composer.innerText().isBlank());
        }
    }

    @Test
    void existingConversationIsPreparedWithoutReloadingOrChangingDraft() throws Exception {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            Page page = context.newPage();
            page.setContent("""
                <div data-turn-key="stopped">
                  <div data-chatgpt-search-unit-key="stopped:user"
                       data-chatgpt-search-message-ids="user-stopped">Previous question</div>
                </div>
                <form><div role="textbox" contenteditable="true">Existing draft</div></form>
                """);
            page.evaluate("window.preparationMarker = 'kept'");
            var composer = ChatEvaluatorRunner.prepareConversation(page, page.url(), false);
            assertEquals("kept", page.evaluate("window.preparationMarker"));
            assertEquals("Existing draft", composer.innerText());
        }
    }

    @Test
    void failedNavigationReportsOpeningStageWithoutSending() {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            context.route("https://chat.test/**", route -> route.abort("namenotresolved"));
            Page page = context.newPage();
            IOException failure = assertThrows(IOException.class,
                    () -> ChatEvaluatorRunner.prepareConversation(page, "https://chat.test/c/existing", true));
            assertTrue(failure.getMessage().contains("opening the conversation"));
            assertTrue(failure.getMessage().contains("No URL was sent"));
        }
    }

    @Test
    void mainDocumentHttpErrorStopsBeforeWaitingForComposer() {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            context.route("https://chat.test/**", route -> route.fulfill(
                    new Route.FulfillOptions().setStatus(503).setContentType("text/html").setBody("Unavailable")));
            IOException failure = assertThrows(IOException.class, () -> ChatEvaluatorRunner.prepareConversation(
                    context.newPage(), "https://chat.test/c/existing", true));
            assertTrue(failure.getMessage().contains("HTTP 503"));
            assertTrue(failure.getMessage().contains("No URL was sent"));
        }
    }

    @Test
    void failedImageAndChildFrameDoNotBlockReadyConversation() throws Exception {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            context.route("https://chat.test/**", route -> {
                if (route.request().url().endsWith("existing")) {
                    route.fulfill(new Route.FulfillOptions().setContentType("text/html")
                            .setBody(READY_PAGE + "<img src='/image'><iframe src='/child'></iframe>"));
                } else route.abort("failed");
            });
            Page page = context.newPage();
            assertEquals(1, ChatEvaluatorRunner.prepareConversation(page, "https://chat.test/c/existing", true).count());
        }
    }

    @Test
    void signInAndCloudflareHaveExplicitErrors() {
        for (String[] fixture : List.of(
                new String[]{"<input id='mobile-auth-email'>", "sign-in required"},
                new String[]{"<div id='challenge-stage'>Checking browser</div>", "Cloudflare verification required"})) {
            try (var context = browser.newContext()) {
                context.setOffline(true);
                Page page = context.newPage();
                page.setContent(fixture[0]);
                IOException failure = assertThrows(IOException.class,
                        () -> ChatEvaluatorRunner.prepareConversation(page, page.url(), false));
                assertTrue(failure.getMessage().contains(fixture[1]));
                assertTrue(failure.getMessage().contains("No URL was sent"));
            }
        }
    }

    @Test
    void closedTabHasExplicitError() {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            Page page = context.newPage();
            page.close();
            IOException failure = assertThrows(IOException.class,
                    () -> ChatEvaluatorRunner.prepareConversation(page, "about:blank", false));
            assertTrue(failure.getMessage().contains("tab was closed"));
        }
    }

    @Test
    void disconnectedBrowserHasExplicitError() {
        Browser isolated = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        Page page = isolated.newPage();
        isolated.close();
        IOException failure = assertThrows(IOException.class,
                () -> ChatEvaluatorRunner.prepareConversation(page, "about:blank", false));
        assertTrue(failure.getMessage().contains("Browser connection lost"));
    }

    @Test
    void respondingPageCanFinishLoadingComposerAndHistory() throws Exception {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            Page page = context.newPage();
            page.setContent("<p>Loading</p>");
            page.evaluate("html => setTimeout(() => document.body.innerHTML = html, 500)", READY_PAGE);
            assertEquals(1, ChatEvaluatorRunner.prepareConversation(page, page.url(), false).count());
        }
    }

    @Test
    void blockedRendererFailsShortDomProbe() {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            Page page = context.newPage();
            page.setContent(READY_PAGE);
            var cdp = context.newCDPSession(page);
            try {
                page.evaluate("setTimeout(() => { while (true) {} }, 100)");
                page.waitForTimeout(200);
                IOException failure = assertThrows(IOException.class,
                        () -> ChatEvaluatorRunner.prepareConversation(page, page.url(), false));
                assertTrue(failure.getMessage().contains("did not respond to a DOM check within 3 seconds"));
                assertTrue(failure.getMessage().contains("No URL was sent"));
            } finally {
                cdp.send("Runtime.terminateExecution");
                cdp.detach();
            }
        }
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
                <div class="writing-block-editor" role="textbox" contenteditable="true">Original block</div>
                <form><div role="textbox" contenteditable="true"></div></form>
                """);
            ChatEvaluatorRunner.awaitReadyHistory(page, page.url());
            var composer = ChatEvaluatorRunner.messageComposer(page);
            assertEquals(1, composer.count());
            composer.fill("https://www.linkedin.com/jobs/view/123/");
            assertEquals("https://www.linkedin.com/jobs/view/123/", composer.innerText());
            assertEquals("Original block", page.locator(".writing-block-editor").innerText());
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
                <form><div id="prompt-textarea" contenteditable="true"></div></form>
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
            page.setContent("<div role='textbox' contenteditable='true'>Unrelated editor</div>"
                    + "<form><div id='prompt-textarea' contenteditable='true'></div></form>");
            assertFalse(ChatEvaluatorRunner.historyReadyForMessage(page, List.of()));
            assertFalse(ChatEvaluatorRunner.historyReadyForMessage(page, List.of(
                    new ChatEvaluatorRunner.Turn("stopped", "", "", "", "", false))));
            page.locator("#prompt-textarea").evaluate("node => node.contentEditable = 'false'");
            assertFalse(ChatEvaluatorRunner.historyReadyForMessage(page, List.of(STOPPED)));
            page.setContent("<div role='textbox' contenteditable='true'>Unrelated editor</div>"
                    + "<form><div id='prompt-textarea' contenteditable='true' aria-disabled='true'></div></form>");
            assertFalse(ChatEvaluatorRunner.historyReadyForMessage(page, List.of(STOPPED)));
            page.setContent("<div role='textbox' contenteditable='true'>Unrelated editor</div>"
                    + "<form><div id='prompt-textarea' contenteditable='true' hidden></div></form>");
            assertFalse(ChatEvaluatorRunner.historyReadyForMessage(page, List.of(STOPPED)));
        }
    }
}
