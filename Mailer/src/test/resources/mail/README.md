# Offline fixtures

All `.eml` files here are synthetic, authored for these tests. None was received
from LinkedIn. Addresses and IDs are fictional; these are not sanitized copies
of real messages. They verify MIME decoding and provisional extraction rules,
not compatibility with a real LinkedIn recommendation template.

- `synthetic-html`: encoded subject, UTF-8 quoted-printable body, HTML entities,
  direct and slugged job paths, repeated links and unrelated navigation.
- `synthetic-plain`: ordered direct URLs and surrounding text punctuation.
- `synthetic-multipart`: nested mixed/alternative MIME, base64 plain text, HTML,
  explicit locally decodable redirect, attachment and forwarded-message exclusion.
- `synthetic-latin`: ISO-8859-1 body and encoded headers.
- `synthetic-unsupported`: recommendation marker with an unknown link format.

Before sender-specific acceptance, obtain representative sanitized original
messages, preserve their MIME structure, sender domain, recommendation wording,
link paths and wrapper parameters, and add expected canonical URLs. Include
the actual language/template variants the configured account receives. Do not
commit private recipients, tokens or account identifiers.
