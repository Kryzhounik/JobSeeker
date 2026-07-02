# JobSeeker Workflow

This file is the public contract for how JobSeeker is run.

API here means a callable entry point for Codex/scripts. It is not an HTTP
server yet.

## Public Calls

### 1. collect_from_search(source)

Reads `collector/config/<source>.properties`, finds vacancy URLs, saves each
vacancy as raw HTML, then sends every saved raw file to `process_raw`.

Java-shaped flow:

```java
collectFromSearch(source) {
    urls = findVacancies(sourceConfig);
    for (url : urls) {
        raw = saveRaw(source, url);
        processRaw(source, raw);
    }
}
```

### 2. collect_from_url(source, url)

Used for debug and calibration. It saves exactly this vacancy as raw HTML, then
sends that saved raw file to `process_raw`.

Java-shaped flow:

```java
collectFromUrl(source, url) {
    raw = saveRaw(source, url);
    processRaw(source, raw);
}
```

For LinkedIn, `saveRaw` uses the logged-in browser page content. It must not use
direct Python HTTP.

### 3. reprocess_raw(source, selector)

Does not search and does not download anything. It takes already saved raw HTML
files and sends them to `process_raw`.

Java-shaped flow:

```java
reprocessRaw(source, selector) {
    raws = findSavedRawFiles(source, selector);
    for (raw : raws) {
        processRaw(source, raw);
    }
}
```

## One Internal Process

All public calls converge here.

```java
processRaw(source, raw) {
    json = analyzeRawWithCodex(source, raw, "analyzer/prompts/analyze_job.md");
    saveJson("data/analyzed/<source>/", json);
    run("python analyzer/save_analyzed_job.py --input <json> --source <source>");
}
```

`process_raw` must not care where raw came from: search, direct URL, or saved
files. This is the main rule that keeps calibration honest.

## Boundaries

- Collectors collect and save raw pages.
- Analyzer prompt extracts structured JSON from saved raw pages.
- `analyzer/save_analyzed_job.py` reads analyzed JSON, applies filters,
  calculates valuation, and writes SQLite.
- Direct user links go through `collect_from_url`; they are not analyzed live.
- No Python string heuristics for job meaning unless we explicitly decide to add
  them later.
