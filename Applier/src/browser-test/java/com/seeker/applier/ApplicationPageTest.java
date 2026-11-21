package com.seeker.applier;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ApplicationPageTest {
    static Playwright playwright;
    static Browser browser;
    @BeforeAll static void start() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }
    @AfterAll static void stop() { browser.close(); playwright.close(); }

    static final String JOB = "<h1>Java engineer</h1><article class='jobs-description__container'>Requirements</article>";

    @Test void readyDescriptionDoesNotRequireH1OrWaitForRecommendationSpinner() throws Exception {
        for (String title : new String[]{"<p>Java engineer</p>", "<a href='/jobs/view/1'>Java engineer</a>"}) {
            try (var context = browser.newContext()) {
                context.setOffline(true);
                context.route("https://linkedin.test/**", r -> r.fulfill(new Route.FulfillOptions().setContentType("text/html")
                        .setBody("<main class='jobs-details'>" + title + "<article class='jobs-description__container'>Requirements</article>"
                                + "<aside><div role='progressbar'>Recommendations loading</div></aside>"
                                + "<button aria-label='Apply on company website'>Apply</button></main>")));
                var state = ApplierRunner.openVacancy(context.newPage(), "https://linkedin.test/jobs/1");
                assertEquals(true, state.get("description"));
                assertEquals(true, state.get("apply"));
            }
        }
    }

    @Test void waitsForLateApplyAfterVacancyDetails() throws Exception {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            context.route("https://linkedin.test/**", r -> r.fulfill(new Route.FulfillOptions().setContentType("text/html")
                    .setBody(JOB + "<script>setTimeout(() => { document.body.innerHTML += '<button aria-label=\"Apply on company website\">Apply</button>'; }, 1300)</script>")));
            Page page = context.newPage();
            var state = ApplierRunner.openVacancy(page, "https://linkedin.test/jobs/1");
            assertEquals(true, state.get("apply"));
        }
    }

    @Test void recognizesDelayedEasyApplyBeforeAnyClick() throws Exception {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            context.route("https://linkedin.test/**", r -> r.fulfill(new Route.FulfillOptions().setContentType("text/html")
                    .setBody(JOB + "<script>setTimeout(() => { document.body.innerHTML += '<button aria-label=\"Easy Apply\">Apply</button>'; }, 500)</script>")));
            var state = ApplierRunner.openVacancy(context.newPage(), "https://linkedin.test/jobs/1");
            assertEquals(true, state.get("easy"));
        }
    }

    @Test void delayedPopupConfirmationDoesNotClickTheOriginalLinkAgain() throws Exception {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            context.route("https://employer.test/**", r -> r.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html").setBody("<main><form><input></form>Application</main>")));
            context.route("https://linkedin.test/**", r -> r.fulfill(new Route.FulfillOptions().setContentType("text/html")
                    .setBody(JOB + """
                        <a aria-label='Apply on company website' href='https://employer.test/form'
                          onclick="event.preventDefault(); window.clicks=(window.clicks||0)+1;
                          const popup=window.open('about:blank'); setTimeout(() => popup.location.href=this.href, 1200)">Apply</a>
                        """)));
            Page page = context.newPage();
            ApplierRunner.openVacancy(page, "https://linkedin.test/jobs/1");
            Page result = ApplierRunner.openApplication(page);
            assertEquals("https://employer.test/form", result.url());
            assertEquals(1, ((Number) page.evaluate("window.clicks")).intValue());
        }
    }

    @Test void followsLinkedInConfirmationToANoopenerFormTab() throws Exception {
        try (var context = browser.newContext()) {
            context.setOffline(true);
            context.route("https://employer.test/**", r -> r.fulfill(new Route.FulfillOptions()
                    .setContentType("text/html").setBody("<main><form><input></form>Application</main>")));
            context.route("https://linkedin.test/**", r -> r.fulfill(new Route.FulfillOptions().setContentType("text/html")
                    .setBody(JOB + """
                        <button aria-label='Apply on company website' onclick="window.initialClicks=(window.initialClicks||0)+1;
                        document.querySelector('aside').innerHTML=`<a aria-label='Apply on company website' target='_blank'
                        rel='noopener' href='https://employer.test/form'>Continue</a>`">Apply</button><aside></aside>
                        """)));
            Page page = context.newPage();
            ApplierRunner.openVacancy(page, "https://linkedin.test/jobs/1");
            assertEquals("https://employer.test/form", ApplierRunner.openApplication(page).url());
            assertEquals(1, ((Number) page.evaluate("window.initialClicks")).intValue());
        }
    }

    @Test void followsPopupAndSameTabRedirectsWithDelayedDestination() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.createContext("/", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                if (path.equals("/start")) {
                    exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + port + "/form");
                    exchange.sendResponseHeaders(302, -1);
                } else {
                    String body;
                    if (path.equals("/form")) {
                        body = "<script>setTimeout(() => document.body.innerHTML='<main><form><input></form>Application</main>', 1200)</script>";
                    } else {
                        boolean popup = path.endsWith("popup");
                        String destination = "http://127.0.0.1:" + port + "/start";
                        body = path.endsWith("noopener")
                                ? JOB + "<a aria-label='Apply on company website' target='_blank' rel='noopener' href='http://127.0.0.1:" + port + "/form'>Apply</a>"
                                : JOB + "<button aria-label='Apply on company website' onclick=\""
                                + (popup ? "window.open('" + destination + "')" : "location.href='" + destination + "'")
                                + "\">Apply</button>";
                    }
                    byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "text/html");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            } finally { exchange.close(); }
        });
        server.start();
        try {
            for (String mode : new String[]{"popup", "same-tab", "noopener"}) {
                boolean popup = !mode.equals("same-tab");
                try (var context = browser.newContext()) {
                    Page page = context.newPage();
                    ApplierRunner.openVacancy(page, "http://localhost:" + port + "/" + mode);
                    Page form = ApplierRunner.openApplication(page);
                    assertEquals("http://127.0.0.1:" + port + "/form", form.url());
                    assertEquals(popup, form != page);
                    assertFalse(form.isClosed());
                    assertEquals(1, form.locator("input").count());
                }
            }
        } finally { server.stop(0); }
    }
}
