---
name: browser-collection
description: Browser mechanics for JobSeeker collectors.
---

# Browser Collection

Purpose: shared browser rules for collectors that must use a logged-in browser
session.

## LinkedIn Browser

Use the Chrome extension browser for LinkedIn collection. Do not use the Codex
in-app browser for normal LinkedIn collection unless Chrome is unavailable and
the user explicitly asks to fall back.

Current reasons:

- Chrome uses the user's logged-in LinkedIn session.
- Chrome has a normal desktop viewport.
- Chrome was measured at roughly 5-7 seconds per saved job in the search UI.
- The in-app browser was measured at roughly 69 seconds per saved job in the
  same search UI test.

## Chrome Tab Ownership

Prefer a Chrome tab created through the Codex Chrome extension. It appears in
the user's Chrome window/group as a Codex-controlled tab.

Do not attach to random user tabs when a controlled Codex tab can be created.
User tabs may be visible in `openTabs`, but they may not be controllable through
`tabs.get`.

If there are multiple Chrome tabs, choose the controlled tab whose URL/title
matches the current collector task. If the target tab is ambiguous, report what
is visible and ask the user which one to use.

## LinkedIn Search UI Flow

Use the human-like search UI flow:

```text
open search page
read visible left-side search cards
run linkedin_preview_filter.py on each card
skip rejected cards
click an accepted card in the left search results
wait for the right-side details pane
save the right-side details pane HTML
continue through the search results
```

If the collection has to be split because of the Codex five-minute tool-call
limit, a temporary checkpoint may record where to resume the search UI, such as
search URL/page/card offset and counters.

That checkpoint is only for resuming browser collection and does not define
what downstream stages process.

Do not normally build a queue of job URLs and then open each
`/jobs/view/<id>/` URL as a separate navigation. That direct-navigation mode is
slower and easier to get wrong.

## LinkedIn Raw HTML

When collecting from the LinkedIn search UI, do not save the entire
`document.documentElement.outerHTML`.

Save the HTML of the right-side job details pane instead, wrapped as a minimal
HTML document with a canonical LinkedIn job URL.

Reason: the full search-page HTML is large and may be truncated by the browser
automation result transport before the details pane appears in the returned
string. The details pane HTML is smaller, contains the vacancy text, and passes
`collector/save_raw_page.py`.

Before saving a LinkedIn pane, verify:

- the current selected job id matches the clicked card;
- no visible job-details progressbar remains;
- the pane text contains `About the job` or another detail marker accepted by
  `collector/save_raw_page.py`.

Raw files are source-owned storage. Whether a run processes one raw file or all
raw files must be decided by the workflow command scope, not by the browser
collector.

## Chrome API Notes

In the Chrome extension browser, `domSnapshot()` may fail. Use
`playwright.evaluate()` for LinkedIn collection.

Treat `playwright.evaluate()` as read-oriented in the Chrome extension browser.
Do not scroll LinkedIn by assigning `scrollTop` inside `evaluate()`. Find the
left search-results scroll container, then scroll it with a user action such as
`locator(...).press("PageDown")`.

Use selectors or coordinates only after reading the visible cards from the DOM.
Do not click blindly.

## Tool-call Limits

The five-minute limit is a Codex tool-call limit, not a LinkedIn browser
session limit. Chrome does not remove that limit, but Chrome collection is fast
enough that normal collection should fit in smaller technical runs.

If a technical run approaches the limit, persist progress and continue from the
same search location/page/card. Do not change business behavior because a tool
call was split.
