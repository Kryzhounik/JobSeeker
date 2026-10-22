# Mailer architecture

## Placement and dependency direction

`Mailer/` is the module directory at the repository root. Use package
`com.seeker.mailer` and Maven artifact `com.seeker:mailer`. It joins the existing
Java 21 reactor in `Driver/pom.xml` as `../Mailer`.

Use the existing thin-JAR and shared `libs/` conventions. The standalone
executable is `Mailer/mailer.jar`. Runtime mail libraries stay within Mailer's
implementation boundary.

```text
DriverApi.checkRecommendations()
    -> WorkflowOrchestrator.checkRecommendations()
        -> Mailer.getRecommendationUrls() : List<String>
        -> WorkflowOrchestrator.processUrls(urls)
            -> existing collection and analysis pipeline
```

Orchestrator depends on Mailer and its existing pipeline dependencies. Mailer
does not depend on Collector, Orchestrator, Applier, DriverApi, or GUI. Internal
module calls use their Java APIs directly; they do not call back through the
DriverApi facade.

## Mailer responsibilities

The module keeps the following responsibilities within one library:

| Responsibility | Owns |
| --- | --- |
| Mail access | Local connection settings, IMAP connection lifetime, folder search, retrieval, and resource cleanup. |
| Message interpretation | MIME decoding, sender recognition, message classification, and useful field/link extraction. |
| Operation progress | One persisted UID checkpoint per successfully completed operation pass. |
| Public facade | Typed mail access and the convenient `getRecommendationUrls()` operation. |

Vacancy collection, database duplicate checks, Fit analysis, matching mail to
applications, and changing vacancy or application statuses belong to consumers.
Application-response tracking is outside the current recommendation operation.

Use Jakarta Mail with Eclipse Angus for IMAP and MIME. Business modules do not
import their message, folder, session, or exception types. The module is an
in-process library; its CLI is another caller of that library.

## Public types and operations

The required scenario contract is:

```text
Mailer.getRecommendationUrls() -> List<String>
```

It returns canonical vacancy URLs, never a batch object containing mail
identifiers. Empty is a normal successful result. Operational failures are
reported through a Mailer-owned checked exception compatible with
`IOException`, so the existing Orchestrator/API exception boundary can be kept.
Preserve the original cause for diagnostics. Connection, authentication,
retrieval, decoding, and local-state failures are errors, never successful empty
recommendation results.

The current mail-level API is:

| Operation | Result |
| --- | --- |
| `listFolders()` | `List<MailFolder>` with server folder names and message-container capabilities. |
| `listMessages(String folder)` | `MailMessageListing` with all message headers and stable selectors in the selected folder. |
| `readMessage(String folder, String id)` | `MailMessageContent` with headers and decoded plain-text and HTML bodies separately. |
| `exportMessage(String folder, String id, Path output)` | `MailExport` describing a complete local `.eml` export. |

DTOs are immutable Mailer-owned types in `com.seeker.mailer`; Jakarta Mail
objects and MIME trees stay internal. Message selectors carry account, folder,
UIDVALIDITY and UID context, so reconnecting does not turn a selector into a
different message. Stale or missing messages are errors.

Listing includes read and unread messages without an arbitrary result limit
and does not fetch bodies. Reading and export do not depend on message type or
recommendation extraction. Raw export preserves the complete message, including
MIME structure and attachment payloads, and remains available when body decoding
fails. It is not reconstructed from decoded DTOs.

Listing, reading and export never advance the recommendation checkpoint. The
recommendation workflow receives only URLs, without message DTOs or identities.
Generic MIME decoding and URL normalization are shared internally.

Construct `Mailer` using the repository root as a `Path`, consistent with the
other modules. Resolve mail settings and open the connection when an operation
needs them. Constructing DriverApi or running an existing operation must not
start Mailer or fail because mailbox settings are absent.

## Configuration and local data

Store local settings under `Data/mailer/config.properties`. Use one configured
account and folder. Settings cover the IMAP endpoint, TLS, account identity,
the selected provider's authentication, and the recommendation search range.

The selected connection is Gmail IMAPS (`imap.gmail.com:993`) using an app
password, verified TLS certificates and hostname. Settings are loaded lazily.
Keep secrets under Git-ignored `Data/`. A tracked example contains placeholders
only. The supported local configuration keys are:

