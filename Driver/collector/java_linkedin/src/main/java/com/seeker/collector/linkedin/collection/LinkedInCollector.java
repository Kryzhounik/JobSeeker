package com.seeker.collector.linkedin.collection;

import com.microsoft.playwright.PlaywrightException;
import com.seeker.collector.linkedin.browser.AuthenticationRequiredException;
import com.seeker.collector.linkedin.config.CollectorConfig;
import com.seeker.collector.linkedin.python.PythonGateway;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class LinkedInCollector {
    private static final int PAGE_STEP = 25;

    private final LinkedInPageClient browser;
    private final PythonGateway python;
    private final CollectorConfig config;
    private final String runId;
    private final List<ScopeItem> scope = new ArrayList<>();
    private final List<PageReport> pages = new ArrayList<>();
    private final OutcomeCounter outcomes = new OutcomeCounter();
    private final Set<String> seenIds = new LinkedHashSet<>();

    LinkedInCollector(
            LinkedInPageClient browser,
            PythonGateway python,
            CollectorConfig config,
            String runId
    ) {
        this.browser = browser;
        this.python = python;
        this.config = config;
        this.runId = runId;
    }

    CollectionReport runBatch(String locationOverride) {
        try {
            List<SearchPlan.Target> plan = SearchPlan.build(
                    config,
                    locationOverride,
                    python.priorityCompanyIds()
            );
            for (SearchPlan.Target target : plan) {
                if (scope.size() >= config.limit()) {
                    break;
                }
                if (!target.collectsJobs()) {
                    System.err.println("LinkedIn seed: " + target.label());
                    browser.openSeed(target);
                    continue;
                }
                collectTarget(target);
            }
            return CollectionReport.complete(
                    runId,
                    scope,
                    outcomes.snapshot(),
                    pages
            );
        } catch (AuthenticationRequiredException error) {
            return CollectionReport.halted(
                    runId,
                    "login_required",
                    scope,
                    outcomes.snapshot(),
                    pages,
                    message(error)
            );
        } catch (CollectionBlockedException error) {
            return CollectionReport.blocked(
                    runId,
                    scope,
                    outcomes.snapshot(),
                    pages,
                    message(error)
            );
        } catch (RuntimeException error) {
            return CollectionReport.blocked(
                    runId,
                    scope,
                    outcomes.snapshot(),
                    pages,
                    message(error)
            );
        }
    }

    CollectionReport runFromUrl(String url) {
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
            LinkedInPageClient.DirectJob direct = browser.openDirect(url, jobId);
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
            processHtml(preview, direct.html());
            return CollectionReport.complete(
                    runId,
                    scope,
                    outcomes.snapshot(),
                    pages
            );
        } catch (AuthenticationRequiredException error) {
            return CollectionReport.halted(
                    runId,
                    "login_required",
                    scope,
                    outcomes.snapshot(),
                    pages,
                    message(error)
            );
        } catch (RuntimeException error) {
            logOutcome(preview, "open_failed", message(error));
            return CollectionReport.blocked(
                    runId,
                    scope,
                    outcomes.snapshot(),
                    pages,
                    message(error)
            );
        }
    }

    private void collectTarget(SearchPlan.Target target) {
        Set<String> targetSeenIds = new LinkedHashSet<>();
        int noNewPages = 0;
        int start = 0;

        while (scope.size() < config.limit()) {
            String pageUrl = browser.openSearch(target, start);
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
            pages.add(new PageReport(
                    target.label(),
                    target.kind().name().toLowerCase(Locale.ROOT),
                    start,
                    pageUrl,
                    materialized.expectedCount(),
                    materialized.materializedCount(),
                    newCards.size(),
                    materialized.terminal()
            ));
            System.err.printf(
                    Locale.ROOT,
                    "%s %s start=%d cards=%d new=%d accepted=%d/%d%n",
                    target.label(),
                    target.kind().name().toLowerCase(Locale.ROOT),
                    start,
                    materialized.materializedCount(),
                    newCards.size(),
                    scope.size(),
                    config.limit()
            );

            int index = 0;
            for (LinkedInPageClient.CardData card : newCards) {
                index++;
                if (!seenIds.add(card.jobId())) {
                    continue;
                }
                Preview preview = card.preview(target.label(), start, index);
                PreviewDecision decision = python.decidePreview(preview);
                if (!decision.shouldOpen()) {
                    outcomes.add("preview_filtered");
                    continue;
                }
                if (scope.size() >= config.limit()) {
                    logOutcome(preview, "not_processed_due_to_limit", "global limit reached");
                    break;
                }
                collectAcceptedCard(materialized, preview);
                if (scope.size() >= config.limit()) {
                    break;
                }
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
            return browser.materializePage();
        } catch (CollectionBlockedException | PlaywrightException first) {
            System.err.println(
                    "LinkedIn page materialization failed once; retrying "
                            + target.label() + " start=" + start + ": " + message(first)
            );
            browser.openSearch(target, start);
            return browser.materializePage();
        }
    }

    private void collectAcceptedCard(
            LinkedInPageClient.MaterializedPage materialized,
            Preview preview
    ) {
        String html;
        try {
            html = browser.openCard(materialized, preview.jobId());
        } catch (CardClickException clickError) {
            try {
                html = browser.openDirect(preview.sourceUrl(), preview.jobId()).html();
            } catch (AuthenticationRequiredException error) {
                throw error;
            } catch (RuntimeException fallbackError) {
                String reason = "search-card click failed: " + message(clickError)
                        + "; direct URL fallback failed: " + message(fallbackError);
                logOutcome(preview, "open_failed", reason);
                return;
            }
        } catch (DetailsNotReadyException error) {
            logOutcome(preview, "incomplete_raw", message(error));
            return;
        }
        processHtml(preview, html);
    }

    private void processHtml(Preview preview, String html) {
        try {
            ProcessResult result = python.processHtml(preview, html);
            outcomes.add(result.status());
            if (result.accepted()) {
                scope.add(new ScopeItem(
                        result.source(),
                        result.jobId(),
                        result.title()
                ));
            }
        } catch (RuntimeException error) {
            logOutcome(preview, "incomplete_raw", message(error));
        }
    }

    private void logOutcome(Preview preview, String status, String reason) {
        try {
            python.logOutcome(preview, status, reason);
        } catch (RuntimeException error) {
            throw new CollectionBlockedException(
                    "Could not persist LinkedIn outcome " + status
                            + " for " + preview.jobId(),
                    error
            );
        }
        outcomes.add(status);
    }

    private String message(Throwable error) {
        String value = error.getMessage();
        return value == null || value.isBlank()
                ? error.getClass().getSimpleName()
                : value;
    }
}
