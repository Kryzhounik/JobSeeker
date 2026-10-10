# Applier version 0.1 execution plan

Goal: deliver version 0.1 so Java can open an application form, Applier can
choose its filler, and the user can review and submit the filled form. Implement
the happy path described in `REQUIREMENTS.md`.

1. Prove shared-browser access with one visible logged-in browser and one job.
   Start or attach through CDP, connect Java and Playwright MCP to the same
   profile, and confirm the browser and form tab survive client disconnects.
2. Change the Java LinkedIn collector to use that browser. Keep its standalone
   `login` and `batch` commands. Collection must use and close only its own tabs;
   remove the current startup behavior that closes other tabs.
3. Add Apply to the existing JavaFX job view. Off the UI thread, pass only the
   selected vacancy URL to Applier. Applier reads its stored location, opens the
   vacancy, clicks its external Apply link, and identifies the company form tab.
4. In Applier, identify the actual form and check first for its adapter: a
   Playwright script written for that form. Run it if present. No adapter
   scripts exist yet, so version 0.1 proceeds to Codex.
5. When no adapter matches, connect Applier to the existing Codex Proxy.
   Configure Playwright MCP for this invocation with the shared CDP endpoint
   and enable the Proxy's browser tools mode. Give Codex the existing resume,
   the local facts from `Data/`, and the vacancy location. It fills the already-opened
   form as far as possible and stops before final submission.
6. Show only "filled" or "partially filled" with a short note about remaining
   work. Keep the form open. The user reviews, completes missing answers,
   submits, and then uses the existing Applied action in the GUI.
7. Check the full path on one external form, including entry of the candidate's
   name and surname from the resume.
   Also confirm that standalone collection still works, does not close the
   application tab, and that preparation alone does not mark the job Applied.

\* If this version works, move `Driver/codex_proxy` one level higher to the
repository root as a shared module, then update both callers and imports.
