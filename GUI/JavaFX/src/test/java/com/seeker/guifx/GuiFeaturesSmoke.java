package com.seeker.guifx;

import java.nio.file.Files;
import java.nio.file.Path;

public final class GuiFeaturesSmoke {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("seeker-gui-smoke-");
        Path blacklistFile = root.resolve(
                "Driver/collector/filtering/linkedin_preview_blocked_titles.txt");
        Path settingsFile = root.resolve("GUI/JavaFX/gui-settings.json");
        try {
            TitleBlacklist blacklist = new TitleBlacklist(root);
            if (!blacklist.add("  Senior   Java  ") || blacklist.add("senior java")) {
                throw new AssertionError("Title blacklist deduplication failed");
            }
            if (!Files.readString(blacklistFile).equals("Senior Java" + System.lineSeparator())) {
                throw new AssertionError("Title blacklist normalization failed");
            }

            SettingsStore settings = new SettingsStore(root);
            settings.saveWindowSize("companies", 812, 631);
            settings.saveTableSort("company", "Applications", true);
            settings.saveCollectedStage("CLEANED");
            SettingsStore restored = new SettingsStore(root);
            if (restored.windowWidth("companies", 760) != 812
                    || restored.windowHeight("companies", 620) != 631
                    || !restored.tableSortColumn("company").equals("Applications")
                    || !restored.tableSortDescending("company")
                    || !restored.collectedStage().equals("CLEANED")) {
                throw new AssertionError("GUI settings restoration failed");
            }
            System.out.println("GUI feature checks OK");
        } finally {
            Files.deleteIfExists(blacklistFile);
            Files.deleteIfExists(blacklistFile.getParent());
            Files.deleteIfExists(blacklistFile.getParent().getParent());
            Files.deleteIfExists(blacklistFile.getParent().getParent().getParent());
            Files.deleteIfExists(settingsFile);
            Files.deleteIfExists(settingsFile.getParent());
            Files.deleteIfExists(settingsFile.getParent().getParent());
            Files.deleteIfExists(root);
        }
    }
}
