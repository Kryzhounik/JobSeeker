package com.seeker.collector.linkedin.collection;

import com.seeker.collector.common.*;
import com.seeker.collector.core.CollectionAccumulator;
import com.seeker.collector.linkedin.config.CollectorConfig;
import com.seeker.collector.linkedin.python.LinkedInGateway;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** In-memory collaborators only: no browser, Python or database is started. */
class MultiQueryCollectionTest {
    @Test void overlappingQueriesShareIdsAndContinueToNextLocation() { run(100, 4, List.of("1", "2", "3")); }
    @Test void sharedBudgetStopsBeforeLaterQueries() { run(2, 2, List.of("1", "2")); }
    @Test void firstQueryCanExhaustBudget() { run(1, 1, List.of("1")); }

    private void run(int limit, int expectedTargets, List<String> accepted) {
        var browser = mock(LinkedInPageClient.class);
        var searchResults = mock(LinkedInSearchResults.class);
        var detailsReader = mock(LinkedInJobDetailsReader.class);
        var gateway = mock(LinkedInGateway.class);
        var processor = mock(VacancyProcessor.class);
        var preparer = mock(LinkedInVacancyPreparer.class);
        var sink = new CollectionAccumulator();
        var settings = new CollectionSettings.LinkedIn(List.of("Java", "AI"),
                List.of("Poland:105072130", "Ukraine:102264497"), List.of(), List.of("remote"),
                List.of("remote"), List.of(), "week", limit);
        var config = new CollectorConfig(settings, 0, "chrome", 90, 60, 3, 20);
        when(gateway.priorityCompanyIds()).thenReturn(List.of());
        var targets = new ArrayList<String>();
        when(browser.openSearch(any(), anyInt())).thenAnswer(call -> {
            SearchPlan.Target target = call.getArgument(0);
            targets.add(target.label() + "/" + target.query());
            return target.pageUrl(0);
        });
        when(searchResults.materializePage(anyInt())).thenAnswer(call -> {
            // ID 1 overlaps all searches. Other IDs are introduced by later targets.
            String next = targets.size() == 1 ? "1" : targets.size() == 2 ? "2" : "3";
            var cards = new ArrayList<LinkedInSearchResults.CardData>();
            for (String id : new LinkedHashSet<>(List.of("1", next))) {
                var card = mock(LinkedInSearchResults.CardData.class);
                when(card.jobId()).thenReturn(id);
                when(card.preview(anyString(), anyInt(), anyInt())).thenReturn(
                        new Preview(id, "https://www.linkedin.com/jobs/view/" + id, "Engineer", "Company",
                                "", "", "", "", 0, 1));
                cards.add(card);
            }
            var page = mock(LinkedInSearchResults.MaterializedPage.class);
            when(page.cards()).thenReturn(cards);
            when(page.terminal()).thenReturn(true);
            when(page.terminalReason()).thenReturn("end");
            return page;
        });
        when(processor.decidePreview(any())).thenReturn(new PreviewDecision("open", "", "", List.of()));
        when(detailsReader.readSearch(anyString())).thenReturn(new LinkedInJobDetailsReader.JobDetails("html", "Engineer", "", "", false));
        when(preparer.process(any(), anyString(), anyBoolean(), any())).thenAnswer(call -> {
            Preview p = call.getArgument(0);
            return new ProcessResult("linkedin", p.jobId(), p.title(), "raw_saved", "");
        });
        var progress = new ArrayList<com.seeker.tools.api.ProgressSnapshot>();
        var collector = new LinkedInCollector(browser, searchResults, detailsReader, gateway, config, "test", progress::add, processor, sink, preparer);
        var result = collector.runBatch();
        assertEquals("complete", result.status());
        assertEquals(limit <= 2 ? "limit_reached" : "plan_exhausted", result.stopReason());
        assertEquals(List.of("Poland/Java", "Poland/AI", "Ukraine/Java", "Ukraine/AI").subList(0, expectedTargets), targets);
        assertEquals(accepted.size(), sink.acceptedCount());
        verify(preparer, times(accepted.size())).process(any(), anyString(), anyBoolean(), any());
        assertEquals(targets.getLast().split("/")[1], progress.getLast().fields().get("Query"));
    }
}
