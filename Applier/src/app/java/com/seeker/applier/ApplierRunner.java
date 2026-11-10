package com.seeker.applier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.seeker.browser.LinkedInJobPage;
import com.seeker.browser.BrowserInteraction;
import com.seeker.browser.ManagedBrowser;
import com.seeker.codex.CodexClient;
import com.seeker.codex.UsageLimitExceededException;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Opens a vacancy, checks for a matching site script, then delegates the form to Codex. */
public final class ApplierRunner {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path projectRoot;

    public ApplierRunner(Path projectRoot) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
    }

    public Result run(String jobUrl) throws IOException, InterruptedException, UsageLimitExceededException {
        Properties runtime = load(projectRoot.resolve("Driver/collector/java_linkedin/runtime.properties"));
        Properties collector = load(projectRoot.resolve("Driver/collector/config/linkedin.properties"));
        Path profile = resolve(projectRoot, runtime.getProperty("profile.directory"));
        String channel = collector.getProperty("browserChannel", "chrome").strip();
        String python = runtime.getProperty("python.executable", "python").strip();

        try (ManagedBrowser browser = ManagedBrowser.connect(profile, channel)) {
            double timeoutMs = Double.parseDouble(
                    collector.getProperty("pageTimeoutSeconds", "60")) * 1000.0;
            browser.context().setDefaultTimeout(timeoutMs);
            browser.context().setDefaultNavigationTimeout(timeoutMs);
            Page jobPage = browser.newPage();
            java.util.Map<?, ?> vacancy;
            try {
                vacancy = openVacancy(jobPage, jobUrl);
            } catch (BrowserInteraction.WaitTimeout stalled) {
                browser.keepOpen(jobPage);
                return new Result("partially_filled", stalled.getMessage()
                        + ". The vacancy remains open for manual preparation.", false);
            }
            if (Boolean.TRUE.equals(vacancy.get("easy"))) {
                return new Result("skipped", "LinkedIn Easy Apply; no form was opened or Codex started.", false);
            }
            String vacancyLocation = vacancy.get("location").toString();
            browser.keepOpen(jobPage);
            String vacancyUrl = jobPage.url();
            Page formPage;
            try {
                formPage = openApplication(jobPage);
            } catch (BrowserInteraction.WaitTimeout stalled) {
                for (Page page : browser.context().pages()) {
                    if (!page.isClosed() && applicationTab(page, jobPage)) browser.keepOpen(page);
                }
                return new Result("partially_filled", stalled.getMessage()
                        + ". No second Apply action was attempted; continue manually in the open tab.", false);
            }
            browser.keepOpen(formPage);
            if (sameHost(vacancyUrl, formPage.url())) {
                return new Result("partially_filled",
                        "The external Apply destination did not open. The vacancy remains open for manual preparation.", false);
            }

            int tabIndex = browser.tabIndex(formPage);
            Path adapter = matchingAdapter(formPage.url());
            if (adapter != null) {
                return runAdapter(adapter, tabIndex, formPage.url());
            }

            String step;
            if (formPage != jobPage) {
                step = "The employer page opened in a new tab; continue in this tab.";
            } else {
                step = "The initial Apply action redirected to the employer page; continue in this tab.";
            }
            return runCodex(formPage.url(), tabIndex, step, vacancyLocation, python);
        }
    }

    static java.util.Map<?, ?> openVacancy(Page jobPage, String jobUrl) throws IOException {
        try (var interaction = new BrowserInteraction(jobPage, ApplierRunner::jobData)) {
            interaction.open("opening the vacancy", jobUrl);
            return interaction.await("waiting for vacancy details and Apply", BrowserInteraction.LOAD, () -> {
                var state = LinkedInJobPage.snapshot(jobPage);
                if (Boolean.TRUE.equals(state.get("auth"))) throw new IOException("LinkedIn requires authentication");
                if (Boolean.TRUE.equals(state.get("closed"))) throw new IOException("Vacancy is closed");
                if (Boolean.TRUE.equals(state.get("description"))
                        && (Boolean.TRUE.equals(state.get("easy")) || Boolean.TRUE.equals(state.get("apply"))))
                    return BrowserInteraction.Observation.ready(state);
                return BrowserInteraction.Observation.pending(state.get("document") + ":" + state.get("title")
                        + ":" + state.get("description"), "vacancy details or Apply not ready");
            });
        }
    }

    private static boolean jobData(com.microsoft.playwright.Request request) {
        return request.url().contains("/voyager/api/") && request.url().toLowerCase().contains("job");
    }

    /** Follows one Apply action, its popup or same-tab redirect, and the optional LinkedIn confirmation link. */
    static Page openApplication(Page jobPage) throws IOException {
        String vacancyUrl = jobPage.url();
        List<Page> before = List.copyOf(jobPage.context().pages());
        java.util.Set<Page> applicationPages = new java.util.HashSet<>();
        try (var interaction = new BrowserInteraction(jobPage, ApplierRunner::jobData)) {
            String initialLink = interaction.await("reading the Apply target", BrowserInteraction.ACTION, () ->
                    BrowserInteraction.Observation.ready((String) BrowserInteraction.read(jobPage, """
                        () => document.querySelector("a[aria-label='Apply on company website' i]")?.href || ''
                        """, null)));
            String href = interaction.await("reading the external destination", BrowserInteraction.ACTION, () ->
                    BrowserInteraction.Observation.ready((String) BrowserInteraction.read(jobPage, """
                        selector => document.querySelector(selector)?.getAttribute('href') || ''
                        """, LinkedInJobPage.EXTERNAL_APPLY)));
            java.util.Set<String> destinations = new java.util.HashSet<>(List.of(applyDestination(vacancyUrl, href), href));
            interaction.clickOnce("clicking external Apply", LinkedInJobPage.EXTERNAL_APPLY);
            final boolean[] confirmationClicked = {false};
            final String[] lastUrl = {""};
            final int[] stable = {0};
            return interaction.await("waiting for the employer application page", BrowserInteraction.LOAD, () -> {
                Page candidate = jobPage;
                for (Page page : jobPage.context().pages()) {
                    if (!before.contains(page) && !page.isClosed() && (applicationTab(page, jobPage)
                            || applicationPages.contains(page) || destinations.contains(page.url()))) {
                        applicationPages.add(page);
                        candidate = page;
                    }
                }
                interaction.watch(candidate);
                if (candidate.isClosed()) throw new IOException("Application tab was closed");
                String url = candidate.url();
                if (url.equals("about:blank")) return BrowserInteraction.Observation.pending("", "popup is loading");
                if (sameHost(vacancyUrl, url)) {
                    String confirmationLink = (String) BrowserInteraction.read(candidate, """
                        () => [...document.querySelectorAll("a[aria-label='Apply on company website' i]")]
                          .find(n => n.getClientRects().length)?.href || ''
                        """, null);
                    boolean confirmation = !confirmationLink.isBlank() && !confirmationLink.equals(initialLink);
                    if (confirmation && !confirmationClicked[0]) {
                        confirmationClicked[0] = true;
                        destinations.add(confirmationLink);
                        destinations.add(applyDestination(candidate.url(), confirmationLink));
                        try (var next = new BrowserInteraction(candidate, ApplierRunner::jobData)) {
                            next.clickOnce("confirming external Apply", "a[aria-label='Apply on company website' i]");
                        }
                    }
                    return BrowserInteraction.Observation.pending("confirmation:" + confirmation, "waiting for an external destination");
                }
                var state = (java.util.Map<?, ?>) BrowserInteraction.read(candidate, """
                    () => ({document: document.readyState,
                      ready: !!document.body && !!document.body.innerText.trim()
                        && !!document.querySelector('form, input, textarea, button, a[href*="apply" i], iframe[title*="application" i]')})
                    """, null);
                stable[0] = url.equals(lastUrl[0]) && Boolean.TRUE.equals(state.get("ready")) ? stable[0] + 1 : 0;
                lastUrl[0] = url;
                if (stable[0] >= 5)
                    return BrowserInteraction.Observation.ready(candidate);
                return BrowserInteraction.Observation.pending(url + ":" + state.get("document") + ":" + state.get("ready"),
                        "external document is loading or redirecting");
            });
        }
    }

    private static boolean applicationTab(Page page, Page vacancy) {
        for (Page current = page; current != null; current = current.opener()) {
            if (current == vacancy) return true;
        }
        return false;
    }

    private static String applyDestination(String jobUrl, String href) {
        URI link = URI.create(jobUrl).resolve(href);
        if (sameHost(jobUrl, link.toString()) && "/safety/go/".equals(link.getPath()) && link.getRawQuery() != null) {
            for (String parameter : link.getRawQuery().split("&")) {
                if (parameter.startsWith("url=")) return URLDecoder.decode(parameter.substring(4), StandardCharsets.UTF_8);
            }
        }
        return link.toString();
    }

    private Result runCodex(String formUrl, int tabIndex, String step,
                            String vacancyLocation, String python)
            throws IOException, InterruptedException, UsageLimitExceededException {
        Path proxy = projectRoot.resolve("Driver/codex_proxy/metrics_proxy.py");
        Path instruction = projectRoot.resolve("Applier/prompts/fill_form.md");
        Path schema = projectRoot.resolve("Applier/result.schema.json");
        Path resumePdf = projectRoot.resolve("Data/Profile/CV_DokE2026M.pdf");
        Path resumeText = projectRoot.resolve("Data/Profile/resume.txt");
        Path candidateProfile = projectRoot.resolve("Data/Profile/resume.ini");
        if (!Files.isRegularFile(resumePdf) || !Files.isRegularFile(resumeText)) {
            throw new IOException("Applier resume is missing from Data/Profile.");
        }
        Path input = Files.createTempFile("seeker-applier-input-", ".json");
        try {
            JSON.writeValue(input.toFile(), JSON.createObjectNode()
                    .put("form_url", formUrl)
                    .put("tab_index", tabIndex)
                    .put("resume_file_path", resumePdf.toString())
                    .put("vacancy_location", vacancyLocation)
                    .put("java_stopped_at", step));
            List<String> command = new ArrayList<>(List.of(
                    python, proxy.toString(),
                    "--run-id", "apply-" + Instant.now().toEpochMilli(),
                    "--operation", "job_application_preparation",
                    "--target", formUrl,
                    "--instruction", instruction.toString(),
                    "--input", input.toString(),
                    "--output-schema", schema.toString(),
                    "--allow-browser-tools",
                    "--browser-cdp-endpoint", ManagedBrowser.CDP_ENDPOINT,
                    "--context", resumeText.toString(),
                    "--context", candidateProfile.toString()
            ));
            Path facts = projectRoot.resolve("Data/Profile/Applier/facts.md");
            if (Files.isRegularFile(facts)) {
                command.add("--context");
                command.add(facts.toString());
            }
            String output = CodexClient.call(command, projectRoot);
            JsonNode result = JSON.readTree(output);
            return new Result(result.path("status").asText("partially_filled"),
                    result.path("note").asText("Preparation finished; inspect the open form."),
                    result.path("submission_observed").asBoolean(false));
        } finally {
            Files.deleteIfExists(input);
        }
    }

    private Result runAdapter(Path adapter, int tabIndex, String formUrl) throws IOException, InterruptedException {
        String node = System.getenv().getOrDefault("NODE", "node");
        Process process = new ProcessBuilder(node, adapter.toString(),
                ManagedBrowser.CDP_ENDPOINT, Integer.toString(tabIndex), formUrl).directory(projectRoot.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        if (exitCode != 0) throw new IOException("Application adapter failed: " + output.strip());
        JsonNode result = JSON.readTree(output);
        return new Result(result.path("status").asText("partially_filled"),
                result.path("note").asText("Adapter finished; inspect the open form."),
                result.path("submission_observed").asBoolean(false));
    }

    private Path matchingAdapter(String formUrl) throws IOException {
        Path adapterDirectory = projectRoot.resolve("Applier/adapters");
        if (!Files.isDirectory(adapterDirectory)) return null;
        String host = URI.create(formUrl).getHost();
        if (host == null) return null;
        Path adapter = adapterDirectory.resolve(host.toLowerCase() + ".mjs").normalize();
        return adapter.getParent().equals(adapterDirectory) && Files.isRegularFile(adapter)
                ? adapter : null;
    }

    private static Properties load(Path path) throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Path resolve(Path root, String configured) {
        Path path = Path.of(configured.strip());
        return path.isAbsolute() ? path.normalize() : root.resolve(path).normalize();
    }

    private static boolean sameHost(String first, String second) {
        String firstHost = URI.create(first).getHost();
        String secondHost = URI.create(second).getHost();
        return firstHost != null && secondHost != null && firstHost.equalsIgnoreCase(secondHost);
    }

    public record Result(String status, String note, boolean submissionObserved) { }
}
