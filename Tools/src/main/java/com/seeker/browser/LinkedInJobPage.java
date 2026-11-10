package com.seeker.browser;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.Map;

/** Vacancy fields read from the open LinkedIn page by collector and Applier. */
public final class LinkedInJobPage {
    public static final String EXTERNAL_APPLY = "#jobs-apply-button-id[aria-label*='on company website' i], "
            + "a[aria-label*='on company website' i], button[aria-label*='on company website' i]";

    /** A single bounded read: spinner/advertisement changes are deliberately not progress. */
    public static Map<?, ?> snapshot(Page page) {
        return (Map<?, ?>) BrowserInteraction.read(page, """
            () => {
              const visible = n => n.getClientRects().length && getComputedStyle(n).visibility !== 'hidden';
              const find = s => [...document.querySelectorAll(s)].find(visible);
              const text = document.body?.innerText || '';
              const description = ['.show-more-less-html__markup', '.description__text', 'article.jobs-description__container',
                '[data-testid="job-details"]', '[data-view-name="job-details"]', '.jobs-details', 'main']
                .map(find).find(Boolean);
              const title = find('h1')?.innerText.trim() || '';
              const location = ['.job-details-jobs-unified-top-card__tertiary-description-container',
                '.jobs-unified-top-card__bullet', '[class*="tertiary-description"]']
                .map(selector => find(selector)?.innerText.replace(/\\s+/g, ' ').trim()).find(Boolean) || '';
              const loading = !!description && [...description.querySelectorAll('[role="progressbar"], [aria-label="In progress"]')].some(visible);
              return {title, location, text, document: document.readyState,
                description: !!description?.innerText.trim() && !loading && (!description.matches('main')
                  || /about the job|role overview|requirements|key responsibilities/i.test(description.innerText)),
                auth: /\\/(login|authwall|checkpoint)(\\/|$)/i.test(locationPath())
                  || !!find("input[name='session_key'], form[action*='login']"),
                closed: /no longer accepting applications|this job is no longer available/i.test(text),
                easy: !!find("#jobs-apply-button-id[aria-label^='Easy Apply' i], button[aria-label^='Easy Apply' i], a[aria-label^='Easy Apply' i]"),
                apply: !!find("#jobs-apply-button-id[aria-label*='on company website' i], a[aria-label*='on company website' i], button[aria-label*='on company website' i]")};
              function locationPath() { return window.location.pathname; }
            }
            """, null);
    }

    private LinkedInJobPage() { }

    public static boolean isEasyApply(Locator root) {
        return root.locator("#jobs-apply-button-id[aria-label^='Easy Apply' i], "
                + "button[aria-label^='Easy Apply' i], a[aria-label^='Easy Apply' i]").count() > 0;
    }

    public static String location(Locator root) {
        for (String selector : new String[]{
                ".job-details-jobs-unified-top-card__tertiary-description-container",
                ".jobs-unified-top-card__bullet",
                "[class*='tertiary-description']"
        }) {
            Locator field = root.locator(selector).first();
            if (field.count() > 0) {
                String text = field.innerText().replaceAll("\\s+", " ").strip();
                if (!text.isEmpty()) return text;
            }
        }
        return "";
    }
}
