# Repository Instructions

Do not read, inspect, search, summarize, or use
`Driver/collector/deprecated_agent_collection/` while executing, driving,
diagnosing, reviewing, or learning the project unless `collectorMode=agent` is
the active mode or the user explicitly requests work on that deprecated
collector. In every other mode, treat the directory as absent.

`GUI/Tkinter/` is deprecated. Do not read, inspect, search, summarize, or use
that directory unless the user explicitly requests work on that legacy GUI.
For all other tasks, treat the directory as absent. `GUI/JavaFX/` is the
current GUI. This restriction does not cover shared files directly under
`GUI/`, such as `GUI/client_data.py`.

The stop-after-each-item rule applies only to a formal staged implementation
plan: a dedicated implementation-plan document or a plan explicitly agreed
with the user as staged implementation. Numbering alone does not make a list
such a plan. Numbered requests, checks, edits, and ordinary task lists in chat
do not require intermediate pauses; handle all requested items unless the user
explicitly asks for pauses.

When executing such a formal plan, complete only one plan item at a time.
After that item, stop and tell the user which item is done and what changed.
Wait for the user's response. The user decides whether to review, test, or
revise that item and when to start the next one. Do not make that decision or
continue to another item on your own.

## Coding Task Handoff

Use this format for small, scoped coding tasks. For larger module-level
development requiring a documentation set, use Module Implementation
Documentation below instead.

When asked to prepare an implementation task for another coding agent, base
it on the decisions already agreed upon. The receiving agent has access to
the repository but not necessarily to the discussion history.

**The goal is to eliminate ambiguity, not to explain how to program.**

Include:

- The objective and expected outcome.
- Relevant repository components and existing contracts.
- Agreed technical and architectural decisions that must be preserved.
- Concrete implementation scope and important constraints.
- Essential acceptance criteria and verification expectations.

**Calibrate the level of detail:**

- Preserve every non-obvious decision that affects correctness or architecture.
- Explain what must be done and how only where the approach has already been
  explicitly decided.
- Leave routine implementation details to the receiving agent.
- Do not enumerate obvious coding steps, methods, or tests.
- Do not repeat the same requirement in different sections.
- Reference existing code instead of describing how it works.
- Avoid speculative edge cases, defensive abstractions, and unnecessary
  infrastructure.
- Do not turn a small task into a comprehensive technical specification.
- Do not impose an arbitrary word limit; use the minimum detail necessary
  for reliable implementation.

Split the work into separate tasks only when there are meaningful
implementation boundaries or review checkpoints.

Output directly executable task instructions, not documentation files,
unless the user explicitly requests files. Preparing a handoff does not
authorize implementing the task or reopening agreed design decisions.

The result should resemble a well-written engineering issue or delegation
to an experienced colleague, not a tutorial.

## Module Implementation Documentation

Use this format for larger developments requiring a module documentation set,
rather than a compact Coding Task Handoff.

### Purpose

Based on the already agreed requirements, architectural decisions, and current
project state, prepare a set of documents sufficient for subsequent
implementation by coding agents.

This task is about **formalizing existing decisions**, not designing the module
from scratch.

Do not reconsider agreed decisions, introduce new functionality, or add
unnecessary architectural complexity. Do not start implementation.

### README.md - Module Overview

A short navigation document.

Its purpose is to help a person or agent quickly determine whether the module
is relevant to the task they are investigating.

Include:

- Module purpose.
- Responsibility boundaries: what the module does and does not do.
- Main capabilities provided by the module.
- High-level interactions with other modules.
- Links to other module documentation.

Do not include implementation details, internal algorithms, or technical
information unnecessary for understanding the module's purpose.

**Keep the README short.** It is an entry point, not comprehensive module
documentation.

### REQUIREMENTS.md - Requirements

Defines **what the implementation must satisfy**.

Include where applicable:

- Functional requirements.
- Constraints and invariants.
- External contracts and compatibility requirements.
- Input and output requirements.
- Non-functional requirements.
- Verifiable acceptance criteria.

Do not describe implementation approaches unless they are part of the agreed
constraints.

Do not duplicate contracts already defined in other documents or schemas.
Reference them instead.

Create this file only when there are meaningful requirements worth maintaining
separately.

### ARCHITECTURE.md - Architecture

Documents **how the module is intended to work and why**.

Include:

- Major components and their responsibilities.
- Architectural boundaries.
- Component interactions and data flows.
- Significant technical decisions.
- Rationale behind non-obvious decisions.
- Important rejected alternatives and reasons for rejection, if previously
  discussed.
- Architectural constraints and invariants.

Do not enumerate every class and method. Do not repeat requirements. Do not
include implementation sequencing.

Document only agreed architectural decisions. Do not invent new ones.

### PLAN.md - Implementation Plan

Transforms the requirements and architecture into **a sequence of concrete
tasks for coding agents**.

Break the implementation into logically complete stages.

For each stage, specify:

- Objective.
- Concrete changes to be made.
- Dependencies on other stages.
- Expected outcome.
- Completion criteria and verification methods.

Plan requirements:

- Each stage must be sufficiently specific to assign to a separate coding agent.
- Whenever practical, each stage should leave the project in a working state.
- Stage ordering must respect dependencies.
- All significant agreed requirements must be covered.
- Do not introduce optional improvements or speculative future features.
- Reference other documents instead of duplicating large sections.
- Avoid prescribing unnecessary implementation details when specifying the
  expected outcome is sufficient.

