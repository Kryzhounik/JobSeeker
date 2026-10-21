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

## Scope of the remaining items

Items 2-4 stop at working low-level mail access: connect to the actual account,
enumerate messages, read them, and export complete `.eml` files for inspection.
They do not implement an application scenario.

The broader requirements and architecture still describe the intended Mailer
boundary. Their recommendation-related deliverables are deferred for these
items. Do not implement `getRecommendationUrls()`, processed-message storage,
vacancy checks, or Orchestrator/DriverApi integration just because those
documents mention them. Leave README, REQUIREMENTS, and ARCHITECTURE unchanged
for this work; record actual configuration and command usage in the item's
completion report.

Low-level reading and export must work independently of the provisional
LinkedIn recognition and extraction code from item 1. Reading an unfamiliar
message must not fail because its recommendation format is unknown.

Use a small standalone CLI alongside the Java API so the user and reviewer can
see what Java actually receives. Both entry points use the same implementation.
The commands below specify the required operations; keep their syntax small
and report the final syntax with each completed item. Use UTF-8 JSON for
structured console results, stderr for diagnostics, and exit codes `0` for
success, `1` for operational failures, and `2` for invalid arguments. Reject
invalid syntax before connecting to mail.

Ordinary automated checks use fixtures, stubs, or a local fixture server. Each
item also requires evidence from the real configured mailbox for its stated
outcome. Obtain missing provider, authentication, or folder details when they
are needed; do not invent values or claim that a simulated connection proves
real access. An assigned item and the supplied account/folder scope cover its
described read-only verification. Reviewers can use the recorded results and
local checks without independently accessing the real mailbox.

## 2. Real mailbox connection and folder inspection

**Deliverable**

- Obtain the actual provider and supported authentication method. Implement
  the connection path needed for that account, without additional login flows.
- Load local settings and credentials from Git-ignored `Data/mailer/` or the
  chosen local credential source. Do not connect during module construction.
- Add a typed Java operation to list the server's folders and identify which
  folders can contain messages. Preserve their actual names and hierarchy.
- Produce the runnable thin `Mailer/mailer.jar` with a minimal `folders`
  command calling that operation. Follow the existing shared `libs/` convention.
- Close connections and folders reliably. Do not change mail flags, send mail,
  move messages, or modify any JobSeeker business state.

**Checks**

- Verify configuration validation, visible authentication/connection errors,
  resource cleanup, and CLI output using offline checks.
- Run `mvn -f Driver/pom.xml -pl :mailer -am package`.
- Using the provided real account, demonstrate a successful connection and
  folder listing. Report the selected account scope without disclosing secrets.
  If access details are missing or connection fails, report that limitation;
  do not mark the real-connection outcome complete.

**Review gate**

An independent reviewer checks connection handling, API/CLI delegation, build
results, and the evidence of real access. Report and stop for the user.

## 3. Complete message enumeration and header inspection

**Deliverable**

- Add a typed Java operation and a `messages --folder <folder>` CLI command
  that enumerate every message in the explicitly selected folder.
- Return message selectors plus available headers such as sender, subject,
  received time, and Message-ID. Do not fetch all bodies merely to list headers.
- Include read and unread messages. Do not silently restrict the result to
  recent messages, LinkedIn senders, a first page, or an arbitrary count.
- Retrieval may use batches, but the default operation must traverse the
  complete selected folder. Handle an empty folder successfully.
- Message selectors must identify the same message after reconnect, because
  listing and reading are separate CLI invocations. Retain folder context and
  use IMAP UID/UIDVALIDITY or an equivalent stable identity rather than a
  mutable sequence index. A stale selector must not select a different message.
- Keep all access read-only. Do not create a processed-message database or
  decide which messages belong to the future recommendation scenario.

**Checks**

- Verify empty folders, mixed read/unread messages, non-LinkedIn senders,
  multiple retrieval batches, complete enumeration, and visible retrieval
  errors using offline cases.
- Verify selector stability across connections and detection of stale or
  missing messages. Confirm listing does not change read flags.
- Build and run relevant tests. Enumerate the real folder chosen for this
  verification and demonstrate that the complete message set is accessible.
  State the folder and count; do not copy private subjects into the report.

**Review gate**

An independent reviewer checks completeness, message identity, read-only
behavior, and the real-mail evidence. Report and stop for the user.

## 4. Message reading and full .eml export

**Deliverable**

- Add a typed Java operation and a `message --folder <folder> --id <id>` CLI
  command to read a selected message using the selector from item 3.
- Expose the headers and decoded plain-text and HTML bodies, separately when
  both exist. Reuse generic MIME handling where appropriate, without requiring
  sender classification, recommendation wording, or successful URL extraction.
- Add an `export --folder <folder> --id <id> --output <path>` CLI command and
  the matching Java operation to save a complete message as `.eml` locally.
- Export the full RFC 822/MIME message: headers, body parts, multipart
  boundaries, declared charsets, transfer encodings, and attachment content.
  Do not replace it with HTML-only output or a reconstruction from decoded DTOs.
- Prefer the original message stream available from the server/library. If
  library serialization changes headers or line endings, verify and report
  that behavior rather than claiming byte-for-byte original preservation.
- Raw export must remain available even if decoded-body interpretation fails
  or the message type is unknown. Store real samples under Git-ignored
  `Data/mailer/`; do not commit private messages.
- Keep CLI and Java reading/export behavior shared and mailbox access read-only.

**Checks**

- Verify plain-text, HTML, multipart alternatives, character/transfer encoding,
  missing bodies, and attachments with generic MIME fixtures.
- Verify that export preserves the complete MIME structure and attachment
  payload, and that a decoding failure does not prevent raw export.
- Verify argument/file-output errors and unchanged server read flags. Build
  the executable JAR and run the relevant tests.
- Read and export actual user-selected messages from the configured mailbox,
  including a recommendation email selected for later inspection. Compare
  Java's decoded output with its `.eml` source and record the local sample
  paths. Do not infer recommendation-selection or link-extraction rules yet.

**Review gate**

An independent reviewer checks the Java API/CLI behavior, MIME/export fidelity,
and evidence from the actual messages. Report and stop for the user's acceptance.

## Decision after the low-level items

After items 2-4 are implemented, independently reviewed, and accepted by the
user, stop implementation. The user and reviewer inspect the real `.eml`
samples and the data returned by Java.

Only then agree which specific messages the application scenario should use
and how their links or other useful data should be extracted. Use that evidence
to define the high-level requirements and a subsequent implementation plan.
No high-level work is authorized by completion of the low-level items.
