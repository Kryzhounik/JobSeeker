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
| Processed-message storage | Mailer's local record of successfully interpreted recommendation-scan messages. |
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
retrieval/interpretation components and additionally applies local processed
tracking. Do not duplicate the MIME parser or sender rules between these APIs.

Construct `Mailer` using the repository root as a `Path`, consistent with the
other modules. Resolve mail settings and open the connection when an operation
needs them. Constructing DriverApi or running an existing operation must not
start Mailer or fail because mailbox settings are absent.

## Configuration and local data

Store local settings under `Data/mailer/config.properties`. Use one configured
account and folder. Settings cover the IMAP endpoint, TLS, account identity,
the selected provider's authentication, and the recommendation search range.
Define and document the concrete keys when implementing mail access.

The real provider, authentication method, folder, and initial range are still
unknown. Do not invent their values or implement several login strategies in
anticipation. Obtain the missing details when they are needed. Keep secrets
under Git-ignored `Data/` or reference the local secret source selected for the
actual account. A tracked example may contain placeholders only.

Use Mailer-owned local SQLite state at `Data/mailer/mailer.sqlite`, separate
from `Data/jobs.sqlite`. A small processed-message table is sufficient. Identify
IMAP messages using the configured account, folder, UIDVALIDITY, and UID, rather
than a mutable sequence number or read flag. No changes to JobSeeker's business
schema are needed for Mailer's bookkeeping.

Mailbox folders are opened for reading. Local state records Mailer's extraction
progress; server flags are not used to coordinate consumers.

## Recommendation extraction and bookkeeping

For each message selected by the configured recommendation search:

1. Skip it if its Mailer identity is already in the processed-message table.
2. Decode and classify it; extract and normalize applicable vacancy URLs.
3. Persist its processed identity after Mailer's interpretation succeeds.
4. Add its URLs to the operation's ordered, distinct output.

Use a deterministic message order and retain link order within each message.
The same vacancy linked several times in one call appears once. Mailer does
not query `jobs.sqlite` to decide whether that vacancy is already known.

An extraction or storage error must remain an error. Do not mark the failed
message processed or return a partial list as if the operation succeeded.
Messages completed before a later failure may already be recorded; do not add
cross-message recovery machinery to change that behavior.

Processed means "Mailer finished interpreting this message." Once the URLs
have been returned, Mailer's work is independent of the vacancy pipeline's
outcome. No acknowledgement, rollback callback, shared transaction, or delivery
queue connects the two modules.

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

LinkedIn-specific rules are **provisional**: only synthetic fixtures are
available. Recognition currently requires a single From address under
`linkedin.com` and English recommendation wording in the subject or body.
This is classification, not sender authentication. Direct numeric or slugged
`/jobs/view/<id>` URLs are normalized. Explicit LinkedIn `/redir/redirect` and
`/safety/go` wrappers can be decoded locally with bounded recursion; unknown
wrappers are not fetched. A recognized recommendation with no interpretable
vacancy link raises `MailException`. Other messages return type `OTHER` and an
empty URL list. These rules do not establish support for actual sender
templates, languages, or wrappers until representative sanitized messages are
provided and verified. Fixture provenance is recorded in
`src/test/resources/mail/README.md`.

No mailbox connection, local configuration loading, processed-message storage,
Mailer scan facade, or workflow integration is implemented in item 1.

Test MIME and sender interpretation with email fixtures, processed tracking
with temporary Mailer state, and workflow routing with injected stubs. Real
mail access is not part of normal unit tests. A live recommendation workflow
can start the real collector and paid analysis, so ordinary review does not
run it implicitly.
