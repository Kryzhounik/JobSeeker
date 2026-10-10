# Applier prototype execution plan

Goal: check that Java can open an application form, Codex can continue filling
that same form, and the user can review and submit it. Implement the happy path
described in `REQUIREMENTS.md`.

1. Prove shared-browser access with one visible logged-in browser and one job.
   Start or attach through CDP, connect Java and Playwright MCP to the same
   profile, and confirm the browser and form tab survive client disconnects.
2. Change the Java LinkedIn collector to use that browser. Keep its standalone
   `login` and `batch` commands. Collection must use and close only its own tabs;
   remove the current startup behavior that closes other tabs.
3. Add Apply to the existing JavaFX job view. Off the UI thread, open the
   selected vacancy, click Apply, and identify the resulting Easy Apply dialog
   or external form tab. Pass that tab's identity and the current step to
   Applier.
4. Connect Applier to the existing Codex Proxy. For this invocation, configure
   Playwright MCP with the shared CDP endpoint and enable the Proxy's browser
   tools mode. Give Codex the existing resume and available approved answers
   from `Data/`. It fills the already-opened form as far as possible and stops
   before final submission.
5. Show only "filled" or "partially filled" with a short note about remaining
   work. Keep the form open. The user reviews, completes missing answers,
   submits, and then uses the existing Applied action in the GUI.
6. Check the full path on one LinkedIn Easy Apply form and one external form.
   Also confirm that standalone collection still works, does not close the
   application tab, and that preparation alone does not mark the job Applied.

\* If the prototype works, move `Driver/codex_proxy` one level higher to the
repository root as a shared module, then update both callers and imports.
