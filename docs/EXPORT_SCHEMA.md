# Export schema

FeedClean writes three kinds of files, all opt-in and all saved through the system file picker (you choose
where): the **full export** (private backup), the **public export** (for sharing), and the **audit report**.
Each carries a `schema_version`, currently `1`. Handles in the examples are obviously fake.

Conventions for every file:

- Handles are lowercase, without `@`.
- Enum values are written by name (`BLOCKED`, `AUTO_DETECTED`, …) in the full export, and in lowercase in the
  public export and audit report.
- Readers must ignore unknown fields and unknown values.

## Full export (backup and migration)

A zip with every database table as JSON (authoritative) and CSV (for spreadsheets). Treat it as private: it
contains notes, display names, exact timestamps and video links.

```
feedclean-export-<yyyy-mm-dd>.zip
├── manifest.json
├── json/accounts.json, tags.json, account_tags.json, sightings.json, block_events.json,
│        action_log.json, sessions.json, audits.json, audit_findings.json
└── csv/<the same nine tables>.csv
```

Not included: settings, and screenshot image files (only their app-private paths, which mean nothing on another
phone).

### manifest.json

```json
{
  "schema_version": 1,
  "app_version": "0.1.0",
  "exported_at": "2026-11-22T10:00:00Z",
  "format": "feedclean-full",
  "counts": { "accounts": 12, "tags": 10, "account_tags": 20, "sightings": 85, "block_events": 3,
              "action_log": 5, "sessions": 0, "audits": 1, "audit_findings": 2 }
}
```

| Field | Meaning |
|---|---|
| `schema_version` | integer, currently `1` |
| `app_version` | FeedClean version that wrote the file |
| `exported_at` | ISO-8601 UTC, seconds precision |
| `format` | always `feedclean-full`; anything else is rejected by the importer |
| `counts` | rows per table, informational only |

### Tables

Each `json/<table>.json` is a JSON array of row objects. Field names are the database column names. Times are
**epoch milliseconds (UTC)**. `null` means "not known / not applicable". Ids are only meaningful inside one
file (the importer remaps them).

| Table | Fields |
|---|---|
| `accounts` | `id`, `handle` (null = provisional), `handle_source` (`PROFILE` `DIALOG` `SOUND_HEURISTIC` `RESOLVE` `IMPORT`), `display_name`, `display_name_skeleton`, `first_seen_at`, `last_seen_at`, `status` (`SEEN` `BLOCKED` `FALSE_POSITIVE` `UNBLOCKED_SUSPECTED`), `account_category`, `shop_tier`, `is_default_handle`, `notes`, `suspected_original_of` (account id), `profile_photo_hash` |
| `tags` | `id`, `name`, `kind` (`SIGNAL` `MANUAL`) |
| `account_tags` | `account_id`, `tag_id`, `source` (`AUTO_DETECTED` `MANUAL`), `created_at` |
| `sightings` | `id`, `account_id`, `seen_at`, `video_url`, `page_signature`, `signals` (object, below), `source` (`OVERLAY` `SHARE` `RECON`), `user_action` (`NONE` `BLOCK` `SKIP` `AUTO_SKIP` `NOT_INTERESTED` `LOG` `FALSE_POSITIVE`), `action_verified`, `skip_reason` (`ORGANIC_SHOP` `PAID_AD` `PROMOTED` `LIVE_SHOPPING`), `screenshot_path`, `session_id` |
| `block_events` | `id`, `account_id`, `attempted_at`, `method` (`ASSISTED` `MANUAL` `IMPORTED`), `verified`, `failure_reason`, `step_reached` |
| `action_log` | `id`, `kind` (`BLOCK` `NOT_INTERESTED`), `at`, `account_id`, `verified` |
| `sessions` | `id`, `started_at`, `ended_at`, `videos_seen`, `ads`, `promoted`, `shop`, `live`, `skipped` |
| `audits` | `id`, `run_at`, `source` (`DATA_EXPORT` `MANUAL`), `export_file_name`, `missing_from_blocklist_count`, `seen_after_block_count` |
| `audit_findings` | `id`, `audit_id`, `account_id`, `handle`, `kind` (`MISSING_FROM_BLOCKLIST` `SEEN_AFTER_BLOCK` `IN_EXPORT_NOT_IN_DB`), `detail` (see "Audit report") |

`signals` is the detector's object: `isPaidAd`, `isPromoted`, `hasShopAnchor`, `isShopCreator`, `isLive`,
`isLiveShopping`, `isLiveEvent` (booleans), `disclosureLabel`, `anchorText`, `ctaText` (strings or null).

CSV files have a header row, comma separators, CRLF line ends, and RFC 4180 quoting. The `sightings` CSV has a
`signals_json` column with the compact signals JSON. To stop spreadsheets from running text as a formula, a text
cell starting with `=`, `+`, `-`, `@`, tab or CR is prefixed with `'`. The importer never reads the CSV files.

### Import

Import reads `manifest.json` and `json/*.json` (also when the zip was re-packed inside one top-level folder),
plans everything first, then writes it in **one transaction**: a damaged file changes nothing. Importing only
writes to FeedClean's database; it never does anything in TikTok.

It is **idempotent**: importing the same file twice adds nothing the second time.

- **Accounts with a handle** merge by handle into the existing account. The existing row's values win; gaps
  (display name, category, shop tier, photo hash, handle source) are filled from the file; the seen range is the
  union; a `SEEN` status takes the file's status; notes from the file are appended unless already present.
