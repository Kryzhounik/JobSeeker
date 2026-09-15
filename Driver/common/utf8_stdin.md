Purpose: transport in-memory text and JSON to an existing Python command on
UTF-8 standard input from PowerShell, without temporary transport files.

Use this fixed transport stanza, replacing only the payload and the documented
Python command's arguments:

```powershell
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)
@'
<payload>
'@ | python <owning-script> <documented-arguments>
```

For JSON, serialize the object as one JSON line before placing it in the
here-string. Keep the opening and closing here-string markers on separate
lines, and the closing marker at the beginning of its line. A serialized JSON
line cannot contain a separate closing-marker line.

Do not put payloads in ordinary single-quoted or double-quoted PowerShell
strings. PowerShell recognizes typographic quotation marks as delimiters in
ordinary strings. A single-quoted here-string preserves straight and
typographic quotes, dollar signs, backticks, and other payload characters
without interpolation. Do not escape or rewrite those characters in the JSON.

The owning Python command still performs all validation and persistence.
This transport does not choose operations, repair results, or change scope.

If JavaScript constructs the tool request, keep the payload in a separate
string value and concatenate it between the stanza's markers. Do not insert
literal line breaks into an ordinary JavaScript string literal. Prefer retaining
the readable command's stdout in the tool session's `store` and using `load`
when constructing the following stdin request, so the text need not be copied
into JavaScript source.

Recovery: if an ordinary-string invocation failed during shell parsing before
Python started, stop the batch. After the user authorizes transport repair and
resumption, resend the same retained payload to the same owning Python command
using this stanza. Do not repeat extraction or create a response file.

If a syntactically incomplete tool request failed before shell dispatch, resend
a complete, syntactically valid request for the same intended shell command
with the same retained payload and parameters. This retries the documented
operation; do not reload the vacancy or change the Python command.
