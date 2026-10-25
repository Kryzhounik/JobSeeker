package com.seeker.collector.linkedin.collection;

import com.microsoft.playwright.PlaywrightException;
import com.seeker.collector.linkedin.browser.AuthenticationRequiredException;
import com.seeker.collector.linkedin.config.CollectorConfig;
import com.seeker.collector.linkedin.python.PythonGateway;
import com.seeker.tools.api.ProgressSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

final class LinkedInCollector {
    private static final int PAGE_STEP = 25;

    private final LinkedInPageClient browser;
    private final PythonGateway python;
    private final CollectorConfig config;
    private final String runId;
    private final Consumer<ProgressSnapshot> progressListener;
    private final List<ScopeItem> scope = new ArrayList<>();
    private final List<PageReport> pages = new ArrayList<>();
    private final OutcomeCounter outcomes = new OutcomeCounter();
    private final Set<String> seenIds = new LinkedHashSet<>();
    private String currentLocation = "";
    private String currentSearch = "";
    private int currentPage;
    private int currentCards;
    private String currentUrl = "";

    LinkedInCollector(
            LinkedInPageClient browser,
            PythonGateway python,
            CollectorConfig config,
            String runId
    ) {
        this(browser, python, config, runId, ignored -> { });
    }

    LinkedInCollector(LinkedInPageClient browser, PythonGateway python, CollectorConfig config,
                      String runId, Consumer<ProgressSnapshot> progressListener) {
        this.browser = browser;
        this.python = python;
        this.config = config;
        this.runId = runId;
        this.progressListener = progressListener;
    }

    CollectionReport runBatch() {
        try {
            List<SearchPlan.Target> plan = SearchPlan.build(
                    config,
                    python.priorityCompanyIds()
            );
            for (SearchPlan.Target target : plan) {
                if (scope.size() >= config.limit()) {
                    break;
                }
                if (!target.collectsJobs()) {
                    currentLocation = target.label();
                    currentSearch = "seed";
                    currentUrl = target.pageUrl(0);
                    progress("opening_seed", "");
                    System.err.println("LinkedIn seed: " + target.label());
                    browser.openSeed(target);
                    continue;
                }
                collectTarget(target);
            }
            String stopReason = scope.size() >= config.limit()
                    ? "limit_reached"
                    : "plan_exhausted";
            return finish("complete", stopReason, "");
        } catch (AuthenticationRequiredException error) {
            return finish("login_required", "authentication_required", failureMessage(error));
        } catch (RuntimeException error) {
            return finish("blocked", "collection_error", failureMessage(error));
        }
    }

    CollectionReport runFromUrl(String url) {
        return runFromUrls(List.of(url));
    }

    CollectionReport runFromUrls(List<String> urls) {
        List<String> rejections = new ArrayList<>();
        for (String url : urls) {
            currentUrl = url;
            String jobId = LinkedInPageClient.jobId(url);
            Preview preview = new Preview(
                    jobId,
                    LinkedInPageClient.canonicalUrl(jobId),
                    "",
                    "",
                    "",
                    "",
                    "",
                    "from-url",
                    0,
                    1
            );
            try {
                LinkedInPageClient.JobDetails direct = browser.openDirect(url, jobId);
                preview = new Preview(
                        jobId,
                        LinkedInPageClient.canonicalUrl(jobId),
                        direct.title(),
                        direct.company(),
                        direct.location(),
                        "",
                        "",
                        "from-url",
                        0,
                        1
                );
                ProcessResult result = operate(preview, null, direct);
                recordResult(result);
                if (!result.accepted()) {
                    rejections.add(urls.size() == 1
                            ? result.reason()
                            : jobId + ": " + result.reason());
                }
            } catch (AuthenticationRequiredException error) {
                return finish("login_required", "authentication_required", failureMessage(error));
            } catch (RuntimeException error) {
                return failedDirectCollection(preview, error);
            }
        }
        return finish(
                "complete",
                urls.size() == 1 ? "direct_url_processed" : "direct_urls_processed",
                String.join("; ", rejections)
        );
    }

