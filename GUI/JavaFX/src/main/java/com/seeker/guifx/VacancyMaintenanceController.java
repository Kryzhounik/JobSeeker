package com.seeker.guifx;

import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

final class VacancyMaintenanceController {
    private final java.util.function.Supplier<ClientData> data;
    private final BackgroundTasks tasks;
    private final TitleBlacklist titleBlacklist;
    private final SettingsStore settingsStore;
    private final java.util.function.Supplier<Stage> owner;
    private final Runnable refreshJobs;
    private final Runnable refreshCollected;
    private final Consumer<String> message;
    private final Consumer<String> openUrl;

    VacancyMaintenanceController(java.util.function.Supplier<ClientData> data, BackgroundTasks tasks,
            TitleBlacklist titleBlacklist, SettingsStore settingsStore, java.util.function.Supplier<Stage> owner,
            Runnable refreshJobs, Runnable refreshCollected, Consumer<String> message, Consumer<String> openUrl) {
        this.data = data;
        this.tasks = tasks;
        this.titleBlacklist = titleBlacklist;
        this.settingsStore = settingsStore;
        this.owner = owner;
        this.refreshJobs = refreshJobs;
        this.refreshCollected = refreshCollected;
        this.message = message;
        this.openUrl = openUrl;
    }

    void openBlacklist() {
        tasks.run("Opening Black titles...", () -> { titleBlacklist.openInNotepad(); return null; },
                ignored -> message.accept("Opened Black titles"), "Black titles failed");
    }

    void startLinkedInCheck() {
        LinkedInAvailabilityChecker checker = new LinkedInAvailabilityChecker(
                data.get(), ClientData.findProjectRoot());
        tasks.runWithProgress("Loading LinkedIn candidates...", checker::run, summary -> {
            message.accept(summary.message());
            refreshJobs.run();
            boolean stopped = !summary.error().isBlank();
            Alert alert = new Alert(stopped ? Alert.AlertType.ERROR : Alert.AlertType.INFORMATION);
            alert.setTitle(stopped ? "LinkedIn check stopped" : "LinkedIn check complete");
            alert.setHeaderText(stopped ? summary.error() : "LinkedIn check complete");
            alert.setContentText(summary.details());
            alert.getDialogPane().setMinWidth(520);
            alert.initOwner(owner.get());
            alert.show();
        }, "LinkedIn check failed");
    }

    void startRefilter(boolean preview) {
        if (preview) {
            tasks.run("Collecting Refilter candidates...", data.get()::collectRefilterCandidates,
                    this::showRefilterPreview, "Refilter failed");
        } else {
            tasks.run("Running Refilter...", () -> {
                List<RefilterCandidate> candidates = data.get().collectRefilterCandidates();
                return data.get().applyRefilterCandidates(candidates);
            }, count -> {
                refreshJobs.run();
                refreshCollected.run();
                message.accept("Refilter applied " + count + " transitions");
            }, "Refilter failed");
        }
    }

    void addTitleToBlacklist(String value) {
        if (tasks.isRunning()) {
            message.accept("Finish the current operation first");
            return;
        }
        tasks.run("Updating title blacklist...", () -> titleBlacklist.add(value), added ->
                message.accept(added ? "Added to title blacklist: " + value.strip()
                        : "Already in title blacklist: " + value.strip()),
                "Title blacklist failed");
    }

    private void showRefilterPreview(List<RefilterCandidate> candidates) {
        var preview = new RefilterPreview(owner.get(), candidates,
                settingsStore.windowWidth("refilter_detail", 1160),
                settingsStore.windowHeight("refilter_detail", 540), this::applyRefilter, openUrl);
        Stage window = preview.window();
        window.setOnHiding(event -> {
            try { settingsStore.saveWindowSize("refilter_detail", window.getWidth(), window.getHeight()); }
            catch (IOException error) { message.accept("Settings save failed: " + error.getMessage()); }
        });
        preview.show();
    }

    private void applyRefilter(List<RefilterCandidate> chosen) {
        tasks.run("Applying selected Refilter transitions...", () -> data.get().applyRefilterCandidates(chosen),
                applied -> {
                    refreshJobs.run();
                    refreshCollected.run();
                    message.accept("Refilter applied " + applied + " transitions");
                }, "Refilter failed");
    }
}

