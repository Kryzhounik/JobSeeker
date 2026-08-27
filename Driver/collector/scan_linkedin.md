---
name: scan-linkedin
description: Browser-based LinkedIn collector for JobSeeker. Use this when collecting LinkedIn jobs, saving LinkedIn raw pages, or calibrating a LinkedIn job URL through the logged-in browser.
---

# Scan LinkedIn

Purpose: LinkedIn collection skill. It collects raw pages through the logged-in
browser and must hand saved source-job IDs to the common workflow.

Use this as the LinkedIn counterpart of `collector/scan_justjoin.py`.
LinkedIn collection is browser-driven because direct Python HTTP requests get
rate-limited and do not use the logged-in session.

For browser mechanics, follow `collector/browser.md`. In particular, LinkedIn
collection currently uses the Chrome extension browser and saves the
right-side details pane from the search UI.

For left-panel card materialization and page-count verification, follow
`collector/linkedin_scroll_results.md`.

## Workflow

This browser collector has two calls: the initial browser-only `AccountRemote`
priority pass, and a country's gap-fill after its guest pass. The country call
follows:

```text
python collector/scan_linkedin.py batch --location <Name[:geoId]> --limit <remaining>
```

Process only the caller-supplied label and return its browser-only scope to the
caller. Do not move to the next configured location inside this browser call.
The caller merges guest and browser scopes, updates the remaining global limit,
and then starts the guest pass for the next location.

AccountRemote runs before any guest collection. Its seed is the first real
country from `locations`, with `remoteBroadWorkplace=remote`; the seed is not
collected. AccountRemote then uses that same browser tab without `location` or
`geoId`, still remote-only. This prioritizes broad EMEA/worldwide remote jobs
mixed with remote jobs in the anchor country before ordinary country passes
include hybrid/office jobs. It does not guarantee a worldwide inventory.

Country calls are gap-fills, not mandatory verification passes. The
caller must calculate the remaining global limit before invoking it. If the
remaining limit is zero, do not open Chrome, inspect the browser result set, or
invoke this collector at all. During the browser pass, stop immediately when
the combined run scope reaches the global limit.

1. Run `python collector/linkedin_search_urls.py`.
2. For AccountRemote only, open its printed `seed:<location>` URL. Wait until
   the anchor country is shown in the search field and remote-only is applied.
   Click the exact `button` named `Search` once and wait for the search to
   finish. Opening the URL alone can leave a previously remembered country;
   this submission commits the seed country to the logged-in search context.
   Do not collect the seed page or count it toward the limit.
3. Then explicitly open the printed `AccountRemote` URL in the same tab.
   It omits both `location` and `geoId`. Verify that the displayed country
   still matches the seed; a different remembered country is not a valid seed.
4. For a country call, open only its matching printed URL. Do not repeat the
   seed or AccountRemote pass inside a country call.
5. Generated URLs use `/jobs/search/` and carry all browser filters. After
   loading, verify the applied values from `collector/config/linkedin.properties`:
   - `experience`: `associate` -> `Associate`, `mid_senior` -> `Mid-Senior level`;
   - `jobTypes`: `full_time` -> `Full-time`, `part_time` -> `Part-time`,
     `contract` -> `Contract`;
   - `remoteBroadWorkplace` applies to the seed and AccountRemote;
     `workplace` applies to country calls, including all configured modes;
   - `datePosted=week` -> `Past week`, `sort=newest` -> `Most recent`.
   Read the applied filter labels/states; do not set filters through checkbox
   clicks. Applied chips such as Remote may be collapsed: read their applied
   state/accessible label, not a requirement that every chip be visible.
   Wait for loading before treating a missing label as a mismatch.
   `/jobs/search-results/` is not an equivalent replacement: follow the endpoint
   policy in `collector/README.md` and the Strict Batch Policy on failure.
6. For each location, keep collecting pages until one of these happens:
   the global `limit` is reached, the location is exhausted by the rules below,
   or a critical blocker defined in `collector/browser.md` appears. A slow or
   temporarily unresponsive browser/plugin is not a critical blocker.
7. Do not sample a few pages from every location. If the first location has
   enough jobs to reach the global limit, stop there and do not move to the
   next location.
8. On each LinkedIn search page, materialize and verify the complete result
   page with `collector/linkedin_scroll_results.md`, then extract
   previews from that verified card set.
