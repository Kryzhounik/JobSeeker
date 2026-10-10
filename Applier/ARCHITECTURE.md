# Applier architecture

This is a minimal design for version 0.1. Behavioral expectations
live in `REQUIREMENTS.md`; execution steps are in `PLAN.md`.

## Shared browser

The browser is an independent, long-lived Chromium process using the existing
`Data/browser_profiles/linkedin` profile. It is not owned by JavaFX, the
collector JAR, or Codex CLI. A small shared Java browser-access component does
"attach if active, otherwise start": connect to the local CDP endpoint when
available, or start the visible browser with that profile and then connect.
Expose CDP only on loopback.

The collector, JavaFX application preparation, and Playwright MCP are clients
of this browser. Java clients connect with Playwright's `connectOverCDP`;
Playwright MCP uses the same `--cdp-endpoint`. Each operation creates or claims
its own tab and closes only a tab it owns when appropriate. Disconnecting a
client must not close the shared default context or browser. Browser shutdown
is a separate explicit operation; manual browser closure is also valid.

`ManagedBrowser` starts or attaches to the local CDP browser. Each Java client
tracks its own pages; disconnecting closes only pages it owns unless the caller
marks a page to remain open. The collector remains independent of the GUI and
preserves its console entry points and login behavior.

## Application flow

1. JavaFX passes the selected job URL to Applier off the UI thread.
2. Applier reads the vacancy location from the stored job and opens an
   application-owned tab in the shared browser. It clicks the
   external Apply control, and observes a redirect or new tab.
3. Applier identifies the actual form and checks for a matching adapter: a
   Playwright script written for that form. If one exists, it fills the opened
   form. No adapter scripts exist yet.
4. If no adapter matches, Applier passes the form tab's current index, exact URL,
   and a short description of where it stopped to `Driver/codex_proxy`. Codex
   checks the index against Playwright MCP's tab list, then selects that
   already-opened tab and continues there.
5. The proxy starts Codex CLI with Playwright MCP configured for the shared
   CDP endpoint. Supply the existing resume and local applicant facts
   from `Data/`, plus the vacancy location. Codex applies the rules in the
   form-filling instruction, fills what they support, and stops before final
   submission. Unknown answers are left for the user.
6. Return filled or partially filled, with a short note about anything left
   to do. Display the result in JavaFX. The browser and form stay open for
   manual review, completion, and submission.
7. The user marks the vacancy Applied through the existing GUI action after
   submitting. Preparation itself does not change application tracking.

A collector tab and an application tab may coexist; each workflow uses its own
tab. If an accidental submission is reported or observed, mention it in the
result message. No separate submission-detection subsystem is required.

The adapter check is part of the normal choice of filler, even though no
adapter scripts exist yet. This does not require writing an adapter script
for version 0.1.

## Existing Codex Proxy

Reuse `Driver/codex_proxy` for CLI invocation, response handling, and token
usage accounting. Its browser-tools mode permits Playwright MCP for Applier
while analyzer calls keep their existing no-tools instruction. Configure
Playwright MCP for an Applier invocation with the shared browser's CDP endpoint.
Do not create another CLI launcher.

Applier supplies the form-filling instruction and input. The proxy continues
to handle CLI invocation and metrics, without form-filling logic. There is no
new retry, cancellation, or recovery mechanism in version 0.1.

## Integration boundaries

- `Driver/collector/java_linkedin`: retain search, extraction, persistence,
  login, and standalone batch commands; switch only browser acquisition and
  collector-owned tab cleanup to the shared model.
- `GUI/JavaFX`: provide the Apply command and display the preparation result in
  the existing job view. Do not make the collector depend on the GUI process.
- `Applier`: own application preparation, the Codex Proxy/MCP handoff, and
  the shared browser-access contract. Decide Java module wiring
  during implementation without duplicating browser lifecycle rules.

The full real-browser acceptance flow still needs to be exercised with an
external form, including collection running while the application form
remains open. LinkedIn Easy Apply can be added later.

References:

- Playwright Java CDP: https://playwright.dev/java/docs/api/class-browsertype
- Playwright MCP CDP endpoint: https://github.com/microsoft/playwright-mcp/blob/main/README.md
