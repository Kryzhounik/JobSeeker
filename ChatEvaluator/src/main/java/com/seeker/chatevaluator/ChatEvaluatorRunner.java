package com.seeker.chatevaluator;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitUntilState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.seeker.applier.shared.ManagedBrowser;
import com.seeker.tools.LinkedInJobAvailability;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/** Sends a vacancy URL to the configured existing ChatGPT conversation. */
public final class ChatEvaluatorRunner {
    private static final String CHAT_CONFIG = "Data/chat_evaluator/config.properties";
    private static final String LINKEDIN_RUNTIME = "Driver/collector/java_linkedin/runtime.properties";
    private static final String LINKEDIN_CONFIG = "Driver/collector/config/linkedin.properties";
    private static final long SUBMISSION_TIMEOUT_MS = 15_000;
    private static final long RESPONSE_TIMEOUT_MS = 180_000;

    private ChatEvaluatorRunner() { }

    public static String run(String vacancyUrl) throws Exception {
        if (vacancyUrl == null || vacancyUrl.isBlank()) {
            throw new IllegalArgumentException("Vacancy URL must not be blank.");
        }
        Path root = Path.of(System.getProperty("seeker.projectRoot", "."))
                .toAbsolutePath().normalize();
        Properties chat = load(root.resolve(CHAT_CONFIG));
        String chatUrl = required(chat, "chat.url", root.resolve(CHAT_CONFIG));
        Properties runtime = load(root.resolve(LINKEDIN_RUNTIME));
        Properties linkedin = load(root.resolve(LINKEDIN_CONFIG));
        String profileValue = required(runtime, "profile.directory", root.resolve(LINKEDIN_RUNTIME));
        Path profile = Path.of(profileValue);
        if (!profile.isAbsolute()) profile = root.resolve(profile).normalize();
        String channel = linkedin.getProperty("browserChannel", "chrome").strip();

        requireAvailableVacancy(vacancyUrl);
        try (ManagedBrowser browser = ManagedBrowser.connect(profile, channel)) {
            Page page = findConversation(browser.context().pages(), chatUrl);
            boolean openConversation = page == null;
            if (openConversation) page = browser.newPage();
            browser.keepOpen(page);
            page.setDefaultTimeout(30_000);
            page.setDefaultNavigationTimeout(60_000);
            if (openConversation) {
                page.navigate(chatUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            }

            boolean signInForm = page.locator("#mobile-auth-email, #mobile-auth-phone").count() > 0;
            if (signInForm) {
                throw new IOException("ChatGPT is showing its sign-in form in the managed LinkedIn browser profile. "
                        + "Sign in manually in the open browser once, then rerun the evaluation; the module does not store your password.");
            }

            Locator composer = page.locator("#prompt-textarea, "
                    + "[contenteditable='true'][data-testid='prompt-textarea'], "
                    + "[contenteditable='true'][role='textbox']");
            try {
                composer.first().waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE).setTimeout(30_000));
            } catch (TimeoutError timeout) {
                if (page.locator("#mobile-auth-email, #mobile-auth-phone").count() > 0) {
                    throw new IOException("ChatGPT is showing its sign-in form in the managed LinkedIn browser profile. "
                            + "Sign in manually in the open browser once, then rerun the evaluation; the module does not store your password.", timeout);
                }
                throw new IOException("The ChatGPT message composer did not become visible. Current page: " + page.url(), timeout);
            }

            requireConversation(page, chatUrl);
            awaitReadyHistory(page, chatUrl);
            composer.first().fill(vacancyUrl);
            Locator sendButton = page.locator("button[type='submit']:visible");
            try {
                sendButton.waitFor(new Locator.WaitForOptions()
                        .setState(WaitForSelectorState.VISIBLE).setTimeout(SUBMISSION_TIMEOUT_MS));
            } catch (TimeoutError timeout) {
                throw new IOException("The ChatGPT send button did not become available; submission was not confirmed.", timeout);
            }
            requireConversation(page, chatUrl);
            List<Turn> before = readTurns(page);
            if (before.isEmpty() || !before.getLast().complete()) {
                throw new IOException("ChatGPT is not ready for another message; no send action was attempted.");
            }
            // One explicit submit action. Never follow it with Enter or an automatic retry.
            sendButton.click(new Locator.ClickOptions().setTimeout(SUBMISSION_TIMEOUT_MS));
            Turn submitted = null;
            long submissionDeadline = System.nanoTime() + SUBMISSION_TIMEOUT_MS * 1_000_000L;
            while (System.nanoTime() < submissionDeadline) {
                requireConversation(page, chatUrl);
                submitted = submittedTurn(before, readTurns(page), vacancyUrl,
                        composer.first().innerText().isBlank());
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

    private static void requireAvailableVacancy(String vacancyUrl) throws IOException, InterruptedException {
        String outcome;
        try {
            var response = LinkedInJobAvailability.fetch(vacancyUrl);
            outcome = LinkedInJobAvailability.classifyResponse(response.statusCode(), response.uri(),
                    response.headers().firstValue("Content-Type").orElse(""),
                    response.body() == null ? "" : response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw interrupted;
        } catch (IllegalStateException | IOException error) {
            throw new IOException("Vacancy availability check failed: " + error.getMessage()
                    + " The URL was not sent to ChatGPT.", error);
        }
        if (!"available".equals(outcome)) {
            throw new IOException("Vacancy is " + outcome + "; the URL was not sent to ChatGPT.");
        }
    }

    private static void requireConversation(Page page, String chatUrl) throws IOException {
        if (!sameConversation(page.url(), chatUrl)) {
            throw new IOException("The page is no longer the configured ChatGPT conversation: " + page.url());
        }
    }

    private static void awaitReadyHistory(Page page, String chatUrl) throws IOException {
        long deadline = System.nanoTime() + 30_000_000_000L;
        String previousKey = "";
        int stableObservations = 0;
        while (System.nanoTime() < deadline) {
            requireConversation(page, chatUrl);
            List<Turn> turns = readTurns(page);
            Turn last = turns.isEmpty() ? null : turns.getLast();
            if (last != null && !last.userId().isBlank() && last.complete()) {
                stableObservations = last.key().equals(previousKey) ? stableObservations + 1 : 0;
                previousKey = last.key();
                if (stableObservations >= 2) return;
            } else {
                previousKey = "";
                stableObservations = 0;
            }
            page.waitForTimeout(250);
        }
        throw new IOException("ChatGPT history is not ready or the previous response is still in progress; "
                + "no URL was sent.");
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
        if (args.length != 1 || !isHttpUrl(args[0])) {
            write(System.err, "Expected one vacancy URL argument. See ChatEvaluator/README.md.\n");
            System.exit(2);
            return;
        }
        try {
            write(System.out, run(args[0]));
        } catch (Exception error) {
            String message = error.getMessage();
            if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
            write(System.err, "ChatEvaluator failed: " + message + System.lineSeparator());
            System.exit(1);
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
