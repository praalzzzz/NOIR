# NOIR 1.0.2 release checklist

## Completed and verified

- [x] Confirmed the existing Android TV/Fire TV Java application, Gradle wrapper, package ID, remote repository, current branch, and existing v1.0.1 release.
- [x] Inspected the current UI, TMDB networking/search, provider status, JSON add-on flow, playback, and existing unit tests.
- [x] Added Movies and Series destinations to the existing navigation.
- [x] TMDB search now queries Polish and English result languages and merges duplicates by media type and TMDB ID.
- [x] Local starter search normalizes Polish diacritics and punctuation.
- [x] Added unit coverage for search normalization.
- [x] Built a debug APK with an empty bundled TMDB key; API 21 minimum, Java 17, package `tv.noirstream`, version 1.0.2 (3).
- [x] Ran `testDebugUnitTest`, `assembleDebug`, and `lintDebug` successfully.
- [x] Verified APK package/version/signature with Android SDK tools.
- [x] Kept the user's `local.properties` out of Git and used `-PTMDB_API_KEY=` to ensure the distributable build does not embed its configured key.

## Not implemented or not verified

- [ ] The reference website was inaccessible to the browser, so exact visual comparison was not possible.
- [ ] No emulator or connected device was available for install/launch testing.
- [ ] Filman is an explicit unavailable stub. No documented provider API or authorized account integration is configured, so provider source resolution and the requested provider bug cannot be fixed or validated in this repository as-is.
- [ ] TMDB is the only metadata/catalog API in the app. It receives Polish and English search queries; localized alternative titles and original-title metadata are not retained as a richer cross-source matching model.
- [ ] Add-ons are trusted, direct-stream JSON manifests installed by URL or local file. There is no remote registry, marketplace search, capability plug-in protocol, or universal Stremio compatibility.
- [ ] No device-level playback, real-provider, or live TMDB integration tests were run.
- [ ] Release APK signing with an owner-provided release key is not configured; this artifact is debug-signed.
- [ ] GitHub push and release publication are handled after local diff review and commit.
