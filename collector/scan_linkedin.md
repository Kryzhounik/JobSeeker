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
2. Open the printed search URL(s) in the logged-in in-app browser.
3. Scroll search results slowly and collect canonical vacancy URLs:
   `https://www.linkedin.com/jobs/view/<id>/`.
4. Stop at `limit` from `collector/config/linkedin.properties`.
5. Save the queue as `data/raw/linkedin/queue.json`.
6. Open each vacancy URL in the same browser.
7. Wait `delaySeconds` between vacancies.
8. Get the raw HTML from the browser page.
9. Save it through the common saver:
   `python collector/save_raw_page.py --source linkedin --url <job_url> --content-file <html_file>`.

Do not use Python `urllib`, `requests`, hidden APIs, or copied cookies for
LinkedIn collection. Do not analyze fields while collecting unless the user asks
for calibration.

After Codex produces analyzed JSON, save it with:
`python analyzer/save_analyzed_job.py --input data/analyzed/linkedin --source linkedin`.

If LinkedIn shows CAPTCHA, checkpoint, suspicious-login, or account-warning UI,
stop and report it.
