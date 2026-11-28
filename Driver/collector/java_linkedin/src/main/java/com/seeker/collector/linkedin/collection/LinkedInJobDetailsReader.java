package com.seeker.collector.linkedin.collection;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.seeker.browser.LinkedInJobPage;
import com.seeker.collector.linkedin.config.CollectorConfig;
import com.seeker.logging.LogOutput;

import java.time.Duration;
import java.util.Map;

final class LinkedInJobDetailsReader {
    private static final String SEARCH_DETAILS_SELECTOR = String.join(", ",
            "[data-testid='lazy-column']:has([id^='JobDetails_AboutTheJob_'])"
                    + ":not(:has([data-testid='lazy-column'] [id^='JobDetails_AboutTheJob_']))",
            "[data-testid='job-details']",
            "[data-view-name='job-details']",
            ".jobs-search__job-details--container",
            ".scaffold-layout__detail",
            ".jobs-details"
    );
    private static final String DIRECT_DETAILS_SELECTOR = String.join(", ",
            SEARCH_DETAILS_SELECTOR,
            "article.jobs-description__container",
            "main"
    );
    private static final String DETAIL_PREVIEW_SCRIPT = """
            root => {
              const clean = value => String(value || '').replace(/\\s+/g, ' ').trim();
              const first = selectors => {
                for (const selector of selectors) {
                  const node = root.querySelector(selector);
                  const value = node ? clean(node.innerText || node.textContent) : '';
                  if (value) return value;
                }
                return '';
              };
              // The direct-page layout puts company and title in consecutive paragraphs.
              const header = root.querySelector('[id^="JobDetails_ManageJobBanner_"]')?.nextElementSibling || root;
              const paragraphs = [...header.querySelectorAll('p')];
              const companyIndex = paragraphs.findIndex(p => p.querySelector('a[href*="/company/"]'));
              const titleParagraph = paragraphs.find(p => p.querySelector('a[href*="/jobs/view/"]'));
              const titleIndex = titleParagraph ? paragraphs.indexOf(titleParagraph) : companyIndex + 1;
              return {
                title: first([
                  '.job-details-jobs-unified-top-card__job-title h1',
                  '.jobs-unified-top-card__job-title',
                  'h1',
                  'p a[href*="/jobs/view/"]'
                ]) || (companyIndex >= 0 ? clean(paragraphs[titleIndex]?.textContent) : ''),
                company: first([
                  '.job-details-jobs-unified-top-card__company-name',
                  '.jobs-unified-top-card__company-name',
                  'p a[href*="/company/"]',
                  'a[href*="/company/"]'
                ]) || clean(paragraphs[companyIndex]?.textContent),
                location: companyIndex >= 0 ? clean(paragraphs[titleIndex + 1]?.textContent).split('·')[0].trim() : ''
              };
            }
            """;

    record JobDetails(String html, String title, String company, String location, boolean easyApply) {
    }

    private final Page page;
    private final LinkedInPageClient browser;
    private final CollectorConfig config;

    LinkedInJobDetailsReader(Page page, LinkedInPageClient browser, CollectorConfig config) {
        this.page = page;
        this.browser = browser;
        this.config = config;
    }

    JobDetails readSearch(String jobId) {
        return readDetails(waitForDetails(page, jobId, true), jobId);
    }

    @LogOutput(include = {"#jobId", "title"})
    JobDetails readDirect(Page direct, String jobId) {
        return readDetails(waitForDetails(direct, jobId, false), jobId);
    }

    private Locator waitForDetails(Page targetPage, String jobId, boolean requireSelection) {
        long deadline = System.nanoTime()
                + Duration.ofSeconds(config.detailsTimeoutSeconds()).toNanos();
        while (System.nanoTime() < deadline) {
            browser.requireNoLoginWall(targetPage);
            Locator details = findReadyDetails(targetPage, jobId, requireSelection);
            if (details != null) {
                return details;
            }
            targetPage.waitForTimeout(250);
        }
        throw new DetailsNotReadyException(
                "LinkedIn job details were not ready for " + jobId
        );
    }

    private Locator findReadyDetails(Page targetPage, String jobId, boolean requireSelection) {
        String selector = requireSelection ? SEARCH_DETAILS_SELECTOR : DIRECT_DETAILS_SELECTOR;
        Locator candidates = targetPage.locator(selector);
        for (int index = 0; index < candidates.count(); index++) {
            Locator candidate = candidates.nth(index);
            if (!candidate.isVisible()) {
                continue;
            }
            String body = candidate.innerText();
            if (!hasDetailMarker(candidate, body) || hasVisibleProgress(candidate)) {
                continue;
            }
            if (!requireSelection || selectionMatches(targetPage, candidate, jobId)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean selectionMatches(Page targetPage, Locator pane, String jobId) {
        if (pane.locator("[id^='JobDetails_AboutTheJob_']").count() > 0) {
            return pane.locator("[id='JobDetails_AboutTheJob_" + jobId + "']").count() > 0
                    && pane.locator("a[href*='/jobs/view/" + jobId + "/']").count() > 0;
        }
        if (targetPage.url().contains("currentJobId=" + jobId)
                || targetPage.url().contains("/jobs/view/" + jobId)) {
            return true;
        }
        return pane.locator("a[href*='/jobs/view/" + jobId + "']").count() > 0;
    }

    private boolean hasVisibleProgress(Locator pane) {
        Locator progress = pane.locator("[role='progressbar'], [aria-label='In progress']");
        for (int index = 0; index < progress.count(); index++) {
            if (progress.nth(index).isVisible()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDetailMarker(Locator pane, String text) {
        return text.contains("About the job")
                || text.contains("Role Overview")
                || text.contains("Requirements")
                || text.contains("Key Responsibilities")
                || pane.locator(".show-more-less-html__markup").count() > 0;
    }

    private JobDetails readDetails(Locator details, String jobId) {
        Map<String, Object> fields = stringObjectMap(details.evaluate(DETAIL_PREVIEW_SCRIPT));
        String location = LinkedInJobPage.location(details);
        if (location.isBlank()) location = text(fields.get("location"));
        return new JobDetails(wrapHtml(jobId, outerHtml(details)), text(fields.get("title")),
                text(fields.get("company")), location,
                LinkedInJobPage.isEasyApply(details));
    }

    private String outerHtml(Locator locator) {
        return String.valueOf(locator.evaluate("element => element.outerHTML"));
    }

    private String wrapHtml(String jobId, String detailsHtml) {
        String url = LinkedInUrls.canonicalUrl(jobId);
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<link rel=\"canonical\" href=\"" + escapeAttribute(url) + "\">"
                + "</head><body>" + detailsHtml + "</body></html>";
    }

    private String escapeAttribute(String value) {
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> stringObjectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new CollectionBlockedException("Browser script returned a non-object value");
        }
        return (Map<String, Object>) map;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