`PLAN.md` is a working document that may be deleted after implementation is
complete.

### General Rules

1. Use the existing discussion context, project code, and agreed decisions as
   the source material.
2. Do not start another design or requirements discussion.
3. Do not turn assumptions into approved requirements or decisions.
4. If essential information is missing, explicitly identify the gap rather than
   inventing an answer.
5. Avoid duplication between documents.
6. Do not create unnecessary documents or sections. Documentation depth should
   match module complexity.
7. Use exact names of existing components, interfaces, and files where useful.
8. Documentation must be understandable to both humans and coding agents
   responsible for implementation.
9. Preserve important architectural rationale, but not the discussion transcript.
10. **Do not modify implementation code. The only deliverables are documents
    for subsequent development.**

## Independent Review - Test Execution Policy

Do not run existing test suites by default during code review.

Assume the implementation agent has already executed the relevant tests and
reported their results.

Focus review effort on:

- Correctness of the implementation.
- Compliance with requirements and architecture.
- Missing edge cases and logical errors.
- Test coverage gaps and incorrect test assumptions.

Inspect test code when relevant, but do not execute tests merely to confirm
that they pass.

Run targeted tests only when necessary to investigate a specific suspected
defect. Explain why the execution is needed.

A passing test suite is not a review finding. Passing tests do not prove
correctness or replace code analysis.

This policy applies to code review, not to explicitly requested test execution.

## Code Review Philosophy

JobSeeker is a personal automation tool, not a banking system or a
mission-critical service.

**The primary review objective is to keep the design and implementation
simple, correct, and maintainable.**

Review priorities:

1. **Simplicity** - Identify unnecessary abstractions, complexity, duplication,
   and overengineering.
2. **Requirements and architecture** - Verify that the implementation does
   what was requested and respects module boundaries.
3. **Correctness** - Focus on real bugs in normal workflows, incorrect
   assumptions, and meaningful edge cases.
4. **Secrets** - Strictly prevent credentials, API keys, tokens, passwords,
   and other authentication secrets from leaking into Git or logs.
5. **Failure handling** - Consider realistic failures proportionally to their
   actual consequences.

**Avoid defensive overengineering:**

- Do not demand elaborate recovery mechanisms, transactions, retries, queues,
  or rollback logic for hypothetical failures.
- Do not treat occasional loss of recoverable data or the need to rerun an
  operation as a critical defect.
- Do not classify ordinary job-search data, application history, or the user's
  publicly available resume as highly sensitive secrets.
- Do not recommend additional complexity merely to satisfy theoretical best
  practices.

**Review findings must be actionable and proportionate.**

For every finding, explain the concrete failure scenario, its realistic impact,
and why addressing it is worth the added complexity.

Prefer a simple solution with understood limitations over a complex solution
designed to prevent every conceivable failure.

Do not ignore real defects. But distinguish problems that materially affect
the tool's usefulness from speculative risks that are acceptable for this
project.

## Prefer Existing Libraries

Prefer suitable standard-library APIs and open-source libraries over equivalent
custom implementations, even when they replace only a few lines of code.

Do not add a dependency when integrating it would be more complex than a small
local implementation.

## Independent Design Judgment During Review

Existing code is not proof of good design. When reviewing new or modified
public APIs and architectural boundaries, evaluate design decisions
independently rather than accepting them merely because they follow existing
patterns.

## No Unrequested Scope Expansion

Implement the requested behavior and the changes necessary to support it.

Do not treat useful follow-ups, convenience features, or anticipated future
needs as implied requirements.

Do not add unrequested public APIs, CLI commands, GUI controls, configuration
options, or other externally visible capabilities unless they are necessary
for the agreed task.

If such an addition is not necessary, omit it. If it is necessary but introduces
a significant decision not already agreed upon, obtain approval before
implementing it.

Routine internal helpers and straightforward integration changes needed for
the requested behavior do not require separate approval.

**Module ownership defines where you may work, not which additional features
you may invent.**

## Cross-Module Changes

Implementation agents must obtain the user's approval before making
significant architectural or design changes in modules outside their
assigned scope. Explain the proposed decision before implementing it.
An explicitly approved task or plan that already covers that decision is
sufficient; do not request the same approval again.

Cross-module changes are not inherently problematic and do not require
separate approval when they are minor, necessary, and consistent with the
existing architecture.

Reviewers should flag cross-module changes only when they introduce
significant architectural or design decisions outside the assigned task's
scope.

Examples include changes to public API design, module responsibilities,
dependency direction, or component lifecycle.

Do not require additional discussion or approval for routine integration
changes, small fixes, or straightforward adaptations of existing contracts.

The goal is to prevent unauthorized architectural decisions, not to create
bureaucracy around ordinary development.

## Determining Task Scope

Determine your area of responsibility from the established conversation
context, your current assignment, and the repository's module boundaries.

Do not require the user to explicitly restate your scope for every task.

If a request falls outside your established scope, ask for explicit confirmation
before investigating or acting. Do not treat the request itself as permission
to expand your scope.

Routine cross-module integration work is not, by itself, a reason to request
clarification. Ask only when the assignment genuinely appears misdirected or
its ownership is materially ambiguous.
