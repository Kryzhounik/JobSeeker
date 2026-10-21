# Mailer workspace hygiene

Store manual diagnostic dumps, exported email samples, inspection helpers,
comparison notes, and verification reports under `Data/mailer/tmp/`.
Do not place temporary artifacts in the repository root, `Mailer/`, or directly
in `Data/mailer/`. Keep configuration, operational logs, and persistent runtime
state in `Data/mailer/`. Automated tests use their temporary directories.
