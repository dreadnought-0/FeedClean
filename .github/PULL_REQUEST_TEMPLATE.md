## What and why

<!-- Short description and linked issue. For selector changes, say what changed and the TikTok version. -->

## Checklist

- [ ] Tests added or updated; `./gradlew :app:testDebugUnitTest` passes
- [ ] Selector/parser changes include a scrubbed fixture under `app/src/test/resources/fixtures/tiktok-<version>/<screen>/`
- [ ] Fixtures are scrubbed (names, handles, captions, sounds, bios, product titles, contacts, location tabs)
- [ ] No raw captures (UI dumps, screenshots, screen recordings) in this pull request
- [ ] No new network calls, analytics, or crash-reporting SDKs
- [ ] TikTok strings/IDs are only in `tiktok.json`, not in Kotlin
- [ ] Project principles in CONTRIBUTING.md respected (user's tap for account actions, rate limiter, no reverse engineering)
- [ ] New automation has a UI toggle and is off by default
- [ ] Docs updated where relevant (EXPORT_SCHEMA, SELECTORS)
