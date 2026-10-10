# Applier requirements

## Purpose

Prepare a job application from the existing JavaFX GUI so the user can review
the filled form in the browser and submit it manually. The LinkedIn collector
must remain usable from the command line without the GUI.

## Browser and collection

- Collection and application use one logged-in LinkedIn account and one
  persistent browser profile. They may use different tabs at the same time.
- A standalone collector or the GUI attaches to the managed browser when it is
  running; otherwise it starts that browser. Neither requires the other to run.
- Finishing collection closes only collector-owned tabs. Finishing the form
  filler does not close the application tab or the browser.
- The browser survives the end of the collector, Codex CLI, and GUI processes.
  It closes only when the user closes it or explicitly requests shutdown.
- The existing login/bootstrap workflow and standalone collection entry points
  must keep working. The collector must not close tabs owned by application
  preparation or the user.

## Application preparation

- An Apply action in the existing JavaFX job view opens the selected vacancy in
  a visible tab and starts preparation without blocking the UI thread.
- Java handles the initial Apply click and identifies LinkedIn Easy Apply versus
  a redirect or new tab containing an external application form.
- The design supports form-specific adapters selected by the actual form/site.
  No adapters are required in the first implementation.
- When no adapter matches, Java invokes Codex CLI with Playwright MCP attached
  to the same browser. The agent works in the application tab, not the
  collector tab.
- The filler may complete multi-step forms but should stop before final
  submission. The form and browser stay open for the user's inspection and
  manual submission. An accidental agent submission is not a fatal condition,
  but must not be presented as an unsubmitted form.
- Use only applicant information explicitly supplied or approved by the user.
  Do not invent answers; ask for input or hand control to the user when needed.
- Login challenges, captchas, unsupported forms, and failures hand control back
  to the user without closing the tab.

## UI and status

- Show whether preparation is running, ready for review, needs user input,
  submitted, or failed. Ready for review must be visibly distinct from Applied.
- A successful filler response alone must not mark the job Applied in SQLite.
  The existing application tracking changes only after user confirmation of
  submission. If the agent appears to have submitted, show that explicitly and
  ask the user to verify before updating the tracked status.
- Errors must leave the opened application tab available for manual recovery.

## Acceptance checks

- Run collection without the GUI; later open the GUI and reuse the same
  browser/profile without another LinkedIn login.
- Keep an application form open while collection finishes; the form remains
  available for manual review.
- Prepare both an Easy Apply form and an external form with no adapter through
  the Codex CLI fallback.
- Verify that finishing Codex leaves the prepared form open and does not mark
  the job Applied; user-confirmed submission does.
- Verify that unavailable CDP, missing applicant information, and an
  unsupported form are reported without losing the browser tab.

No automatic submission, initial adapter implementation, or redesign of the
overall collection workflow is required.
