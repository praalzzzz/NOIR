# NOIR add-on development

## What an add-on is

The current NOIR add-on format is a **declarative JSON catalog**, not an executable provider SDK. It describes movie and series titles, plus direct HTTP(S) media URLs that you own or are authorized to use. A series can list season/episode numbers and an explicit stream URL for each episode. NOIR does not execute remote code, scrape websites, collect streaming credentials, resolve unsupported source pages, or bypass DRM or access controls.

An add-on can supply movie sources and custom series episode sources. TMDB can separately supply metadata, but NOIR does not automatically match TMDB episodes to add-on streams. An add-on cannot register Java/Kotlin/JavaScript provider code, define an update registry, declare permissions, or specify a supported NOIR version.

## Getting started

1. Create a UTF-8 JSON file.
2. Add an `items` array with at least one movie with a direct stream, or a TV series with one or more episode stream entries.
3. Either host the file at an HTTPS URL reachable from the Android TV/Fire TV device, or copy it to storage visible to the device. New remote manifests require HTTPS; previously installed HTTP manifests remain refreshable for compatibility.
4. In NOIR, open **Add-ons** and enter the manifest URL, or choose **Choose local JSON manifest** in the system file picker. Remote URLs are fetched without following redirects, capped at 1 MB, and screened against local/private IP ranges.
5. Review the publisher, version, catalog size, and limitations in the confirmation dialog before installing. Cancel leaves the manifest uninstalled.
6. Open a title and choose **Try stream in NOIR**. Structural validation does not prove the URL is reachable, authorized, or decodable; only an actual playback attempt can test the source on that device.

A website URL by itself is not a provider. NOIR currently supports a static declarative catalog, not arbitrary website search, scraping, OAuth, or account sessions. See the [provider compatibility report](provider-compatibility.md) for current integrations, regional research, and known limitations.

The manifest is limited to 1 MB and 100 items.

### Minimal manifest

This fictional manifest uses an explicit progressive format; its example URL is not playable.

```json
{
  "name": "My test library",
  "items": [
    {
      "title": "Sample Video",
      "stream": "https://media.example.org/sample.mp4",
      "format": "progressive"
    }
  ]
}
```

## Manifest reference

Unknown fields are ignored. Root fields:

| Field | Required | Meaning and validation |
|---|---:|---|
| `name` | No | Display name; defaults to `Community add-on`; maximum 120 characters. |
| `id` | No | Optional stable add-on identifier. Up to 100 letters, digits, dots, underscores, or hyphens. |
| `description` | No | Description shown in the installed add-on card; maximum 500 characters. |
| `author` | No | Optional author label; maximum 120 characters. |
| `version` | No | Optional display version; maximum 64 characters. |
| `items` | Yes | Array of 1–100 catalog item objects. |

Item fields:

| Field | Required | Meaning and validation |
|---|---:|---|
| `id` | No | Stable item identifier, unique within this manifest; up to 100 letters, digits, dots, underscores, or hyphens. If omitted, the item's array index is used. |
| `type` | No | `movie` or `tv`; defaults to `movie`. |
| `title` | Yes | Non-empty display title; maximum 200 characters. |
| `year` | No | Display year or release label. |
| `overview` | No | Display synopsis. |
| `poster` | No | Safe HTTP(S) artwork URL or an absolute path such as a TMDB image path. |
| `backdrop` | No | Safe HTTP(S) artwork URL or absolute path; defaults to `poster`. |
| `genres` | No | Array of up to 30 non-empty genre labels. |
| `stream` | Movies: yes; TV: optional | Direct HTTP(S) title stream URL. It must pass NOIR's URL rules and either have a supported media extension or a supported `format`. A TV item can use `episodes` instead. |
| `format` | No | `hls`, `dash`, or `progressive`. If omitted, NOIR infers HLS/DASH and common progressive formats from the URL path. |
| `subtitles` | No | Array of external subtitle objects. Only WebVTT (`.vtt`) and SubRip (`.srt`) URLs are accepted. |
| `episodes` | TV only | Array of 1–500 explicit episode entries. A TV item needs either a valid title `stream` or this non-empty array. |

Each subtitle object has a required `url` and optional `language` (or `lang`) and `label`. Invalid subtitle entries invalidate the manifest item list rather than being silently accepted.

