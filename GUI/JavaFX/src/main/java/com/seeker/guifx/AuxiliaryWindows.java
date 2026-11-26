package com.seeker.guifx;

import java.nio.file.Path;
import java.util.function.Consumer;

final class AuxiliaryWindows {
    private final CompaniesController companies;
    private final ApplicationsController applications;
    private final RunsController runs;
    private final CollectedJobsController collected;
    private final ConfigController config;
    private final WindowSupport windows;

    AuxiliaryWindows(
            ClientData data,
            Consumer<Integer> openJob,
            SettingsStore settingsStore,
            Consumer<String> addTitleToBlacklist,
            Consumer<CollectionRunDto> resumeRun,
            Path collectionSettingsRoot
    ) {
        windows = new WindowSupport(settingsStore);
        companies = new CompaniesController(data, windows);
        applications = new ApplicationsController(data, windows, openJob, companies::showCompanies);
        runs = new RunsController(data, windows, resumeRun);
        collected = new CollectedJobsController(data, windows, addTitleToBlacklist, settingsStore);
        config = new ConfigController(data, windows, collectionSettingsRoot);
    }

    void showCompanies(Integer focusId) { companies.showCompanies(focusId); }
    void showApplications(Integer focusId) { applications.showApplications(focusId); }
    void showRuns() { runs.showRuns(); }
    void showCollected() { collected.showCollected(); }
    void showConfig() { config.showConfig(); }
    void refreshRunsIfOpen() { runs.refreshIfOpen(); }
    void refreshCompaniesIfOpen() { companies.refreshIfOpen(); }
    void refreshApplicationsIfOpen() { applications.refreshIfOpen(); }
    void refreshCollectedIfOpen() { collected.refreshIfOpen(); }
    void setWorkflowRunning(boolean running) { runs.setWorkflowRunning(running); }
    void shutdown() { windows.shutdown(); }
}
