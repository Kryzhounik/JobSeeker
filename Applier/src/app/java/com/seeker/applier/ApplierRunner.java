package com.seeker.applier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.seeker.applier.shared.LinkedInJobPage;
import com.seeker.applier.shared.ManagedBrowser;
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
import java.util.function.Predicate;

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
            jobPage.navigate(jobUrl);
            String vacancyUrl = jobPage.url();
            Locator vacancy = jobPage.locator("body");
            if (LinkedInJobPage.isEasyApply(vacancy)) {
                return new Result("skipped", "LinkedIn Easy Apply; no form was opened or Codex started.", false);
            }
            String vacancyLocation = LinkedInJobPage.location(vacancy);
            Locator apply = jobPage.locator(
                    "#jobs-apply-button-id[aria-label*='on company website' i], "
                            + "a[aria-label*='on company website' i], "
                            + "button[aria-label*='on company website' i]");
            if (apply.count() == 0) {
                browser.keepOpen(jobPage);
                return new Result("partially_filled", "No external Apply link was found for this vacancy.", false);
            }

            Page formPage = clickForPopup(jobPage, apply.first());
            formPage.waitForTimeout(800);
            if (sameHost(vacancyUrl, formPage.url())) {
                // LinkedIn sometimes records "Clicked apply" first, then exposes
                // the actual company-site link on the same job page.
                Locator companyLink = jobPage.locator("a[aria-label='Apply on company website' i]");
                if (companyLink.count() > 0) {
                    formPage = clickForPopup(jobPage, companyLink.first());
                    formPage.waitForTimeout(800);
                }
            }
            browser.keepOpen(jobPage);
            browser.keepOpen(formPage);

            if (sameHost(vacancyUrl, formPage.url())) {
                return new Result("partially_filled",
                        "The external Apply destination did not open. The vacancy remains open for manual preparation.",
                        false);
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

    private static Page clickForPopup(Page jobPage, Locator apply) {
        List<Page> pagesBeforeClick = List.copyOf(jobPage.context().pages());
        String href = apply.getAttribute("href");
        String destination = applyDestination(jobPage.url(), href);
        Predicate<Page> isApplyPage = page -> jobPage.equals(page.opener())
                || destination != null && (destination.equals(page.url()) || href.equals(page.url()));
        int popupTimeoutMs = "_blank".equalsIgnoreCase(apply.getAttribute("target")) ? 30000 : 4000;
        try {
            return jobPage.context().waitForPage(
                    new BrowserContext.WaitForPageOptions().setTimeout(popupTimeoutMs)
                            .setPredicate(isApplyPage), () -> {
                        try {
                            apply.click();
                        } catch (TimeoutError clickTimeout) {
                            throw new IllegalStateException("Clicking external Apply timed out.", clickTimeout);
                        }
                    });
        } catch (TimeoutError noPopup) {
            for (Page page : jobPage.context().pages()) {
                if (!pagesBeforeClick.contains(page) && isApplyPage.test(page)) {
                    return page;
                }
            }
            return jobPage;
        }
    }

    private static String applyDestination(String jobUrl, String href) {
        if (href == null) return null;
        URI link = URI.create(jobUrl).resolve(href);
        if (sameHost(jobUrl, link.toString()) && "/safety/go/".equals(link.getPath())
                && link.getRawQuery() != null) {
            for (String parameter : link.getRawQuery().split("&")) {
                if (parameter.startsWith("url=")) {
                    return URLDecoder.decode(parameter.substring(4), StandardCharsets.UTF_8);
                }
            }
        }
        return link.toString();
    }

    private Result runCodex(String formUrl, int tabIndex, String step,
                            String vacancyLocation, String python)
            throws IOException, InterruptedException, UsageLimitExceededException {
        Path proxy = projectRoot.resolve("Driver/codex_proxy/metrics_proxy.py");
        Path instruction = projectRoot.resolve("Applier/fill_form.md");
        Path schema = projectRoot.resolve("Applier/result.schema.json");
        Path resumePdf = projectRoot.resolve("Data/applier/CV_DokE2026M.pdf");
        Path resumeText = projectRoot.resolve("Data/applier/resume.txt");
        Path candidateProfile = projectRoot.resolve("Driver/analyzer/config/resume.ini");
        if (!Files.isRegularFile(resumePdf) || !Files.isRegularFile(resumeText)) {
            throw new IOException("Applier resume is missing from Data/applier.");
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
            Path facts = projectRoot.resolve("Data/applier/facts.md");
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
