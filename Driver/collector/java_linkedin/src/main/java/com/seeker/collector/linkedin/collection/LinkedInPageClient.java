package com.seeker.collector.linkedin.collection;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import com.seeker.collector.linkedin.browser.AuthenticationRequiredException;
import com.seeker.collector.linkedin.browser.PersistentLinkedInSession;
import com.seeker.collector.linkedin.config.CollectorConfig;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class LinkedInPageClient {
    private static final String LINKEDIN_JOBS = "https://www.linkedin.com/jobs/";
    private static final int EXPECTED_PAGE_SIZE = 25;
    private static final String SEARCH_DETAILS_SELECTOR = String.join(", ",
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
    private static final String LAYOUT_SCRIPT = """
            () => {
              const visible = element => !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
              const classicPanels = [...document.querySelectorAll('.scaffold-layout__list > div')]
                .filter(panel => visible(panel) && panel.querySelector('[data-occludable-job-id]'));
              const lazyColumns = [...document.querySelectorAll('[data-testid="lazy-column"]')]
                .filter(column => visible(column) && column.querySelector('[role="button"][componentkey^="job-card-component-ref-"]'));
              if (classicPanels.length === 1 && lazyColumns.length === 0) {
                const cards = [...classicPanels[0].querySelectorAll('[data-occludable-job-id]')];
                return {
                  layout: 'classic',
                  panelCount: classicPanels.length,
                  rawCount: cards.length,
                  ids: cards.map(card => String(card.getAttribute('data-occludable-job-id') || '').trim())
                };
              }
              if (lazyColumns.length === 1 && classicPanels.length === 0) {
                const cards = [...lazyColumns[0].querySelectorAll('[role="button"][componentkey^="job-card-component-ref-"]')];
                return {
                  layout: 'lazy',
                  panelCount: lazyColumns.length,
                  rawCount: cards.length,
                  ids: cards.map(card => String(card.getAttribute('componentkey') || '')
                    .replace(/^job-card-component-ref-/, '').trim())
                };
              }
              return {
                layout: '',
                panelCount: classicPanels.length + lazyColumns.length,
                rawCount: 0,
                ids: []
              };
            }
            """;
    private static final String PREVIEW_SCRIPT = """
            (element, jobId) => {
              const clean = value => String(value || '').replace(/\\s+/g, ' ').trim();
              const text = selector => {
                const node = element.querySelector(selector);
                return node ? clean(node.innerText || node.textContent) : '';
              };
              const first = selectors => {
                for (const selector of selectors) {
                  const value = text(selector);
                  if (value) return value;
                }
                return '';
              };
              const ignored = /^(promoted|viewed|easy apply|actively reviewing|be an early applicant)$/i;
              const lines = String(element.innerText || '').split(/\\r?\\n/)
                .map(clean).filter(value => value && !ignored.test(value));
              let title = first([
                '[data-view-name="job-card-title"]',
                'a[href*="/jobs/view/"] strong',
                '.job-card-list__title',
                '[class*="job-card"][class*="title"]'
              ]);
              let company = first([
                '.artdeco-entity-lockup__subtitle',
                '.job-card-container__primary-description',
                '[class*="primary-description"]',
                '[class*="subtitle"]'
              ]);
              let location = first([
                '.job-card-container__metadata-item',
                '.artdeco-entity-lockup__caption',
                '[class*="metadata-item"]',
                '[class*="caption"]'
              ]);
              if (!title && lines.length) title = lines[0];
              if (!company && lines.length > 1) company = lines[1];
              if (!location && lines.length > 2) location = lines[2];
              const allText = clean(element.innerText || '');
              const workplaceMatch = allText.match(/\\b(remote|hybrid|on-site)\\b/i);
              const salaryMatch = allText.match(/(?:[$€£]\\s?[\\d,.]+(?:\\s*[-–]\\s*[$€£]?\\s?[\\d,.]+)?[^\\n]*)/);
              return {
                jobId: String(jobId),
                title,
                company,
                location,
                workplace: workplaceMatch ? workplaceMatch[1].toLowerCase() : '',
                salary: salaryMatch ? clean(salaryMatch[0]) : ''
              };
            }
            """;
    private static final String DETAIL_PREVIEW_SCRIPT = """
            root => {
              const clean = value => String(value || '').replace(/\\s+/g, ' ').trim();
              const first = selectors => {
                for (const selector of selectors) {
                  const node = root.querySelector(selector) || document.querySelector(selector);
                  const value = node ? clean(node.innerText || node.textContent) : '';
                  if (value) return value;
                }
                return '';
              };
              return {
                title: first([
                  '.job-details-jobs-unified-top-card__job-title h1',
                  '.jobs-unified-top-card__job-title',
                  'h1'
                ]),
                company: first([
                  '.job-details-jobs-unified-top-card__company-name',
                  '.jobs-unified-top-card__company-name',
                  'a[href*="/company/"]'
                ]),
                location: first([
                  '.job-details-jobs-unified-top-card__tertiary-description-container',
                  '.jobs-unified-top-card__bullet',
                  '[class*="tertiary-description"]'
                ])
              };
            }
            """;

    enum Layout {
        CLASSIC,
        LAZY
    }

    record CardData(
            String jobId,
            String title,
            String company,
            String location,
            String workplace,
            String salary
    ) {
        Preview preview(String label, int start, int index) {
            return new Preview(
                    jobId,
                    canonicalUrl(jobId),
                    title,
                    company,
                    location,
                    workplace,
                    salary,
                    label,
                    start,
                    index
            );
        }
    }

    record MaterializedPage(
            Layout layout,
            List<CardData> cards,
            int expectedCount,
            int materializedCount,
            boolean terminal
    ) {
    }

    record DirectJob(String html, String title, String company, String location) {
    }

    private record LayoutSnapshot(Layout layout, int rawCount, List<String> ids) {
    }

    private final PersistentLinkedInSession session;
    private final Page page;
    private final CollectorConfig config;
    private final NavigationThrottle throttle;

    LinkedInPageClient(
            PersistentLinkedInSession session,
            CollectorConfig config
    ) {
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
        throttle.beforeAction(page);
        page.locator("button.jobs-search-box__submit-button").click();
        page.waitForTimeout(750);
    }

    String openSearch(SearchPlan.Target target, int start) {
        String url = target.pageUrl(start);
        navigate(page, url);
        requireAuthenticated("LinkedIn session is no longer authenticated");
        verifyQuery(url, page.url());
        return url;
    }

    MaterializedPage materializePage() {
        List<String> previous = List.of();
        LayoutSnapshot stable = null;
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            LayoutSnapshot current = readLayoutSnapshot();
            if (current == null || current.ids().isEmpty()) {
                // A missing Next button is normal while the search UI is still
                // mounting. Only an explicit empty/end message can prove an
                // empty terminal page before any card IDs have appeared.
                if (hasExplicitTerminalText()) {
                    return new MaterializedPage(null, List.of(), 0, 0, true);
                }
            } else if (current.ids().equals(previous)) {
                stable = current;
                break;
            } else {
                previous = current.ids();
            }
            page.waitForTimeout(500);
        }
        if (stable == null) {
            throw new CollectionBlockedException(
                    "LinkedIn search result card IDs did not stabilize"
            );
        }
        validateIds(stable);

        Map<String, CardData> cards = new LinkedHashMap<>();
        LayoutSnapshot current = stable;
        int unchanged = 0;
        long materializationDeadline = System.nanoTime()
                + Duration.ofSeconds(30).toNanos();
        while (cards.size() < EXPECTED_PAGE_SIZE
                && unchanged < 3
                && System.nanoTime() < materializationDeadline) {
            validateIds(current);
            int previousCount = cards.size();
            readVisibleCards(current, cards);
            unchanged = cards.size() == previousCount ? unchanged + 1 : 0;
            if (cards.size() >= EXPECTED_PAGE_SIZE) {
                break;
            }
            scrollToLastCard(current);
            page.waitForTimeout(500);
            LayoutSnapshot next = readLayoutSnapshot();
            if (next != null) {
                if (next.layout() != current.layout()) {
                    unchanged = 0;
                }
                current = next;
            }
        }
        boolean terminal = isTerminalPage();
        if (cards.size() < EXPECTED_PAGE_SIZE && !terminal) {
            throw new CollectionBlockedException(
                    "LinkedIn page materialized " + cards.size()
                            + " of " + EXPECTED_PAGE_SIZE + " cards"
            );
        }
        return new MaterializedPage(
                current.layout(),
                List.copyOf(cards.values()),
                terminal ? cards.size() : EXPECTED_PAGE_SIZE,
                cards.size(),
                terminal
        );
    }

    private void readVisibleCards(
            LayoutSnapshot snapshot,
            Map<String, CardData> cards
    ) {
        for (String jobId : snapshot.ids()) {
            if (cards.containsKey(jobId)) {
                continue;
            }
            Locator card = cardLocator(snapshot.layout(), jobId);
            if (card.count() != 1) {
                throw new CollectionBlockedException(
                        "Expected one card for " + jobId + ", found " + card.count()
                );
            }
            card.evaluate(
                    "element => element.scrollIntoView({block:'center', inline:'nearest', behavior:'instant'})"
            );
            cards.put(jobId, waitForCardData(snapshot.layout(), card, jobId));
        }
    }

    private void scrollToLastCard(LayoutSnapshot snapshot) {
        if (snapshot.ids().isEmpty()) {
            return;
        }
        String jobId = snapshot.ids().get(snapshot.ids().size() - 1);
        cardLocator(snapshot.layout(), jobId).evaluate(
                "element => element.scrollIntoView({block:'end', inline:'nearest', behavior:'instant'})"
        );
    }

    String openCard(MaterializedPage materializedPage, String jobId) {
        Locator card = cardLocator(materializedPage.layout(), jobId);
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
        Locator details = waitForDetails(page, jobId, true);
        return wrapHtml(jobId, outerHtml(details));
    }

    DirectJob openDirect(String sourceUrl, String jobId) {
        Page direct = session.newPage();
        try {
            navigate(direct, sourceUrl);
            if (session.isLoginWall(direct)) {
                throw new AuthenticationRequiredException(
                        "LinkedIn session is no longer authenticated"
                );
            }
            Locator details = waitForDetails(direct, jobId, false);
            Map<String, Object> fields = stringObjectMap(details.evaluate(DETAIL_PREVIEW_SCRIPT));
            return new DirectJob(
                    wrapHtml(jobId, outerHtml(details)),
                    text(fields.get("title")),
                    text(fields.get("company")),
                    text(fields.get("location"))
            );
        } finally {
            direct.close();
        }
    }

    private CardData waitForCardData(Layout layout, Locator card, String jobId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        CardData latest = null;
        while (System.nanoTime() < deadline) {
            Map<String, Object> value = stringObjectMap(card.evaluate(PREVIEW_SCRIPT, jobId));
            latest = new CardData(
                    jobId,
                    text(value.get("title")),
                    text(value.get("company")),
                    text(value.get("location")),
                    text(value.get("workplace")),
                    text(value.get("salary"))
            );
            boolean ready;
            if (layout == Layout.CLASSIC) {
                ready = card.locator("a[href*='/jobs/view/" + jobId + "']").count() > 0
                        && !latest.title().isBlank();
            } else {
                ready = !latest.title().isBlank()
                        && !latest.company().isBlank()
                        && !latest.location().isBlank();
            }
            if (ready) {
                return latest;
            }
            page.waitForTimeout(100);
        }
        throw new CollectionBlockedException(
                "Card " + jobId + " did not materialize; last preview=" + latest
        );
    }

    private Locator waitForDetails(Page targetPage, String jobId, boolean requireSelection) {
        long deadline = System.nanoTime()
                + Duration.ofSeconds(config.detailsTimeoutSeconds()).toNanos();
        while (System.nanoTime() < deadline) {
            if (session.isLoginWall(targetPage)) {
                throw new AuthenticationRequiredException(
                        "LinkedIn session is no longer authenticated"
                );
            }
            String selector = requireSelection
                    ? SEARCH_DETAILS_SELECTOR
                    : DIRECT_DETAILS_SELECTOR;
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
            targetPage.waitForTimeout(250);
        }
        throw new DetailsNotReadyException(
                "LinkedIn job details were not ready for " + jobId
        );
    }

    private boolean selectionMatches(Page targetPage, Locator pane, String jobId) {
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

    private Locator cardLocator(Layout layout, String jobId) {
        if (layout == Layout.CLASSIC) {
            return page.locator("[data-occludable-job-id='" + jobId + "']");
        }
        return page.locator(
                "[data-testid='lazy-column'] [role='button']"
                        + "[componentkey='job-card-component-ref-" + jobId + "']"
        );
    }

    private LayoutSnapshot readLayoutSnapshot() {
        Map<String, Object> result = stringObjectMap(page.evaluate(LAYOUT_SCRIPT));
        String layoutName = text(result.get("layout"));
        if (layoutName.isBlank()) {
            return null;
        }
        Layout layout = "classic".equals(layoutName) ? Layout.CLASSIC : Layout.LAZY;
        int rawCount = ((Number) result.get("rawCount")).intValue();
        List<?> values = (List<?>) result.get("ids");
        List<String> ids = values.stream().map(String::valueOf).toList();
        return new LayoutSnapshot(layout, rawCount, ids);
    }

    private void validateIds(LayoutSnapshot snapshot) {
        Set<String> unique = new LinkedHashSet<>(snapshot.ids());
        if (snapshot.rawCount() != snapshot.ids().size()
                || unique.size() != snapshot.ids().size()) {
            throw new CollectionBlockedException(
                    "LinkedIn page has duplicate or inconsistent card IDs"
            );
        }
        List<String> invalid = snapshot.ids().stream()
                .filter(id -> !id.matches("\\d+"))
                .toList();
        if (!invalid.isEmpty()) {
            throw new CollectionBlockedException("Malformed LinkedIn job IDs: " + invalid);
        }
    }

    private boolean isTerminalPage() {
        if (hasExplicitTerminalText()) {
            return true;
        }
        Locator next = page.locator(
                "button[aria-label='View next page'], button:has-text('View next page')"
        );
        if (next.count() == 0) {
            return true;
        }
        Locator button = next.last();
        return button.isDisabled()
                || "true".equalsIgnoreCase(button.getAttribute("aria-disabled"));
    }

    private boolean hasExplicitTerminalText() {
        String body = page.locator("body").innerText().toLowerCase(Locale.ROOT);
        return body.contains("no matching jobs found")
                || body.contains("no jobs found")
                || body.contains("you've viewed all jobs")
                || body.contains("end of results");
    }

    private void verifyQuery(String requestedUrl, String actualUrl) {
        Map<String, String> expected = queryParameters(requestedUrl);
        Map<String, String> actual = queryParameters(actualUrl);
        for (String key : List.of("f_E", "f_WT", "f_JT", "f_TPR", "sortBy", "f_C")) {
            String value = expected.get(key);
            if (value != null && !value.equals(actual.get(key))) {
                throw new CollectionBlockedException(
                        "LinkedIn did not retain " + key + "=" + value
                                + "; actual URL is " + actualUrl
                );
            }
        }
    }

    private Map<String, String> queryParameters(String url) {
        Map<String, String> result = new HashMap<>();
        String query = URI.create(url).getRawQuery();
        if (query == null || query.isBlank()) {
            return result;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length == 1
                            ? ""
                            : URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
            );
        }
        return result;
    }

    private void navigate(Page targetPage, String url) {
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
        throw new AuthenticationRequiredException(message);
    }

    private String outerHtml(Locator locator) {
        return String.valueOf(locator.evaluate("element => element.outerHTML"));
    }

    private String wrapHtml(String jobId, String detailsHtml) {
        String url = canonicalUrl(jobId);
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

    static String canonicalUrl(String jobId) {
        return "https://www.linkedin.com/jobs/view/" + jobId + "/";
    }

    static String jobId(String url) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("/jobs/view/(\\d+)")
                .matcher(url);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Not a canonical LinkedIn job URL: " + url);
        }
        return matcher.group(1);
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