- **Provisional accounts** (no handle) stay provisional. One matches an existing account only when that is
  certain: the same display name and `first_seen_at` (the same row exported earlier), or every one of its
  sightings is already present under one account (it was merged since). The "unknown creator" row (no handle,
  no name) maps to the existing one. A display name alone never merges accounts.
- **Foreign keys** (`account_id`, `tag_id`, `session_id`, `audit_id`, `suspected_original_of`) are remapped.
- **Duplicates are skipped**: sightings with the same `page_signature` and `seen_at`; block events with the same
  account, `attempted_at`, `method` and `verified`; sessions with the same `started_at`; audits with the same
  `run_at`, `source` and `export_file_name` (with their findings); tags by name; tag assignments by
  account, tag and source.
- **Action log**: rows from the last hour (the rate limiter's window) or from the future are **never imported**,
  so an import cannot change the current rate-limit budget. Older rows are history and are added unless an
  identical row (`kind`, `at`, `verified`) exists. Existing rows are never changed or removed.
- Rows with unknown references or enum values are skipped and counted in the import report.
- Files with a newer `schema_version`, without a manifest, or with another `format` are rejected.

## Public export (for sharing)

A single JSON file with only what is needed to describe an account and why it was flagged:

```json
{
  "schema_version": 1,
  "generated": "2026-11",
  "app_version": "0.1.0",
  "accounts": [
    {
      "handle": "example_shop_01",
      "tags": ["organic_shop", "paid_ad", "shop_anchor"],
      "first_reported": "2026-10",
      "evidence_type": ["ad_label", "shop_anchor"]
    }
  ]
}
```

| Field | Meaning |
|---|---|
| `schema_version` | integer, currently `1` |
| `generated` | `YYYY-MM` (UTC) when the file was written |
| `app_version` | FeedClean version that wrote the file |
| `accounts[].handle` | lowercase, no `@` |
| `accounts[].tags` | tag names, sorted |
| `accounts[].first_reported` | `YYYY-MM` (UTC) of the account's first sighting |
| `accounts[].evidence_type` | kinds of evidence behind the tags, sorted (vocabulary below) |

Accounts are sorted by handle. An account is included only when it has a **known handle**, is **not marked as a
false positive**, and has **at least one allowed tag**.

**Stripped:** notes, screenshots, display names, exact timestamps, sightings, sessions, block history, statuses,
and internal ids.

**Tags included by default** are those derived from on-screen labels: `paid_ad`, `promoted`, `shop_anchor`,
`organic_shop`, `live_shopping`. The **subjective tags** `ai_generated`, `sexual_content`, `impersonator`,
`reposted`, `bot_like` are included only when Settings → Data & privacy → "Public export: include subjective
tags" is on. Tags you create yourself are never included.

### `evidence_type` vocabulary

| Value | Meaning |
|---|---|
| `ad_label` | the "Ad" / "Sponsored" disclosure label was on screen (`paid_ad`) |
| `promoted_label` | a "Promotional content" / "Paid partnership" label was on screen (`promoted`) |
| `shop_anchor` | the TikTok Shop product anchor was on screen (`shop_anchor`, and `organic_shop` when the anchor was detected) |
| `shop_creator` | the profile showed a Shop seller marker (`organic_shop` without a detected anchor) |
| `live_shopping_label` | a LIVE shopping label was on screen (`live_shopping`) |
| `manual_report` | the user added the tag by hand; no on-screen label was detected for it (always the case for subjective tags) |

A tag gets the label-based value when FeedClean detected it on screen at least once (tag source
`AUTO_DETECTED`), otherwise `manual_report`. Consumers must ignore unknown values.

## Audit report

The unblock audit compares the blocklist in TikTok's "Download your data" export with FeedClean's own records.
It can save its report as JSON or CSV.

```json
{
  "schema_version": 1,
  "format": "feedclean-audit",
  "run_at": "2026-11-22T10:00:00Z",
  "source": "data_export",
  "export_file_name": "user_data_tiktok.zip",
  "counts": { "missing_from_blocklist": 1, "seen_after_block": 0, "in_export_not_in_db": 1 },
  "findings": [
    { "handle": "example_shop_01", "kind": "missing_from_blocklist", "detail": "blocked_at=2026-10-03T12:00:00Z" },
    { "handle": "example_shop_02", "kind": "in_export_not_in_db", "detail": "export_date=2026-09-01T08:00:00Z" }
  ]
}
```

The CSV has the columns `handle,kind,detail`.

`detail` (also stored in `audit_findings.detail`) is a list of `key=value` pairs joined by `;`. Dates are
ISO-8601 UTC. Keys:

| Key | Used by | Meaning |
|---|---|---|
| `blocked_at` | missing, seen after | time of FeedClean's latest verified block |
| `newer_than_export` | missing | `true` when the block is newer than every dated entry of the export (the export may predate it; the account is then not marked "unblocked (suspected)") |
| `sightings_after` | seen after | number of sightings after the block |
| `last_seen_at` | seen after | latest of those sightings |
| `export_date` | in export, not in DB | block date given by TikTok's export |
| `known_account` | in export, not in DB | `true` when FeedClean already knows the handle (without a recorded block) |

## Versioning policy

- `schema_version` is an integer, bumped on any breaking change (removed or renamed field, changed meaning).
- Adding optional fields or enum values does not bump the version; readers must ignore unknown fields and
  values.
- Each bump ships with a migration note in this file, and the app keeps importing older versions where feasible.
- Files with a newer `schema_version` than the app understands are rejected with a clear message, not
  half-imported.

### Changelog

- **1**: initial version (full export, public export, audit report).