    private void collectTarget(SearchPlan.Target target) {
        currentLocation = target.label();
        currentSearch = target.kind().name().toLowerCase(Locale.ROOT);
        Set<String> targetSeenIds = new LinkedHashSet<>();
        int noNewPages = 0;
        int start = 0;

        while (scope.size() < config.limit()) {
            currentPage = start / PAGE_STEP + 1;
            currentCards = 0;
            currentUrl = target.pageUrl(start);
            progress("opening_search", "");
            String pageUrl = browser.openSearch(target, start);
            progress("reading_cards", "");
            LinkedInPageClient.MaterializedPage materialized = materializeWithOneRetry(
                    target,
                    start
            );
            List<LinkedInPageClient.CardData> newCards = new ArrayList<>();
            int newForTarget = 0;
            for (LinkedInPageClient.CardData card : materialized.cards()) {
                if (targetSeenIds.add(card.jobId())) {
                    newForTarget++;
                }
                if (!seenIds.contains(card.jobId())) {
                    newCards.add(card);
                }
            }
            noNewPages = newForTarget == 0 ? noNewPages + 1 : 0;
            String stopReason = pageStopReason(materialized, noNewPages);
            int pageIndex = recordPage(
                    target,
                    start,
                    pageUrl,
                    materialized,
                    newCards.size(),
                    newForTarget,
                    stopReason
            );
            printProgress(target, start, materialized, newCards.size());
            currentCards = materialized.materializedCount();
            progress("processing", "");
            if (isIncompletePage(materialized)) {
                throw new CollectionBlockedException(
                        "LinkedIn page materialized "
                                + materialized.materializedCount()
                                + " of " + materialized.expectedCount()
                                + " cards"
                );
            }

            int index = 0;
            for (LinkedInPageClient.CardData card : newCards) {
                index++;
                if (!seenIds.add(card.jobId())) {
                    continue;
                }
                Preview preview = card.preview(target.label(), start, index);
                if (scope.size() >= config.limit()) {
                    recordResult(logOutcome(
                            preview, "not_processed_due_to_limit", "global limit reached"
                    ));
                    break;
                }
                recordResult(operate(preview, materialized, null));
                if (scope.size() >= config.limit()) {
                    break;
                }
            }

            if (scope.size() >= config.limit()) {
                updatePageStopReason(pageIndex, "limit_reached");
            }

            if (scope.size() >= config.limit()
                    || materialized.terminal()
                    || noNewPages >= 2) {
                return;
            }
            start += PAGE_STEP;
        }
    }

    private LinkedInPageClient.MaterializedPage materializeWithOneRetry(
            SearchPlan.Target target,
            int start
    ) {
        try {
            return browser.materializePage(start);
        } catch (CollectionBlockedException | PlaywrightException first) {
            System.err.println(
                    "LinkedIn page materialization failed once; retrying "
                            + target.label() + " start=" + start + ": " + message(first)
            );
            browser.openSearch(target, start);
            return browser.materializePage(start);
        }
    }

    private ProcessResult operate(
            Preview preview,
            LinkedInPageClient.MaterializedPage materialized,
            LinkedInPageClient.JobDetails details
    ) {
        currentUrl = preview.sourceUrl();
        progress("filtering_preview", preview.jobId() + " " + preview.title());
        PreviewDecision decision = python.decidePreview(preview);
        if (!decision.shouldOpen()) {
            return result(preview, "preview_filtered", decision.reason());
        }
        if (details != null) {
            return processHtml(preview, details);
        }
        try {
            progress("opening_job", preview.jobId() + " " + preview.title());
            details = browser.openCard(materialized, preview.jobId());
        } catch (CardClickException clickError) {
            try {
                details = browser.openDirect(preview.sourceUrl(), preview.jobId());
            } catch (AuthenticationRequiredException error) {
                throw error;
            } catch (RuntimeException fallbackError) {
                String reason = "search-card click failed: " + message(clickError)
                        + "; direct URL fallback failed: " + message(fallbackError);
                return logOutcome(preview, "open_failed", reason);
            }
        } catch (DetailsNotReadyException error) {
            return logOutcome(preview, "incomplete_raw", message(error));
        }
        return processHtml(preview, details);
    }

    private ProcessResult processHtml(Preview preview, LinkedInPageClient.JobDetails details) {
        progress("filtering_content", preview.jobId() + " " + preview.title());
        return python.processHtml(preview, details.html(), details.easyApply());
    }

    private ProcessResult logOutcome(Preview preview, String status, String reason) {
        try {
            python.logOutcome(preview, status, reason);
        } catch (RuntimeException error) {
            throw new CollectionBlockedException(
                    "Could not persist LinkedIn outcome " + status
                            + " for " + preview.jobId(),
                    error
            );
        }
        return result(preview, status, reason);
    }

