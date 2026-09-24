# Java orchestration for `job_facts`

- [x] Add one Python bridge call that runs and persists one `job_facts` turn.
- [x] Add one thin Java JPy call returning the persistent `thread_id`.
- [x] Group vacancies by configurable `vacancies_per_agent`.
- [x] Invoke the grouped runner from `WorkflowOrchestrator`.
- [ ] Remove Desktop ownership of `job_facts` from the workflow docs.
- [x] Run the focused Python and Java checks.
- [ ] TODO: add `parallel_agents` only after sequential execution is stable.
