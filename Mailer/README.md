# Mailer

Mailer is a standalone Java module for receiving and interpreting email in
JobSeeker. It hides IMAP, MIME, and sender-specific message formats behind a
typed Java API.

For LinkedIn recommendations, Mailer provides vacancy URLs. The existing
workflow orchestrator decides how to process those vacancies. Mailer also
keeps its own record of processed messages.

- [Requirements](REQUIREMENTS.md)
- [Architecture](ARCHITECTURE.md)
- [Implementation plan and review gates](PLAN.md)
