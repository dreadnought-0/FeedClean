# Privacy

FeedClean is local-first. Nothing leaves your phone unless you explicitly trigger it.

## What it reads

Only the **on-screen accessibility tree of the TikTok app**: labels, descriptions, and view IDs of what is
currently visible. The service is configured for TikTok's package only. FeedClean does not read other apps. If
the system shows overlay windows (such as the share sheet) while TikTok is in use, it reads what is needed to
find the controls it is working with, and does not store contact names from them.

## What it stores

All data lives in a database in app-private storage on your phone:

- **Accounts:** the @handle when known, the display name, when first and last seen, a status (seen, blocked,
  false positive, suspected unblocked), a category such as "Shop", and your own notes.
- **Tags:** what was detected (paid ad, promoted, Shop anchor, live shopping) and any manual tags you add.
- **Sightings:** when a post was seen, which signals matched, what action you took (none, skip, auto-skip,
  Not interested, block, log, false positive), whether it was verified, an optional video link if you shared
  one, and an optional screenshot path (screenshots are off by default).
- **Block events and the action log:** each block attempt with its outcome, and the timestamps that the rate
  limiter uses.
- **Sessions (opt-in, off by default):** counts of videos seen per viewing session.
- **Audits:** results of comparing your TikTok data export against your own records.
- **Decision log (on by default, can be turned off):** one line per post FeedClean evaluated: when, the kind of
  screen, the post type it detected, what it decided (skip, keep, ignore, and so on) and the reason code. It never
  contains creator names, @handles, captions, comments or any other text from TikTok. Lines older than 7 days are
  deleted automatically and the log is capped at about 5 MB (oldest lines go first). It stays on the phone; you can
  view it and export it from the Debug screen, and an export only goes where you save it with the system file
  picker. Turn it off under Settings, Data & privacy, "Decision log".

## What it never stores

- Contact names or any other content from the system share sheet.
- Anything from other apps.
- Your TikTok password or tokens. FeedClean never asks for them.

## Backups

Android backup is disabled (`allowBackup=false`) and data-extraction rules exclude everything, so nothing is
silently copied to cloud backups or device transfers.

## Network

FeedClean has no analytics or crash-reporting SDKs. The only network request is the one you trigger with
**Resolve** on a shared short link: a single HTTPS `HEAD` request to that link with redirects not followed, read
only to learn the handle from the `Location` header. Cleartext traffic is disabled.

## Exports

Exports are opt-in and go through the system file picker, so you choose where the file is saved.

- **Full export** contains everything above, for your own backup. Treat it as private.
- **Public export** is for sharing and removes personal data: notes, screenshots, display names, exact
  timestamps, sightings, and session data are stripped; only accounts with a handle are included, with coarse
  dates (month). See [EXPORT_SCHEMA.md](EXPORT_SCHEMA.md).

## Deleting your data

Settings has a **Clear all data** action, and uninstalling the app removes everything.
