# LinkedIn Result Scrolling

Materialize LinkedIn search cards by their existing card containers. Do not
infer the page size from the initially rendered links.

## Required Browser State

Follow [`browser.md`](browser.md) and use the logged-in Chrome extension
browser. Run this procedure after a LinkedIn search page has loaded and before
preview filtering or clicking any result card.

## Page Model

LinkedIn normally creates the complete page as card containers before it
renders every card's link and text. Treat these as different sets:

- Expected cards: ordered unique `job_id` values from
  `[data-occludable-job-id]`.
- Materialized cards: expected containers whose own
  `a[href*="/jobs/view/"]` has appeared and contains the same `job_id`.

Never use the number of currently rendered job links as the expected page
size. A normal 25-result page may initially expose only 7-14 links while all
25 card containers are already present.

## Procedure

1. Locate exactly one left results panel with
   `.scaffold-layout__list > div`.
2. Poll `[data-occludable-job-id]` every 500 ms for at most 10 seconds. Treat
   the ordered unique ID set as stable only after the same non-empty sequence
   appears in two consecutive reads.
3. Record that stable ordered set as `expected_ids`.
4. For each ID in `expected_ids`, select its exact container:
   `[data-occludable-job-id="<job_id>"]`.
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

6. Wait up to 2 seconds for `a[href*="/jobs/view/"]` inside that same
   container.
7. Verify that the link's `/jobs/view/<job_id>/` value matches the container
   ID. Only then add the ID to `materialized_ids` and extract its preview.
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
- a card does not materialize its own matching job link within the bounded
  wait;
- the final expected and materialized ID sets differ.

Report the search label, page URL, `start`, expected count, materialized count,
and exact missing or mismatched IDs. Do not silently substitute another scroll
method.
