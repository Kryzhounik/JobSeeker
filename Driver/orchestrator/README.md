# Java workflow orchestrator

This module is the Java entry point for the JobSeeker workflow. `batch linkedin`
calls the existing LinkedIn collector, sends its complete ordered scope to the
existing agent relevance filter through the metered Codex CLI proxy, validates
the returned IDs, marks them `NONRELEVANT` in one transaction, runs grouped
`job_facts`, and returns the remaining scope in collection order.

The Python proxy is reached through the process-wide JPy runtime already used
by the collector. No separate Python process is started for orchestration; the
proxy itself launches the configured Codex CLI command.

Build the collector and orchestrator together from the project root:

```powershell
mvn -f Driver/pom.xml package
```

Run the configured LinkedIn batch from the project root:

```powershell
java -jar Driver/orchestrator/target/job-seeker-orchestrator.jar batch linkedin
```

Run `job_facts` for an existing explicit scope without collecting again:

```powershell
java -jar Driver/orchestrator/target/job-seeker-orchestrator.jar job-facts <run-id> <source> <comma-separated-job-ids>
```
