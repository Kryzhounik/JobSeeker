# Applier architecture

This is a minimal design for the happy-path prototype. Behavioral expectations
live in `REQUIREMENTS.md`; execution steps are in `PLAN.md`.

## Shared browser

The browser is an independent, long-lived Chromium process using the existing
`Data/browser_profiles/linkedin` profile. It is not owned by JavaFX, the
collector JAR, or Codex CLI. A small shared Java browser-access component does
"attach if active, otherwise start": discover and validate the managed local
CDP endpoint, start the visible browser with that profile if absent, then
connect. Expose CDP only on loopback and use the managed browser.

The collector, JavaFX application preparation, and Playwright MCP are clients
of this browser. Java clients connect with Playwright's `connectOverCDP`;
Playwright MCP uses the same `--cdp-endpoint`. Each operation creates or claims
its own tab and closes only a tab it owns when appropriate. Disconnecting a
client must not close the shared default context or browser. Browser shutdown
is a separate explicit operation; manual browser closure is also valid.

Current `PersistentLinkedInSession` launches a Playwright-owned context,
deletes other tabs on startup, and closes that context after collection.
Those behaviors must be changed for the shared-browser path. Simply removing
its `close()` call is insufficient because collection runs in a separate
process. Keep collection logic independent of the GUI and preserve its console
entry points and login behavior.

## Application flow

1. JavaFX starts preparation for the selected job URL off the UI thread.
2. Java opens an application-owned tab in the shared browser, clicks the
   initial Apply control, and observes a modal, redirect, or new tab.
3. Java passes the resulting form tab's identity and a short description of
   where it stopped to the existing `Driver/codex_proxy`. The identity must be
   matchable to the tab exposed by Playwright MCP; the agent selects that
   already-opened tab and continues there.
4. The proxy starts Codex CLI with Playwright MCP configured for the shared
   CDP endpoint. Supply the existing resume and available approved answers
   from `Data/`. Codex fills as much as these support and stops before final
   submission. Unknown answers are left for the user.
5. Return filled or partially filled, with a short note about anything left
   to do. Display the result in JavaFX. The browser and form stay open for
   manual review, completion, and submission.
6. The user marks the vacancy Applied through the existing GUI action after
   submitting. Preparation itself does not change application tracking.

A collector tab and an application tab may coexist; each workflow uses its own
tab. If an accidental submission is reported or observed, mention it in the
result message. No separate submission-detection subsystem is required.

Future form-specific adapters can be selected by the actual form/site. The
prototype goes directly through Codex; it does not need an empty adapter
registry or an adapter framework.

## Existing Codex Proxy

Reuse `Driver/codex_proxy` for CLI invocation, response handling, and token
usage accounting. Its browser-tools mode permits Playwright MCP for Applier
while analyzer calls keep their existing no-tools instruction. Configure
Playwright MCP for an Applier invocation with the shared browser's CDP endpoint.
Do not create another CLI launcher.

Applier supplies the form-filling instruction and input. The proxy continues
to handle CLI invocation and metrics, without form-filling logic. There is no
new retry, cancellation, or recovery mechanism in this prototype.

## Integration boundaries

- `Driver/collector/java_linkedin`: retain search, extraction, persistence,
  login, and standalone batch commands; switch only browser acquisition and
  collector-owned tab cleanup to the shared model.
- `GUI/JavaFX`: add the Apply command and display the preparation result in the
  existing job view. Do not make the collector depend on the GUI process.
- `Applier`: own application preparation, the Codex Proxy/MCP handoff, and
  the shared browser-access contract. Decide Java module wiring
  during implementation without duplicating browser lifecycle rules.

Validate the attach/reuse/disconnect behavior with one browser and one job
before changing the collector's normal batch path. CDP attachment and MCP
configuration are supported by Playwright, but coexistence with this project's
current launch options needs an integration test.

References:

- Playwright Java CDP: https://playwright.dev/java/docs/api/class-browsertype
- Playwright MCP CDP endpoint: https://github.com/microsoft/playwright-mcp/blob/main/README.md
