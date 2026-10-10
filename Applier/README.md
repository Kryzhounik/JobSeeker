# Applier

Applier version 0.1 prepares job-application forms for jobs selected in the Java GUI.
The GUI passes the vacancy URL to Applier; Applier reads the stored vacancy
location and handles the browser, facts, and form filling.
LinkedIn Easy Apply vacancies are skipped without calling Codex.
It shares the logged-in browser with the independently runnable LinkedIn
collector. Applier first looks for a host-specific Playwright adapter. If no
adapter matches, it uses the existing Codex Proxy with Playwright MCP. No
adapter scripts are installed yet, so version 0.1 uses the Codex path. The
form stays open for human review, completion of missing answers, and submission.

The local resume lives at `Data/applier/CV_DokE2026M.pdf`. Its extracted UTF-8 text at
`Data/applier/resume.txt` is passed to Codex as context; the PDF path is passed
for form uploads. Replace both files together when updating the resume.
Applicant facts, separate from the decision rules in `fill_form.md`, go in
`Data/applier/facts.md`. The current file lists the preferred and other
available locations. Applier reads the vacancy location from `Data/jobs.sqlite`
so Codex can apply the location rule. Add confirmed facts as testing reveals new fields;
add choice rules to `fill_form.md`. `Data/` is excluded from Git.

`REQUIREMENTS.md` defines the expected behavior; `ARCHITECTURE.md` describes
the shared-browser design; `PLAN.md` records the implementation sequence.