Each episode object requires `season` (0–99), `episode` (1–999), `title`, and `stream`. Season/episode pairs must be unique within that series. `format` and `subtitles` follow the same rules as the title fields; `overview` is optional. The player opens the explicitly declared stream when the user selects **Play episode**.

### Valid fictional example

The example uses reserved `.example.org` hostnames. It passes the manifest structure checks, but it is deliberately **not playable**.

```json
{
  "id": "sample.library",
  "name": "Sample authorized library",
  "description": "A fictional catalog for testing the manifest format.",
  "author": "Example Author",
  "version": "1.0.0",
  "items": [
    {
      "id": "sample-film",
      "type": "movie",
      "title": "Sample Film",
      "year": "2025",
      "overview": "Fictional metadata for testing.",
      "poster": "https://images.example.org/poster.jpg",
      "backdrop": "https://images.example.org/backdrop.jpg",
      "genres": ["Drama"],
      "stream": "https://media.example.org/sample.m3u8",
      "format": "hls",
      "subtitles": [
        {
          "language": "en",
          "label": "English",
          "url": "https://media.example.org/sample.vtt"
        }
      ]
    }
  ]
}
```

To create a minimal catalog, keep the `items` field and an item with `title` and `stream`; omit the optional metadata and subtitles. Choose a stream format supported by the NOIR player and use only a direct URL you are authorized to distribute.

## Metadata and series behavior

The app preserves a title's type, display name, year, overview, artwork paths, genres, add-on name, and source format. Missing artwork is allowed and rendered using the app's placeholder. A TV item can define its own explicit `episodes` array, organized by season and episode number, and each entry can provide its own direct stream and subtitles. An optional title-level `stream` is still a single direct stream, not an episode map.

TMDB supplies live catalog metadata, details, season lists, and episode metadata when a TMDB API key is configured. Enter a personal key by selecting the catalog status (**CURATED EDITION** or **LIVE DISCOVERY**) in the TV app header; it is stored in app-private preferences on that device. TMDB does not supply playable video. TMDB episode listings are metadata only; use the add-on `episodes` mapping to supply an authorized stream for each playable episode.

## Testing and debugging

- Test with fictional metadata and placeholder URLs first; do not label mock data as a real integration.
- Use a URL reachable from the TV, not merely from the development computer.
- Reinstall or use **Refresh** after publishing a manifest change.
- For a series, add an `episodes` entry with a distinct season/episode number and a direct source for every episode you want to play. TMDB metadata alone does not provide those streams.
- Validation errors appear in the Add-ons screen. A manifest that passes validation may still point to an unavailable stream or unsupported codec.
- Select **Try stream in NOIR** to test playback. ExoPlayer reports network, format, and decoder failures in the app.
- Device logs can be inspected with `adb logcat -s NOIR`.
- Run the repository's JVM parser tests and checks:

  ```powershell
  .\gradlew.bat testDebugUnitTest
  .\gradlew.bat assembleDebug
  .\gradlew.bat lintDebug
  ```

The automated parser tests use mocked JSON and do not contact TMDB or validate a real stream. No physical Fire TV device is required for these JVM tests.

## Installation, updates, and distribution

NOIR installs manifests by HTTP(S) URL or by selecting a local JSON document. It stores the URL or persistable document URI locally; removing a local add-on releases the document permission. The installed add-on screen supports enable, disable, refresh (for remote manifests), and removal. There is no public registry configured. A curator can host a catalog or publish a directory of trusted manifest URLs separately; the application does not assume a registry endpoint. Executable add-on packages are not supported.

Keep item IDs stable between releases, update the optional `version` when the catalog changes, and host updates at the installed URL. NOIR displays the version as metadata; it does not perform semantic version comparisons or compatibility enforcement.

## Security and playback boundaries

- Do not put passwords, cookies, API keys, session IDs, signatures, or access tokens in URLs or manifest fields.
- URLs with embedded user information and credential-like query parameter names are rejected. Magnet and torrent links are not supported.
- HTTP is permitted for compatibility but is unencrypted; prefer HTTPS.
- A valid URL is not proof that a source can be played. The user explicitly starts playback, and Media3 reports actual loading/decoding failures.
- NOIR does not load executable remote add-ons, so this format has no code permissions or entry point.
- Do not use the manifest to redistribute media unless you have authorization to do so.
