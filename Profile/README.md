# Profile

Profile defines JobSeeker's personalization files: shared candidate facts and
module-specific search, filtering, scoring and application preferences.
It contains documentation and neutral [templates](Template/), not a user's
actual profile or technical runtime configuration.

The title block-list template contains an existing starter list rather than an
empty file. Review it and remove exclusions unsuitable for your job search.

## Setup

Copy the **contents** of `Profile/Template/` into `Data/Profile/`, preserving
subdirectories. Do not overwrite an existing profile: when upgrading, compare
templates and add only missing files or entries. Fill in the copied files before
running collection, analysis or application preparation; sample values are not
facts about you. The application reads `Data/Profile/`, never `Template/`.

Replace `resume.txt` with your plain UTF-8 resume. For application uploads,
add your corresponding PDF as `Data/Profile/CV_DokE2026M.pdf`: this is the
filename currently expected by Applier. No sample PDF is included. Update the
PDF and text together. Add only confirmed facts to the Applier Markdown files;
remove the template instructions rather than passing them to an agent.

## Files

Personal candidate data and preferences live in `Data/Profile/`, excluded from
Git together with the rest of `Data`. Preserve this directory when transferring
the application to another machine. A fresh checkout requires a local profile;
it does not contain the owner's personal data; use the templates to create it.

Shared candidate data lives directly in the profile root. Module-specific data
mirrors the project structure. Consumers read the same files; there are no
separate Analyzer and Applier copies of the candidate profile.

| File relative to Data/Profile | Purpose |
| --- | --- |
| `resume.ini` | Shared language proficiency, available remote locations, relocation destinations and technology experience |
| `resume.txt` | UTF-8 resume text supplied to agents |
| `CV_DokE2026M.pdf` | Resume document uploaded to application forms; update together with resume.txt |
| `Applier/facts.md` | Confirmed applicant answers: contact details, authorization, compensation, notice period; do not duplicate resume.ini |
| `Applier/candidate_context.md` | Additional candidate facts and factual boundaries used for cover letters |
| `Driver/collector/linkedin.properties` | Search keywords, ordered locations, seniority, workplace modes, employment types, publication window and collection limit |
| `Driver/collector/justjoin.properties` | JustJoin search technology, location, languages, experience, publication window, ordering and limit |
| `Driver/collector/linkedin_preview_filter.ini` | Preview-filter enablement, blocked-title file, uncertain-title policy and maximum publication age |
| `Driver/collector/linkedin_preview_blocked_titles.txt` | Blocked title fragments, one per line; blank lines and # comments are ignored |
| `Driver/collector/linkedin_content_filter.ini` | Title pass words and unwanted mandatory technologies |
| `Driver/analyzer/filter.ini` | Language, programming-language, remote and relocation eligibility policies |
| `Driver/analyzer/interest.ini` | Technology-interest bonus ceiling, default bonus and keyword weights |

INI and properties files describe their entries in comments. In `resume.ini`,
each language maps to CEFR A1-C2. Location values are interest scores; presence
means the location is available. `relocation.base` is added to a destination's
score. Technology experience ranges from 0 (none) to 5 (core expertise).
Keyword and block-list entries use the meaning of their section; individual
words do not need separate descriptions. Markdown answers and candidate context
are free-form confirmed facts, not configuration keys.

Technical configuration stays in the modules: browser/runtime paths, collector
mode, timeouts, request delays, API page size, model configuration and execution
parallelism. Generated output, logs, databases and browser sessions are not
part of the profile. Company priorities and blacklists already stored in the
database remain there; this file migration does not redesign their storage.
