# Java workflow orchestrator

This module is the Java entry point for the JobSeeker workflow. `batch linkedin`
calls the existing LinkedIn collector, sends its complete ordered scope to the
existing agent relevance filter through the metered Codex CLI proxy, validates
the returned IDs, marks them `NONRELEVANT` in one transaction, runs grouped
`job_facts`, candidate fit, job interest, and the existing database save.

The Python proxy is reached through the process-wide JPy runtime already used
by the collector. No separate Python process is started for orchestration; the
proxy itself launches the configured Codex CLI command.

Build the collector and orchestrator together from the project root:

```powershell
mvn -f Driver/pom.xml package
```

## Operations

The application performs these operations:

1. Collect LinkedIn vacancies and persist their raw/readable data.
2. Run the agent title filter and mark rejected vacancies `NONRELEVANT`.
3. Extract `job_facts` in configured groups.
4. Run the deterministic candidate-fit gate.
5. Evaluate the remaining vacancies in one persistent candidate-fit agent
   session.
6. Calculate `job_interest`.
7. Save the fully scored vacancies to SQLite.

## Console entry points

Run commands from the project root.

### Complete workflow

Runs all operations listed above. Currently `linkedin` is the only supported
source.

```powershell
java -jar Driver/orchestrator/target/job-seeker-orchestrator.jar batch linkedin
```

### Job-facts only

Runs operation 3 for an existing explicit scope. It does not collect vacancies
or continue to candidate fit.

```powershell
java -jar Driver/orchestrator/target/job-seeker-orchestrator.jar job-facts <run-id> <source> <comma-separated-job-ids>
```

### Finish analysis

Runs operations 4–7 for a scope whose `job_facts` files already exist.

```powershell
java -jar Driver/orchestrator/target/job-seeker-orchestrator.jar finish-analysis <run-id> <source> <comma-separated-job-ids>
```

### LinkedIn collector utilities

These are lower-level collector commands. They do not run the title filter,
analysis, scoring, or final save.

```powershell
java -jar Driver/collector/java_linkedin/target/linkedin-collector.jar login
java -jar Driver/collector/java_linkedin/target/linkedin-collector.jar batch
```

`login` opens the configured browser profile for LinkedIn authentication.
Collector `batch` performs collection only. Normal production execution should
use the orchestrator's `batch linkedin` command.

There are currently no separate Java console commands for the title filter,
candidate fit, job interest, or database save. They are exposed only as parts
of `batch` and `finish-analysis`.
