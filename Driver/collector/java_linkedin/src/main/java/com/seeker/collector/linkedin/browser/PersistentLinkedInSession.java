package com.seeker.collector.linkedin.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

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
                        .setViewportSize(1440, 1000)
                        .setTimeout(settings.pageTimeoutSeconds() * 1000.0);
        if (settings.channel() != null && !settings.channel().isBlank()) {
            launch.setChannel(settings.channel());
        }

        this.context = playwright.chromium().launchPersistentContext(
                profileDirectory,
                launch
        );
        context.setDefaultTimeout(settings.pageTimeoutSeconds() * 1000.0);
        context.setDefaultNavigationTimeout(settings.pageTimeoutSeconds() * 1000.0);
        this.primaryPage = context.pages().isEmpty()
                ? context.newPage()
                : context.pages().get(0);
    }

    public Page primaryPage() {
        return primaryPage;
    }

    public Page newPage() {
        return context.newPage();
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
