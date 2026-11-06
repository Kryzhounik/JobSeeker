---
name: applier-cover-letter
description: Get a saved cover letter or generate one for a stored JobSeeker vacancy by internal jobs.id.
---
Run from `C:/Users/user/Documents/Seeker` with Java 21 or newer:

```powershell
java -jar Applier/applier.jar cover-letter <jobs.id> [output.txt]
```

Use internal `jobs.id`, not a LinkedIn ID. Without the output argument stdout contains the letter. With it, the writer saves UTF-8 text and stdout contains the absolute file path. Quote paths with spaces. Relative paths resolve against the project root; the parent directory must exist. Diagnostics use stderr; nonzero exit means failure. Do not assemble vacancy or candidate data yourself. Writing instructions remain in `Applier/prompts/cover_letter_prompt.md`.

The command returns the saved letter without calling Codex. Each vacancy has one letter; it is generated and stored only when none exists. The optional file receives that same text.
