package com.seeker.collector.linkedin.browser;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.seeker.applier.shared.ManagedBrowser;

import java.io.IOException;
import java.nio.file.Path;

/** Uses the shared browser and owns only tabs opened by this collector run. */
public final class PersistentLinkedInSession implements AutoCloseable {
    private final ManagedBrowser managedBrowser;
    private final BrowserContext context;
    private final Page primaryPage;

    public PersistentLinkedInSession(Path profileDirectory, BrowserSettings settings)
            throws IOException {
        this.managedBrowser = ManagedBrowser.connect(profileDirectory, settings.channel());
        this.context = managedBrowser.context();
        context.setDefaultTimeout(settings.pageTimeoutSeconds() * 1000.0);
        context.setDefaultNavigationTimeout(settings.pageTimeoutSeconds() * 1000.0);
        this.primaryPage = managedBrowser.newPage();
        primaryPage.bringToFront();
    }

    public Page primaryPage() {
        return primaryPage;
    }

    public Page newPage() {
        return managedBrowser.newPage();
    }

    public boolean isAuthenticated(Page page) {
        return !isLoginWall(page) && page.locator(
                "[data-test-global-nav-link='jobs'], "
                        + "a[href*='/mynetwork/'], "
                        + "a[href*='/messaging/'], "
                        + ".global-nav"
        ).count() > 0;
    }

    public boolean isLoginWall(Page page) {
        String url = page.url().toLowerCase(java.util.Locale.ROOT);
        if (url.contains("/login") || url.contains("/authwall")) {
            return true;
        }
        return page.locator("input[name='session_key'], form[action*='login']").count() > 0;
    }

    @Override
    public void close() {
        managedBrowser.close();
    }
}
