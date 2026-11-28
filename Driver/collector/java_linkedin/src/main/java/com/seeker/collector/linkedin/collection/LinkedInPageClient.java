package com.seeker.collector.linkedin.collection;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitUntilState;
import com.seeker.collector.linkedin.browser.AuthenticationRequiredException;
import com.seeker.collector.linkedin.browser.PersistentLinkedInSession;
import com.seeker.collector.linkedin.config.CollectorConfig;
import com.seeker.logging.LogException;
import org.slf4j.event.Level;

import java.time.Duration;

final class LinkedInPageClient {
    private static final String LINKEDIN_JOBS = "https://www.linkedin.com/jobs/";

    private final PersistentLinkedInSession session;
    private final Page page;
    private final CollectorConfig config;
    private final NavigationThrottle throttle;

    LinkedInPageClient(PersistentLinkedInSession session, CollectorConfig config) {
        this.session = session;
        this.config = config;
        this.throttle = new NavigationThrottle(config.delaySeconds());
        this.page = session.primaryPage();
    }

    void preflight() {
        navigate(page, LINKEDIN_JOBS);
        requireAuthenticated("The persistent browser profile is not logged in to LinkedIn");
    }

    void openSeed(SearchPlan.Target target) {
        openSearch(target, 0);
        // LinkedIn may retain the previous search geography if AccountRemote opens
        // too soon after the seed. This 20-second wait is an observed workaround,
        // not a guarantee that LinkedIn has persisted the new geography.
        page.waitForTimeout(20_000);
    }

    String openSearch(SearchPlan.Target target, int start) {
        String url = target.pageUrl(start);
        navigate(page, url);
        requireAuthenticated("LinkedIn session is no longer authenticated");
        LinkedInUrls.verifyQuery(url, page.url());
        return url;
    }

    void openCard(Locator card, String jobId) {
        if (card.count() != 1) {
            throw new CardClickException(
                    "Search card locator count for " + jobId + " is " + card.count()
            );
        }
        try {
            throttle.beforeAction(page);
            card.click(new Locator.ClickOptions()
                    .setTimeout(config.detailsTimeoutSeconds() * 1000.0));
        } catch (PlaywrightException error) {
            throw new CardClickException("Could not click search card " + jobId, error);
        }
    }

    /** The caller closes the returned tab after reading the details. */
    Page openDirect(String sourceUrl) {
        Page direct = session.newPage();
        try {
            navigate(direct, sourceUrl);
            requireNoLoginWall(direct);
            return direct;
        } catch (RuntimeException error) {
            direct.close();
            throw error;
        }
    }

    void requireNoLoginWall(Page targetPage) {
        if (session.isLoginWall(targetPage)) {
            throw new AuthenticationRequiredException(
                    "LinkedIn session is no longer authenticated\nURL: " + targetPage.url()
            );
        }
    }

    private void navigate(Page targetPage, String url) {
        int totalAttempts = config.navigationRetries() + 1;
        for (int attempt = 1; attempt <= totalAttempts; attempt++) {
            try {
                navigateOnce(targetPage, url);
                return;
            } catch (TimeoutError error) {
                if (attempt == totalAttempts) {
                    throw new CollectionBlockedException(
                            "LinkedIn navigation timed out for " + url
                                    + " after " + totalAttempts + " attempts ("
                                    + config.pageTimeoutSeconds() + " seconds each)",
                            error
                    );
                }
            }
        }
    }

    @LogException(level = Level.WARN, types = TimeoutError.class)
    private void navigateOnce(Page targetPage, String url) {
        throttle.beforeAction(targetPage);
        targetPage.navigate(
                url,
                new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(config.pageTimeoutSeconds() * 1000.0)
        );
        targetPage.waitForTimeout(500);
    }

    private void requireAuthenticated(String message) {
        long deadline = System.nanoTime()
                + Duration.ofSeconds(config.pageTimeoutSeconds()).toNanos();
        while (System.nanoTime() < deadline) {
            if (session.isAuthenticated(page)) {
                return;
            }
            if (session.isLoginWall(page)) {
                break;
            }
            page.waitForTimeout(250);
        }
        throw new AuthenticationRequiredException(message + "\nURL: " + page.url());
    }
}
