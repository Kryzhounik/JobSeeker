package com.seeker.applier.shared;

import com.microsoft.playwright.Locator;

/** Vacancy fields read from the open LinkedIn page by collector and Applier. */
public final class LinkedInJobPage {
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
