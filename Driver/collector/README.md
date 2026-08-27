# Collector

## LinkedIn browser search URLs

Two logged-in browser search endpoints coexist and are not interchangeable:

- `/jobs/search/` is the primary, classic search. It accepts filters in the URL
  (`f_E`, `f_WT`, `f_JT`, `f_TPR`, `sortBy`) and pagination via `start`.
- `/jobs/search-results/` is the alternative semantic search. Its vacancy
  coverage can be substantially smaller and worse for collection.

On 2026-08-27, in the same logged-in Chrome session with identical `Java`,
`Moldova`, and `Past week` parameters, classic search showed 173 results,
including EMEA jobs from micro1, Crossing Hurdles, and Hire Feed. Semantic
search showed only 3 results. Classic URL filters, `start=25` / `start=50`,
card materialization, and opening job details were also verified to work.

Switch to `/jobs/search-results/` ONLY after confirming that the primary
`/jobs/search/` really does not work. A changed layout, a failed selector, or a
temporary timeout is not enough. Do not treat the alternative as an equivalent
replacement or silently switch to it during a batch.

The `/jobs/search-results/` selection introduced by commit `8e37cf3`
(2026-08-12) has been reverted. The generator and browser instructions now use
classic search, URL filters, and `start` pagination.

### Remote priority before country passes

`AccountRemote` is first in `locations` and belongs only to the browser:

1. Open `seed:<first country>` with `remoteBroadWorkplace=remote`; collect
   nothing from this page. Confirm it once with the ordinary `Search` button
   to remember the country. The current first country is Moldova.
2. In the same tab, open AccountRemote without `location` or `geoId`, still
   remote-only. Collect broad EMEA/worldwide remote jobs mixed with the anchor
   country's remote jobs before spending the limit on hybrid/office positions.
3. While the global limit remains, process each actual country in order:
   guest prefetch, then browser gap-fill with the normal `workplace` settings.

The priority pass shares the same deduplication and global limit. If it fills
the limit, no country pass runs. The guest script skips AccountRemote when
reading the shared configured location list; the workflow never sends it as a
guest `--location`.

### Ukraine seed and locationless remote

Also checked on 2026-08-27 using the pre-`8e37cf3` URL settings: open Ukraine,
then open classic search without `location` or `geoId`, with `f_WT=2`.
LinkedIn retained the Ukraine context and showed 282 results. The fully
materialized first page contained 13 `EMEA (Remote)` and 12 Ukrainian remote
vacancies. This sequence works; it is not evidence of a worldwide inventory.
The explicit `Ukraine + Remote` control also showed 282 results and shared
24 of those 25 first-page IDs. Removing geography did not establish a distinct
worldwide ranking in this check. The priority comes from running this
remote-only pass before country passes that also include hybrid/office jobs.

The subsequent Moldova seed check exposed a required detail: opening a seed
URL alone still allowed the locationless request to fall back to the previously
remembered Ukraine. Submitting the Moldova seed with `Search` fixed this;
the locationless result then retained Moldova. Always confirm the seed and
verify its retained country, rather than assuming a URL visit persisted it.

## LinkedIn guest and browser coverage

The LinkedIn guest endpoint is a cheap prefetch, not an inventory-equivalent
replacement for the logged-in browser search.

For each location, the guest collector first searches companies whose
`companies.priority` is `1`, using their `linkedin_id` values in `f_C`. If the
run limit is still not reached, it continues with the normal company-unfiltered
search for the same location. Both passes share the same limit and ID dedup.

Measured for `Moldova:106178099` with `Java` and `Past week`:

- the browser search displayed `EMEA (Remote)` vacancies;
- guest pages returned only Moldova/Chisinau locations;
- none of 17 visible browser EMEA job IDs appeared in the guest pages;
- guest pagination returned 10 cards at `start=0`, 2 at `start=9`, and no cards
  from `start=18` onward.

Therefore the browser pass is required to discover EMEA vacancies that are
shown inside a country search. The initial AccountRemote pass prioritizes this
browser coverage. Later, if a country's guest pass reaches the remaining
global limit, additional browser-only vacancies may still be absent from that
run. This is a known coverage tradeoff, not evidence of identical inventories.
