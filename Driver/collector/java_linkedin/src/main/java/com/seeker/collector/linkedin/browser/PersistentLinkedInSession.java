package com.seeker.collector.linkedin.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Owns only the Playwright process and persistent browser context lifecycle. */
public final class PersistentLinkedInSession implements AutoCloseable {
    private final Playwright playwright;
    private final BrowserContext context;
    private final Page primaryPage;

    public PersistentLinkedInSession(
            Path profileDirectory,
            BrowserSettings settings
    ) throws IOException {
        Files.createDirectories(profileDirectory);
        this.playwright = Playwright.create();

        BrowserType.LaunchPersistentContextOptions launch =
                new BrowserType.LaunchPersistentContextOptions()
                        .setHeadless(settings.headless())
                        .setViewportSize(null)
                        .setArgs(List.of("--window-size=1440,1000"))
                        .setTimeout(settings.launchTimeoutSeconds() * 1000.0)
                        .setIgnoreDefaultArgs(List.of("--disable-extensions"));
        if (settings.channel() != null && !settings.channel().isBlank()) {
            launch.setChannel(settings.channel());
        }

        this.context = playwright.chromium().launchPersistentContext(
                profileDirectory,
                launch
        );
        context.setDefaultTimeout(settings.pageTimeoutSeconds() * 1000.0);
        context.setDefaultNavigationTimeout(settings.pageTimeoutSeconds() * 1000.0);

        // The persistent profile can contain pages restored from a crashed
        // Chrome session. Let Chrome finish creating its startup targets,
        // then keep exactly one newly-created collector page.
        this.primaryPage = context.newPage();
        String primaryPageMarker = "job-seeker-collector-" + UUID.randomUUID();
        primaryPage.evaluate(
                "marker => { window.name = marker; }",
                primaryPageMarker
        );
        waitForStartupPagesToStabilize(primaryPage);
        closeEveryPageExcept(primaryPageMarker);
        primaryPage.waitForTimeout(250);
        closeEveryPageExcept(primaryPageMarker);
        List<Page> remainingPages = context.pages().stream()
                .filter(page -> !page.isClosed())
                .toList();
        if (remainingPages.size() != 1
                || !hasPageMarker(remainingPages.get(0), primaryPageMarker)) {
            throw new IllegalStateException(
                    "Could not isolate one collector-owned browser page"
            );
        }
        primaryPage.bringToFront();
    }

    public Page primaryPage() {
        return primaryPage;
    }

    public Page newPage() {
        return context.newPage();
    }

    private void waitForStartupPagesToStabilize(Page clockPage) {
        int previousCount = -1;
        int unchangedPolls = 0;
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline && unchangedPolls < 4) {
            int currentCount = context.pages().size();
            unchangedPolls = currentCount == previousCount ? unchangedPolls + 1 : 0;
            previousCount = currentCount;
            clockPage.waitForTimeout(250);
        }
    }

    private void closeEveryPageExcept(String retainedPageMarker) {
        for (Page candidate : List.copyOf(context.pages())) {
            if (!candidate.isClosed() && !hasPageMarker(candidate, retainedPageMarker)) {
                candidate.close();
            }
        }
    }

    private boolean hasPageMarker(Page page, String expectedMarker) {
        if (page.isClosed()) {
            return false;
        }
        Object marker = page.evaluate("() => window.name");
        return expectedMarker.equals(String.valueOf(marker));
    }

    public boolean isAuthenticated(Page page) {
        if (isLoginWall(page)) {
            return false;
        }
        return page.locator(
                "[data-test-global-nav-link='jobs'], "
                        + "a[href*='/mynetwork/'], "
                        + "a[href*='/messaging/'], "
                        + ".global-nav"
        ).count() > 0;
    }

    public boolean isLoginWall(Page page) {
        String url = page.url().toLowerCase(Locale.ROOT);
        if (url.contains("/login") || url.contains("/authwall")) {
            return true;
        }
        return page.locator(
                "input[name='session_key'], form[action*='login']"
        ).count() > 0;
    }

    @Override
    public void close() {
        context.close();
        playwright.close();
    }
}
