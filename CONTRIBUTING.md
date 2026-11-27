# Contributing to FeedClean

Thanks for helping. Before you start, read [How it works](README.md#how-it-works) in the README and
[docs/SELECTORS.md](docs/SELECTORS.md), which explains how detection is configured.

## Project principles

These are non-negotiable. Pull requests that break them will not be merged.

1. **The user starts every account action.** Code may detect, highlight, log, and prepare a block. A block or a
   "Not interested" only starts from the user's tap. No unattended or mass blocking, no background bot loops.
   - Auto-skip is navigation only: one scroll forward, while TikTok is in the foreground. It is never an account
     action.
   - "Not interested" is a manual one-tap action by default. The automated mode is opt-in, off by default,
     rate-limited, and warns about account risk.
2. **Keep the shared rate limiter.** By default there are at least 5 s between assisted actions and at most 60
   per hour. It counts persisted attempts, so a restart does not reset it, and it covers both blocks and
   automated "Not interested" taps.
3. **No reverse engineering of TikTok.** No decompiling, patching, or repackaging the TikTok APK. No
   certificate-pinning bypass, no private APIs, no scraping TikTok servers. Allowed inputs are the on-screen
   accessibility tree, on-device OCR of screenshots, links the user shares to the app, and TikTok's official
   "Download your data" export.
4. **Local first and private.** Data stays on the phone. No analytics or crash-reporting SDKs. The only network
   call is the user-tapped **Resolve** of a shared short link. `allowBackup=false`. Exports are opt-in and go
   through the system file picker.
5. **Every behaviour has a toggle, and automation is off by default.** Users opt in to anything automated.
6. **All TikTok selectors live in `app/src/main/assets/selectors/tiktok.json`.** No TikTok strings or IDs in
   Kotlin.
7. **Never commit raw captures.** Raw UI dumps, screenshots, and screen recordings of your feed contain other
   people's names and your account state. Keep raw captures outside the repository. Test fixtures must be
   scrubbed (see below).
8. **No secrets and no TikTok branding.** No keys or credentials in the repository. No TikTok logos, names, or
   marks in the app name or icon.

For anything large, open an issue and agree on an approach before writing a lot of code.

## Dev setup

- Android Studio (current stable) and its bundled JDK. On Linux: `export JAVA_HOME=/opt/android-studio/jbr`.
- `./gradlew :app:assembleDebug` builds. `./gradlew :app:testDebugUnitTest` runs the unit tests.
- See [docs/SETUP.md](docs/SETUP.md) to install and enable the service on a device.

## Code style

- Kotlin, Jetpack Compose (Material 3), Navigation 3, Room (KSP), kotlinx.serialization, DataStore,
  coroutines/Flow. Manual DI through `AppContainer`. Please do not add a DI framework.
- The parser and detector work on the `UiNode` abstraction. `AccessibilityNodeInfo` appears only in the service
  adapter, so the logic stays JVM-testable.
- The block and Not-interested executors verify each step and stop on any mismatch. They never retry blindly.
- Identity is keyed by @handle. Display names are provisional and are labelled "possible" in the UI.
- Keep dependencies minimal. New dependencies need a justification in the pull request.

## Tests

- Logic changes need unit tests. `CurationPolicy`, the rate limiter, and state machines use table-driven or
  fake-clock tests.
- **Selector or parser changes need a fixture**: a scrubbed capture under
  `app/src/test/resources/fixtures/tiktok-<version>/<screen>/`. Keep older fixtures. See
  [docs/SELECTORS.md](docs/SELECTORS.md) for the scrubbing rules.
- Run `./gradlew :app:testDebugUnitTest` before opening a pull request.

## Scrubbing fixtures

Scrub every fixture before it goes into the repository. Replace creator names, handles, captions, sound titles,
bios, and product titles with placeholders that keep the pattern. Replace share-sheet contact names and location
tabs. Review your diff line by line.

## Pull request checklist

The pull request template repeats this list:

- Tests added or updated and passing.
- Fixtures scrubbed; no raw captures.
- No new network calls, analytics, or crash SDKs.
- Project principles respected. New behaviour has a toggle and is off by default if it automates anything.
- Docs updated where relevant, for example `docs/EXPORT_SCHEMA.md` for schema changes. For selector changes,
  describe what changed (and the TikTok version) in the pull request.

By contributing, you agree that your contributions are licensed under Apache-2.0.
