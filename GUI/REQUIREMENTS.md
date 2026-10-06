# GUI requirements

Checklist for the JavaFX GUI, assembled from the conversation and the current
Tkinter behavior. `GUI/Tkinter/README.md` describes the legacy implementation;
this file describes the user-visible behavior to reproduce.

## Main window

- [ ] Show a sortable table of analyzed vacancies above a detail area. Keep
  Score, Fit, Interest, Status, Remote, Reloc and Location before Company and
  Title. Also show Role, Seniority, Language, Salary, Added, Reason code and
  Reason.
- [ ] Selecting a vacancy shows its source URL with an Open-in-browser action,
  its ID and source ID, score fields, status, role, seniority, location, remote
  fields, relocation, salary, languages and added date. The vacancy title is
  available in the detail fields; it does not need a second oversized heading.
- [ ] Use a compact left metadata pane and a wider right pane. The default
  right pane shows sortable Technology, Req, Level and Raw values with the
  summary below. A small Text/Skills toggle replaces that right pane with the
  full cleaned vacancy text from `source_job_texts.readable_text` and back.
- [ ] Support horizontal and vertical scrolling, including mouse-wheel
  scrolling. Resizing columns must keep cell text and hyperlinks aligned.
- [ ] Sort by clicking any table header; repeat the click to reverse direction.
  Numeric values and dates sort by value, not lexicographically. Keep the
  selected main-table sort after Refresh.
- [ ] Allow multiple selected vacancies. The detail area follows the focused
  selected row; status actions apply to all selected rows.

## Text, links and filters

- [ ] Display read-only and editable fields differently. Fit and Interest are
  the conspicuously editable fields; other vacancy metadata remains copyable.
- [ ] Make visible text in the main table, auxiliary tables, detail fields,
  full descriptions, summaries and preview grids selectable and copyable.
  Mouse selection followed by Ctrl+C copies the selected substring where text
  selection is active. Search and editable fields also support normal Ctrl+V
  and context-menu actions.
- [ ] In Title cells of the main and Collected tables, allow selecting a title
  fragment and adding just that fragment to the title blacklist. Right-clicking
  without a selection offers the whole title instead. Apply these actions only
  to Title. Ignore blank and case-insensitive duplicate blacklist entries.
- [ ] Links react only when the visible link text is clicked, not anywhere in
  its cell. The main table links Company to the matching company row, Applied
  to the matching application, Added to the date filter, and Reason code to
  the reason filter. Company text includes its application count, e.g.
  `Intellias (6)`.
- [ ] Offer checkboxes for every available vacancy status and a Show zero
  checkbox for score-zero vacancies. Changing them updates the table. Obtain
  status choices from `job_statuses`.
- [ ] Search by internal vacancy ID, source job ID or source URL/LinkedIn ID.
  Date is an inclusive `>=` added-date filter in `DD.MM.YYYY` format. Reason
  accepts comma-separated codes and matches any of them (`IN`). Search/Enter
  applies the text filters; Clear resets ID, Date and Reason. Clicking a date
  fills Date; clicking a reason adds it without duplicates.
- [ ] Hovering over a Reason code shows its description from
  `candidate_fit_reason_codes`; hovering over the column header lists all
  available codes and descriptions.

## Editing and status

- [x] Status buttons use the database's available status values. Setting a
  status updates every selected vacancy. When a result no longer matches the
  active filters, remove it from the visible list.
- [ ] Fit and Interest can be changed directly in their existing detail fields.
  Enter or focus loss saves them and recalculates Score. Fit is an integer from
  0 to 100; Interest is a nonnegative integer. Invalid input restores the last
  saved values and reports the error.
- [x] Marking a vacancy Applied creates one application dated today with
  initial application status Applied in the same transaction as the job-status
  change. Repeating the action does not create a duplicate application.

## Companies and applications

- [ ] Companies opens a table with Priority first, then Company, LinkedIn ID,
  Applications count and Blacklisted. All headers sort. Priority and
  Blacklisted are checkboxes that save immediately; Priority is stored as
  numeric 0/1 and defaults to 0.
- [ ] Edit LinkedIn ID in the company table; Enter or focus loss saves, Escape
  cancels. Empty means NULL; nonempty IDs are unique. Add a company from a
  required name and optional LinkedIn ID below the table. Reject duplicate
  names or nonempty LinkedIn IDs and focus the newly added company.
- [ ] Opening Companies through a vacancy-company link selects and scrolls to
  that company.
- [ ] Applications opens a sortable table with linked vacancy title, linked
  company, application date and editable status. Title returns to and focuses
  the vacancy in the main table; Company opens and focuses its company row.
  The main-table Applied link focuses the corresponding application.
- [ ] Application statuses are Applied, Refused and Confirmed. Change status
  directly in the table with an in-cell selector, saving immediately. Show
  application dates as `DD.MM.YYYY`.

## Collected and configuration

- [x] Collect is the first toolbar action and visually distinct. It starts the
  existing Java LinkedIn collector's `batch` command in the background. Show
  the reported accepted/limit count as it arrives; page-by-page jumps are fine.
  Show completion or an error in the GUI. Use the collector's existing event
  logging and configured Java runtime.
- [ ] Collected opens source jobs before analysis, with Source ID, Stage,
  Collector, Title, Company, Location, Workplace, Salary and URL. A single-stage
  selector offers All and the stages present in the data. Selecting a row shows
  its full cleaned text. Open or double-click opens the source URL, except
  double-click on Title enters title-text selection. Provide Refresh.
- [ ] A small gear button opens Config. Show `config` table switches as
  checkboxes and save each change immediately as 0/1. Also edit the positive
  integer LinkedIn collection limit from
  `Driver/collector/config/linkedin.properties`; Enter or focus loss saves it.

## Availability and refilter

- [x] Check LinkedIn runs in the background over LinkedIn vacancies whose
  status is New and calculated Score is greater than zero. Query the public
  job-posting endpoint with a five-second interval. Mark Closed only when the
  response contains `No longer accepting applications`.
- [ ] Show progress, closed and skipped counts. HTTP 404 skips the vacancy and
  continues; HTTP 429, other HTTP/network failures or unexpected HTML stop the
  run and report the error. Keep an append-only JSONL diagnostic log beside
  the GUI with request, response and outcome events.
- [x] Refilter runs the existing source-vacancy filter and applies all newly
  rejected transitions. Refilter detail collects the same candidates without
  changing data and opens a preview with Source ID, Title, Previous status,
  Action, Fit, Original and Match. Sort the preview by any column; its text is
  selectable and copyable, and a plain title click opens the source URL.
- [x] Each preview row has a checkbox, initially checked. An All checkbox
  selects or clears every row. Confirm applies only checked transitions;
  Cancel or closing the preview leaves them unapplied. Show the resulting
  count and refresh affected lists.
- [ ] The bottom Black titles action opens the active title-blocklist file in
  Windows Notepad.

## Persistence and runtime

- [ ] Persist status and Show zero selections, main/Companies/Applications/
  Collected sorting, the Collected stage filter, and the sizes of the main,
  Companies, Applications, Collected, Config and Refilter detail windows.
  Restore them after reopening; Refresh preserves the current selections and
  sorting. Keep GUI preferences in a local file beside that GUI.
- [ ] Keep long-running Collect, Check LinkedIn and Refilter work off the UI
  thread, show live status, report failures, and prevent overlapping runs of
  those three operations. Secondary windows remain usable when the main window
  is minimized and restored.
- [ ] Provide a convenient double-click Windows launcher for the JavaFX GUI
  with a recognizable icon and without a lingering console window.