9. After the current page is exhausted, navigate to the same generated URL with
   `start += 25` (`0`, `25`, `50`, ...). Preserve all its filter parameters;
   do not build pagination from the selected job's details URL. Verify the
   selected page number, retained country, and applied filter state after
   loading. A country heading can update before its cards, so wait for the
   result list to finish replacing old cards before materialization. Use the
   classic pagination's exact `button` named `View next page` only to detect
   exhaustion, not to apply or preserve filters. Do not use a details-pane
   carousel named `Next`.
10. Respect `delaySeconds` from `collector/config/linkedin.properties` as the
   minimum interval between browser navigation actions. Measure it from the
   previous search-page navigation start. If collecting, filtering, saving, or
   logging the current page already took longer than `delaySeconds`, open the
   next page immediately.
11. For every collected search-result card, extract a small preview object:
   title, company, location, workplace, salary when visible, and canonical URL.
   In the LazyColumn layout, read `job_id` from the exact
   `componentkey="job-card-component-ref-<job_id>"` prefix and construct the
   canonical URL because unselected cards do not expose their own link.
12. Run the preview fields directly through:
   `python collector/filtering/linkedin_filter.py preview --job-id <job-id> --source-url <canonical-url> --title <title> --company <company>`.
   Read the JSON decision from stdout. Do not create preview input or output
   files.
   This checks title block words, the company blacklist, then
   `(linkedin, job_id)` in `source_jobs`. Any match is skipped before the
   vacancy is opened.
13. For every card with `preview_decision = "open"`, process it until it has
    exactly one collection outcome logged with
    `collector/logging/linkedin_logger.py collection`.
    First try the normal search UI path: find/click the left search-result
    card by `data-occludable-job-id`, matching canonical href, or exact
    `componentkey="job-card-component-ref-<job_id>"`, not by title text. Then
    verify that the selected details link contains the same `job_id` and wait
    for the job details pane.
14. Keep skipped preview cards in the report for debugging false rejects.
15. Stop after saving the caller-supplied remaining number of vacancies. The
    browser call must not restart the configured global limit. Increment its
    counter only after a new raw page is successfully saved. Blocked previews,
    registry duplicates, `already_raw`, and failures do not consume the limit.
16. Do not normally build a queue and later open each `/jobs/view/<id>/` URL.
    The normal LinkedIn path is search UI card -> details pane -> raw save.
    If the accepted card cannot be found/clicked in the current search UI
    (`locator_count_0`, virtualized-card miss, or stale DOM), use the card's
    `source_url` as a fallback: open that exact LinkedIn job URL in the
    logged-in Chrome tab, wait for complete details, and save it through the
    same raw saver. This fallback is only for already accepted preview cards.
17. Respect `delaySeconds` as the minimum interval between browser
    navigation/click actions. Do not wait a full extra delay after saving a
    vacancy. If filtering, clicking, loading, saving, or logging the current
    vacancy already took longer than `delaySeconds`, continue immediately.
18. Before saving raw HTML, wait until the vacancy details are loaded:
    no visible `progressbar` / `In progress` remains for the job details, the
    selected job id matches the clicked card, and at least one detail marker is
    visible: `About the job`, `Role Overview`, `Requirements`, or
    `Key Responsibilities`.
19. If details do not load before the timeout, do not save the page as a normal
    raw vacancy. Log it as `incomplete_raw` and continue or report the blocking
    problem.
20. Get the raw HTML from the right-side details pane, not from the whole
    search page. For `source_url` fallback pages, get the loaded job details
    content from the job page instead.
21. Save each verified pane HTML as a UTF-8 temporary file under
   `../Data/_tmp_collect`. Then run the stable command for that pane:
   `python collector/process_linkedin_panes.py --job-id <job-id> --source-url <canonical-url> --title <title> --company <company> --pane-html <absolute-pane-html-path> --label <location-label> --start <start> --index <index>`.
   This command performs the already-defined deterministic sequence through
   `save_raw_page.py`, `extract_linkedin_readable_text_v2.py`, the content
   filter, and the collection logger. Read its JSON result from stdout. Add the
   returned source/job ID to the in-memory run scope only when `status` is
   `raw_saved`. The processor deletes the temporary pane HTML after success.
   Do not create browser-results, run-scope, or content-decision JSON files.
   Do not recreate this sequence as an inline shell command.
22. The saver records processing status `RAW` only after valid raw content is
    present on disk and sets `source_jobs.collection_method = browser` for a
    newly collected vacancy. The cleaner writes analyzer-ready text to SQLite
    `source_job_texts` and records `CLEANED`. If cleaning fails, leave the
   vacancy at `RAW`, report the failure, and do not analyze it.
