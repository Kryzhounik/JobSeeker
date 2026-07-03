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
3. Open the printed non-seed search URL(s) in the logged-in in-app browser.
4. For search pages, use LinkedIn pagination (`start=...`) rather than
   scrolling when possible.
5. For every visible search-result card, extract a small preview object:
   title, company, location, workplace, salary when visible, and canonical URL.
6. Run the preview object through:
   `python common/preview_filter.py --input <preview_json>`.
7. Collect canonical vacancy URLs with `preview_decision = "open"`:
   `https://www.linkedin.com/jobs/view/<id>/`.
8. Keep skipped preview cards in the queue/report for debugging false rejects.
9. Stop at `limit` from `collector/config/linkedin.properties`.
10. Save the queue as `data/raw/linkedin/queue.json`.
11. Open each passed vacancy URL in the same browser.
12. Wait `delaySeconds` between vacancies.
13. Get the raw HTML from the browser page.
14. Save it through the common saver:
   `python collector/save_raw_page.py --source linkedin --url <job_url> --content-file <html_file>`.

Do not use Python `urllib`, `requests`, hidden APIs, or copied cookies for
LinkedIn collection. Do not run full vacancy analysis while collecting unless
the user asks for calibration. Preview filtering is allowed because it only
uses visible search-card text and deterministic rules from
`common/config/preview_filter.ini`.

After Codex produces analyzed JSON, save it with:
`python analyzer/save_analyzed_job.py --input data/analyzed/linkedin --source linkedin`.

If LinkedIn shows CAPTCHA, checkpoint, suspicious-login, or account-warning UI,
stop and report it.
