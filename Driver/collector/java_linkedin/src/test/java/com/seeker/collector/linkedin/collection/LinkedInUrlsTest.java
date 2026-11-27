package com.seeker.collector.linkedin.collection;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinkedInUrlsTest {
    @Test
    void acceptsTheCurrentPageWhenOnlyLinkedInTrackingParametersDiffer() {
        String requested = "https://www.linkedin.com/jobs/search-results/"
                + "?keywords=Java&geoId=105072130"
                + "&f_SAL=f_SA_id_225001%3A272001%24f_SA_id_226001%3A272015%2C274001&f_TPR=r604800"
                + "&origin=JOB_SEARCH_PAGE_SEARCH_BUTTON"
                + "&refresh=true&start=0";
        String actual = "https://www.linkedin.com/jobs/search-results/"
                + "?currentJobId=123&keywords=Java&geoId=105072130"
                + "&f_SAL=f_SA_id_226001%3A274001%2C272015%24f_SA_id_225001%3A272001&f_TPR=r604800"
                + "&origin=JOB_SEARCH_PAGE_JOB_FILTER";

        assertEquals("", LinkedInUrls.searchQueryMismatch(requested, actual));
    }

    @Test
    void rejectsARestoredPageForAnotherLocation() {
        String requested = "https://www.linkedin.com/jobs/search/"
                + "?keywords=Java&geoId=105072130&start=25";
        String actual = "https://www.linkedin.com/jobs/search/"
                + "?keywords=Java&geoId=106178099&start=25";

        assertEquals(
                "geoId expected=105072130 actual=106178099",
                LinkedInUrls.searchQueryMismatch(requested, actual)
        );
    }

    @Test
    void rejectsDroppedSearchFilters() {
        String requested = "https://www.linkedin.com/jobs/search-results/?keywords=Java&f_SAL=f_SA_id_227001%3A277001";
        String actual = "https://www.linkedin.com/jobs/search-results/?keywords=Java";
        assertEquals("f_SAL expected=f_SA_id_227001:277001 actual=null",
                LinkedInUrls.searchQueryMismatch(requested, actual));
    }

    @Test
    void rejectsARestoredPageAtAnotherOffset() {
        String requested = "https://www.linkedin.com/jobs/search/"
                + "?keywords=Java&start=25";
        String actual = "https://www.linkedin.com/jobs/search/"
                + "?keywords=Java&start=150";

        assertEquals(
                "start expected=25 actual=150",
                LinkedInUrls.searchQueryMismatch(requested, actual)
        );
    }
}