| Key | Meaning |
| --- | --- |
| `imap.host`, `imap.port` | Gmail IMAPS endpoint. |
| `imap.username`, `imap.appPassword` | Mailbox account and Google app password. |
| `imap.timeoutMillis` | Connect/read/write timeout; default 15000 ms. |
| `recommendations.folder` | Explicit recommendation scan folder. |
| `recommendations.originalRecipient` | Original To address before forwarding. |
| `recommendations.startAfterUid` | Explicit initial UID boundary, used only when no checkpoint exists. |

Folder, original recipient, initial UID boundary and credentials remain in local
configuration. A new operation requires an explicit initial boundary; a changed
account, folder, original recipient or UIDVALIDITY must fail rather than silently
reuse or reset the existing checkpoint. Unrelated mail operations do not require
the recommendation settings.

Manual message samples, diagnostic dumps and verification reports belong under
`Data/mailer/tmp/`. They are not part of tracked source.

Use one persistent UID checkpoint per operation; no SQLite message table or
list of processed messages is needed. The checkpoint carries the account,
folder and UIDVALIDITY context so an old UID cannot be reused in a different
scope. `Data/mailer/recommendations.properties` stores the one recommendation
checkpoint: account fingerprint, folder, original recipient, UIDVALIDITY and
lastUid. A temporary adjacent file is atomically renamed after the pass and
mailbox cleanup succeed. There is no fallible mail/log action after the commit.
No business database changes are needed.

Mailbox folders are opened READ_ONLY. Operations do not mark messages read,
move or delete messages, or send mail. Local state records Mailer's extraction
progress; server flags and timestamps are not progress records.

## Recommendation extraction and bookkeeping

For one recommendation pass:

1. Read its operation checkpoint and visit later UIDs in ascending order.
2. Check the sender/template and forwarding headers below; log and skip
   nonmatching messages without parsing their bodies.
3. Parse each matching message using its known format. Collect an ordered,
   distinct URL list. Log the subject, identifier and extracted URLs.
4. On an error, log it with the subject and identifier, stop immediately and
   throw. Leave the persisted checkpoint unchanged and do not return partial
   output as a successful result.
5. Only after the whole pass and cleanup succeed, persist its highest scanned
   UID once. Skipped messages also count toward that UID.

Use a deterministic message order and retain link order within each message.
The same vacancy linked several times in one call appears once. Mailer does
not query `jobs.sqlite` to decide whether that vacancy is already known.

An extraction or checkpoint-write error must remain an error. A retry repeats
the failed pass from its old checkpoint, including earlier successful messages.
No per-message state or cross-message recovery machinery is required.

The checkpoint means "Mailer successfully finished this pass." Once URLs have
been returned, downstream work is independent. Mailer never starts a browser,
analyzer or vacancy workflow. No acknowledgement, rollback callback, shared
transaction or delivery queue connects the modules.

`Mailer.getRecommendationUrls()` and `recommendation-urls` share the same
in-process implementation. They open one folder READ_ONLY, bound the pass by
UIDNEXT, process later UIDs in ascending order, and persist the checkpoint once
after closing folder/store successfully. Reversed/empty IMAP ranges are never
queried. A retry after any failed pass starts from the previous checkpoint.
Calls in the same JVM are serialized. Current operations append simple local
UTF-8 JSON lines to `Data/mailer/mailer.log`: header/body reads, available seen
flag, filter/extraction result, export outcome, and controlled errors with
message identifiers and subjects. The log is separate from operation progress.
Constructing Mailer opens no log file. Credentials, full bodies and private
provider responses are excluded; diagnostics do not enter stdout JSON.

## Supported recommendation formats

Filter headers before reading bodies. Require the exact original To address,
X-Forwarded-To equal to the configured mailbox login, and X-Forwarded-For
containing both the original recipient and mailbox account. Match these sender
and template pairs; display names and localized subjects are not predicates:

| Sender | X-LinkedIn-Template | Card link rule |
| --- | --- | --- |
| `jobs-noreply@linkedin.com` | `email_jobs_viewed_job_reminder_01` | Title anchors with `text-md leading-regular text-color-brand`. Excludes the original vacancy in the h1 heading. |
| `jobs-noreply@linkedin.com` | `email_jobs_facet_suggestions` | Closest enclosing anchor around the `div` title with `font-bold text-md leading-regular text-system-blue-50`. |
| `jobalerts-noreply@linkedin.com` | `email_job_alert_digest_01` | Title anchors with `font-bold text-md leading-regular text-system-blue-50`; supports both observed subject variants. |

