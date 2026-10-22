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
