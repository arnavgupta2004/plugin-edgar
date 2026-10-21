# How to use the SEC EDGAR plugin

This plugin reads company data and filings from [SEC EDGAR](https://www.sec.gov/edgar/search/), the public database of US company filings. EDGAR needs no account or API key. Each task has its own documentation page with full examples.

## SEC requirements

SEC's [fair access rules](https://www.sec.gov/search-filings/edgar-search-assistance/accessing-edgar-data) apply to every request:

- **User-Agent:** automated tools must identify themselves with a name and a contact email. Every task requires `userAgent`, for example `userAgent: "Example Co admin@example.com"`, and fails before calling SEC if it doesn't contain both.
- **Rate limit:** at most 10 requests per second from all your tools together. Each task spaces its requests to stay under `maxRequestsPerSecond` (default 5, at most 10). The limit applies to each task run, so tasks running in parallel each count against SEC's limit: lower `maxRequestsPerSecond` when several EDGAR tasks run at the same time.
- **Retries:** a request that SEC rejects with HTTP 429 or 503 is retried. Each attempt first retries once after the delay in SEC's `Retry-After` header (or 1 second if SEC sends none). If SEC still refuses, the task makes up to two more attempts, 10 and then 20 seconds later; these waits don't read `Retry-After`, so a longer delay asked by SEC can be cut short. If every attempt is refused, the task fails and suggests lowering `maxRequestsPerSecond`.

## Tasks

### company.Lookup

Resolves stock tickers to the company's CIK (SEC's Central Index Key, the identifier the other EDGAR tasks use), name and exchange, from SEC's company ticker file. Tickers are matched case-insensitively, and `BRK.B` also matches EDGAR's `BRK-B`. The task fails, naming them, if SEC lists no company for one or more tickers. `fetchType` controls the output: `FETCH` (default) returns `rows`, `FETCH_ONE` the first company in `row`, `STORE` an ION file in internal storage.

### filings.List

Lists one company's filings, newest first, given its `ticker` or its `cik`, optionally filtered by `forms` (exact form types such as `8-K` or `10-K/A`) and by an inclusive `startDate` / `endDate` on the filing date. SEC returns the most recent filings (at least a year, or 1,000) in one response and older ones in additional pages; the task reads an older page only when `startDate` reaches it or when it still needs results, and stops at `maxResults` (default 100, at most 10,000). Each filing has its accession number, form, filing and report dates, acceptance time, 8-K item numbers and the URL of its primary document. `fetchType` works as for `company.Lookup`; use `STORE` for long histories.

### filings.Download

Downloads a filing's primary document and, optionally, its exhibits to internal storage, given the filer's `cik` and the filing's `accessionNumber` (both returned by `filings.List`). Exhibits are selected by the **document type the filer declared** in the filing index, such as `EX-99.1` for a press release, never by file name: exhibit file names follow no convention, and Intel's Q2 2026 earnings release, for example, is `q226earningsrelease.htm`. `exhibits` takes types, case-insensitively, where `*` matches any characters: `EX-99*` matches `EX-99.1` and `EX-99.2`. For material contracts use `EX-10.*`, since `EX-10*` also matches the XBRL files (`EX-101.SCH`, `EX-101.LAB`, …). Each document is streamed to internal storage under its original file name; the output gives its type, file name, description, SEC URL and storage `uri`.