Other templates, including `email_application_confirmation_with_nba_01`, are
logged and skipped without reading their bodies. Matching messages with no
recognized HTML cards or invalid card URLs fail the pass. `/comm/jobs/view/`
and direct `/jobs/view/` links share canonicalization to
`https://www.linkedin.com/jobs/view/<id>/`; first-occurrence order is retained.
No title filters, vacancy lookup, browser or analyzer is called.

Keep message identities and any needed provenance inside Mailer. There is no
need to expose them through `WorkflowResult` or to store an email archive for
this scenario.

## Orchestrator integration

Add a small recommendation-source dependency that can be replaced by a stub
in tests. A Java functional interface or existing equivalent is enough; a
dependency-injection framework is unnecessary.

`checkRecommendations()` calls Mailer once to obtain the URL list and calls the
existing `processUrls(urls.toArray(String[]::new))` once when it is nonempty.
Return the unchanged `WorkflowResult` from that call. Do not reconstruct the
pipeline, reclassify duplicates, or interpret mail inside Orchestrator.

For an empty list, return an empty `WorkflowResult` with source `linkedin`,
status `complete`, null run ID and collection, empty processed scope, zero
filtering count, and a message such as `No new recommendations`. No collection
run took place, so do not manufacture a `CollectionReport`.

Mailer failures propagate before `processUrls()` is called. Existing duplicate
handling and result semantics remain those of the current workflow. Searching
application history or producing a per-recommendation advisory report is a
separate change, not part of this operation.

## Facade and CLI integration

`DriverApi.checkRecommendations()` delegates to Orchestrator and returns its
`WorkflowResult`. The `check-recommendations` router command delegates to the
Orchestrator CLI in the same JVM, just like `process-urls`.

The standalone Mailer command `recommendation-urls` only serializes the list
returned by the Mailer API. It shares configuration, parsing, and processed
tracking with the integrated workflow and advances the same checkpoint. It does
not implement a second scan. Its successful output is a JSON array of URLs.

The standalone CLI also exposes the mail-level operations:

```text
java -jar Mailer/mailer.jar folders
java -jar Mailer/mailer.jar messages --folder <folder>
java -jar Mailer/mailer.jar message --folder <folder> --id <id>
java -jar Mailer/mailer.jar export --folder <folder> --id <id> --output <path.eml>
java -jar Mailer/mailer.jar recommendation-urls
```

Run it from the repository root. Structured results use UTF-8 JSON on stdout;
diagnostics and failures go to stderr. Exit codes are `0` for success, `1` for
operational failure and `2` for invalid arguments. Validate syntax before opening
the mailbox or changing state. All commands delegate to the common Java API.

The integrated `check-recommendations` command accepts no additional arguments
and prints the existing `WorkflowResult` JSON. Preserve the existing router and
Orchestrator CLI conventions without adding a result wrapper or child JVM.

## Message interpretation

Jakarta Mail decodes headers, character sets and transfer encodings; jsoup parses
HTML locally without loading external resources. MIME body parts are visited in
wire order, including both multipart alternatives. Plain text and HTML remain
separate. Attachments, non-inline named file parts and encapsulated messages are
excluded from body interpretation, but remain in raw export. Explicit inline
body parts can carry filenames.

Only links from recommendation cards are returned; account, unsubscribe,
search and unrelated links are excluded. Direct numeric or slugged vacancy
links are canonicalized by ID. Known LinkedIn `/redir/redirect` and `/safety/go`
wrappers are decoded locally with bounded recursion. Mailer never fetches a
vacancy or opens a browser to interpret a link.

The separate `internal.MessageInterpreter` helper accepts an input stream with
a caller-supplied identifier and receipt time; the caller owns the stream. Its
English wording-based classification is provisional and is not used by
`getRecommendationUrls()`. That operation uses the sender/template and card
rules above.

## Verification boundary

Use representative sanitized email fixtures for sender-specific rules;
synthetic MIME fixtures alone do not establish compatibility with real sender
formats. Fixture provenance is documented under `src/test/resources/mail/`.

Automated checks use fixtures, temporary local state and stubbed mail/workflow
dependencies. They cover extraction, MIME, repeated calls, failure/checkpoint
behavior, CLI delegation and isolation from unrelated operations. They do not
connect to a real mailbox, start a browser or analyzer, run Codex, or change
production databases. Live checks are separate user-directed actions; manual
evidence belongs in `Data/mailer/tmp/`, not in this architecture document.
