# Application adapters

Applier checks this directory after opening the actual application form. A
matching adapter is a Node.js ES module named after the form host, for example
`jobs.example.com.mjs`. Applier runs it with two arguments: the shared browser's
CDP endpoint, the current tab index, and the form URL for the already-open
form tab.

An adapter must continue in that tab, stop before final submission, and print
one JSON object to stdout with `status` (`filled` or `partially_filled`), a
short `note`, and `submission_observed` (boolean). No adapter scripts are
installed yet; Applier uses the Codex path when no matching host script exists.
