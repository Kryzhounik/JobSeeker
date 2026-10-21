# Mailer requirements

This document defines behavior and acceptance criteria. Structural decisions
belong in [ARCHITECTURE.md](ARCHITECTURE.md); implementation and
independent review follow [PLAN.md](PLAN.md).

## R1. Responsibility boundary

Mailer receives, searches, classifies, and interprets email. It extracts useful
message data and keeps one progress checkpoint per application operation.

Mailer does not collect vacancies, check the JobSeeker vacancy database for
duplicates, calculate Fit, match messages to applications, change vacancy or
application statuses, or call Collector, Orchestrator, or Applier. Consumers
may depend on Mailer; Mailer does not depend on them.

The immediate use case is checking vacancy recommendations received from
LinkedIn. Application-response tracking is not part of this implementation.

## R2. Mail access

- Connect to the configured IMAP mailbox and search the configured folder and
  message range. Do not use the unread flag as the processing record.
- Read messages without marking them read, moving them, deleting them, or
  sending mail. Operation checkpoints are local to Mailer.
- Keep account settings and credentials outside tracked source files. Do not
  print credentials or complete private messages in routine diagnostics.
- Connection, authentication, retrieval, and local-state failures must be
  reported as errors. They must not look like an empty recommendation list.
- Constructing the module must not open a mailbox connection. Unrelated
  JobSeeker operations must not require Mailer configuration or credentials.

The selected provider is Gmail with an app password and IMAPS. The current
recommendation folder is explicitly INBOX, and the user selected an initial
boundary after UID 425. Keep these values in local configuration, not source.
Do not invent an initial range for another account or automatically reset a
checkpoint with a different scope.

## R3. Interpretation

- Decode message headers, character sets, transfer encoding, plain text, HTML,
  and multipart bodies into Mailer-owned Java DTOs.
- Filter recommendation candidates by the agreed sender, forwarding headers,
  and subject. Skip nonmatching messages without interpreting their bodies.
- Parse matching messages using a known, verified format. A parsing error
  stops the pass immediately and is logged with the message subject and
  identifier. Propagate the exception; do not continue with later messages.
  For the currently supported messages, X-LinkedIn-Template replaces localized
  subject matching: three verified templates cover the four inspected variants.
  Exact sender/template pairing, original To recipient and X-Forwarded-To/For
  determine whether a message belongs to this operation.
- Extract vacancy links from the actual recommendation content. Account,
  unsubscribe, search, and unrelated links are not vacancy recommendations.
- Return direct LinkedIn vacancy URLs accepted by the existing URL workflow.
  Normalize tracking variants to `https://www.linkedin.com/jobs/view/<id>/`
  when the vacancy ID is available in the message.
- Decode redirect wrappers present in representative messages when their
  destination can be extracted locally. Do not open a browser or fetch the
  vacancy to interpret its email link. Report an encountered unsupported
  recommendation format rather than silently treating it as no recommendations.

Use representative sanitized email fixtures for sender-specific behavior.
Synthetic fixtures can verify MIME handling, but do not establish compatibility
with a real LinkedIn message format on their own.

## R4. Recommendation API

The scenario entry point is `Mailer.getRecommendationUrls()`, returning
`List<String>`.

- Return URLs from matching messages after the operation's last successful UID
  checkpoint.
- Return each canonical URL once per call, preserving a deterministic order.
  This only removes repeated links in Mailer's output; existing-vacancy
  detection remains Collector's responsibility.
- A successful search with no new recommendations returns an empty list.
- Orchestrator receives only these URLs. Message DTOs, message identifiers,
  sender details, and Mailer's bookkeeping are not added to workflow results.

Mail-level access uses typed Mailer DTOs rather than Jakarta Mail objects.
It must not consume recommendation messages merely because another caller
searches or reads them.

## R5. One progress checkpoint per operation

- Persist one last successfully scanned IMAP UID per operation, scoped to the
  configured account, folder, and UIDVALIDITY. There is no table of all messages.
- Visit new messages in ascending UID order. Skip candidates that do not match
  the agreed sender, forwarding and subject criteria; parse matching candidates.
