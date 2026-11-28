package com.seeker.collector.linkedin.collection;

import com.seeker.logging.LogInput;
import com.seeker.logging.LogOutput;
import org.slf4j.event.Level;

import com.seeker.collector.common.*;

import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Page;
import com.seeker.collector.linkedin.browser.AuthenticationRequiredException;
import com.seeker.collector.linkedin.collection.LinkedInJobDetailsReader.JobDetails;
import com.seeker.collector.linkedin.collection.LinkedInSearchResults.CardData;
import com.seeker.collector.linkedin.collection.LinkedInSearchResults.MaterializedPage;
import com.seeker.collector.linkedin.config.CollectorConfig;
import com.seeker.collector.linkedin.python.LinkedInGateway;
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
    private final LinkedInSearchResults searchResults;
    private final LinkedInJobDetailsReader detailsReader;
    private final LinkedInGateway python;
    private final VacancyProcessor processor;
    private final CollectionSink sink;
    private final LinkedInVacancyPreparer preparer;
    private final CollectorConfig config;
    private final String runId;
    private final Consumer<ProgressSnapshot> progressListener;
    private final List<PageReport> pages = new ArrayList<>();
    private final Set<String> seenIds = new LinkedHashSet<>();
    private String currentLocation = "";
    private String currentSearch = "";
    private String currentQuery = "";
    private int currentPage;
    private int currentCards;
    private String currentUrl = "";
    private Integer urlCount;

    LinkedInCollector(LinkedInPageClient browser, LinkedInSearchResults searchResults,
                      LinkedInJobDetailsReader detailsReader, LinkedInGateway python, CollectorConfig config,
                      String runId, Consumer<ProgressSnapshot> progressListener,
                      VacancyProcessor processor, CollectionSink sink, LinkedInVacancyPreparer preparer) {
        this.browser = browser;
        this.searchResults = searchResults;
        this.detailsReader = detailsReader;
        this.python = python;
        this.config = config;
        this.runId = runId;
        this.progressListener = progressListener;
        this.processor = processor;
        this.sink = sink;
        this.preparer = preparer;
    }

    SourceResult runBatch() {
        try {
            List<SearchPlan.Target> plan = SearchPlan.build(
                    config,
                    python.priorityCompanyIds()
            );
            for (SearchPlan.Target target : plan) {
                if (sink.acceptedCount() >= config.search().limit()) {
                    break;
                }
                if (!target.collectsJobs()) {
                    openSeed(target);
                    continue;
                }
                collectTarget(target);
            }
            String stopReason = sink.acceptedCount() >= config.search().limit()
                    ? "limit_reached"
                    : "plan_exhausted";
            return finish("complete", stopReason, "");
        } catch (AuthenticationRequiredException error) {
            return finish("login_required", "authentication_required", failureMessage(error));
        } catch (RuntimeException error) {
            return finish("blocked", "collection_error", failureMessage(error));
        }
    }

    @LogInput(include = "#target.label")
    private void openSeed(SearchPlan.Target target) {
        currentLocation = target.label();
        currentSearch = "seed";
        currentQuery = target.query();
        currentUrl = target.pageUrl(0);
        progress("opening_seed", "");
        browser.openSeed(target);
    }

    SourceResult runFromUrl(String url) {
        return runFromUrls(List.of(url));
    }

    SourceResult runFromUrls(List<String> urls) {
        urlCount = urls.size();
        List<String> rejections = new ArrayList<>();
        for (String url : urls) {
            currentUrl = url;
            String jobId = LinkedInUrls.jobId(url);
            Preview preview = new Preview(
                    jobId,
                    LinkedInUrls.canonicalUrl(jobId),
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
                ProcessResult result = operate(preview, null);
                recordResult(result);
                if (!result.accepted()) {
                    rejections.add(urls.size() == 1
                            ? result.reason()
                            : jobId + ": " + result.reason());
                }
            } catch (AuthenticationRequiredException error) {
                return finish("login_required", "authentication_required", failureMessage(error));
            } catch (RuntimeException error) {
                return finish("blocked", "collection_error", failureMessage(error));
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
        currentQuery = target.query();
        Set<String> targetSeenIds = new LinkedHashSet<>();
        int noNewPages = 0;
        int start = 0;

        while (sink.acceptedCount() < config.search().limit()) {
            currentPage = start / PAGE_STEP + 1;
            currentCards = 0;
            currentUrl = target.pageUrl(start);
            progress("opening_search", "");
            String pageUrl = browser.openSearch(target, start);
            progress("reading_cards", "");
            MaterializedPage materialized = materializeWithOneRetry(
                    target,
                    start
            );
            List<CardData> newCards = new ArrayList<>();
            int newForTarget = 0;
            for (CardData card : materialized.cards()) {
                if (card.related()) continue;
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
            for (CardData card : newCards) {
                index++;
                if (!seenIds.add(card.jobId())) {
                    continue;
                }
                Preview preview = card.preview(target.label(), start, index);
                if (sink.acceptedCount() >= config.search().limit()) {
                    recordResult(logOutcome(
                            preview, "not_processed_due_to_limit", "global limit reached"
                    ));
                    break;
                }
                recordResult(operate(preview, materialized));
                if (sink.acceptedCount() >= config.search().limit()) {
                    break;
                }
            }

            if (sink.acceptedCount() >= config.search().limit()) {
                updatePageStopReason(pageIndex, "limit_reached");
            }

            if (sink.acceptedCount() >= config.search().limit()
                    || materialized.terminal()
                    || noNewPages >= 2) {
                return;
            }
            start += PAGE_STEP;
        }
    }

    private MaterializedPage materializeWithOneRetry(
            SearchPlan.Target target,
            int start
    ) {
        try {
            return searchResults.materializePage(start);
        } catch (CollectionBlockedException | PlaywrightException ignored) {
            return retryMaterialization(target, start);
        }
    }

    @LogInput(level = Level.WARN, include = {"#target.label", "start"})
    private MaterializedPage retryMaterialization(SearchPlan.Target target, int start) {
        browser.openSearch(target, start);
        return searchResults.materializePage(start);
    }

    private ProcessResult operate(
            Preview preview,
            MaterializedPage materialized
    ) {
        currentUrl = preview.sourceUrl();
        boolean processing = false;
        try {
            JobDetails details = null;
            if (materialized == null) {
                progress("opening_job", preview.jobId() + " " + preview.title());
                details = readDirect(preview.sourceUrl(), preview.jobId());
                preview = new Preview(preview.jobId(), preview.sourceUrl(), details.title(),
                        details.company(), details.location(), "", "", preview.label(),
                        preview.start(), preview.index());
            }
            processing = true;
            progress("filtering_preview", preview.jobId() + " " + preview.title());
            PreviewDecision decision = processor.decidePreview(preview);
            if (!decision.shouldOpen()) {
                return result(preview, "preview_filtered", decision.reason());
            }
            if (details == null) {
                processing = false;
                progress("opening_job", preview.jobId() + " " + preview.title());
                details = readCard(materialized, preview);
            }
            if (preview.title().isBlank()) {
                throw new DetailsNotReadyException("LinkedIn vacancy title is empty");
            }
            processing = true;
            return processHtml(preview, details);
        } catch (AuthenticationRequiredException error) {
            throw error;
        } catch (DetailsNotReadyException error) {
            return logOutcome(preview, "incomplete_raw", message(error));
        } catch (RuntimeException error) {
            return logOutcome(preview, processing ? "processing_failed" : "open_failed", message(error));
        }
    }

    private JobDetails readCard(MaterializedPage materialized, Preview preview) {
        try {
            browser.openCard(searchResults.cardLocator(materialized.layout(), preview.jobId()), preview.jobId());
        } catch (CardClickException error) {
            return readDirect(preview.sourceUrl(), preview.jobId());
        }
        return detailsReader.readSearch(preview.jobId());
    }

    private JobDetails readDirect(String sourceUrl, String jobId) {
        Page direct = browser.openDirect(sourceUrl);
        try {
            return detailsReader.readDirect(direct, jobId);
        } finally {
            direct.close();
        }
    }

    private ProcessResult processHtml(Preview preview, JobDetails details) {
        progress("filtering_content", preview.jobId() + " " + preview.title());
        return preparer.process(preview, details.html(), details.easyApply(), processor);
    }

    @LogOutput(level = Level.WARN, include = {"jobId", "status", "reason"},
            condition = "#result.status != 'not_processed_due_to_limit'")
    private ProcessResult logOutcome(Preview preview, String status, String reason) {
        try {
            processor.logOutcome(preview, status, reason);
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
        sink.record(result);
        progress("processing", result.jobId() + " " + result.title()
                + (result.reason().isBlank() ? "" : "\n" + result.reason()));
    }

    private void progress(String stage, String message) {
        progressListener.accept(snapshot(stage, message));
    }

    CollectionProgress snapshot(String stage, String message) {
        CollectionProgress progress = new CollectionProgress(runId, stage, currentLocation, currentSearch,
                currentPage, currentCards, sink.acceptedCount(), urlCount == null ? config.search().limit() : 0,
                sink.outcomes(), message, urlCount);
        var fields = new java.util.LinkedHashMap<>(progress.fields());
        fields.put("Query", currentQuery);
        return new CollectionProgress(progress.runId(), progress.stage(), progress.action(), progress.progress(), fields, progress.details());
    }

    List<PageReport> pages() { return List.copyOf(pages); }

    private int recordPage(
            SearchPlan.Target target,
            int start,
            String pageUrl,
            MaterializedPage materialized,
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
            MaterializedPage materialized,
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

    private boolean isIncompletePage(MaterializedPage materialized) {
        return "unknown_short_page".equals(materialized.terminalReason())
                || "incomplete_page".equals(materialized.terminalReason());
    }

    private SourceResult finish(String status, String stopReason, String message) {
        return new SourceResult(status, stopReason, message);
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
