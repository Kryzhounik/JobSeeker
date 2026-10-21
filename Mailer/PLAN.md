# Mailer implementation plan

## Execution and independent review gates

This is a formal staged implementation plan. Its numbered items are execution
boundaries, not a checklist to complete in one run.

Before starting, read the applicable `AGENTS.md`,
[REQUIREMENTS.md](REQUIREMENTS.md), and
[ARCHITECTURE.md](ARCHITECTURE.md).

For every implementation item:

1. Implement only the item explicitly assigned by the user. Do not start later
   items, including their scaffolding, while waiting.
2. Run the relevant checks specified for that item. Report the item number,
   changed files and behavior, exact checks and actual results, and any
   unresolved limitation. Then **stop and wait for the user**.
3. The user decides when to request an independent review. The reviewer must
   be a different agent from the implementer and must inspect the actual diff
   against the requirements, architecture, and current item. The implementer's
   self-review is not this independent review.
4. Report review findings, or explicitly report that no actionable findings
   were found, together with verification limits. Stop for the user's decision.
5. If the user requests revisions, keep them within the current item. After
   revisions, report and stop again. The independent reviewer checks the
   revised result before the item can pass its review gate.
6. Start the next implementation item only after the independent review gate
   is satisfied **and the user explicitly instructs the implementer to start
   that next item**.

Approval of the overall plan, a passing build, a completed independent review,
silence, or an agent's own judgment never authorizes the next item. Do not
automatically launch a review or advance to another item after implementing an
item. The user controls the sequence.

Implementation is assigned to writer agents. The coordinating/reviewing agent
authors these documents and reviews the work; it does not implement the Java
changes. Reviewers report findings rather than silently fixing the code.

Creating these documents is not execution of the implementation items below.
No item is complete merely because it is described here.

## 1. Mailer module and offline message interpretation

**Deliverable**

- Implement the independent Java 21 Maven module in `Mailer/` and add it to the existing reactor,
  following its thin-JAR/shared-dependency conventions.
- Implement immutable mail DTOs, MIME decoding, message classification, and
  LinkedIn recommendation URL extraction using the agreed boundary.
- Keep interpretation runnable entirely from local email fixtures. Do not
  add live account access, processed storage, or workflow integration yet.
- Use representative sanitized messages for LinkedIn-specific rules. If none
  are available, implement and verify generic MIME behavior, report the missing
  fixture, and obtain it before claiming sender-specific extraction is accepted.

**Checks**

- Build the module and its reactor dependencies.
- Verify decoded HTML/plain-text and multipart messages, character encoding,
  relevant vacancy links, exclusion of unrelated links, URL normalization, and
  deterministic duplicate removal with fixtures that exercise real behavior.
- Confirm that Mailer has no dependency on its business consumers and that
  Jakarta Mail types do not leak into its DTOs.

**Review gate**

An independent reviewer checks the module boundary, fixture evidence, and
extraction behavior. Report, stop, and wait for the user's instruction.

## 2. IMAP access, configuration, and typed mail API

**Deliverable**

- Implement local settings, IMAP connection/search/retrieval, and cleanup.
- Expose `findMessages(MailQuery)` using the DTOs and interpretation from item 1.
- Keep mailbox access read-only and separate from processed-message tracking.
- Load connection settings when needed. Missing mail settings must not affect
  unrelated operations or module construction.
- Document the concrete local configuration keys in the architecture document
  or a small tracked example. Do not place account secrets in tracked files.
- Obtain the actual provider/authentication details when required. Do not add
  alternate login flows without a concrete need.

**Checks**

- Verify query-to-search mapping, successful retrieval, visible failures, and
  connection/folder cleanup using replaceable mail access or a local fixture
  server. Ordinary tests must not use the real mailbox.
- Verify that a mail-level search does not change processed-message state or
  mark server messages read.
- Build the module and run its relevant tests. If the user separately requests
  a live read, report its settings scope and actual result without exposing
  credentials or private message contents.

**Review gate**

An independent reviewer checks the public types, configuration boundary,
resource handling, and read-only behavior. Report and stop.

## 3. Recommendation API, local processed record, and Mailer CLI

**Deliverable**

- Implement `Mailer.getRecommendationUrls()` by composing the existing mail
  access and interpretation components.
- Add the small Mailer-owned processed-message table under `Data/mailer/`.
  Apply the success/failure semantics in requirements R4-R5.
- Add the standalone `recommendation-urls` CLI, using the same API and state.
  Produce the runnable thin `Mailer/mailer.jar`.
- Keep all message identities and tracking inside Mailer. Do not add caller
  acknowledgement, recovery queues, or vacancy-database access.

**Checks**

- With stubbed mail access and temporary state, verify first extraction,
  repeated calls, reconstruction with persisted state, empty results, multiple
  messages, repeated URLs, and visible interpretation/storage failures.
- Verify that failed messages are not marked successfully processed and that
  ordinary `findMessages()` calls do not consume recommendations.
- Verify CLI argument rejection without opening mail, JSON output, error
  output, and exit codes without a real mailbox.
- Run `mvn -f Driver/pom.xml -pl :mailer -am package` and inspect the JAR's
  runtime dependency paths.

**Review gate**

An independent reviewer checks the observable API behavior and storage
semantics, including their deliberately local scope. Report and stop.

## 4. Orchestrator recommendation operation

**Deliverable**

- Add `WorkflowOrchestrator.checkRecommendations()` and the small injectable
  recommendation source needed for offline verification.
- Pass a nonempty URL list to the existing `processUrls()` exactly once and
  return its result. Implement the agreed successful empty result separately.
- Propagate Mailer errors before the pipeline starts.
- Keep existing constructors and operations usable without Mailer settings.
  Do not add mail DTOs, application matching, or a second pipeline here.

**Checks**

- With stubs, verify URL order, one invocation of the existing URL workflow,
  result propagation, empty output, and mail errors.
- Confirm that empty results and Mailer failures do not start collection or
  analysis, and existing operations do not initialize the mailbox.
- Run the relevant existing Orchestrator tests and
  `mvn -f Driver/pom.xml -pl :workflow-orchestrator -am test`.

**Review gate**

An independent reviewer checks delegation, dependency direction, result
semantics, and compatibility with the existing workflow. Report and stop.

## 5. DriverApi and workflow CLI exposure

**Deliverable**

- Add the thin `DriverApi.checkRecommendations()` delegation.
- Add `check-recommendations` to the Orchestrator CLI and the Driver API CLI
  router. Route within the same JVM and use the existing result serialization.
- Update the relevant API/Orchestrator usage documentation with the new
  operation. Keep `Mailer/README.md` short; put technical details in the
  architecture or requirements document.

**Checks**

- Verify Java delegation and CLI routing with stubs. Verify that extra
  arguments are rejected before mail access and that stdout remains valid
  workflow JSON with diagnostics on stderr.
- Run the relevant existing API routing tests and
  `mvn -f Driver/pom.xml -pl :job-seeker-api -am package`.
- Confirm that the standalone Mailer command and the integrated workflow share
  the same Mailer implementation and do not duplicate interpretation logic.
- Report whether any user-requested live verification ran. Do not start the
  real recommendation workflow as an implicit final test.

**Review gate**

An independent reviewer checks the complete implemented path and the remaining
verification limits. Report and stop for the user's acceptance. Do not start
additional features after this item.
