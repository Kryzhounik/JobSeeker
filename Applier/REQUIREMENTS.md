# Applier requirements

## Purpose

Prepare a job application from the existing JavaFX GUI so the user can review
the filled form in the browser and submit it manually. The LinkedIn collector
must remain usable from the command line without the GUI.

Version 0.1 is the first working happy-path implementation. Keep it small;
fix additional problems as they arise in use.

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

- An Apply action in the existing JavaFX job view passes the selected vacancy
  URL to Applier without blocking the UI thread. The GUI does not read
  applicant facts or choose form answers.
- For version 0.1, Applier opens the vacancy, clicks an external Apply link, and identifies the
  redirect or new tab containing the company's application form. LinkedIn
  Easy Apply is outside this first implementation.
- An adapter is a Playwright script written for a specific application form.
  Choose by the form actually opened, including after a redirect or new tab.
  First check for a matching adapter and run it if present. Only when no
  adapter matches, invoke Codex CLI through the existing `Driver/codex_proxy`
  with Playwright MCP attached to the same browser. There are no adapter
  scripts yet; version 0.1 therefore uses the Codex path.
- For the Codex path, pass the already-opened form tab's identity, matchable
  through Playwright MCP, and a short description of where Java stopped.
  Codex continues
  in that tab without reopening the application or using the collector tab.
  If Apply opened a new tab, pass the identity of that resulting form tab.
- The filler may complete multi-step forms but should stop before final
  submission. The form and browser stay open for the user's inspection and
  manual submission. An accidental agent submission is not a fatal condition,
  but must not be presented as an unsubmitted form.
- Use the existing resume and a separate local facts file under `Data/`.
  Keep decision rules in the form-filling instruction, not in the facts file.
  Applier reads the stored vacancy location so the location rule can choose from the available
  locations. Facts and rules can grow as new questions arise; a complete
  catalogue is not required upfront.
- Fill what the available information supports. Do not invent missing answers;
  leave them for the user and briefly explain what remains. If the form blocks
  further progress, leave it open for manual completion.

## Result and application tracking

- Two preparation results are enough: filled, or partially filled with a short
  explanation of what remains. The user reviews the actual form in the browser.
- Preparation does not mark the vacancy Applied or create an application record.
  After submitting manually, the user uses the existing Applied action in the
  GUI. No new submission-confirmation workflow is required.
- If an accidental submission is reported or observed, say so in the result
  message; do not describe the form as still awaiting submission.

## Acceptance checks

- Run collection without the GUI; later open the GUI and reuse the same
  browser/profile without another LinkedIn login.
- Keep an application form open while collection finishes; the form remains
  available for manual review.
- Prepare an external form through the existing Codex Proxy and Playwright
  MCP, continuing in the tab opened by Java. Check that at least the name
  and surname from the resume can be entered where the form asks for them.
- Verify that finishing Codex leaves the prepared form open and does not mark
  the job Applied; the existing manual Applied action still works.

Automatic submission, writing the first adapter script, retries, crash recovery,
a cancellation system, and a detailed task-status model are outside this
version 0.1. Handle additional cases when actual use shows they are needed. Do
not redesign the collection workflow.
