package com.seeker.collector.linkedin.collection;

import lombok.extern.slf4j.Slf4j;

import com.seeker.collector.common.Preview;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.seeker.collector.linkedin.config.CollectorConfig;
import com.seeker.collector.common.JsonSupport;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.seeker.collector.linkedin.collection.LinkedInUrls.canonicalUrl;

@Slf4j
final class LinkedInSearchResults {
    private static final int EXPECTED_PAGE_SIZE = 25;
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
    private static final String PAGINATION_SCRIPT = """
            () => {
              const visible = element => !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
              const containers = [...document.querySelectorAll([
                '[data-testid="pagination-controls-list"]',
                '.artdeco-pagination',
                '.jobs-search-pagination',
                'nav[aria-label*="pagination" i]'
              ].join(','))].filter(visible);
              if (!containers.length) {
                return {present: false, currentPage: 0, lastPage: 0};
              }
              const container = containers[containers.length - 1];
              const controls = [...container.querySelectorAll('button, a')].filter(visible);
              const pageNumber = element => {
                const label = String(element.getAttribute('aria-label') || '').trim();
                const value = String(element.textContent || '').replace(/\s+/g, ' ').trim();
                const match = label.match(/(\\d+)/) || value.match(/^(\\d+)$/);
                return match ? Number(match[1]) : 0;
              };
              const pages = controls.map(pageNumber).filter(value => Number.isInteger(value) && value > 0);
              const current = controls.find(element =>
                element.matches('[aria-current="page"], [aria-current="true"], [aria-pressed="true"]')
                || element.matches('.active, .selected, [class*="--active"], [class*="--selected"]')
                || !!element.closest('.active, .selected, [class*="--active"], [class*="--selected"]')
              );
              return {
                present: true,
                currentPage: current ? pageNumber(current) : 0,
                lastPage: pages.length ? Math.max(...pages) : 0
              };
            }
            """;
    private static final String RESULT_COUNT_SCRIPT = """
            () => {
              const visible = element => !!(element.offsetWidth || element.offsetHeight || element.getClientRects().length);
              const preferred = [...document.querySelectorAll([
                '[componentkey="searchResultsHeaderComponent"] p',
                '.jobs-search-results-list__title-heading',
                '.jobs-search-results-list__subtitle',
                '.jobs-search-results-list__text'
              ].join(','))].filter(visible);
              const list = [...document.querySelectorAll('.scaffold-layout__list')].find(visible);
              const fallback = list
                ? [...list.querySelectorAll('small, span')].filter(visible)
                : [];
              for (const element of [...preferred, ...fallback]) {
                const value = String(element.innerText || element.textContent || '')
                  .replace(/\\u00a0/g, ' ').trim();
                const match = value.match(/(?:^|\\n|\\s)([\\d][\\d\\s,.]*)\\s+results?\\b/i);
                if (!match) continue;
                const digits = match[1].replace(/\\D/g, '');
                if (digits) return {present: true, total: Number(digits)};
              }
              return {present: false, total: 0};
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
              // The new card's first three paragraphs are title, company, and location.
              const paragraphs = [...element.querySelectorAll('p')].map(p => clean(p.textContent));
              const lazy = element.matches('[componentkey^="job-card-component-ref-"]');
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
              if (lazy) {
                const titleParagraph = element.querySelector('p');
                title = clean((titleParagraph?.querySelector('[aria-hidden="true"]') || titleParagraph)?.textContent);
                company = paragraphs[1] || '';
                location = paragraphs[2] || '';
              } else {
                if (!title && lines.length) title = lines[0];
                if (!company && lines.length > 1) company = lines[1];
                if (!location && lines.length > 2) location = lines[2];
              }
              const panel = element.closest('[componentkey="SearchResultsMainContent"]');
              const relatedBanner = panel && [...panel.querySelectorAll('p')].find(p =>
                /We found more results related to your search|We had trouble finding exact matches/i.test(p.textContent));
              const related = !!relatedBanner && !!(relatedBanner.compareDocumentPosition(element) & Node.DOCUMENT_POSITION_FOLLOWING);
              const allText = clean(element.innerText || '');
              const workplaceMatch = allText.match(/\\b(remote|hybrid|on-site)\\b/i);
              const salaryMatch = allText.match(/(?:[$€£]\\s?[\\d,.]+(?:\\s*[-–]\\s*[$€£]?\\s?[\\d,.]+)?[^\\n]*)/);
              return {
                jobId: String(jobId),
                title,
                company,
                location,
                workplace: workplaceMatch ? workplaceMatch[1].toLowerCase() : '',
                salary: salaryMatch ? clean(salaryMatch[0]) : '',
                related
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
            String salary,
            boolean related
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
            Integer totalResults,
            int materializedCount,
            String actualUrl,
            int scrollIterations,
            int unchangedIterations,
            String cardIdsHash,
            boolean terminal,
            String terminalReason,
            int nextCount,
            boolean nextVisible,
            boolean nextDisabled,
            String nextAriaDisabled,
            String nextLabel
    ) {
    }

    private record LayoutSnapshot(Layout layout, int rawCount, List<String> ids) {
    }

    private record PaginationState(boolean present, int currentPage, int lastPage) {
        boolean isLastPage() {
            return present && currentPage > 0 && currentPage == lastPage;
        }
    }

    private record ResultCountState(
            boolean present,
            int total,
            int expectedCount,
            boolean lastPage
    ) {
    }

    private record TerminalState(
            boolean terminal,
            String reason,
            int nextCount,
            boolean nextVisible,
            boolean nextDisabled,
            String nextAriaDisabled,
            String nextLabel
    ) {
    }

    private final Page page;
    private final CollectorConfig config;

    LinkedInSearchResults(Page page, CollectorConfig config) {
        this.page = page;
        this.config = config;
    }

    MaterializedPage materializePage(int start) {
        List<String> previous = List.of();
        LayoutSnapshot stable = null;
        ResultCountState resultCount = missingResultCount();
        boolean resultCountChecked = false;
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            LayoutSnapshot current = readLayoutSnapshot();
            if (current == null || current.ids().isEmpty()) {
                MaterializedPage emptyPage = readEmptyPage(start);
                if (emptyPage != null) {
                    return emptyPage;
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
        int scrollIterations = 0;
        long materializationDeadline = System.nanoTime()
                + Duration.ofSeconds(30).toNanos();
        TerminalState observedTerminal = terminalState();
        while (cards.size() < expectedCount(resultCount)
                && System.nanoTime() < materializationDeadline) {
            validateIds(current);
            int previousCount = cards.size();
            readVisibleCards(current, cards);
            unchanged = cards.size() == previousCount ? unchanged + 1 : 0;
            if (!resultCountChecked) {
                resultCount = stableResultCountState(start, cards.size());
                resultCountChecked = true;
            }
            if (cards.size() >= expectedCount(resultCount)) {
                break;
            }
            observedTerminal = terminalState();
            if (observedTerminal.terminal() && !resultCount.present()) {
                break;
            }
            // Keep giving a slow page time to materialize. Re-scroll only
            // periodically while nothing changes instead of declaring the
            // location finished after three 500 ms polls.
            if (unchanged == 0 || unchanged % 4 == 0) {
                scrollIterations++;
                scrollToLastCard(current);
            }
            page.waitForTimeout(500);
            LayoutSnapshot next = readLayoutSnapshot();
            if (next != null) {
                if (next.layout() != current.layout()) {
                    unchanged = 0;
                }
                current = next;
            }
        }
        TerminalState terminalState = observedTerminal.terminal()
                ? observedTerminal
                : terminalState();
        int expectedCount = expectedCount(resultCount);
        if (resultCount.present()) {
            if (cards.size() < expectedCount) {
                terminalState = terminalState(terminalState, false, "incomplete_page");
            } else if (resultCount.lastPage()) {
                terminalState = terminalState(
                        terminalState,
                        true,
                        "result_count_exhausted"
                );
            } else {
                terminalState = terminalState(terminalState, false, "");
            }
        } else if (cards.size() < EXPECTED_PAGE_SIZE && !terminalState.terminal()) {
            terminalState = terminalState(terminalState, false, "unknown_short_page");
        }
        return materializedPage(
                current.layout(),
                cards,
                expectedCount,
                resultCount.present() ? resultCount.total() : null,
                scrollIterations,
                unchanged,
                terminalState
        );
    }

    private MaterializedPage readEmptyPage(int start) {
        // A missing Next button is normal while the search UI is mounting.
        TerminalState emptyState = terminalState();
        ResultCountState count = resultCountState(start);
        boolean explicitEnd = emptyState.terminal() && "explicit_end_text".equals(emptyState.reason());
        if (!explicitEnd && !(count.present() && count.expectedCount() == 0)) {
            return null;
        }
        ResultCountState emptyCount = stableResultCountState(start, 0);
        if (!explicitEnd && (!emptyCount.present() || emptyCount.expectedCount() != 0)) {
            return null;
        }
        return materializedPage(
                null,
                Map.of(),
                0,
                emptyCount.present() ? emptyCount.total() : null,
                0,
                0,
                terminalState(emptyState, true, "result_count_exhausted")
        );
    }

    private MaterializedPage materializedPage(
            Layout layout,
            Map<String, CardData> cards,
            int expectedCount,
            Integer totalResults,
            int scrollIterations,
            int unchangedIterations,
            TerminalState terminalState
    ) {
        return new MaterializedPage(
                layout,
                List.copyOf(cards.values()),
                expectedCount,
                totalResults,
                cards.size(),
                page.url(),
                scrollIterations,
                unchangedIterations,
                cardIdsHash(cards.keySet()),
                terminalState.terminal(),
                terminalState.reason(),
                terminalState.nextCount(),
                terminalState.nextVisible(),
                terminalState.nextDisabled(),
                terminalState.nextAriaDisabled(),
                terminalState.nextLabel()
        );
    }

    private TerminalState terminalState(
            TerminalState source,
            boolean terminal,
            String reason
    ) {
        return new TerminalState(
                terminal,
                reason,
                source.nextCount(),
                source.nextVisible(),
                source.nextDisabled(),
                source.nextAriaDisabled(),
                source.nextLabel()
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
                        "Expected one card for " + jobId + ", found " + card.count(),
                        canonicalUrl(jobId)
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
        Locator card = cardLocator(snapshot.layout(), jobId);
        if (card.count() != 1) {
            throw new CollectionBlockedException(
                    "Expected one job card for scrolling, found " + card.count(),
                    canonicalUrl(jobId)
            );
        }
        card.evaluate(
                "element => element.scrollIntoView({block:'end', inline:'nearest', behavior:'instant'})"
        );
    }

    private CardData waitForCardData(Layout layout, Locator card, String jobId) {
        long deadline = System.nanoTime()
                + Duration.ofSeconds(config.detailsTimeoutSeconds()).toNanos();
        CardData latest = null;
        while (System.nanoTime() < deadline) {
            Map<String, Object> value = stringObjectMap(card.evaluate(PREVIEW_SCRIPT, jobId));
            latest = new CardData(
                    jobId,
                    text(value.get("title")),
                    text(value.get("company")),
                    text(value.get("location")),
                    text(value.get("workplace")),
                    text(value.get("salary")),
                    Boolean.TRUE.equals(value.get("related"))
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
        logUnmaterializedCard(layout, card, jobId);
        throw new CollectionBlockedException(
                "Card " + jobId + " did not materialize; last preview=" + latest,
                canonicalUrl(jobId)
        );
    }

    private void logUnmaterializedCard(Layout layout, Locator card, String jobId) {
        if (!log.isWarnEnabled()) return;
        Object diagnostic = card.evaluate("""
                element => {
                  const bounds = node => {
                    const {top, left, bottom, right, width, height} = node.getBoundingClientRect();
                    return {top, left, bottom, right, width, height};
                  };
                  const scrollContainers = [];
                  for (let parent = element.parentElement; parent; parent = parent.parentElement) {
                    const overflowY = getComputedStyle(parent).overflowY;
                    if (!/auto|scroll|hidden|clip/.test(overflowY)
                        && parent !== document.scrollingElement) continue;
                    scrollContainers.push({
                      tag: parent.tagName,
                      id: parent.id,
                      className: parent.className,
                      bounds: bounds(parent),
                      overflowY,
                      scrollTop: parent.scrollTop,
                      scrollHeight: parent.scrollHeight,
                      clientHeight: parent.clientHeight
                    });
                  }
                  return {
                    visibility: document.visibilityState,
                    visible: !!element.getClientRects().length,
                    viewport: {width: innerWidth, height: innerHeight},
                    cardBounds: bounds(element),
                    scrollContainers,
                    text: (element.innerText || '').slice(0, 1000),
                    html: element.outerHTML.slice(0, 4000)
                  };
                }
                """);
        log.warn(
                "LinkedIn card materialization failed: job_id={} layout={} url={} diagnostic={}",
                jobId, layout, page.url(), JsonSupport.GSON.toJson(diagnostic)
        );
    }

    Locator cardLocator(Layout layout, String jobId) {
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

    private TerminalState terminalState() {
        boolean explicitEndText = hasExplicitTerminalText();
        PaginationState pagination = paginationState();
        Locator next = page.locator(
                "button[data-testid^='pagination-controls-next-button-'], "
                        + "button[aria-label='View next page'], button:has-text('View next page')"
        );
        int nextCount = next.count();
        if (nextCount == 0) {
            boolean terminal = explicitEndText || pagination.isLastPage();
            return new TerminalState(
                    terminal,
                    explicitEndText
                            ? "explicit_end_text"
                            : pagination.isLastPage() ? "pagination_last_page" : "",
                    0,
                    false,
                    false,
                    "",
                    ""
            );
        }
        Locator button = next.last();
        boolean hiddenNext = "pagination-controls-next-button-hidden".equals(button.getAttribute("data-testid"));
        boolean visible = !hiddenNext && button.isVisible();
        boolean disabled = button.isDisabled();
        String ariaDisabled = text(button.getAttribute("aria-disabled"));
        String label = text(button.getAttribute("aria-label"));
        if (label.isBlank()) {
            label = text(button.textContent());
        }
        if (label.length() > 160) {
            label = label.substring(0, 160);
        }
        boolean terminal = explicitEndText
                || hiddenNext
                || (pagination.isLastPage()
                && (disabled || "true".equalsIgnoreCase(ariaDisabled)));
        String reason;
        if (explicitEndText) {
            reason = "explicit_end_text";
        } else if (terminal) {
            reason = "pagination_last_page";
        } else {
            reason = "";
        }
        return new TerminalState(
                terminal,
                reason,
                nextCount,
                visible,
                disabled,
                ariaDisabled,
                label
        );
    }

    private PaginationState paginationState() {
        Map<String, Object> value = stringObjectMap(page.evaluate(PAGINATION_SCRIPT));
        boolean present = Boolean.TRUE.equals(value.get("present"));
        int currentPage = value.get("currentPage") instanceof Number number
                ? number.intValue()
                : 0;
        int lastPage = value.get("lastPage") instanceof Number number
                ? number.intValue()
                : 0;
        return new PaginationState(present, currentPage, lastPage);
    }

    private ResultCountState resultCountState(int start) {
        Map<String, Object> value = stringObjectMap(page.evaluate(RESULT_COUNT_SCRIPT));
        boolean present = Boolean.TRUE.equals(value.get("present"));
        if (!present || !(value.get("total") instanceof Number number)) {
            return missingResultCount();
        }
        int total = Math.max(0, number.intValue());
        int expectedCount = expectedPageCount(total, start);
        return new ResultCountState(
                true,
                total,
                expectedCount,
                start + expectedCount >= total
        );
    }

    private ResultCountState stableResultCountState(int start, int observedCards) {
        Integer previousTotal = null;
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            ResultCountState current = resultCountState(start);
            boolean valid = current.present()
                    && current.total() >= start + observedCards;
            if (valid) {
                if (previousTotal != null
                        && previousTotal.intValue() == current.total()) {
                    return current;
                }
                previousTotal = current.total();
            } else {
                previousTotal = null;
            }
            page.waitForTimeout(250);
        }
        return missingResultCount();
    }

    private ResultCountState missingResultCount() {
        return new ResultCountState(false, 0, EXPECTED_PAGE_SIZE, false);
    }

    private int expectedCount(ResultCountState resultCount) {
        return resultCount.present()
                ? resultCount.expectedCount()
                : EXPECTED_PAGE_SIZE;
    }

    static int expectedPageCount(int totalResults, int start) {
        return Math.min(EXPECTED_PAGE_SIZE, Math.max(0, totalResults - start));
    }

    private String cardIdsHash(Set<String> ids) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String id : ids) {
                digest.update(id.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private boolean hasExplicitTerminalText() {
        String body = page.locator("body").innerText().toLowerCase(Locale.ROOT);
        return body.contains("no matching jobs found")
                || body.contains("no jobs found")
                || body.contains("you've viewed all jobs")
                || body.contains("end of results");
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
