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

Keep four small internal responsibilities, without turning them into a plugin
framework or separate services:

| Responsibility | Owns |
| --- | --- |
| Mail access | Local connection settings, IMAP connection lifetime, folder search, retrieval, and resource cleanup. |
| Message interpretation | MIME decoding, sender recognition, message classification, and useful field/link extraction. |
| Operation progress | One persisted UID checkpoint per successfully completed operation pass. |
| Public facade | Typed mail access and the convenient `getRecommendationUrls()` operation. |

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
Preserve the original cause for diagnostics.

For mail-level search and interpretation, provide:

```text
Mailer.findMessages(MailQuery query) -> List<MailMessage>
```

Use small immutable DTOs or records owned by Mailer:

- `MailQuery`: sender and received-after search criteria; the mailbox folder
  comes from local settings. Define the behavior of omitted criteria explicitly.
- `MailMessage`: opaque message identifier, sender, subject, received time,
  decoded text, recognized message type, and extracted vacancy URLs.
- A simple message-type value distinguishes recognized LinkedIn
  recommendations from other messages. Add classifications when their
  interpretation is actually implemented.

The provider-specific identity and MIME tree stay internal. Only mail-level
consumers receive `MailMessage`. The recommendation workflow uses
`getRecommendationUrls()` and does not receive message DTOs.

`findMessages()` searches and interprets matching messages without changing
recommendation-processing state. `getRecommendationUrls()` uses the same
retrieval/interpretation components and additionally applies the operation UID
checkpoint. Do not duplicate the MIME parser or sender rules between these APIs.

Construct `Mailer` using the repository root as a `Path`, consistent with the
other modules. Resolve mail settings and open the connection when an operation
needs them. Constructing DriverApi or running an existing operation must not
start Mailer or fail because mailbox settings are absent.

## Configuration and local data

Store local settings under `Data/mailer/config.properties`. Use one configured
account and folder. Settings cover the IMAP endpoint, TLS, account identity,
the selected provider's authentication, and the recommendation search range.
Define and document the concrete keys when implementing mail access.

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

The current user-selected recommendation scope is INBOX after UID 425.
Original recipient and credential values remain only in local configuration.
Unrelated mail operations do not require the recommendation settings.

Use one persistent UID checkpoint per operation; no SQLite message table or
list of processed messages is needed. The checkpoint carries the account,
folder and UIDVALIDITY context so an old UID cannot be reused in a different
scope. `Data/mailer/recommendations.properties` stores the one recommendation
checkpoint: account fingerprint, folder, original recipient, UIDVALIDITY and
lastUid. A temporary adjacent file is atomically renamed after the pass and
mailbox cleanup succeed. There is no fallible mail/log action after the commit.
No business database changes are needed.

Mailbox folders are opened for reading. Local state records Mailer's extraction
progress; server flags are not used to coordinate consumers.

## Recommendation extraction and bookkeeping

For one recommendation pass:

1. Read its operation checkpoint and visit later UIDs in ascending order.
2. Check the agreed sender, forwarding and subject criteria; log and skip
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

The four sanitized real-structure fixtures cover 10, 4, 1 and 4 recommendation
cards. Full original HTML is additionally checked locally under
`Data/mailer/tmp/`; those private files are not required by automated tests.

Keep message identities and any needed provenance inside Mailer. There is no
need to expose them through `WorkflowResult` or to store an email archive for
this scenario.

## Orchestrator integration

Add a small recommendation-source dependency that can be replaced by a stub
in tests. A Java functional interface or existing equivalent is enough; a
dependency-injection framework is unnecessary.

`checkRecommendations()` obtains the URL list and calls the existing
`processUrls(urls.toArray(String[]::new))` once when it is nonempty. Return the
unchanged `WorkflowResult` from that call. Do not reconstruct the pipeline,
reclassify duplicates, or interpret mail inside Orchestrator.

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
tracking with the integrated workflow. It does not implement a second scan.

Validate each new command's syntax before starting mail access. Follow the
existing stdout/stderr conventions; mailbox diagnostics must not corrupt JSON.

## Verification boundary

### Item 1 implementation status

The offline module uses Java 21 bytecode, Eclipse Angus Jakarta Mail for MIME
decoding, and jsoup for local HTML parsing. It has no business-module dependency.
The inherited reactor build produces a thin `Mailer/mailer.jar` with manifest
references to repository-root `libs/`. It is a library at this stage; a main
class and the standalone CLI belong to item 3.

`MailQuery`, `MailMessage`, `MailMessageType`, and `MailException` are owned by
`com.seeker.mailer`. Null query criteria omit that restriction; `receivedAfter`
is exclusive. A null message receipt time means unavailable, not the sender's
Date header. The list of extracted URLs is defensively copied.

`internal.MessageInterpreter` decodes an input stream with a caller-supplied
opaque identifier and receipt time. The caller owns the stream. Jakarta Mail
types are confined to the internal implementation. MIME body parts are visited
in wire order, including both multipart alternatives, then canonical links are
deduplicated in first-occurrence order. Attachments, named file parts, and
encapsulated forwarded messages are excluded from recommendation content.
HTML is parsed locally without loading external resources.

The older `MessageInterpreter` rules remain **provisional** and are not used by
`getRecommendationUrls()`. Their recognition requires a single From address under
`linkedin.com` and English recommendation wording in the subject or body.
This is classification, not sender authentication. Direct numeric or slugged
`/jobs/view/<id>` URLs are normalized. Explicit LinkedIn `/redir/redirect` and
`/safety/go` wrappers can be decoded locally with bounded recursion; unknown
wrappers are not fetched. A recognized recommendation with no interpretable
vacancy link raises `MailException`. Other messages return type `OTHER` and an
empty URL list. These rules do not establish support for actual sender
templates, languages, or wrappers until representative sanitized messages are
provided and verified for that older API. The recommendation API instead uses
the verified header/card rules above. Original synthetic fixture provenance is recorded in
`src/test/resources/mail/README.md`.

No mailbox connection, local configuration loading, processed-message storage,
Mailer scan facade, or workflow integration is implemented in item 1.

Test MIME and sender interpretation with email fixtures, operation checkpoints
with temporary Mailer state, and workflow routing with injected stubs. Real
mail access is not part of normal unit tests. A live recommendation workflow
can start the real collector and paid analysis, so ordinary review does not
run it implicitly.
