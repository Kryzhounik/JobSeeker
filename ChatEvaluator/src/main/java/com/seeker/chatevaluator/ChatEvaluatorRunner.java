package com.seeker.chatevaluator;

import com.google.gson.JsonObject;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitUntilState;
import com.seeker.applier.shared.ManagedBrowser;
import com.seeker.tools.LinkedInJobAvailability;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

/** Sends a vacancy URL to the configured existing ChatGPT conversation. */
public final class ChatEvaluatorRunner {
    private static final String CHAT_CONFIG = "Data/chat_evaluator/config.properties";
    private static final String LINKEDIN_RUNTIME = "Driver/collector/java_linkedin/runtime.properties";
    private static final String LINKEDIN_CONFIG = "Driver/collector/config/linkedin.properties";
    private static final String COMPOSER_SELECTOR = "form #prompt-textarea, "
            + "form [contenteditable='true'][data-testid='prompt-textarea'], "
            + "form [contenteditable='true'][role='textbox']";
    private static final long SUBMISSION_TIMEOUT_MS = 15_000;
    private static final long RESPONSE_TIMEOUT_MS = 180_000;
    private static final long CONVERSATION_LOAD_TIMEOUT_MS = 90_000;
    private static final long TAB_PROBE_TIMEOUT_MS = 3_000;

    private ChatEvaluatorRunner() { }

    public static String run(String vacancyUrl) throws Exception {
        return run(Path.of(System.getProperty("seeker.projectRoot", ".")), vacancyUrl);
    }

    public static String run(Path projectRoot, String vacancyUrl) throws Exception {
        return evaluation(projectRoot).run(vacancyUrl).answer();
    }

    public static GptEvaluation.Result run(Path projectRoot, String vacancyUrl, int jobId) throws Exception {
        return evaluation(projectRoot).run(vacancyUrl, jobId);
    }

    private static GptEvaluation evaluation(Path projectRoot) {
        Path root = projectRoot.toAbsolutePath().normalize();
        return new GptEvaluation(url -> evaluateInBrowser(root, url), new PythonEvaluationStore(root));
    }

    private static String evaluateInBrowser(Path projectRoot, String vacancyUrl) throws Exception {
        if (vacancyUrl == null || vacancyUrl.isBlank()) {
            throw new IllegalArgumentException("Vacancy URL must not be blank.");
        }
        Path root = projectRoot.toAbsolutePath().normalize();
        Properties chat = load(root.resolve(CHAT_CONFIG));
        String chatUrl = required(chat, "chat.url", root.resolve(CHAT_CONFIG));
        Properties runtime = load(root.resolve(LINKEDIN_RUNTIME));
        Properties linkedin = load(root.resolve(LINKEDIN_CONFIG));
        String profileValue = required(runtime, "profile.directory", root.resolve(LINKEDIN_RUNTIME));
        Path profile = Path.of(profileValue);
        if (!profile.isAbsolute()) profile = root.resolve(profile).normalize();
        String channel = linkedin.getProperty("browserChannel", "chrome").strip();

        boolean availabilityConfirmed = availableViaHttp(() -> LinkedInJobAvailability.fetch(vacancyUrl));
        try (ManagedBrowser browser = ManagedBrowser.connect(profile, channel)) {
            if (!availabilityConfirmed) requireAvailableVacancyInBrowser(browser, vacancyUrl);
            Page page = findConversation(browser.context().pages(), chatUrl);
            boolean openConversation = page == null;
            if (openConversation) page = browser.newBackgroundPage();
            browser.keepOpen(page);
            enableFocusEmulation(browser, page);
            page.setDefaultTimeout(30_000);
            Locator composer = prepareConversation(page, chatUrl, openConversation);
            composer.fill(vacancyUrl);
            requireConversation(page, chatUrl);
            List<Turn> before = readTurns(page);
            if (!historyReadyForMessage(page, before)) {
                throw new IOException("ChatGPT is not ready for another message; no send action was attempted.");
            }
            submitOnce(page, chatUrl, composer);
            Turn submitted = null;
            long submissionDeadline = System.nanoTime() + SUBMISSION_TIMEOUT_MS * 1_000_000L;
            while (System.nanoTime() < submissionDeadline) {
                requireConversation(page, chatUrl);
                submitted = submittedTurn(before, readTurns(page), vacancyUrl,
                        composer.innerText().isBlank());
                if (submitted != null) break;
                page.waitForTimeout(250);
            }
            if (submitted == null) {
                throw new IOException("ChatGPT did not confirm a new user message within 15 seconds. "
                        + "No second submission was attempted.");
            }

            long deadline = System.nanoTime() + RESPONSE_TIMEOUT_MS * 1_000_000L;
            String previousObservedResponse = "";
            int stableObservations = 0;
            while (System.nanoTime() < deadline) {
                page.waitForTimeout(750);
                requireConversation(page, chatUrl);
                String response = completedResponse(submitted, before, readTurns(page));
                if (!response.isBlank() && response.equals(previousObservedResponse)) {
                    stableObservations++;
                } else {
                    previousObservedResponse = response;
                    stableObservations = 0;
                }
                if (stableObservations >= 2) return response;
            }
            throw new TimeoutError("Timed out waiting for the completed ChatGPT answer to submitted turn "
                    + submitted.key() + ".");
        }
    }

