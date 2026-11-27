# Setup

Written for Android 13+ with extra detail for Samsung One UI (tested on Android 17 / One UI 9). The in-app
**Setup** checklist shows the live status of each step below.

## 1. Install

Pick one:

- **Release APK (easiest):** download `FeedClean-<version>.apk` from the
  [latest release](https://github.com/dreadnought-0/FeedClean/releases/latest), open it, and allow your browser or
  file manager to install unknown apps. Optionally check it against `SHA256SUMS` from the same release
  (`sha256sum -c SHA256SUMS`). Release APKs are signed with the FeedClean release key, so later releases install
  over earlier ones and keep your data.
- **Android Studio:** open the project, connect your phone with USB debugging on, and press Run.
- **ADB:** build with `./gradlew :app:assembleDebug`, then
  `adb install -r app/build/outputs/apk/debug/app-debug.apk`. You can also `adb install` the release APK.

A debug build and a release build are signed with different keys, so one can't update the other: uninstall first
(this deletes FeedClean's local data, so export it first if you want to keep it). Installs over ADB are exempt from
Android developer verification, so if your phone ever refuses the APK for that reason, install it with
`adb install` instead.

## 2. Allow restricted settings (if the toggle is greyed out)

Android blocks accessibility access for apps installed outside a store until you allow it:

Settings, Apps, FeedClean, the three-dot menu (top right), **Allow restricted settings**. You may need to try to
enable the service once first before this menu item appears.

## 3. Enable the service

Settings, Accessibility, **Installed apps** (on One UI; on other builds look for "Downloaded apps"), FeedClean,
switch on, and accept the system warning. FeedClean's service is scoped to the TikTok package only.

## 4. Battery

Samsung may kill background services. Set Settings, Apps, FeedClean, Battery, to **Unrestricted**. Optionally also
add FeedClean to Battery, Background usage limits, **Never sleeping apps**.

## 5. Advanced Protection

If Android's **Advanced Protection** mode (Android 16+) is on, it disables accessibility services from apps that
are not accessibility tools. FeedClean is not an assistive tool and does not claim to be one, so it will be
switched off. Turn Advanced Protection off if you want to use FeedClean.

## First run

1. Open FeedClean and go through the **Setup** checklist until every row is green.
2. The **Home** screen shows today's counts and quick toggles: *Curate feed*, *Auto-skip organic Shop*,
   *Skip-cover*, and the automation **kill-switch**. Nothing is automated until you turn something on.
3. Open TikTok. A small **bubble** appears (grey when nothing is detected, with colours for ad, promoted, Shop,
   and live). Tap it for the card: Block, Skip, Not interested, Log only, Mark false positive.
4. **Long-press the bubble to pause all automation** (the kill-switch). Long-press again, or use Home, to re-arm.
5. Review Settings: every behaviour has a toggle. Enabling automated "Not interested" shows an account-risk note;
   read it first.

## Troubleshooting

- **Accessibility toggle greyed out:** see step 2.
- **Service turns itself off or stops working after a while:** check Battery is Unrestricted and Advanced
  Protection is off, then toggle the service off and on.
- **No bubble in TikTok:** confirm the service is on and "Show bubble" is enabled in Settings. See Debug below.
- **Nothing is detected after a TikTok update:** selectors may need updating. Open the Debug screen to see what
  matched, then see [SELECTORS.md](SELECTORS.md).
- **Detection is wrong in another language:** selectors are currently English only.
- **Debug screen:** shows the live parsed screen and which selector strategy matched. Useful for bug reports,
  but check it for personal data before sharing.
