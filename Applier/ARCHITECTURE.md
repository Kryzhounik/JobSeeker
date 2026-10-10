# Applier architecture

This is the implementation design for the executor. It may be removed after
implementation; behavioral expectations live in `REQUIREMENTS.md`.

## Shared browser

The browser is an independent, long-lived Chromium process using the existing
`Data/browser_profiles/linkedin` profile. It is not owned by JavaFX, the
collector JAR, or Codex CLI. A small shared Java browser-access component does
"attach if active, otherwise start": discover and validate the managed local
CDP endpoint, start the browser with that profile if absent, then connect.
Serialize startup so two clients cannot launch competing browsers for the same
profile. Expose CDP only on loopback and do not attach to an unrelated browser.

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

The application flow requires a visible browser. If an existing browser cannot
support visible manual review, report that state rather than silently opening
a second instance on the locked profile.

## Application flow

1. JavaFX starts an Apply task for the selected job URL and shows progress.
2. Java opens an application-owned tab in the shared browser, clicks the
   initial Apply control, and observes a modal, redirect, or new tab.
3. Form identification chooses an adapter by the actual form/site, not only
   the original LinkedIn URL. An adapter has a match check and a fill operation.
   The registry is initially empty.
4. Without a match, Java starts Codex CLI with Playwright MCP configured for
   the shared CDP endpoint. The task identifies the application tab and supplies
   only approved applicant data. The agent prepares the form and reports
   `ready_for_review`, `needs_input`, `submitted`, or `failed`.
5. Java checks the reported outcome and that the relevant tab still exists,
   then updates JavaFX. Codex disconnects; the browser and application tab stay
   open. The user reviews and submits in that tab.
6. Existing application tracking is updated only on user-confirmed submission.

The GUI must not perform browser automation on the JavaFX UI thread. A
collector tab and an application tab may coexist, but neither workflow may
close or commandeer the other's tab. An agent instruction to avoid final
submission is a preference, not a technical guarantee; detect and surface a
reported or observable submission rather than assuming it never happens.

## Integration boundaries

- `Driver/collector/java_linkedin`: retain search, extraction, persistence,
  login, and standalone batch commands; switch only browser acquisition and
  collector-owned tab cleanup to the shared model.
- `GUI/JavaFX`: add the Apply command and progress/review states to the
  existing job view. Do not make the collector depend on the GUI process.
- `Applier`: own application preparation, form-adapter contract, Codex CLI/MCP
  handoff, and the shared browser-access contract. Decide Java module wiring
  during implementation without duplicating browser lifecycle rules.

Validate the attach/reuse/disconnect behavior with one browser and one job
before changing the collector's normal batch path. CDP attachment and MCP
configuration are supported by Playwright, but coexistence with this project's
current launch options needs an integration test.

References:

- Playwright Java CDP: https://playwright.dev/java/docs/api/class-browsertype
- Playwright MCP CDP endpoint: https://github.com/microsoft/playwright-mcp/blob/main/README.md
