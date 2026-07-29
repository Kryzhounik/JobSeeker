---
name: validate-readable-analysis
description: Validate that LinkedIn readable text extraction preserves job analysis output by comparing JSON produced from raw HTML and cleaned TXT for the same vacancies. Use before or after changing collector/extract_linkedin_readable_text_v*.py, or when checking whether HTML cleaning loses job fields.
---

# Validate Readable Analysis

Goal: prove that `raw HTML -> readable TXT` does not change the structured job
analysis we care about.

Do not validate this by checking text prefixes, tails, or line counts only. The
required test is semantic:

```text
same vacancy raw HTML -> job_facts/extract.md -> raw_json
same vacancy readable TXT -> job_facts/extract.md -> readable_json
compare raw_json vs readable_json
```

## Inputs

- Raw HTML files: `../Data/raw/<source>/pages/*.html`
- Readable text files: `../Data/readable_v*/<source>/pages/*.txt`
- Job-facts rules: `analyzer/job_facts/extract.md`

Use the same vacancy id/stem for both files.

Default sample when the user does not specify one: first 10 raw files sorted by
file name.

## Test Flow

```text
raw_html = ../Data/raw/<source>/pages/<id>.html
raw_json = analyze(raw_html, analyzer/job_facts/extract.md)

txt = extract(raw_html)
txt_json = analyze(txt, analyzer/job_facts/extract.md)

assert raw_json == txt_json for collected fields
```

## Comparison Fields

Compare collected job fields, at minimum:

- `source_url`
- `title`
- `company`
- `location`
- `remote_type`
- `remote_scope`
- `relocation`
- `seniority`
- `role`
- `salary`
- `languages`
- `technologies`

## Raw HTML Analysis Rule

When analyzing raw HTML, analyze the primary vacancy page only. Ignore LinkedIn
page chrome, recommended jobs, company sidebars, ads, navigation, and unrelated
footer content. This simulates what the analyzer would have meant if it received
HTML directly.

## Output

Write a report under this test folder:

```text
test/validate-readable-analysis/results/raw_vs_readable_<version>_<sample>.json
```

Report shape:

```json
{
  "source": "linkedin",
  "cleaner": "collector/extract_linkedin_readable_text_v2.py",
  "sample": "first 10 raw/readable pairs sorted by file name",
  "checked_fields": ["title", "company", "technologies"],
  "result": {
    "jobs_checked": 10,
    "jobs_with_differences": 0
  },
  "pairs": [
    {
      "id": "4357714383",
      "raw_json": {},
      "readable_json": {},
      "differences": []
    }
  ]
}
```

If there are differences, list the field, raw value, readable value, and likely
reason. Do not silently fix the cleaner during validation. Report cleaner
improvement ideas separately.
