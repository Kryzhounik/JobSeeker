# Mailer

Mailer is a standalone Java module for receiving and interpreting email in
JobSeeker. It hides IMAP, MIME, and sender-specific message formats behind a
typed Java API.

For LinkedIn recommendations, Mailer provides vacancy URLs. The existing
workflow orchestrator decides how to process those vacancies. Mailer also
keeps one UID checkpoint for the last successful recommendation pass.

Java callers use `new Mailer(projectRoot).getRecommendationUrls()` and receive
`List<String>` of canonical vacancy URLs. Mailer does not start a browser or
analysis. The equivalent standalone command, run from the repository root, is:

```powershell
java -jar Mailer/mailer.jar recommendation-urls
```

Local settings are in `Data/mailer/config.properties`, progress is in
`Data/mailer/recommendations.properties`, and diagnostics are in
`Data/mailer/mailer.log`. Configuration keys and supported formats are described
in the architecture document. Manual inspection files belong in `Data/mailer/tmp/`.

- [Architecture](ARCHITECTURE.md)
