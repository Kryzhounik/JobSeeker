# LinkedIn Result Scrolling

Materialize LinkedIn search cards by their existing card containers. Do not
infer the page size from the initially rendered links.

## Required Browser State

Follow [`browser.md`](browser.md) and use the logged-in Chrome extension
browser. Run this procedure after a LinkedIn search page has loaded and before
preview filtering or clicking any result card.

## Page Model

LinkedIn currently exposes one of two search-card layouts:

- Classic: `[data-occludable-job-id="<job_id>"]` containers whose own
  `a[href*="/jobs/view/"]` appears after materialization.
- LazyColumn: `[data-testid="lazy-column"]` contains card buttons with
  `componentkey="job-card-component-ref-<job_id>"`. Unselected cards do not
  contain a `/jobs/view/` link.

Use exactly one layout on a page. Never use the number of global job links as
the expected page size.

## Procedure

1. Detect exactly one supported layout:
   - classic panel: `.scaffold-layout__list > div` with
     `[data-occludable-job-id]`;
   - LazyColumn: exactly one `[data-testid="lazy-column"]` containing
     `[role="button"][componentkey^="job-card-component-ref-"]`.
2. Poll its card containers every 500 ms for at most 10 seconds. Extract the
   ordered ID from `data-occludable-job-id` or from the exact LazyColumn
   `componentkey` prefix. Treat the ordered unique ID set as stable only after
   the same non-empty sequence appears in two consecutive reads.
3. Record that stable ordered set as `expected_ids`. Reject duplicate, empty,
   or malformed IDs.
4. For each ID, select its exact container using the detected layout.
5. Bring that container into view with the locator-scoped operation:

   ```js
   await card.evaluate((element) => {
     element.scrollIntoView({
       block: "center",
       inline: "nearest",
       behavior: "instant",
     });
   });
   ```

6. For classic cards, wait up to 2 seconds for the card's matching
   `/jobs/view/<job_id>/` link. For LazyColumn cards, verify that the exact
   `componentkey` remains present and that title, company, and location preview
   text has materialized. A link is not required before the card is selected.
7. Only then add the ID to `materialized_ids` and extract its preview. Build
   the canonical URL as `https://www.linkedin.com/jobs/view/<job_id>/` when an
   unselected LazyColumn card has no link.
8. Continue in `expected_ids` order until every expected container has been
   checked.
9. Declare scrolling complete only when
   `materialized_ids == expected_ids` as sets and no container failed its ID
   match.

Do not stop merely because the panel reached its visual bottom.

## Page-Size Contract

For a normal non-terminal LinkedIn page, require 25 card containers, 25 unique
expected IDs, and 25 materialized IDs.

Require the raw container count to equal the unique expected-ID count. Treat
duplicate or missing container IDs as a partial page.

A page with fewer than 25 expected containers may be accepted only when
[`scan_linkedin.md`](scan_linkedin.md) independently proves that it is a
terminal page. Do not infer terminal state from the short count itself.

## Forbidden Substitutions

Do not replace the procedure with:

- counting global or initially visible job anchors;
- assigning `scrollTop`;
- fixed-pixel `scrollBy` jumps;
- `PageDown` loops;
- physical/synthetic wheel gestures;
- scrolling the document or right-side details pane.

These methods either skipped card materialization or timed out during
calibration. The card-addressed procedure produced 25/25 on independent fresh
pages, including the page that previously exposed only 14 rendered links.

## Failure Contract

Return the page as `partial`, not complete, when:

- the results-panel selector resolves to anything other than one element;
- the expected ID set does not stabilize;
- a non-terminal page exposes fewer than 25 expected IDs;
- a card does not satisfy the materialization rule for its detected layout;
- the final expected and materialized ID sets differ.

Report the search label, page URL, `start`, expected count, materialized count,
and exact missing or mismatched IDs. Do not silently substitute another scroll
method.
