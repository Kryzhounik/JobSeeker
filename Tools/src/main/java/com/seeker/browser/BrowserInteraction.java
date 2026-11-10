package com.seeker.browser;

import com.microsoft.playwright.*;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import com.microsoft.playwright.options.WaitUntilState;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** One operation on one page. Only caller-selected data and semantic milestones extend waiting. */
public final class BrowserInteraction implements AutoCloseable {
    public record Policy(long probeMillis, long stalledMillis, long maximumMillis) {
        public Policy {
            if (probeMillis <= 0 || stalledMillis < probeMillis || maximumMillis < stalledMillis)
                throw new IllegalArgumentException("Invalid browser waiting policy");
        }
    }

    public static final Policy LOAD = new Policy(3_000, 30_000, 180_000);
    public static final Policy ACTION = new Policy(3_000, 20_000, 120_000);
    public static final Policy RESPONSE = new Policy(3_000, 180_000, 900_000);

    public record Observation<T>(T result, String progress, String detail) {
        public static <T> Observation<T> pending(String progress, String detail) {
            return new Observation<>(null, progress, detail);
        }
        public static <T> Observation<T> ready(T value) {
            return new Observation<>(value, "", "ready");
        }
    }

    @FunctionalInterface
    public interface Probe<T> { Observation<T> poll() throws IOException; }

    /** Waiting ended without an explicit page/browser failure; callers may offer manual continuation. */
    public static final class WaitTimeout extends IOException {
        private WaitTimeout(String message) { super(message); }
    }

    private final Page page;
    private final Predicate<Request> relevantData;
    private final Set<String> milestones = new HashSet<>();
    private final Set<Page> watched = new HashSet<>();
    private final java.util.List<Runnable> detach = new java.util.ArrayList<>();
    private long lastProgress = System.nanoTime();
    private String lastUseful = "operation started";
    private String failure;
    private int documentStatus;
    private final Set<Request> loading = new HashSet<>();
    private String resourceError;
    private boolean initialLoadingUnknown;

    public BrowserInteraction(Page page, Predicate<Request> relevantData) {
        this.page = page;
        this.relevantData = relevantData;
        // Attaching cannot prove that earlier application requests have completed,
        // even when the document itself has already reached DOMContentLoaded.
        initialLoadingUnknown = !"about:blank".equals(page.url());
        watch(page);
    }

    /** Include a popup in the same operation's progress and failure observations. */
    public void watch(Page target) {
        if (!watched.add(target)) return;
        if (target.isClosed() || !target.context().browser().isConnected()) return;
        if (target.url().startsWith("chrome-error:")) failure = "browser could not load the document";
        Consumer<Request> started = request -> {
            if (significant(target, request)) {
                loading.add(request);
                advance("request started " + path(request.url()));
            }
        };
        Consumer<Response> response = value -> {
            Request request = value.request();
            if (mainDocument(target, request)) {
                if (target == page) documentStatus = value.status();
                if (value.status() >= 400) failure = "main document returned HTTP " + value.status();
                advance("main document HTTP " + value.status() + " " + path(request.url()));
            } else if (significant(target, request)) {
                if (value.status() >= 400) {
                    resourceError = "resource returned HTTP " + value.status() + " " + path(request.url());
                    if (relevantData.test(request)) failure = resourceError;
                } else advance("resource response " + path(request.url()));
            }
        };
        Consumer<Request> finished = request -> {
            if (loading.remove(request) || significant(target, request)) {
                Response value = request.response();
                if (value != null && value.status() < 400) advance("download complete " + path(request.url()));
            }
        };
        Consumer<Request> failed = request -> {
            if (loading.remove(request) || significant(target, request)) {
                resourceError = "resource load failed: " + request.failure() + " " + path(request.url());
                if (relevantData.test(request)) failure = resourceError;
            }
            if (mainDocument(target, request) && !"net::ERR_ABORTED".equals(request.failure()))
                failure = "main document load failed: " + request.failure();
        };
        Consumer<Page> crashed = ignored -> failure = "tab crashed";
        target.onRequest(started);
        target.onResponse(response);
        target.onRequestFinished(finished);
        target.onRequestFailed(failed);
        target.onCrash(crashed);
        detach.add(() -> {
            target.offRequest(started);
            target.offResponse(response);
            target.offRequestFinished(finished);
            target.offRequestFailed(failed);
            target.offCrash(crashed);
        });
    }

