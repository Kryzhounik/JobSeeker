# Known Issues

## Collector filter configuration names

The shared Python Collector applies preview and content filters to vacancies
from both LinkedIn and JustJoinIT, but the corresponding configuration files
are named `linkedin_preview_filter.ini` and `linkedin_content_filter.ini`.
Their names no longer reflect the sources and responsibilities they cover.

## Intermittent "Job details were not ready" timeout

The collector intermittently fails to find the Job Details section in the
LinkedIn search-results view and eventually reports `Job details were not
ready` after a timeout. The issue does not reproduce on every run.

On the affected laptop, navigating directly to the full vacancy page makes
the section visible and is being used as a workaround. A monitor-resolution
interaction is suspected, but the cause has not been confirmed.