- Update the checkpoint only after the entire pass, retrieval, parsing and
  cleanup succeed. Advance past skipped messages as well as parsed messages.
- On any failure, stop, propagate the exception and leave the old checkpoint
  unchanged. Do not return a partial URL list as a successful result.
- A retry may repeat earlier successful work from the failed pass. This is
  intentional; no per-message progress, queue or recovery protocol is needed.
- Server read/unread flags and timestamps are not the progress record. Ordinary
  mail listing, reading and export do not advance the operation checkpoint.
- A changed account, folder or UIDVALIDITY must not silently reuse an old UID.
  Obtain an explicit initial range for a new operation or changed scope.

Mailer only returns URLs. It does not start a browser or an analyzer for either
empty or nonempty results. Downstream handling of URLs is outside Mailer, and
does not participate in its checkpoint or require acknowledgement.

## R5a. Local operation log

Append UTF-8 JSON lines to `Data/mailer/mailer.log`. Record the operation,
message identifier, subject, available server read/unread flag, whether only
headers or the body were read, filtering reason, extracted URLs or body counts,
export outcome, and errors. Do not log credentials or full message bodies.
Log parse failures before propagating them; preserve their subject and ID.

Low-level operations and the recommendation pass log their actual actions.
Recommendation filtering logs its reason without reading the body. Extraction
logs canonical URLs; a failure logs the current subject, selector and cause.

## R6. Orchestrator operation

Add `WorkflowOrchestrator.checkRecommendations()` returning `WorkflowResult`.

1. Call `Mailer.getRecommendationUrls()` once.
2. For a nonempty list, call the existing `processUrls()` once with those URLs
   in the returned order and return its result.
3. For an empty list, return a successful empty `WorkflowResult` with an
   explanatory message, without starting Collector, a browser, or analysis.
4. Propagate Mailer failures without starting the vacancy pipeline.

The existing pipeline continues to own vacancy collection, filtering, analysis,
scoring, duplicate handling, and persistence. This operation does not add a
separate analysis path or application-history report.

## R7. Java facade and console entry points

- Add `DriverApi.checkRecommendations()` as a thin delegation to Orchestrator.
- Add `check-recommendations` to the Orchestrator CLI and the existing
  `Driver/api/job-seeker-api.jar` router. It accepts no additional arguments.
- CLI success prints the existing `WorkflowResult` JSON to stdout. Diagnostics
  and failures go to stderr. Preserve existing module CLI conventions and do
  not add another result wrapper or child Java process.
- Provide a small standalone Mailer CLI:
  `java -jar Mailer/mailer.jar recommendation-urls`. It calls the same Mailer
  API, prints the URL list as a JSON array, and does not start the pipeline.
  This command updates Mailer's operation checkpoint just like the API.
- For the standalone Mailer CLI, use exit code `0` for a successful result,
  `1` for an execution failure, and `2` for invalid arguments. Reject invalid
  syntax before opening the mailbox or changing Mailer state.

## R8. Verification

Regular automated checks use sanitized email fixtures, temporary local state,
and stubbed mail/workflow dependencies. They do not connect to a real mailbox,
start a browser, run Codex, or change the production databases.

Acceptance evidence must cover:

| Behavior | Required evidence |
| --- | --- |
| Interpretation | Representative recommendation emails yield the expected canonical vacancy URLs; unrelated links are excluded. |
| MIME handling | HTML/plain-text and multipart fixtures are decoded without losing the links needed by the scenario. |
| Repeated calls | UIDs through the last successful pass stay skipped after reconstructing Mailer with the same temporary checkpoint. |
| Failures | A retrieval or interpretation failure stops the pass, is logged and propagated, and leaves the previous checkpoint unchanged. |
| Orchestration | Nonempty URLs reach the existing URL workflow once; empty output and mail failures do not start it. |
| Entry points | Java facade and CLI delegate to the common implementation and preserve results and diagnostics. |
| Isolation | Existing non-mail commands do not require mailbox settings and their relevant tests still pass. |

Live checks are separate, user-directed actions. Report whether they ran and
their actual outcome. Missing account details or representative real messages
must be reported honestly; passing synthetic tests is not a claim that a real
mailbox or sender format has been verified.
