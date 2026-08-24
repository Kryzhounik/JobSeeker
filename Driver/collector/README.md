# Collector

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
shown inside a country search. Under the current global-limit policy, if a
guest pass reaches the limit before the browser pass starts, those browser-only
EMEA vacancies may be absent from that run. This is a known coverage tradeoff,
not evidence that guest and browser returned the same inventory.
