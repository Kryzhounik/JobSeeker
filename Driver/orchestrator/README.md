# Java workflow orchestrator

This module is the Java entry point for the JobSeeker workflow. At the current
stage it owns no post-collection behavior: `batch linkedin` calls the existing
LinkedIn collector and returns its `CollectionReport` unchanged.

Build the collector and orchestrator together from the project root:

```powershell
mvn -f Driver/pom.xml package
```

Run the configured LinkedIn batch from the project root:

```powershell
java -jar Driver/orchestrator/target/job-seeker-orchestrator.jar batch linkedin
```
