# Known Issues

## Alternative human-language requirements

The system does not represent OR alternatives for human-language requirements.
LinkedIn vacancy `4483298612` lists Kazakh or Russian as an alternative.

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

## Minor: Empty LinkedIn keyword list is accepted

`CollectionSettings.LinkedIn` currently accepts an empty `keywords` list
(e.g. `keywords=`). With ordinary locations, `SearchPlan` creates no searches
and collection may finish as `complete` without checking any vacancies. With
`AccountRemote`, `keywords().getFirst()` fails and collection becomes `blocked`.

An empty query list is invalid user configuration, not a request for an
unfiltered search. Reject it during settings validation with a clear error,
before starting collection. Add a focused regression test when addressing this.

Severity: **Minor**. Deferred; not blocking Issue #4 review.
