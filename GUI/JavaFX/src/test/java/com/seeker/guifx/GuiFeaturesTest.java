package com.seeker.guifx;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuiFeaturesTest {
    @TempDir Path root;

    @Test void titleBlacklistNormalizesAndDeduplicates() throws Exception {
        Path blacklistFile = root.resolve(
                "Data/Profile/Driver/collector/linkedin_preview_blocked_titles.txt");
        TitleBlacklist blacklist = new TitleBlacklist(root);
        if (!blacklist.add("  Senior   Java  ") || blacklist.add("senior java")) {
            throw new AssertionError("Title blacklist deduplication failed");
        }
        if (!Files.readString(blacklistFile).equals("Senior Java" + System.lineSeparator())) {
            throw new AssertionError("Title blacklist normalization failed");
        }
    }

    @Test void settingsPersistAndRestore() throws Exception {
        SettingsStore settings = new SettingsStore(root);
        settings.saveStatusFilters(java.util.Map.of("New", true), false, true);
        settings.saveWindowSize("companies", 812, 631);
        settings.saveTableSort("company", "Applications", true);
        settings.saveCollectedStage("CLEANED");
        SettingsStore restored = new SettingsStore(root);
        if (restored.windowWidth("companies", 760) != 812
                || restored.windowHeight("companies", 620) != 631
                || !restored.tableSortColumn("company").equals("Applications")
                || !restored.tableSortDescending("company")
                || !restored.collectedStage().equals("CLEANED")
                || !restored.showUnavailable()) {
            throw new AssertionError("GUI settings restoration failed");
        }
    }
}
