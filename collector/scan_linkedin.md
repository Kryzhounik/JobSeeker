---
name: scan-linkedin
description: Browser-based LinkedIn collector for JobSeeker. Use this when collecting LinkedIn jobs, saving LinkedIn raw pages, or calibrating a LinkedIn job URL through the logged-in browser.
---

# Scan LinkedIn

Purpose: LinkedIn collection skill. It collects raw pages through the logged-in
browser and must hand saved raw/readable pages to the common workflow.

Use this as the LinkedIn counterpart of `collector/scan_justjoin.py`.
LinkedIn collection is browser-driven because direct Python HTTP requests get
rate-limited and do not use the logged-in session.

For browser mechanics, follow `collector/browser.md`. In particular, LinkedIn
collection currently uses the Chrome extension browser and saves the
right-side details pane from the search UI.

## Workflow

1. Run `python collector/linkedin_search_urls.py`.
2. Open `seed:<location>` URL(s) first, but do not collect vacancies from them.
   They only anchor LinkedIn's sticky remote-search location.
3. Process printed non-seed search URLs strictly in config order.
4. For each location, keep collecting pages until one of these happens:
   the global `limit` is reached, the location is exhausted by the rules below,
   or a blocking error appears.
5. Do not sample a few pages from every location. If the first location has
   enough jobs to reach the global limit, stop there and do not move to the
   next location.
6. On each LinkedIn search page, collect the complete result page, not only the
   initially visible cards. Scroll the left search-results panel until no new
   cards appear, then extract previews from all cards found on that page.
7. After the current result page is exhausted, move to the next page with
   `start += 25` (or the next-page control if LinkedIn changes the URL shape).
   Do not use a fixed list like `0/25/50/75`; continue until exhausted or
   until `limit` is reached.
8. Respect `delaySeconds` from `collector/config/linkedin.properties` as the
   minimum interval between browser navigation actions. Measure it from the
   previous search-page navigation start. If collecting, filtering, saving, or
   logging the current page already took longer than `delaySeconds`, open the
   next page immediately.
9. For every collected search-result card, extract a small preview object:
   title, company, location, workplace, salary when visible, and canonical URL.
10. Run the preview object through:
   `python common/preview_filter.py --input <preview_json>`.
11. For every card with `preview_decision = "open"`, click the card in the
    left LinkedIn search results and wait for the right-side job details pane.
12. Keep skipped preview cards in the report for debugging false rejects.
13. Stop at `limit` from `collector/config/linkedin.properties`.
14. Do not normally build a queue and later open each `/jobs/view/<id>/` URL.
    The normal LinkedIn path is search UI card -> details pane -> raw save.
15. Respect `delaySeconds` as the minimum interval between browser
    navigation/click actions. Do not wait a full extra delay after saving a
    vacancy. If filtering, clicking, loading, saving, or logging the current
    vacancy already took longer than `delaySeconds`, continue immediately.
16. Before saving raw HTML, wait until the vacancy details are loaded:
    no visible `progressbar` / `In progress` remains for the job details, the
    selected job id matches the clicked card, and at least one detail marker is
    visible: `About the job`, `Role Overview`, `Requirements`, or
    `Key Responsibilities`.
17. If details do not load before the timeout, do not save the page as a normal
    raw vacancy. Log it as `incomplete_raw` and continue or report the blocking
    problem.
18. Get the raw HTML from the right-side details pane, not from the whole
    search page.
19. Save it through the common saver:
   `python collector/save_raw_page.py --source linkedin --url <job_url> --content-file <html_file>`.

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

A search page is processed only after the left LinkedIn results panel has been
scrolled until it stops loading new cards. The initial visible cards are not
enough.

For every page, record:

- search label;
- `start` value;
- page URL;
- number of cards seen after scrolling the results panel;
- number of new unique `job_id` values;
- visible end-of-results text, if any;
- next-page button state, if visible.

The current location is exhausted only when at least one of these is true:

- the page shows a clear no-results or end-of-results message;
- there are no result cards after the page finishes loading;
- LinkedIn exposes a Next button and it is missing or disabled;
- two consecutive `start` pages produce no new unique `job_id` values after
  the results panel has been fully scrolled.

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
- why the collector continued, moved to the next location, or stopped.

Do not use Python `urllib`, `requests`, hidden APIs, or copied cookies for
LinkedIn collection. Do not run full vacancy analysis while collecting unless
the user asks for calibration. Preview filtering is allowed because it only
uses visible search-card text and deterministic rules from
`common/config/preview_filter.ini`.

After Codex produces analyzed JSON, save it with:
`python workflow/save_analyzed_job.py --input data/analyzed/linkedin --source linkedin`.

If LinkedIn shows CAPTCHA, checkpoint, suspicious-login, or account-warning UI,
stop and report it.
