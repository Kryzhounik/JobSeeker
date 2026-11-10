package com.seeker.browser;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import java.io.IOException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

/** Local HTML only: isolated headless browser, no account or managed browser. */
class BrowserInteractionTest {
    static Playwright playwright;
    static Browser browser;
    @BeforeAll static void start() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }
    @AfterAll static void stop() { browser.close(); playwright.close(); }

    @Test void semanticProgressExtendsWaitingPastTheInitialStallBudget() throws Exception {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            Page page = context.newPage();
            page.setContent("<main data-step='0'></main>");
            page.evaluate("() => { let step=0; const timer=setInterval(() => { document.querySelector('main').dataset.step=++step; if(step===6) clearInterval(timer); }, 250); }");
            try (var interaction = new BrowserInteraction(page, r -> false)) {
                int result = interaction.await("loading useful data", new BrowserInteraction.Policy(100, 700, 5000), () -> {
                    int step = Integer.parseInt((String) BrowserInteraction.read(page,
                            "() => document.querySelector('main').dataset.step", null, 100));
                    return step == 6 ? BrowserInteraction.Observation.ready(step)
                            : BrowserInteraction.Observation.pending("data step " + step, "loading");
                });
                assertEquals(6, result);
            }
        }
    }

    @Test void spinnerAndAdvertisementActivityDoNotExtendWaiting() {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            Page page = context.newPage();
            page.setContent("<main>LoadingР В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В Р вЂ Р В РІР‚С™Р вЂ™Р’В Р В Р’В Р вЂ™Р’В Р В РІР‚в„ўР вЂ™Р’В Р В Р’В Р В РІР‚В Р В Р’В Р Р†Р вЂљРЎв„ўР В Р Р‹Р Р†РІР‚С›РЎС›Р В Р’В Р вЂ™Р’В Р В Р вЂ Р В РІР‚С™Р Р†РІР‚С›РЎС›Р В Р’В Р Р†Р вЂљРІвЂћСћР В РІР‚в„ўР вЂ™Р’В¦</main><aside></aside>");
            page.evaluate("() => setInterval(() => document.querySelector('aside').innerText=Date.now(), 50)");
            try (var interaction = new BrowserInteraction(page, r -> false)) {
                IOException failure = assertThrows(IOException.class, () -> interaction.await("loading application",
                        new BrowserInteraction.Policy(100, 500, 3000), () -> {
                            BrowserInteraction.read(page, "() => document.body.innerText", null, 100);
                            return BrowserInteraction.Observation.pending("spinner", "still loading");
                        }));
                assertTrue(failure.getMessage().contains("no useful progress"));
                assertTrue(failure.getMessage().contains("last useful progress"));
                assertTrue(failure.getMessage().contains("loading application"));
            }
        }
    }

    @Test void lateReplacedInputAndButtonAreFoundAndActedOnOnce() throws Exception {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            Page page = context.newPage();
            page.setContent("<main></main>");
            page.evaluate("""
                () => { window.clicks=0; setTimeout(() => {
                  document.querySelector('main').innerHTML='<input id="value"><button id="go" disabled>Wait</button>';
                  setTimeout(() => document.querySelector('#go').outerHTML='<button id="go" onclick="window.clicks++">Go</button>', 300);
                }, 500); }
                """);
            try (var interaction = new BrowserInteraction(page, r -> false)) {
                interaction.fill("entering value", "#value", "one");
                interaction.clickOnce("clicking replaced button", "#go");
                assertEquals("one", page.locator("#value").inputValue());
                assertEquals(1, ((Number) page.evaluate("window.clicks")).intValue());
            }
        }
    }

    @Test void newAndExistingPagesSurviveRedirectAndDelayedUsefulRequests() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/start")) {
                    exchange.getResponseHeaders().add("Location", "/page");
                    exchange.sendResponseHeaders(302, -1);
                } else {
                    String body;
                    if (path.startsWith("/data")) {
                        Thread.sleep(300);
                        body = "done";
                    } else {
                        body = "<main>Loading</main><script>setTimeout(async () => { await fetch('/data1'); "
                                + "await fetch('/data2'); await fetch('/data3'); window.ready=true; }, 150)</script>";
                    }
                    exchange.getResponseHeaders().add("Content-Type", "text/html");
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally { exchange.close(); }
        });
        server.start();
        try {
            for (boolean existing : new boolean[]{false, true}) {
                try (var context = browser.newContext()) {
                    Page page = context.newPage();
                    String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/start";
                    if (existing) page.navigate(url);
                    try (var interaction = new BrowserInteraction(page, r -> r.url().contains("/data"))) {
                        if (!existing) interaction.open("opening redirected document", url);
                        assertTrue(interaction.await("loading data", new BrowserInteraction.Policy(100, 600, 5000), () ->
                                Boolean.TRUE.equals(BrowserInteraction.read(page, "() => !!window.ready", null, 500))
                                        ? BrowserInteraction.Observation.ready(true)
                                        : BrowserInteraction.Observation.pending("", "waiting for useful data")));
                        assertTrue(page.url().endsWith("/page"));
                    }
                }
            }
        } finally { server.stop(0); executor.shutdownNow(); }
    }

    @Test void evenContinuedSemanticChangesHaveAnOverallBound() {
        try (var context = browser.newContext()) {
            Page page = context.newPage();
            page.setContent("<main></main>");
            page.evaluate("() => { window.step=0; setInterval(() => window.step++, 100); }");
            try (var interaction = new BrowserInteraction(page, r -> false)) {
                IOException failure = assertThrows(IOException.class, () -> interaction.await("unending operation",
                        new BrowserInteraction.Policy(500, 2000, 3000), () -> BrowserInteraction.Observation.pending(
                                BrowserInteraction.read(page, "() => window.step", null, 100).toString(), "continuing")));
                assertTrue(failure.getMessage().contains("overall safety limit"));
            }
        }
    }

    @Test void aHangingRelevantRequestDoesNotCountAsOngoingProgress() {
        try (var context = browser.newContext()) {
            context.route("https://data.test/**", route -> { /* Deliberately never responds. */ });
            Page page = context.newPage();
            page.setContent("<main>Loading</main>");
            try (var interaction = new BrowserInteraction(page, r -> r.url().contains("data.test"))) {
                page.evaluate("() => { fetch('https://data.test/jobs').catch(() => {}); }");
                IOException failure = assertThrows(IOException.class, () -> interaction.await("waiting for job data",
                        new BrowserInteraction.Policy(100, 600, 3000), () ->
                                BrowserInteraction.Observation.pending("", "request has not completed")));
                assertTrue(failure.getMessage().contains("no confirmed progress"));
                assertTrue(failure.getMessage().contains("unfinished"));
            }
        }
    }

    @Test void requiredRequestHttpFailureReportsItsActualStatus() {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            context.route("https://data.test/**", route -> route.fulfill(new Route.FulfillOptions().setStatus(503).setBody("Unavailable")));
            Page page = context.newPage();
            page.setContent("<main>Loading</main>");
            try (var interaction = new BrowserInteraction(page, r -> r.url().contains("data.test"))) {
                page.evaluate("() => { fetch('https://data.test/required').catch(() => {}); }");
                IOException failure = assertThrows(IOException.class, () -> interaction.await("loading required data",
                        BrowserInteraction.LOAD, () -> BrowserInteraction.Observation.pending("", "waiting for data")));
                assertTrue(failure.getMessage().contains("HTTP 503"));
                assertTrue(failure.getMessage().contains("loading required data"));
            }
        }
    }

    @Test void preexistingHungRequestHasABoundedUnknownLoadingPeriod() {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            context.route("https://page.test/**", route -> route.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html").setBody("<main>Loading</main>")));
            context.route("https://data.test/**", route -> { /* Already started, never completes. */ });
            Page page = context.newPage();
            page.navigate("https://page.test/");
            Request active = page.waitForRequest(r -> r.url().contains("data.test"), () ->
                    page.evaluate("() => { fetch('https://data.test/required').catch(() => {}); }"));
            assertTrue(active.timing() == null || active.timing().responseEnd < 0);
            try (var interaction = new BrowserInteraction(page, r -> r.url().contains("data.test"))) {
                IOException failure = assertThrows(IOException.class, () -> interaction.await("existing loading",
                        new BrowserInteraction.Policy(100, 500, 4000), () ->
                                BrowserInteraction.Observation.pending("", "waiting for interface")));
                assertTrue(failure.getMessage().contains("initial loading state remains unknown"));
                assertTrue(failure.getMessage().contains("no confirmed progress"));
            }
        }
    }

    @Test void failureOfARequestStartedBeforeObserverIsStillReported() {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            context.route("https://page.test/**", route -> route.fulfill(new Route.FulfillOptions().setBody("Loading")));
            var pending = new java.util.concurrent.atomic.AtomicReference<Route>();
            context.route("https://data.test/**", pending::set);
            Page page = context.newPage();
            page.navigate("https://page.test/");
            page.waitForRequest(r -> r.url().contains("data.test"), () ->
                    page.evaluate("() => { fetch('https://data.test/required').catch(() => {}); }"));
            page.waitForCondition(() -> pending.get() != null, new Page.WaitForConditionOptions().setTimeout(3000));
            try (var interaction = new BrowserInteraction(page, r -> r.url().contains("data.test"))) {
                pending.get().abort("failed");
                IOException failure = assertThrows(IOException.class, () -> interaction.await("existing request failure",
                        BrowserInteraction.LOAD, () -> BrowserInteraction.Observation.pending("", "waiting")));
                assertTrue(failure.getMessage().contains("resource load failed"));
                assertTrue(failure.getMessage().contains("ERR_FAILED"));
            }
        }
    }

    @Test void slowScriptOrDataCompletesWithinBoundedLoadingGrace() throws Exception {
        slowLoading(false);
    }

    @Test void alreadyStartedScriptOrSpaRequestGetsInitialLoadingGrace() throws Exception {
        slowLoading(true);
    }

    private void slowLoading(boolean attachLate) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/boot.js") || path.equals("/data")) {
                    exchange.getResponseHeaders().add("Content-Type", path.endsWith("js") ? "application/javascript; charset=utf-8" : "application/json; charset=utf-8");
                    exchange.getResponseHeaders().add("X-Content-Type-Options", "nosniff");
                    String prefix = path.endsWith("js") ? "window.part=0;\n" : "{\"padding\":\"";
                    String chunk = path.endsWith("js") ? "window.part++;\n".repeat(700) : " ".repeat(8192);
                    String suffix = path.endsWith("js") ? "window.ready=true;" : "\"}";
                    exchange.sendResponseHeaders(200, prefix.length() + chunk.length() * 20 + suffix.length());
                    exchange.getResponseBody().write(prefix.getBytes(StandardCharsets.UTF_8));
                    exchange.getResponseBody().flush();
                    for (int i = 0; i < 20; i++) {
                        exchange.getResponseBody().write((path.endsWith("js") ? "window.part++;\n".repeat(700) : " ".repeat(8192)).getBytes(StandardCharsets.UTF_8));
                        exchange.getResponseBody().flush();
                        Thread.sleep(200);
                    }
                    exchange.getResponseBody().write((path.endsWith("js") ? "window.ready=true;" : "\"}").getBytes(StandardCharsets.UTF_8));
                } else {
                    String body = path.equals("/script-page") ? "<main>Loading</main><script src='/boot.js'></script>"
                            : "<main>Loading</main><script>fetch('/data').then(r=>r.json()).then(()=>window.ready=true)</script>";
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        try {
            for (String path : new String[]{"/data-page", "/script-page"}) {
                try (var context = browser.newContext()) {
                    Page page = context.newPage();
                    String url = "http://127.0.0.1:" + server.getAddress().getPort() + path;
                    if (attachLate) {
                        String resource = path.equals("/script-page") ? "/boot.js" : "/data";
                        Request active = page.waitForRequest(r -> r.url().endsWith(resource), () ->
                                page.navigate(url, new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.COMMIT)));
                        if (resource.equals("/data")) page.waitForLoadState(com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED);
                        assertTrue(active.timing() == null || active.timing().responseEnd < 0, "Request must already be running before observer creation");
                        assertFalse(Boolean.TRUE.equals(page.evaluate("!!window.ready")));
                    }
                    try (var interaction = new BrowserInteraction(page, r -> r.url().endsWith("/data"))) {
                        if (!attachLate) interaction.open("opening", url);
                        assertTrue(interaction.await("streaming one resource", new BrowserInteraction.Policy(500, 1500, 10000), () ->
                                Boolean.TRUE.equals(BrowserInteraction.read(page, "() => !!window.ready", null, 500))
                                        ? BrowserInteraction.Observation.ready(true)
                                        : BrowserInteraction.Observation.pending("", "still downloading")));
                    }
                }
            }
        } finally { server.stop(0); executor.shutdownNow(); }
    }
}
