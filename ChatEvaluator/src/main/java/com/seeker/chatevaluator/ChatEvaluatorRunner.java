package com.seeker.chatevaluator;

import com.google.gson.JsonObject;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Request;
import com.seeker.browser.ManagedBrowser;
import com.seeker.browser.BrowserInteraction;
import com.seeker.browser.LinkedInJobPage;
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

/** Sends a vacancy URL to the configured existing ChatGPT conversation. */
public final class ChatEvaluatorRunner {
    private static final String CHAT_CONFIG = "Data/chat_evaluator/config.properties";
    private static final String LINKEDIN_RUNTIME = "Driver/collector/java_linkedin/runtime.properties";
    private static final String LINKEDIN_CONFIG = "Driver/collector/config/linkedin.properties";
    private static final String COMPOSER_SELECTOR = "form #prompt-textarea, "
            + "form [contenteditable='true'][data-testid='prompt-textarea'], "
            + "form [contenteditable='true'][role='textbox']";

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
            prepareConversation(page, chatUrl, openConversation);
            return sendAndAwait(page, chatUrl, vacancyUrl);

        }
    }

    static Locator messageComposer(Page page) {
        return page.locator(":is(" + COMPOSER_SELECTOR + "):visible");
    }

    private static boolean conversationData(Request request) {
        String path = URI.create(request.url()).getPath();
        return path.contains("/backend-api/") && (path.contains("conversation") || path.endsWith("/me"));
    }

    static Locator prepareConversation(Page page, String chatUrl, boolean navigate) throws IOException {
        try (var interaction = new BrowserInteraction(page, ChatEvaluatorRunner::conversationData)) {
            if (navigate) interaction.open("opening the conversation", chatUrl);
            String[] previousKey = {""};
            int[] stable = {0};
            return interaction.await("preparing the conversation", BrowserInteraction.LOAD, () -> {
                Map<?, ?> state = conversationState(page);
                String stage = state.get("stage").toString();
                if (stage.equals("sign-in required") || stage.equals("Cloudflare verification required"))
                    throw new IOException(stage + ". Complete it manually in the managed browser");
                requireConversation(page, chatUrl);
                String key = state.get("key").toString();
                stable[0] = stage.equals("ready") && key.equals(previousKey[0]) ? stable[0] + 1 : 0;
                previousKey[0] = stage.equals("ready") ? key : "";
                if (stable[0] >= 2) return BrowserInteraction.Observation.ready(messageComposer(page));
                return BrowserInteraction.Observation.pending(stage + ":" + key + ":" + state.get("document"), stage);
            });
        } catch (IOException failure) {
            throw new IOException(failure.getMessage() + ". No URL was sent.", failure);
        }
    }

    private static Map<?, ?> conversationState(Page page) {
        return (Map<?, ?>) BrowserInteraction.read(page,"""
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
              const turns = [...document.querySelectorAll('[data-turn-key]')].map(turn => {
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
            });
              return {stage, key, turns, document: document.readyState,
                composerEmpty: !!editor && !(editor.innerText || editor.value || '').trim()};
            }
            """, COMPOSER_SELECTOR);
    }

    private static void enableFocusEmulation(ManagedBrowser browser, Page page) {
        JsonObject focus = new JsonObject();
        focus.addProperty("enabled", true);
        browser.context().newCDPSession(page).send("Emulation.setFocusEmulationEnabled", focus);
    }

    private static void requireNoObstacle(Map<?, ?> state) throws IOException {
        String stage = state.get("stage").toString();
        if (stage.equals("sign-in required") || stage.equals("Cloudflare verification required"))
            throw new IOException(stage + ". Complete it manually in the managed browser");
    }

    static String sendAndAwait(Page page, String chatUrl, String vacancyUrl) throws IOException {
        try (var interaction = new BrowserInteraction(page, ChatEvaluatorRunner::conversationData)) {
            interaction.fill("entering the vacancy URL", ":is(" + COMPOSER_SELECTOR + "):visible", vacancyUrl);
            List<Turn> before = interaction.await("checking history before sending", BrowserInteraction.ACTION, () -> {
                requireConversation(page, chatUrl);
                var state = conversationState(page);
                requireNoObstacle(state);
                if (!"ready".equals(state.get("stage")))
                    return BrowserInteraction.Observation.pending(state.get("stage").toString(), state.get("stage").toString());
                List<Turn> turns = turns(state);
                return turns.isEmpty() ? BrowserInteraction.Observation.pending("", "waiting for history")
                        : BrowserInteraction.Observation.ready(turns);
            });
            requireConversation(page, chatUrl);
            interaction.clickOnce("sending the vacancy URL",
                    "form:has(:is(" + COMPOSER_SELECTOR + ")) button[type='submit']");
            // Confirmation is a separate observation. It never dispatches another action.
            Turn submitted = interaction.await("confirming the new user message (no second submission)",
                    BrowserInteraction.RESPONSE, () -> {
                requireConversation(page, chatUrl);
                var state = conversationState(page);
                requireNoObstacle(state);
                List<Turn> turns = turns(state);
                boolean empty = Boolean.TRUE.equals(state.get("composerEmpty"));
                Turn turn = submittedTurn(before, turns, vacancyUrl, empty);
                return turn == null ? BrowserInteraction.Observation.pending("", "new message ID not confirmed")
                        : BrowserInteraction.Observation.ready(turn);
            });
            String[] previous = {""};
            long[] stableSince = {0};
            return interaction.await("waiting for the answer to turn " + submitted.key(), BrowserInteraction.RESPONSE, () -> {
                requireConversation(page, chatUrl);
                var state = conversationState(page);
                requireNoObstacle(state);
                List<Turn> turns = turns(state);
                String response = completedResponse(submitted, before, turns);
                if (!response.equals(previous[0])) stableSince[0] = System.nanoTime();
                previous[0] = response;
                if (!response.isBlank() && (System.nanoTime() - stableSince[0]) / 1_000_000 >= 1500)
                    return BrowserInteraction.Observation.ready(response);
                String progress = turns.stream().filter(t -> submitted.key().equals(t.key())
                        && submitted.userId().equals(t.userId())).map(t -> t.assistantId() + ":" + t.response().length() + ":" + t.response().hashCode()).findFirst().orElse("");
                return BrowserInteraction.Observation.pending(progress, "waiting for a completed, stable answer with the matching IDs");
            });
        }
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
        try {
            enableFocusEmulation(browser, vacancy);
            requireAvailableVacancy(vacancy, vacancyUrl);
        } finally {
            vacancy.close();
        }
    }

    static void requireAvailableVacancy(Page vacancy, String vacancyUrl) throws IOException {
        try (var interaction = new BrowserInteraction(vacancy,
                request -> request.url().contains("/voyager/api/") && request.url().toLowerCase().contains("job"))) {
            interaction.open("opening the vacancy page", vacancyUrl);
            interaction.await("checking vacancy availability", BrowserInteraction.LOAD, () -> {
                var state = LinkedInJobPage.snapshot(vacancy);
                if (Boolean.TRUE.equals(state.get("auth"))) throw new IOException("LinkedIn requires authentication");
                if (Boolean.TRUE.equals(state.get("closed"))) throw new IOException("Vacancy is closed");
                if (!Boolean.TRUE.equals(state.get("description")))
                    return BrowserInteraction.Observation.pending(state.get("document").toString(), "waiting for the vacancy description or a closure notice");
                String html = (String) BrowserInteraction.read(vacancy, "() => document.documentElement.outerHTML", null);
                String outcome = LinkedInJobAvailability.classifyResponse(200, URI.create(vacancy.url()),
                        "text/html", html, state.get("text").toString());
                if (!"available".equals(outcome)) throw new IOException("Vacancy is " + outcome);
                return BrowserInteraction.Observation.ready(true);
            });
        } catch (IOException | IllegalStateException error) {
            throw new IOException("Vacancy browser availability check failed: " + error.getMessage()
                    + ". The URL was not sent to ChatGPT.", error);
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
        return Boolean.TRUE.equals(BrowserInteraction.read(page, """
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

    private static List<Turn> turns(Map<?, ?> state) {
        List<?> values = (List<?>) state.get("turns");
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