    private ProcessResult result(Preview preview, String status, String reason) {
        return new ProcessResult("linkedin", preview.jobId(), preview.title(), status, reason);
    }

    private void recordResult(ProcessResult result) {
        outcomes.add(result.status());
        if (result.accepted()) {
            scope.add(new ScopeItem(result.source(), result.jobId(), result.title()));
        }
        progress("processing", result.jobId() + " " + result.title()
                + (result.reason().isBlank() ? "" : "\n" + result.reason()));
    }

    private void progress(String stage, String message) {
        progressListener.accept(new CollectionProgress(runId, stage, currentLocation, currentSearch,
                currentPage, currentCards, scope.size(), config.limit(),
                outcomes.snapshot(), message));
    }

    private int recordPage(
            SearchPlan.Target target,
            int start,
            String pageUrl,
            LinkedInPageClient.MaterializedPage materialized,
            int newCards,
            int newForTarget,
            String stopReason
    ) {
        PageReport report = new PageReport(
                pages.size() + 1,
                target.label(),
                target.kind().name().toLowerCase(Locale.ROOT),
                start,
                pageUrl,
                materialized.actualUrl(),
                materialized.layout() == null
                        ? ""
                        : materialized.layout().name().toLowerCase(Locale.ROOT),
                materialized.expectedCount(),
                materialized.totalResults(),
                materialized.materializedCount(),
                newCards,
                newForTarget,
                materialized.scrollIterations(),
                materialized.unchangedIterations(),
                materialized.cardIdsHash(),
                materialized.terminal(),
                materialized.terminalReason(),
                materialized.nextCount(),
                materialized.nextVisible(),
                materialized.nextDisabled(),
                materialized.nextAriaDisabled(),
                materialized.nextLabel(),
                stopReason
        );
        pages.add(report);
        python.logPage(runId, report);
        return pages.size() - 1;
    }

    private void updatePageStopReason(int pageIndex, String stopReason) {
        PageReport report = pages.get(pageIndex).withStopReason(stopReason);
        pages.set(pageIndex, report);
        python.logPage(runId, report);
    }

    private String pageStopReason(
            LinkedInPageClient.MaterializedPage materialized,
            int noNewPages
    ) {
        if (materialized.terminal()) {
            return materialized.terminalReason();
        }
        if (isIncompletePage(materialized)) {
            return materialized.terminalReason();
        }
        if (noNewPages >= 2) {
            return "two_pages_without_new_ids";
        }
        return "continue";
    }

    private boolean isIncompletePage(LinkedInPageClient.MaterializedPage materialized) {
        return "unknown_short_page".equals(materialized.terminalReason())
                || "incomplete_page".equals(materialized.terminalReason());
    }

    private void printProgress(
            SearchPlan.Target target,
            int start,
            LinkedInPageClient.MaterializedPage materialized,
            int newCards
    ) {
        System.err.printf(
                Locale.ROOT,
                "%s %s start=%d cards=%d new=%d accepted=%d/%d%n",
                target.label(),
                target.kind().name().toLowerCase(Locale.ROOT),
                start,
                materialized.materializedCount(),
                newCards,
                scope.size(),
                config.limit()
        );
    }

    private CollectionReport report(String status, String message) {
        return new CollectionReport(
                "linkedin",
                runId,
                status,
                scope.size(),
                List.copyOf(scope),
                outcomes.snapshot(),
                List.copyOf(pages),
                message
        );
    }

    private CollectionReport finish(String status, String stopReason, String message) {
        CollectionReport report = report(status, message);
        progress(status, message.isBlank() ? stopReason : message);
        python.finishRun(
                runId,
                status,
                stopReason,
                report.acceptedCount(),
                message
        );
        System.err.printf(
                "LinkedIn collection finished: run_id=%s status=%s accepted=%d stop_reason=%s%n",
                runId,
                status,
                report.acceptedCount(),
                stopReason
        );
        return report;
    }

    private CollectionReport failedDirectCollection(
            Preview preview,
            RuntimeException error
    ) {
        recordResult(logOutcome(preview, "open_failed", message(error)));
        return finish("blocked", "direct_url_error", failureMessage(error));
    }

    private String failureMessage(Throwable error) {
        if (error instanceof CollectionBlockedException blocked && !blocked.url().isBlank()) {
            return message(error);
        }
        return message(error) + (currentUrl.isBlank() ? "" : "\nURL: " + currentUrl);
    }

    private String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank()
                ? error.getClass().getSimpleName()
                : value;
    }
}
