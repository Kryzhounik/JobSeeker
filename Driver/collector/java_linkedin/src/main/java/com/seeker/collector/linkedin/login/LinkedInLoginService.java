package com.seeker.collector.linkedin.login;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import com.seeker.collector.linkedin.browser.PersistentLinkedInSession;

import java.io.IOException;
import java.time.Duration;

/** One-time bootstrap of the browser authentication state. */
public final class LinkedInLoginService {
    private static final String LINKEDIN_JOBS = "https://www.linkedin.com/jobs/";

    public LoginResult login(LoginRequest request) {
        try (PersistentLinkedInSession session = new PersistentLinkedInSession(
                request.profileDirectory(),
                request.browserSettings()
        )) {
            Page page = session.primaryPage();
            page.navigate(
                    LINKEDIN_JOBS,
                    new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(
                                    request.browserSettings().pageTimeoutSeconds() * 1000.0
                            )
            );
            page.waitForTimeout(500);

            long deadline = System.nanoTime()
                    + Duration.ofSeconds(request.timeoutSeconds()).toNanos();
            while (System.nanoTime() < deadline) {
                if (session.isAuthenticated(page)) {
                    return result(request, "ready", "");
                }
                page.waitForTimeout(500);
            }
            return result(
                    request,
                    "login_required",
                    "LinkedIn login was not completed before timeout"
            );
        } catch (IOException | RuntimeException error) {
            return result(request, "blocked", message(error));
        }
    }

    private LoginResult result(LoginRequest request, String status, String message) {
        return new LoginResult(
                "linkedin",
                status,
                request.profileDirectory().toString(),
                message
        );
    }

    private String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank()
                ? error.getClass().getSimpleName()
                : value;
    }
}