23. The content stage runs the equivalent of:
   `python collector/filtering/linkedin_filter.py content --source linkedin --job-id <job_id> --title <title>`.
   When rejected, this command saves the exact matched text, matched keyword,
   regex pattern, and rule to SQLite `content_filter_rejections`.
   Title pass words from `collector/filtering/linkedin_content_filter.ini` are
   checked first; a matching title bypasses all readable-content rules.
   If `content_decision = "skip"`, log `content_filtered` with the returned
   rule/reason, do not add the vacancy to the run scope or limit counter, and do
   not invoke the analyzer. Keep its raw HTML and stored readable text for
   calibration. If
   `content_decision = "analyze"`, log `raw_saved`, increment the limit counter,
   and hand the source/job ID to the analyzer scope.

## Collection Outcomes

Every preview card with `preview_decision = "open"` must end with one logged
outcome in SQLite:

```text
raw_saved
content_filtered
already_raw
not_processed_due_to_limit
incomplete_raw
open_failed
```

Log with:

```text
python collector/logging/linkedin_logger.py collection \
  --label <search-label> \
  --start <start> \
  --card-index <index> \
  --job-id <job_id> \
  --source-url <source_url> \
  --title <title> \
  --company <company> \
  --status <status> \
  --reason <short reason>
```

Use `open_failed` only after both the normal search-card click and the
`source_url` fallback fail. If the global `limit` is reached before opening an
accepted preview card, log `not_processed_due_to_limit`. Accepted preview cards
must not remain only in `_tmp_collect` without one of these statuses.

## Delay Semantics

`delaySeconds` is a throttle for browser navigation/click frequency. It is not
an extra sleep after work is already done.

For both search pages and vacancy pages:

```text
nextAllowedNavigationAt = previousNavigationStartedAt + delaySeconds
sleep(max(0, nextAllowedNavigationAt - now))
open next URL
```

This prevents opening many LinkedIn pages in a burst, while avoiding useless
extra waiting when loading, scrolling, saving, or logging already consumed the
delay window.

## Search Page Exhaustion

A search page is processed only after its left-panel cards have been
materialized and verified by `collector/linkedin_scroll_results.md`.

For every page, record:

- search label;
- `start` value;
- page URL;
- number of expected and materialized cards reported by the scrolling
  instruction;
- number of new unique `job_id` values;
- visible end-of-results text, if any;
- next-page button state, if visible.

The current location is exhausted only when at least one of these is true:

- the page shows a clear no-results or end-of-results message;
- there are no result cards after the page finishes loading;
- LinkedIn exposes a Next button and it is missing or disabled;
- two consecutive `start` pages produce no new unique `job_id` values after
  the results panel has been fully materialized and verified.

Do not treat a single weird page, timeout, empty DOM read, or unknown LinkedIn
response as exhaustion. In that case retry once, log what happened, and only
then decide whether to continue, stop, or report a blocking problem.

If an intentionally huge `start` value is tested, record what LinkedIn actually
does. It may show no results, redirect, clamp to another page, repeat earlier
results, or show an error. Do not assume the behavior in advance.

Observed LinkedIn behavior: a huge `start` may show `No matching jobs found`
and still render unrelated fallback sections such as `Top job picks for you`
with job links. Treat that as location exhaustion for the current search. Do
not collect fallback recommendations as search results.

## Tool-call Limits

Codex browser tool calls may need to be split into smaller technical runs.
That is not business batching and must not change collection behavior.

If a tool call is split, continue from saved state:

- same current location;
- same current page/start;
- same collected candidate set;
- same global limit.

Each technical run must report and persist enough state to explain:

- which location and `start` were processed;
- how many cards were seen on the page;
- how many new unique candidates were added;
- how many cards were skipped by preview filter;
- how many accepted cards became `raw_saved`, `already_raw`,
  `not_processed_due_to_limit`, `incomplete_raw`, or `open_failed`;
- why the collector continued, moved to the next location, or stopped.

Do not use Python `urllib`, `requests`, hidden APIs, or copied cookies for
LinkedIn collection. Do not run full vacancy analysis while collecting unless
the user asks for calibration. Preview filtering is allowed because it only
uses visible search-card text and deterministic rules from
`collector/filtering/linkedin_preview_filter.ini`.

Return the explicit set of saved source/job IDs to the caller. The
collector does not run analysis, scoring, or database save stages itself.

If LinkedIn shows CAPTCHA, checkpoint, suspicious-login, or account-warning UI,
stop and report it.