    static Locator messageComposer(Page page) {
        return page.locator(":is(" + COMPOSER_SELECTOR + "):visible");
    }

    static Locator prepareConversation(Page page, String chatUrl, boolean navigate) throws IOException {
        long deadline = System.nanoTime() + CONVERSATION_LOAD_TIMEOUT_MS * 1_000_000L;
        LoadStatus status = new LoadStatus();
        if (!navigate) status.advance("checking the existing conversation");
        Consumer<Request> failed = request -> {
            if (request.isNavigationRequest() && request.frame().equals(page.mainFrame())) {
                status.failure = "main document load failed: " + request.failure();
            }
        };
        Consumer<Page> crashed = ignored -> status.failure = "the conversation tab crashed";
        page.onRequestFailed(failed);
        page.onCrash(crashed);
        boolean probing = false;
        try {
            requireConnectedTab(page);
            if (navigate) {
                Response response = page.navigate(chatUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.COMMIT)
                        .setTimeout(CONVERSATION_LOAD_TIMEOUT_MS));
                if (response == null || response.status() >= 400) {
                    throw new IOException("ChatGPT main document returned "
                            + (response == null ? "no response" : "HTTP " + response.status()) + ". No URL was sent.");
                }
                status.advance("main document received (HTTP " + response.status() + ")");
            }
            String previousKey = "";
            int stableObservations = 0;
            while (System.nanoTime() < deadline) {
                requireConnectedTab(page);
                if (status.failure != null) throw new IOException("ChatGPT " + status.failure + ". No URL was sent.");
                probing = true;
                Map<?, ?> state = conversationState(page);
                probing = false;
                if (status.failure != null) throw new IOException("ChatGPT " + status.failure + ". No URL was sent.");
                String stage = (String) state.get("stage");
                status.advance(stage + ", document=" + state.get("document"));
                if (stage.equals("sign-in required") || stage.equals("Cloudflare verification required")) {
                    throw new IOException("ChatGPT " + stage + ". Complete it manually in the managed browser. No URL was sent.");
                }
                requireConversation(page, chatUrl);
                String key = (String) state.get("key");
                stableObservations = stage.equals("ready") && key.equals(previousKey) ? stableObservations + 1 : 0;
                previousKey = stage.equals("ready") ? key : "";
                if (stableObservations >= 2) return messageComposer(page);
                page.waitForTimeout(250);
            }
            throw new IOException("ChatGPT tab responds but is not ready. " + status.describe() + ". No URL was sent.");
        } catch (PlaywrightException error) {
            String reason = probing && error instanceof TimeoutError
                    ? "tab did not respond to a DOM check within 3 seconds"
                    : failureDetails(error);
            throw new IOException("ChatGPT failed while " + status.describe() + ": " + reason
                    + ". No URL was sent.", error);
        } finally {
            page.offRequestFailed(failed);
            page.offCrash(crashed);
        }
    }

    private static void requireConnectedTab(Page page) throws IOException {
        if (!page.context().browser().isConnected()) throw new IOException("Browser connection lost. No URL was sent.");
        if (page.isClosed()) throw new IOException("Conversation tab was closed. No URL was sent.");
    }

    private static final class LoadStatus {
        String stage = "opening the conversation";
        String failure;
        long changedAt = System.nanoTime();

        void advance(String next) {
            if (!stage.equals(next)) { stage = next; changedAt = System.nanoTime(); }
        }

        String describe() {
            return stage + "; no stage change for " + (System.nanoTime() - changedAt) / 1_000_000_000L + " s";
        }
    }

    private static Map<?, ?> conversationState(Page page) {
        var handle = page.waitForFunction("""
            selector => {
              const visible = node => node.getClientRects().length && getComputedStyle(node).visibility !== 'hidden';
              const has = selector => [...document.querySelectorAll(selector)].some(visible);
              const editors = [...document.querySelectorAll(selector)].filter(visible);
              const editor = editors.length === 1 ? editors[0] : null;
              const last = [...document.querySelectorAll('[data-turn-key]')].at(-1);
              const user = last?.querySelector('[data-chatgpt-search-unit-key$=":user"]');
              const key = last?.getAttribute('data-turn-key')?.trim() || '';
              let stage;
              if (has('#mobile-auth-email, #mobile-auth-phone')) stage = 'sign-in required';
              else if (has('#challenge-running, #challenge-stage, iframe[src*="challenges.cloudflare.com"]'))
                stage = 'Cloudflare verification required';
              else if (!editor) stage = 'waiting for a unique message composer (matches=' + editors.length + ')';
              else if (editor.matches(':disabled') || editor.getAttribute('contenteditable') === 'false'
                       || editor.closest('[aria-disabled="true" i], [inert]')) stage = 'message composer is disabled';
              else if (!key || !user?.getAttribute('data-chatgpt-search-message-ids')?.trim()) stage = 'waiting for conversation history';
              else if (has('button[data-testid="stop-button"], button#stop-button')) stage = 'previous response is still generating';
              else stage = 'ready';
              return {stage, key, document: document.readyState};
            }
            """, COMPOSER_SELECTOR, new Page.WaitForFunctionOptions()
                .setPollingInterval(250).setTimeout(TAB_PROBE_TIMEOUT_MS));
        try {
            return (Map<?, ?>) handle.jsonValue();
        } finally {
            handle.dispose();
        }
    }

    private static void enableFocusEmulation(ManagedBrowser browser, Page page) {
        JsonObject focus = new JsonObject();
        focus.addProperty("enabled", true);
        browser.context().newCDPSession(page).send("Emulation.setFocusEmulationEnabled", focus);
    }

    private static void submitOnce(Page page, String chatUrl, Locator composer) throws IOException {
        long deadline = System.nanoTime() + SUBMISSION_TIMEOUT_MS * 1_000_000L;
        Locator sendButton = composer.locator("xpath=ancestor::form[1]")
                .locator("button[type='submit']:visible");
        String lastState = "not checked";
        while (System.nanoTime() < deadline) {
            requireConversation(page, chatUrl);
            // Check readiness and click in one task; background tabs need not produce animation frames.
            lastState = (String) sendButton.evaluateAll("""
                buttons => {
                  const visibility = 'visibility=' + document.visibilityState;
                  if (buttons.length !== 1) return 'matches=' + buttons.length + ', ' + visibility;
                  const button = buttons[0];
                  if (!button.isConnected) return 'detached, ' + visibility;
                  if (button.matches(':disabled')) return 'disabled, ' + visibility;
                  if (button.closest('[aria-disabled="true" i], [inert]')) return 'aria-disabled/inert, ' + visibility;
                  button.click();
                  return 'clicked';
                }
                """);
            if ("clicked".equals(lastState)) return;
            page.waitForTimeout(250);
        }
        throw new IOException("The ChatGPT send button in the message composer was not ready "
                + "within 15 seconds (" + lastState + "); no send action was attempted.");
    }

    @FunctionalInterface
    interface AvailabilityRequest {
        HttpResponse<String> fetch() throws IOException, InterruptedException;
    }

    /** False means HTTP could not confirm availability; check with the existing browser session. */
    static boolean availableViaHttp(AvailabilityRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response;
        try {
            response = request.fetch();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (IOException networkFailure) {
            return false;
        }
        String outcome;
        try {
            if (response.statusCode() == 429) return false;
            outcome = LinkedInJobAvailability.classifyResponse(response.statusCode(), response.uri(),
                    response.headers().firstValue("Content-Type").orElse(""),
                    response.body() == null ? "" : response.body());
        } catch (IllegalStateException error) {
            throw new IOException("Vacancy HTTP availability check failed: " + failureDetails(error)
                    + " The URL was not sent to ChatGPT.", error);
        }
        if (!"available".equals(outcome)) {
            throw new IOException("Vacancy is " + outcome + "; the URL was not sent to ChatGPT.");
        }
        return true;
    }

    static String failureDetails(Throwable error) {
        StringBuilder details = new StringBuilder(error.toString());
        for (Throwable cause = error.getCause(); cause != null; cause = cause.getCause()) {
            details.append("; caused by ").append(cause);
        }
        return details.toString();
    }

    private static void requireAvailableVacancyInBrowser(ManagedBrowser browser, String vacancyUrl)
            throws IOException {
        Page vacancy = browser.newBackgroundPage();
        String stage = "opening the vacancy page";
        try {
            enableFocusEmulation(browser, vacancy);
            Response response = vacancy.navigate(vacancyUrl, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED).setTimeout(30_000));
            if (response == null) throw new IOException("The browser returned no vacancy response.");
            if (response.status() == 200) {
                stage = "waiting for the vacancy description or a closure notice";
                vacancy.waitForFunction("""
                    () => {
                      if (/\\/(login|authwall|checkpoint)(\\/|$)/i.test(location.pathname)) return true;
                      const text = (document.body?.innerText || '').toLowerCase();
                      if (text.includes('no longer accepting applications')
                          || text.includes('this job is no longer available')) return true;
                      if (document.querySelector("input[name='session_key'], form[action*='login']")) return true;
                      const visible = node => node.getClientRects().length && getComputedStyle(node).visibility !== 'hidden';
                      return [...document.querySelectorAll(
                        '.show-more-less-html__markup, .description__text, article.jobs-description__container, '
                        + '[data-testid="job-details"], [data-view-name="job-details"], .jobs-details, main'
                      )].some(node => {
                        if (!visible(node)) return false;
                        if ([...node.querySelectorAll('[role="progressbar"], [aria-label="In progress"]')].some(visible)) return false;
                        const content = (node.innerText || '').trim().toLowerCase();
                        return content && (node.matches('.show-more-less-html__markup, .description__text, article.jobs-description__container')
                          || ['about the job', 'role overview', 'requirements', 'key responsibilities']
                            .some(marker => content.includes(marker)));
                      });
                    }
                    """, null, new Page.WaitForFunctionOptions().setPollingInterval(250).setTimeout(30_000));
            }
            stage = "reading the vacancy page";
            if (vacancy.locator("input[name='session_key'], form[action*='login']").count() > 0) {
                throw new IOException("LinkedIn requires authentication; the URL was not sent to ChatGPT.");
            }
            String outcome = LinkedInJobAvailability.classifyResponse(response.status(), URI.create(vacancy.url()),
                    response.headers().getOrDefault("content-type", ""), vacancy.content(),
                    vacancy.locator("body").innerText());
            if (!"available".equals(outcome)) {
                throw new IOException("Vacancy is " + outcome + "; the URL was not sent to ChatGPT.");
            }
        } catch (TimeoutError error) {
            throw new IOException("Vacancy browser availability check timed out while " + stage
                    + ". Current URL: " + vacancy.url() + ". The URL was not sent to ChatGPT.", error);
        } catch (PlaywrightException | IllegalStateException error) {
            throw new IOException("Vacancy browser availability check failed: " + failureDetails(error)
                    + " The URL was not sent to ChatGPT.", error);
        } finally {
            vacancy.close();
        }
    }

    private static void requireConversation(Page page, String chatUrl) throws IOException {
        if (!sameConversation(page.url(), chatUrl)) {
            throw new IOException("The page is no longer the configured ChatGPT conversation: " + page.url());
        }
    }

    static void awaitReadyHistory(Page page, String chatUrl) throws IOException {
        prepareConversation(page, chatUrl, false);
    }

    static boolean historyReadyForMessage(Page page, List<Turn> turns) {
        if (turns.isEmpty() || turns.getLast().key().isBlank() || turns.getLast().userId().isBlank()) {
            return false;
        }
        // A stopped previous response need not be complete. Check whether the UI
        // accepts a new message without weakening completedResponse for our answer.
        return Boolean.TRUE.equals(page.evaluate("""
            selector => {
              const visible = node => node.getClientRects().length
                && getComputedStyle(node).visibility !== 'hidden';
              const stopping = [...document.querySelectorAll(
                'button[data-testid="stop-button"], button#stop-button'
              )].some(visible);
              const editors = [...document.querySelectorAll(selector)].filter(visible);
              const editor = editors.length === 1 ? editors[0] : null;
              return !stopping && !!editor && !editor.matches(':disabled')
                && editor.getAttribute('contenteditable') !== 'false'
                && !editor.closest('[aria-disabled="true" i], [inert]');
            }
            """, COMPOSER_SELECTOR));
    }

    private static List<Turn> readTurns(Page page) {
        List<?> values = (List<?>) page.locator("[data-turn-key]").evaluateAll("""
            turns => turns.map(turn => {
              const user = turn.querySelector('[data-chatgpt-search-unit-key$=":user"]');
              const assistant = Array.from(turn.querySelectorAll('[data-chatgpt-search-unit-key$=":assistant"]')).at(-1);
              const id = message => (message?.getAttribute('data-chatgpt-search-message-ids') || '').trim().split(/\\s+/)[0];
              const text = message => {
                if (!message) return '';
                const body = Array.from(message.children)
                  .filter(child => !child.hasAttribute('data-conversation-role'))
                  .map(child => child.innerText).join('\\n');
                return (body || message.innerText).trim();
              };
              return [turn.getAttribute('data-turn-key') || '', id(user), text(user),
                id(assistant), text(assistant),
                turn.querySelector('[data-talvt-turn-state]')?.getAttribute('data-talvt-turn-state') === 'complete'];
            })
            """);
        return values.stream().map(value -> {
            List<?> turn = (List<?>) value;
            return new Turn((String) turn.get(0), (String) turn.get(1), (String) turn.get(2),
                    (String) turn.get(3), (String) turn.get(4), Boolean.TRUE.equals(turn.get(5)));
        }).toList();
    }

    record Turn(String key, String userId, String userText,
                String assistantId, String response, boolean complete) { }

    static Turn submittedTurn(List<Turn> before, List<Turn> current,
                              String vacancyUrl, boolean composerEmpty) {
        if (!composerEmpty) return null;
        Set<String> priorKeys = new HashSet<>();
        Set<String> priorUsers = new HashSet<>();
        for (Turn turn : before) {
            priorKeys.add(turn.key());
            priorUsers.add(turn.userId());
        }
        int anchor = -1;
        if (!before.isEmpty()) {
            String lastKey = before.getLast().key();
            for (int i = 0; i < current.size(); i++) {
                if (lastKey.equals(current.get(i).key())) anchor = i;
            }
            // A missing anchor may mean navigation or replacement of the history.
            if (anchor < 0) return null;
        }
        for (int i = current.size() - 1; i > anchor; i--) {
            Turn turn = current.get(i);
            if (!turn.key().isBlank() && !turn.userId().isBlank()
                    && !priorKeys.contains(turn.key()) && !priorUsers.contains(turn.userId())
                    && vacancyUrl.equals(turn.userText())) return turn;
        }
        return null;
    }

    static String completedResponse(Turn submitted, List<Turn> before, List<Turn> current) {
        if (submitted == null) return "";
        for (Turn turn : current) {
            if (!submitted.key().equals(turn.key())
                    || !submitted.userId().equals(turn.userId())
                    || !submitted.userText().equals(turn.userText())) continue;
            if (!turn.complete() || turn.response().isBlank() || turn.assistantId().isBlank()) return "";
            boolean oldAnswer = before.stream().anyMatch(old -> old.assistantId().equals(turn.assistantId()));
            return oldAnswer ? "" : turn.response();
        }
        return "";
    }

    private static Page findConversation(List<Page> pages, String chatUrl) {
        for (Page page : pages) {
            if (!page.isClosed() && sameConversation(page.url(), chatUrl)) return page;
        }
        return null;
    }

    private static boolean sameConversation(String current, String configured) {
        return current.equals(configured) || current.replaceAll("/+$", "").equals(configured.replaceAll("/+$", ""));
    }

    private static Properties load(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Required configuration file is missing: " + path);
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path)) { properties.load(reader); }
        return properties;
    }

    private static String required(Properties properties, String key, Path source) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Missing '" + key + "' in " + source);
        return value.strip();
    }

    public static void main(String[] args) {
        int exitCode = execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int execute(String[] args) {
        if (args.length != 1 || !isHttpUrl(args[0])) {
            write(System.err, "Expected one vacancy URL argument. See ChatEvaluator/README.md.\n");
            return 2;
        }
        try {
            write(System.out, run(args[0]));
            return 0;
        } catch (Exception error) {
            String message = error.getMessage();
            if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
            write(System.err, "ChatEvaluator failed: " + message + System.lineSeparator());
            return 1;
        }
    }

    private static void write(java.io.PrintStream stream, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        stream.write(bytes, 0, bytes.length);
        stream.flush();
    }

    private static boolean isHttpUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
