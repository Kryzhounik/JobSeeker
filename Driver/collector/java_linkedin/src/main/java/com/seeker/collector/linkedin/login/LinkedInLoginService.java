package com.seeker.collector.linkedin.login;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import com.seeker.collector.linkedin.browser.BrowserSettings;
import com.seeker.collector.linkedin.browser.PersistentLinkedInSession;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

/** One-time bootstrap of the browser authentication state. */
public final class LinkedInLoginService {
    private static final String LINKEDIN_JOBS = "https://www.linkedin.com/jobs/";
    private static final int LOGIN_TIMEOUT_SECONDS = 600;

    private final Path profileDirectory;
    private final BrowserSettings browserSettings;

    public LinkedInLoginService(
            Path profileDirectory,
            BrowserSettings browserSettings
    ) {
        this.profileDirectory = profileDirectory;
        this.browserSettings = browserSettings;
    }

    public LoginResult login() throws IOException {
        try (PersistentLinkedInSession session = new PersistentLinkedInSession(
                profileDirectory,
                browserSettings
        )) {
            Page page = session.primaryPage();
            page.navigate(
                    LINKEDIN_JOBS,
                    new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(browserSettings.pageTimeoutSeconds() * 1000.0)
            );
            page.waitForTimeout(500);

            long deadline = System.nanoTime()
                    + Duration.ofSeconds(LOGIN_TIMEOUT_SECONDS).toNanos();
            while (System.nanoTime() < deadline) {
                if (session.isAuthenticated(page)) {
                    return result("ready", "");
                }
                page.waitForTimeout(500);
            }
            return result(
                    "login_required",
                    "LinkedIn login was not completed before timeout"
            );
        }
    }

    private LoginResult result(String status, String message) {
        return new LoginResult(
                "linkedin",
                status,
                profileDirectory.toString(),
                message
        );
    }
}
