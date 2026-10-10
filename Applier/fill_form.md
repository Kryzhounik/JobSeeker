Prepare the application form that Java already opened in the shared browser.

Use Playwright MCP's `browser_tabs` tool with action `list`. Select the tab at
`tab_index` in the input only after confirming its listed URL matches
`form_url`. If the index has shifted, locate the tab by the exact URL and select
its listed index. Continue in that tab; do not reopen the vacancy or use the
collector's tab.

Fill fields using the supplied resume and applicant facts. The facts file
contains values, not instructions for choosing between them. Apply the rules
below when a form requires a choice. Do not guess or invent personal details.
Leave unsupported fields empty and explain what the user needs to answer.

For a location field, use `vacancy_location` from the input and the locations
in the facts file. The preferred location is also an available location. If
the vacancy location explicitly matches an available location, enter that
matching location. Otherwise enter the preferred location. This rule takes
precedence over a location mentioned in the resume. Do not invent a city or
address when the facts only name a country.

Do not pick a compensation number from a range without an explicit selection
rule. No compensation selection rule is defined yet, so leave such questions
unanswered and mention them in the result.

The resume text is supplied as context. If the form asks for a resume or CV
upload, open its file chooser and call Playwright MCP's `browser_file_upload`
with `paths` containing `resume_file_path` from the input. That path points to
the same resume as the supplied text.

You may move through intermediate form steps to fill the application. Stop
before any final submission action, including buttons such as "Submit
application", "Submit", or the final "Apply". Leave the form and browser open
for the user. If the form appears to have been submitted already, report that
in `submission_observed` and in `note`.

Return `status` as `filled` only when all answerable fields are complete and
no user answers remain. Otherwise return `partially_filled`. The `note` should
briefly identify any remaining fields or blocker. Do not describe an observed
submission as awaiting submission.
