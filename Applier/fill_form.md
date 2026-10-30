Prepare the application using the employer page that Java already opened in the
shared browser.

Use Playwright MCP's `browser_tabs` tool with action `list`. Confirm that the
tab at `tab_index` matches `form_url`; if the index shifted, locate it by exact
URL. If that tab is already marked `(current)`, continue without selecting it
again. Select only when changing to a different tab needed for the application.
Do not call `bringToFront()`, focus browser windows, or reselect the current tab
for filling or verification. Continue in the form tab; do not reopen the LinkedIn
vacancy or use the collector's tab. The employer page may show the job description before
the form. If so, follow its Apply link or button to the form, switching to the
new tab if one opens.

Fill fields using the supplied resume, shared candidate profile `resume.ini`,
and applicant facts. The facts file
contains values, not instructions for choosing between them. Apply the rules
below when a form requires a choice. Do not guess or invent personal details.
Leave unsupported fields empty and explain what the user needs to answer.

For an application or office location, use `vacancy_location` from the input and
the available countries in `[locations]`. Choosing an application country does
not assert current residence or work authorization there.
Java reads `vacancy_location` from the LinkedIn page. If it
is empty, first read the vacancy location on the already-open employer page;
an empty input does not establish that the vacancy has no matching location.
If the vacancy location explicitly matches an available country, enter that
matching country. Otherwise choose the concrete country with the highest
interest score in `[locations]`. When the form offers a restricted set of
countries, choose among the available countries it actually offers, preferring
the vacancy's country, then the highest interest score. Leave the field unanswered
if none of the offered countries is available. For example, when the choices
are Bulgaria and Georgia, the listed availability of Georgia supports selecting
Georgia without confirming current residence there. This rule takes
precedence over a location mentioned in the resume. Do not invent a city or
address when the facts only name a country.

For a phone number field, use the number in the facts for the applicant's
residence country selected in the form. The application/office country must
not override this choice. If the form does not ask for residence, match the
vacancy location to the countries with phone numbers in the facts; otherwise
choose the highest-interest of those countries using `[locations]`. If the
selected residence has no listed number, leave the phone unanswered and mention
it in the result. If neither residence nor phone is required, do not select
an applicant location just for those fields.

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
