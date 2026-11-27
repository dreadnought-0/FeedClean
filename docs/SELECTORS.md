# Selectors: how detection works and how to fix it

## How detection works

All TikTok-specific strings, content descriptions, view IDs, and regexes live in one file:
`app/src/main/assets/selectors/tiktok.json`. There are no TikTok strings or IDs in Kotlin.

- Each key (for example `feed.disclosureLabel` or `dialog.confirm`) is an **ordered list of strategies**. The
  first one that matches wins, and the snapshot records which strategy matched (visible on the Debug screen).
- Order strategies from most to least stable: **semantic content-desc or text first**, then semantic view IDs
  (`title`, `viewpager`), and **obfuscated IDs (like `ibp`) only as hints** because they change between releases.
- If only a fallback strategy matches, a TikTok update probably changed something. Fix the primary strategy.
- Strings live under `locales` keyed by language (currently `en`).
- The file has a `verified_app_versions` list of TikTok versions it has been checked against.
- A user can load an override file from Settings to try a fix without rebuilding.

The parser works on a `UiNode` abstraction. In tests, an XML adapter reads `uiautomator dump` files, so scrubbed
dumps double as regression fixtures.

## When TikTok updates

1. **Re-capture** the affected screen, either with **Capture screen** on the overlay card (it saves a redacted
   screen tree that you can export from the Debug screen) or with `uiautomator dump`. Save raw captures outside
   the repository:
   ```sh
   adb shell uiautomator dump /sdcard/dump.xml
   adb pull /sdcard/dump.xml ~/dump.xml
   ```
   On animated screens the dump can fail with "could not get idle state". Temporarily disable animations, dump,
   and restore the original value in the same command:
   ```sh
   old=$(adb shell settings get global animator_duration_scale)
   adb shell settings put global animator_duration_scale 0
   adb shell uiautomator dump /sdcard/dump.xml
   adb shell settings put global animator_duration_scale "$old"
   ```
2. **Scrub** the capture (rules below) before it goes anywhere near the repository.
3. **Add a fixture** under `app/src/test/resources/fixtures/tiktok-<version>/<screen>/`. Keep older fixtures too.
4. **Update `tiktok.json`**: fix the selectors and add the new version to `verified_app_versions`.
5. **Run the tests:** `./gradlew :app:testDebugUnitTest`.
6. **Open a pull request** and describe what changed (and the TikTok version) in it.

## Scrubbing rules for fixtures

Raw captures contain other people's names, your location, and your account state. **Never commit raw captures.**
Keep raw dumps, screenshots, and screen recordings outside the repository. A fixture must keep the *pattern* of
each value so the matchers still work, while replacing the content:

- Creator display names, @handles, captions, hashtags, sound titles, bios, and product titles: replace with
  placeholders that keep the shape (for example display name `Seller Name`, handle `seller_handle01`, sound
  `Sound: original sound - seller_handle01 by Seller Name`, a caption with the same hashtag structure but
  generic words). Keep numbers like "123 sold" where selectors depend on them.
- Share-sheet contact names: replace with placeholders such as `Contact A`, or delete those rows entirely.
- Location-based feed tabs (a tab named after a city or region): replace with a generic name.
- Anything that identifies you: your own handle, avatar descriptions, notification text, device name.
- Keep the disclosure labels ("Ad", "Promotional content"), button labels, content-descs, class names, view IDs,
  and bounds intact. They are the point of the fixture.
- Replacements must be consistent within a file. If the handle appears in the sound credit and in a dialog,
  use the same placeholder in both.
- Search the final file for anything that still looks like a real name before committing, and look at your
  diff, not only the file.
- Screenshots are not fixtures. Do not commit them unless fully redacted.
