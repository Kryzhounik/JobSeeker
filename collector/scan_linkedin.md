---
name: scan-linkedin
description: Browser-based LinkedIn collector for JobSeeker. Use this when collecting LinkedIn jobs, saving LinkedIn raw pages, or calibrating a LinkedIn job URL through the logged-in browser.
---

# Scan LinkedIn

Use this as the LinkedIn counterpart of `collector/scan_justjoin.py`.
LinkedIn collection is browser-driven because direct Python HTTP requests get
rate-limited and do not use the logged-in session.

## Workflow

1. Run `python collector/linkedin_search_urls.py`.
2. Open `seed:<location>` URL(s) first, but do not collect vacancies from them.
   They only anchor LinkedIn's sticky remote-search location.
3. Process printed non-seed search URLs strictly in config order.
4. For each location, keep collecting pages until one of these happens:
   the global `limit` is reached, LinkedIn has no more results for that
   location, or a blocking error appears.
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
8. Wait `delaySeconds` from `collector/config/linkedin.properties` between
   search-page transitions.
9. For every collected search-result card, extract a small preview object:
   title, company, location, workplace, salary when visible, and canonical URL.
10. Run the preview object through:
   `python common/preview_filter.py --input <preview_json>`.
11. Collect canonical vacancy URLs with `preview_decision = "open"`:
   `https://www.linkedin.com/jobs/view/<id>/`.
12. Keep skipped preview cards in the queue/report for debugging false rejects.
13. Stop at `limit` from `collector/config/linkedin.properties`.
14. Save the queue as `data/raw/linkedin/queue.json`.
15. Open each passed vacancy URL in the same browser.
16. Wait `delaySeconds` between vacancies.
17. Get the raw HTML from the browser page.
18. Save it through the common saver:
   `python collector/save_raw_page.py --source linkedin --url <job_url> --content-file <html_file>`.

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
`python analyzer/save_analyzed_job.py --input data/analyzed/linkedin --source linkedin`.

If LinkedIn shows CAPTCHA, checkpoint, suspicious-login, or account-warning UI,
stop and report it.
