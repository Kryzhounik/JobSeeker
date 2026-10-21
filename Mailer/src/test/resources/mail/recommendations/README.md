# Sanitized recommendation fixtures

These four fixtures are derived from the actual HTML card structures in the
messages selected by the user: recommendation 439, Turnitin 437, Luxoft 438,
and Intellias 432. They cover three `X-LinkedIn-Template` values and the two
observed subject variants of `email_job_alert_digest_01`.

Card DOM structure and CSS classes are retained. All visible text is replaced,
personal recipients and forwarding addresses use example.invalid, unrelated
headers are removed, image sources are removed, and link tracking is replaced
with `trk=fixture`. Public vacancy IDs are retained in card order and recorded
in each `-ids.json` file. The reminder fixture retains its non-recommendation
heading link, which the parser must exclude. Search/unsubscribe links also
must not appear in results.

These are reduced, sanitized structural fixtures, not complete original MIME
messages. The originals and inspection helpers remain under Git-ignored
`Data/mailer/tmp/`. A separate live comparison checks the full original HTML.
