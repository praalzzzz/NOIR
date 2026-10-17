# NOIR v1.0.2 — Bilingual Search and Navigation

## Changes

- Added dedicated Movies and Series destinations to NOIR's Android TV navigation.
- TMDB title searches now query Polish (`pl-PL`) and English (`en-US`) and deduplicate the merged results by media type and TMDB ID.
- Local starter-catalog search now ignores diacritics and punctuation, including Polish `ł`.
- Added unit coverage for local search normalization.
- Added Gradle property and environment variable overrides for `TMDB_API_KEY`, allowing a distributable build without embedding a developer's local key.

## Verification

- `testDebugUnitTest`: passed (18 tests).
- `assembleDebug`: passed.
- `lintDebug`: passed with existing warnings and no lint errors.
- APK: debug-signed; package `tv.noirstream`; version 1.0.2 (version code 3); minimum Android API 21.

## Limitations

- This is a debug APK, not a release-key-signed production package.
- No emulator or connected device was available for installation or playback checks.
- TMDB requires the user to configure an API key. The shipped APK has no bundled key.
- Filman source resolution is not implemented. NOIR supports installed data-only JSON manifests containing authorized direct HTTP(S) media URLs; it has no public add-on registry and does not claim Stremio protocol compatibility.
- Search queries Polish and English TMDB result languages, but the application does not maintain a complete alternate-title index across metadata sources.
- The reference website could not be opened during this release, so visual parity was not verified.
