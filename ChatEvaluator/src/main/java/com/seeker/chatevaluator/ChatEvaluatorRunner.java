package com.seeker.chatevaluator;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitUntilState;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.seeker.applier.shared.ManagedBrowser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

/** Sends a vacancy URL to the configured existing ChatGPT conversation. */
public final class ChatEvaluatorRunner {
    private static final String CHAT_CONFIG = "Data/chat_evaluator/config.properties";
    private static final String LINKEDIN_RUNTIME = "Driver/collector/java_linkedin/runtime.properties";
    private static final String LINKEDIN_CONFIG = "Driver/collector/config/linkedin.properties";
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

        try (ManagedBrowser browser = ManagedBrowser.connect(profile, channel)) {
            Page page = findConversation(browser.context().pages(), chatUrl);
            if (page == null) page = browser.newPage();
            browser.keepOpen(page);
            browser.context().setDefaultTimeout(30_000);
            browser.context().setDefaultNavigationTimeout(60_000);
            page.navigate(chatUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));

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

            Locator assistantMessages = page.locator("[data-chatgpt-search-unit-key$=':assistant']");
            int priorCount = assistantMessages.count();
            composer.first().fill(vacancyUrl);
            composer.first().press("Enter");

            long deadline = System.nanoTime() + RESPONSE_TIMEOUT_MS * 1_000_000L;
            String response = "";
            String previousObservedResponse = "";
            int stableObservations = 0;
            boolean sawNewAnswer = false;
            while (System.nanoTime() < deadline) {
                page.waitForTimeout(750);
                int count = assistantMessages.count();
                Locator assistantMessage = count > priorCount
                        ? assistantMessages.nth(count - 1)
                        : page.locator("[data-talvt-turn-state]").last();
                if (count > priorCount) {
                    sawNewAnswer = true;
                    String answerText = (String) assistantMessage.evaluate(
                            "element => Array.from(element.children)"
                                    + ".filter(child => child.getAttribute('data-conversation-role') !== 'assistant')"
                                    + ".map(child => child.innerText).join('\\n')");
                    response = (answerText == null || answerText.isBlank()
                            ? assistantMessage.innerText() : answerText).strip();
                }
                Locator turn = count > priorCount
                        ? assistantMessage.locator("xpath=ancestor::*[@data-turn-key][1]")
                        : page.locator("[data-turn-key]").last();
                Locator state = turn.locator("[data-talvt-turn-state]");
                boolean complete = count > priorCount
                        && "complete".equals(state.getAttribute("data-talvt-turn-state"));
                if (response.equals(previousObservedResponse)) {
                    stableObservations++;
                } else {
                    previousObservedResponse = response;
                    stableObservations = 0;
                }
                if (sawNewAnswer && !response.isBlank() && complete && stableObservations >= 2) {
                    return response;
                }
            }
            throw new TimeoutError("Timed out waiting for a completed new ChatGPT answer.");
        }
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

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: ChatEvaluatorRunner <vacancy-url>");
        System.out.println(run(args[0]));
    }
}
