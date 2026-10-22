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
