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

For a phone number field, apply the same location-selection rule above and use
the number listed for that location in the facts file. If the selected
location has no listed number, leave the phone field unanswered and mention
it in the result. If the form asks for neither location nor phone, there is
no need to select a location.

For a compensation field, use the monthly range in the facts. Lower requested
pay corresponds to a more attractive vacancy; higher requested pay corresponds
to a less attractive one. Start at the midpoint for a typical acceptable role.
Assess actual responsibilities, working conditions, and fit with the resume,
not just attractive or unattractive words in the vacancy. One positive or one
negative factor may move the amount modestly from the midpoint, but cannot by
itself justify either end of the range. For the current USD 4,000–6,000 range,
fully remote work alone would suggest about USD 4,700, not USD 4,000.

Move close to the lower end only when at least two strong, independent
advantages coincide, such as fully remote work, direct hands-on work on agent
systems or other advanced technology, and a strong match with the applicant's
experience. Move close to the upper end only when at least two strong,
independent drawbacks coincide, such as mandatory office work, legacy
maintenance, night shifts, or user support. Do not count the same condition
twice under different names. Mentions of AI or agents in a product description
may count as a small positive because the role might involve those systems,
but they do not establish hands-on work with them and must not be weighted as
highly as explicit responsibilities.
Weigh advantages against drawbacks rather than counting them mechanically.
Select a monthly amount rounded to a multiple of USD 100; do not use excessive
precision or exceed the range. If the form requests a different pay period,
convert the selected monthly amount to the requested period using the form's
units.

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
