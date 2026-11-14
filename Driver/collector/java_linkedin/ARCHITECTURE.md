# Collector architecture

Collector remains one Maven module at `Driver/collector/java_linkedin` with the
existing artifact and CLI entry point. `core.CollectorApi` is its public API.
The existing `LinkedInApplication` delegates to it for compatibility. Java clients
use the shared DTOs from `com.seeker.collector.common`; LinkedIn commands, settings,
run IDs and existing result/progress fields retain their meaning.

## Responsibilities and flow

`core.CollectionService` selects and starts the source, owns the shared processing
session and completes the run. `CollectionAccumulator` owns accepted scope,
outcome counters and failure messages; core forms `CollectionReport` from this
state and source diagnostics. Source discovery/configuration and mixed-source
execution are not part of this stage: the existing entry points start LinkedIn.

`common.CollectorSource` is the retrieval boundary. Sources receive a
`VacancyProcessor` and a `CollectionSink`. They can check a preview before fetching
details, register raw content, submit a prepared vacancy and report its outcome.
The processor does not repeat preview checks when processing a full description.
Each source supplies progress snapshots in the existing shared progress contract.
Legacy page diagnostics are optional; only LinkedIn supplies `PageReport` entries.

`linkedin` owns Playwright, authentication, browser/session lifecycle, search plans,
pagination, card/description extraction, page diagnostics, age rejection and the
optional EasyReasoning cleaner. The source prepares text before handing it to
common processing. Its existing per-vacancy failure handling and traversal rules
are retained. The cleaner is not selected or called by a generic source switch.

Shared filtering, registration and persistence continue through the native
Python `CollectorService`. It accepts `PreparedVacancy` data, not browser HTML or
a cleaner callback. `LinkedInPreparation` owns the existing Python HTML/age work
and uses shared raw registration. Java gateways and Python bridge functions only
adapt values and delegate. Existing Python module/file names and batch tables
are retained; no storage migration is included here.

## Prepared data and structured fields

`Preview` carries explicit source/job identity and extracted preview fields.
Its old constructor is retained only for existing LinkedIn callers.
`PreparedVacancy` carries the preview, ready-to-process description, optional
Easy Apply marker and a separate `fields` map. The map preserves source-native
structured data rather than inventing a universal taxonomy or flattening it into
the description. The common processor does not interpret these fields as new
filtering or analysis rules.

The contract was checked against actual JustJoinIT listing and full-offer HTTP
responses: `body` contains HTML; `requiredSkills` and `niceToHaveSkills` contain
named skills with numeric levels; `languages` contains language codes and levels
such as `en` / `B2`. Locations, employment/salary variants, experience/workplace
fields and publication metadata can also be preserved in `fields`. Numeric skill
levels and language levels retain their original representations.

Structured fields remain accessible in `ProcessResult` and accepted `ScopeItem`
objects. They are optional and omitted from JSON when absent, preserving the
existing LinkedIn JSON even for serializers configured with `serializeNulls`.
Only this field's omission policy is customized; DTOs and nested values still use
automatic serialization. No new DB
columns or downstream interpretation are introduced.

## JustJoinIT HTTP source

`justjoin.JustJoinSource` implements the same retrieval contract independently of
the browser source. `JustJoinConfig` loads the existing technical and personal
JustJoin settings; the listing requests preserve the Python HTTP fetcher's API
parameters (category, publication age, languages, experience, sorting, offset and
page size). The existing `location` setting forms the website referer, not an API
location filter, just as in that fetcher. Requests are sequential and respect
`delaySeconds`; `limit=0` means unlimited accepted offers.

The source checks each listing preview through the shared processor before
loading the full offer. Repeated slugs in listing pages are visited once. Direct
URLs load the full offer to obtain preview metadata, then use the same processing.
Identity is `(justjoin, publication slug)`, consistent with existing registration;
the native API ID remains in structured fields.

Full API JSON is saved through shared raw registration. Jsoup converts its HTML
`body` into readable text, preserving paragraph/list boundaries and entities.
All other full-offer fields pass unchanged into `PreparedVacancy.fields` and the
common processing result. Neither LinkedIn preparation, its age parser, nor
EasyReasoning is involved. HTTP/parsing/processing errors propagate to the caller;
no retries or partial-failure continuation were added.

The implementation is exercised independently with local HTTP fixtures and an
explicitly enabled JPy integration test using a temporary database. Existing
CollectorApi/CLI entry points still select LinkedIn. Source selection and the
combined workflow remain outside this stage; the historical Python raw-download
script remains a standalone utility, not a second shared-processing pipeline.

Orchestrator still starts only LinkedIn and owns subsequent workflow stages.
Semantic deduplication remains in EasyReasoning, invoked by Orchestrator, outside
Collector. Collector does not analyze, score or deduplicate descriptions.
