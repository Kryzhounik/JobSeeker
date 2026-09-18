package com.seeker.collector.linkedin.collection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinkedInPageClientTest {
    @Test
    void calculatesExpectedCardsFromTotalResultsAndStart() {
        assertEquals(25, LinkedInPageClient.expectedPageCount(267, 0));
        assertEquals(25, LinkedInPageClient.expectedPageCount(267, 225));
        assertEquals(17, LinkedInPageClient.expectedPageCount(267, 250));
        assertEquals(0, LinkedInPageClient.expectedPageCount(267, 275));
    }

    @Test
    void acceptsTheCurrentPageWhenOnlyLinkedInTrackingParametersDiffer() {
        String requested = "https://www.linkedin.com/jobs/search/"
                + "?keywords=Java&location=Poland&geoId=105072130"
                + "&f_E=3%2C4&f_WT=2&f_JT=F&f_TPR=r604800"
                + "&sortBy=DD&origin=JOB_SEARCH_PAGE_SEARCH_BUTTON"
                + "&refresh=true&start=0";
        String actual = "https://www.linkedin.com/jobs/search/"
                + "?currentJobId=123&keywords=Java&location=Poland&geoId=105072130"
                + "&f_E=3%2C4&f_WT=2&f_JT=F&f_TPR=r604800"
                + "&sortBy=DD&origin=JOB_SEARCH_PAGE_SEARCH_BUTTON&refresh=true";

        assertEquals("", LinkedInPageClient.searchQueryMismatch(requested, actual));
    }

    @Test
    void rejectsARestoredPageForAnotherLocation() {
        String requested = "https://www.linkedin.com/jobs/search/"
                + "?keywords=Java&location=Poland&geoId=105072130&start=25";
        String actual = "https://www.linkedin.com/jobs/search/"
                + "?keywords=Java&location=Moldova&geoId=106178099&start=25";

        assertEquals(
                "location expected=Poland actual=Moldova",
                LinkedInPageClient.searchQueryMismatch(requested, actual)
        );
    }

    @Test
    void rejectsARestoredPageAtAnotherOffset() {
        String requested = "https://www.linkedin.com/jobs/search/"
                + "?keywords=Java&start=25";
        String actual = "https://www.linkedin.com/jobs/search/"
                + "?keywords=Java&start=150";

        assertEquals(
                "start expected=25 actual=150",
                LinkedInPageClient.searchQueryMismatch(requested, actual)
        );
    }
}
