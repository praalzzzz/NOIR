# NOIR v1.0.3 — Custom Catalog Safety and Provider Status

## Highlights

- Adds a review-and-confirm step before a custom JSON catalog is installed.
- Screens new remote catalog URLs for HTTPS, blocks local/private address resolution, rejects redirects, and retains bounded manifest downloads.
- Documents provider availability and the limits of the current integrations.

## UI Improvements

- The Providers screen labels external destinations as browser/app links and clarifies that NOIR does not search or play them.
- Adds links for CDA, Bajeczki.org, and Paramount+ with notes about unknown per-title rights, price, or regional availability.
- The Add-ons screen explains that custom URLs must point to a NOIR-compatible catalog manifest; an ordinary website URL is not a searchable provider.

## Provider Fixes

- The audit confirmed the registered Filman integration is an unavailable stub: it reports unsupported and returns no sources. This release does not add Filman, CDA, Bajeczki.org, Paramount+, or South Park stream extraction.
- TMDB remains metadata-only. Custom catalogs remain static, user-supplied JSON lists with explicit direct stream URLs.

## New Features

- Custom catalog install confirmation displays publisher, version, item count, and integration limitations before saving.
- `docs/provider-compatibility.md` records source research, regional notes, and the South Park test status.

## Bug Fixes

- Remote catalog installs require HTTPS. Manifest requests reject localhost/private/reserved address ranges, do not follow redirects, and remain limited to 1 MB with bounded connect/read timeouts.
- Invalid/unavailable providers are described separately from external services and metadata catalogs.

## Installation

Download `NOIR-v1.0.3.apk` from this release and install it on an Android device or Android TV/Fire TV device that permits sideloading. This is a debug-signed APK, not a Play Store release. No TMDB key is bundled; add your own key in the app to enable live metadata/search.

## Compatibility

- Android 5.0+ (minimum SDK 21)
- Target SDK 35
- Package: `tv.noirstream`
- Version code 4, version name 1.0.3

## Known Issues

- No in-app search/source resolver or authentication flow exists for Filman, CDA, Bajeczki.org, South Park Studios, Paramount+, TVP VOD, Rakuten TV, or Plex.
- The requested South Park search-to-playback flow was not verified. JustWatch Poland reported no series streaming availability on 18 October 2026; the official site is an external browser fallback only.
- There is no hosted addon marketplace/catalog. Users add one compatible static JSON manifest at a time.
- DNS rebinding cannot be ruled out by a client-side pre-resolution check. No emulator or physical Android device was attached, so installation, navigation, external sign-in, and playback were not runtime-tested.
- Unit tests, lint, and the debug build pass locally. The APK is signed with the standard Android debug certificate.
