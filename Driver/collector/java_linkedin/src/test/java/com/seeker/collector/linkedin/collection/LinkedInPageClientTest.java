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
}