    private boolean significant(Page target, Request request) {
        return mainDocument(target, request) || relevantData.test(request)
                || ((request.resourceType().equals("script") || request.resourceType().equals("stylesheet"))
                    && request.frame().equals(target.mainFrame()));
    }

    private boolean mainDocument(Page target, Request request) {
        return request.isNavigationRequest() && request.frame().equals(target.mainFrame());
    }

    private static String path(String url) {
        try { var uri = URI.create(url); return uri.getHost() + uri.getPath(); }
        catch (IllegalArgumentException ignored) { return "document"; }
    }

    private void advance(String milestone) {
        if (milestone != null && !milestone.isBlank() && milestones.add(milestone)) {
            lastProgress = System.nanoTime();
            lastUseful = milestone.substring(0, Math.min(180, milestone.length()));
        }
    }

    /** Navigation is issued once; a slow commit is observed, never retried by reloading. */
    public void open(String stage, String url) throws IOException {
        try {
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.COMMIT)
                    .setTimeout(LOAD.probeMillis()));
        } catch (TimeoutError slowCommit) {
            // The navigation is still in flight. Continue observing it.
        } catch (PlaywrightException error) {
            throw diagnostic(stage, error.getMessage(), error);
        }
        await(stage, LOAD, () -> documentStatus >= 200 && documentStatus < 300
                && !page.url().equals("about:blank")
                ? Observation.ready(true) : Observation.pending("", "waiting for main document"));
    }

    public <T> T await(String stage, Policy policy, Probe<T> probe) throws IOException {
        long started = System.nanoTime();
        boolean inheritedLoadingUnknown = initialLoadingUnknown;
        initialLoadingUnknown = false;
        lastProgress = started;
        String detail = "not observed yet";
        while (true) {
            check(stage);
            if (Thread.currentThread().isInterrupted()) throw diagnostic(stage, "interrupted", null);
            try {
                Observation<T> observation = probe.poll();
                check(stage);
                advance(observation.progress());
                detail = observation.detail();
                if (observation.result() != null) return observation.result();
            } catch (TimeoutError temporarilyBusy) {
                detail = "DOM temporarily unavailable (short probe timed out)";
            } catch (PlaywrightException error) {
                check(stage);
                String message = error.getMessage();
                if (message == null || !(message.contains("Execution context was destroyed")
                        || message.contains("Cannot find context") || message.contains("navigation")))
                    throw diagnostic(stage, message, error);
                detail = "document is being replaced during navigation";
            } catch (IOException error) {
                throw diagnostic(stage, error.getMessage(), error);
            }
            long now = System.nanoTime();
            if (elapsed(started, now) >= policy.maximumMillis())
                throw new WaitTimeout(diagnostic(stage, "overall safety limit reached; " + detail, null).getMessage());
            if (elapsed(lastProgress, now) >= policy.stalledMillis()) {
                // An in-flight resource is evidence of loading, not evidence of byte progress.
                // Its grace period is anchored to this phase, never renewed by repeated requests.
                long slowLoadBudget = Math.min(policy.maximumMillis(), policy.stalledMillis() * 3);
                if ((!loading.isEmpty() || inheritedLoadingUnknown) && elapsed(started, now) < slowLoadBudget) {
                    page.waitForTimeout(100);
                    continue;
                }
                String reason = !loading.isEmpty() ? "loading remains unfinished; no confirmed progress"
                        : inheritedLoadingUnknown ? "initial loading state remains unknown; no confirmed progress"
                        : "no useful progress";
                if (resourceError != null) reason += "; " + resourceError;
                throw new WaitTimeout(diagnostic(stage, reason + "; " + detail, null).getMessage());
            }
            // Pumps Playwright events; Thread.sleep would prevent network observations.
            page.waitForTimeout(100);
        }
    }

    private static long elapsed(long start, long end) { return (end - start) / 1_000_000; }

    private void check(String stage) throws IOException {
        if (page.context().browser() != null && !page.context().browser().isConnected())
            throw diagnostic(stage, "Browser connection lost", null);
        if (page.isClosed()) throw diagnostic(stage, "tab was closed", null);
        if (failure != null) throw diagnostic(stage, failure, null);
    }

    private IOException diagnostic(String stage, String reason, Throwable cause) {
        return new IOException(stage + ": " + reason + "; last useful progress: " + lastUseful
                + "; idle " + elapsed(lastProgress, System.nanoTime()) + " ms", cause);
    }

    /** Bounded DOM snapshot; use this inside probes instead of unbounded nested reads. */
    public static Object read(Page page, String function, Object argument) {
        return read(page, function, argument, LOAD.probeMillis());
    }

    static Object read(Page page, String function, Object argument, long timeout) {
        // Return a primitive snapshot, not a live object requiring another renderer evaluation
        // during jsonValue(). The renderer can become busy again immediately after the probe.
        var handle = page.waitForFunction("arg => JSON.stringify({value: (" + function + ")(arg)})", argument,
                new Page.WaitForFunctionOptions().setPollingInterval(100).setTimeout(timeout));
        try { return new Gson().fromJson((String) handle.jsonValue(), java.util.Map.class).get("value"); }
        finally { handle.dispose(); }
    }

    public void fill(String stage, String selector, String text) throws IOException {
        await(stage, ACTION, () -> {
            page.locator(selector).fill(text, new Locator.FillOptions().setTimeout(ACTION.probeMillis()));
            return Observation.ready(true);
        });
    }

    public Locator find(String stage, String selector) throws IOException {
        return await(stage, LOAD, () -> {
            boolean found = Boolean.TRUE.equals(read(page, """
                selector => [...document.querySelectorAll(selector)].filter(n => n.getClientRects().length
                  && getComputedStyle(n).visibility !== 'hidden').length === 1
                """, selector));
            return found ? Observation.ready(page.locator(selector))
                    : Observation.pending("", "waiting for a unique visible element");
        });
    }

    /** Readiness and dispatch are atomic in the renderer, including in background tabs.
     * An ambiguous dispatch failure is never retried. Confirmation belongs to a separate await. */
    public void clickOnce(String stage, String selector) throws IOException {
        await(stage, ACTION, () -> {
            // First obtain readiness without dispatch, so a short busy-DOM timeout is recoverable.
            boolean ready = Boolean.TRUE.equals(read(page, """
                selector => {
                  const nodes = [...document.querySelectorAll(selector)].filter(n => n.getClientRects().length
                    && getComputedStyle(n).visibility !== 'hidden');
                  return nodes.length === 1 && !nodes[0].matches(':disabled')
                    && !nodes[0].closest('[aria-disabled="true" i], [inert]');
                }
                """, selector));
            if (!ready) return Observation.pending("", "waiting for a unique enabled click target");
            try {
                // waitForFunction may re-evaluate its expression after navigation. Dispatch through
                // one CDP command instead: re-evaluating a click could submit the same operation twice.
                Object clicked = dispatchClick(page, selector);
                return Boolean.TRUE.equals(clicked) ? Observation.ready(true)
                        : Observation.pending("", "click target was replaced or disabled");
            } catch (PlaywrightException ambiguous) {
                // Dispatch may have succeeded before navigation replaced the context. Never dispatch again;
                // the caller's separate result/confirmation probe determines success or reports a stall.
                return Observation.ready(true);
            }
        });
    }

    private static boolean dispatchClick(Page page, String selector) {
        CDPSession session = page.context().newCDPSession(page);
        try {
            JsonObject parameters = new JsonObject();
            parameters.addProperty("expression", "(" + """
                selector => {
                  const nodes = [...document.querySelectorAll(selector)].filter(n => n.getClientRects().length
                    && getComputedStyle(n).visibility !== 'hidden');
                  if (nodes.length !== 1 || nodes[0].matches(':disabled')
                      || nodes[0].closest('[aria-disabled="true" i], [inert]')) return false;
                  nodes[0].click(); return true;
                }
                """ + ")(" + new Gson().toJson(selector) + ")");
            parameters.addProperty("timeout", ACTION.probeMillis());
            parameters.addProperty("returnByValue", true);
            parameters.addProperty("userGesture", true);
            JsonObject result = session.send("Runtime.evaluate", parameters);
            if (result.has("exceptionDetails")) throw new PlaywrightException("DOM click outcome is unknown");
            return result.getAsJsonObject("result").has("value")
                    && result.getAsJsonObject("result").get("value").getAsBoolean();
        } finally { session.detach(); }
    }

    @Override public void close() { detach.forEach(Runnable::run); }
}
