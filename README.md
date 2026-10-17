# NOIR for Fire TV

NOIR is a native Android TV / Fire TV catalog and player for direct HTTP(S) streams that you are authorized to access. It has a TV-first interface with remote navigation, TMDB metadata/search, series seasons and episodes, My List, Continue Watching, official-service links, and user-installed JSON add-ons.

## NOTICE
This is the first version of the app (or i didnt delete the line)
This app was made for polish language so thats why the providers are polish
If the app doesnt work then its because of the beta (i mean beta now, 17.10.2026)
**THIS APP IS VIBECODED!!!**

## Build on Windows 11

Prerequisites: Android Studio with Android SDK Platform 35 and Build Tools, JDK 17, and accepted Android SDK licenses. The wrapper uses Gradle 8.9 and Android Gradle Plugin 8.7.2.

1. Install Android Studio from <https://developer.android.com/studio>.
2. Open this folder in Android Studio and let Gradle sync. Install SDK components if prompted.
3. Live TMDB metadata is optional. In the project-root `local.properties`, set `TMDB_API_KEY=...`; do not commit or share that file. The file is machine-specific and is git-ignored.
4. From PowerShell in the project folder, run:

   ```powershell
   .\gradlew.bat assembleDebug
   .\gradlew.bat lintDebug
   ```

5. The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

The APK contains the TMDB key at build time, so keep a personal build private. This is a debug build, not a Play Store release.

## Install on Fire TV

1. On the Fire TV, enable **Settings > My Fire TV > Developer Options > ADB Debugging**. If Developer Options is hidden, open **About** and select the device name seven times.
2. Find the Fire TV's IP address under **Settings > My Fire TV > About > Network**.
3. On Windows, install Android SDK Platform Tools (or use the `adb.exe` installed with Android Studio).
4. In PowerShell, from the project folder, run:

   ```powershell
   adb connect FIRE_TV_IP:5555
   adb install -r .\app\build\outputs\apk\debug\app-debug.apk
   ```

5. Accept the ADB prompt on the TV, then launch **NOIR** from the app library. Use the D-pad, Select, and Back.

## APIs and configuration

### Optional: TMDB API key

- **Service:** The Movie Database (TMDB).
- **Why/features:** Enables live discovery shelves, artwork and title metadata, title details, TV seasons/episodes, and online search. Without it, the included starter catalog and local search remain available.
- **Where to obtain:** <https://www.themoviedb.org/settings/api>
- **Price/licensing:** TMDB's developer API is free for non-commercial use with attribution according to its published FAQ; commercial use requires permission/licensing. Check the current [FAQ](https://developer.themoviedb.org/docs/faq) and [API terms](https://www.themoviedb.org/documentation/api/terms-of-use) before use.
- **Configure:** Add `TMDB_API_KEY=your_key` to project-root `local.properties`, then rebuild. `local.properties.example` is a safe template; do not commit the real key.
- **Required?** No, not to build or browse the starter catalog. Required for live TMDB-backed features.

No other API key is required by the app. There is no OpenSubtitles or other third-party caption API integration.

## Add-ons and playback

The **Add-ons** tab accepts an HTTP(S) URL or a local JSON document selected with the system file picker. Only install manifests you trust. Manifests are limited to 1 MB and 100 entries. Each entry must provide a title and a direct HTTP(S) media URL. The format may be specified as `hls`, `dash`, or `progressive`; supported file extensions are also detected when format is omitted. Installed add-ons can be enabled, disabled, refreshed, or removed. The app displays optional name, description, author, and version metadata. See the [add-on developer tutorial](docs/addon-development.md) for the supported manifest fields and a complete fictional example.

```json
{
  "name": "My authorized library",
  "items": [
    {
      "id": "sample-1",
      "type": "movie",
      "title": "My Video",
      "year": "2025",
      "overview": "A video I am authorized to watch.",
      "poster": "https://images.example.org/poster.jpg",
      "backdrop": "https://images.example.org/backdrop.jpg",
      "genres": ["Drama"],
      "stream": "https://media.example.org/video.m3u8",
      "format": "hls",
      "subtitles": [
        {
          "language": "pl",
          "label": "Polski",
          "url": "https://media.example.org/video.pl.vtt"
        }
      ]
    }
  ]
}
```

Replace all example hostnames and stream links with your own authorized resources; the example is not playable. Supported external subtitle formats are WebVTT (`.vtt`) and SubRip (`.srt`). Embedded/HLS/DASH audio and text tracks can be selected from the in-app player. Availability depends on the stream and device decoder. The player supports resume position and shows a buffering indicator; unsupported codecs or inaccessible sources report playback errors.

The app blocks `magnet:`/torrent URLs and rejects URL query parameters that look like credentials or tokens. Do not put passwords, session cookies, or access tokens in a manifest or URL. HTTP is enabled for user-supplied streams for compatibility, but it is unencrypted and should be avoided; prefer HTTPS. Add-on manifests and media URLs are persisted locally so installed add-ons and Continue Watching can be restored.

## Provider status and limitations

- **Official services:** The Providers tab links to TVP VOD, Rakuten TV Free, Plex, South Park Studios, and JustWatch Polska. These are external links and leave NOIR; country, catalog, language, account, and Fire TV app availability can vary. JustWatch is a discovery guide, not a video player. CDA.pl and bajeczki.org do not have an app integration here.
- **Filman:** No supported documented integration was found. NOIR does not collect Filman credentials, scrape the site, resolve its links, or bypass CAPTCHA/DRM/access controls. Filman playback is not available.
- **Series episodes:** TMDB supplies season/episode metadata only; those entries are not playable unless a separate authorized add-on supplies a direct stream.
- **Add-on format:** Add-ons are declarative catalogs, not executable provider plugins. The current format does not map individual episode metadata to stream URLs. See the [add-on developer tutorial](docs/addon-development.md) for exact capabilities and limitations.
- **Captions:** There is no bulk caption-source search service. Captions are available only when embedded in a stream or explicitly declared by an installed add-on.
- **Device verification:** The APK builds and lint is run locally; no physical Fire TV device is attached for runtime testing.

TMDB attribution is included in the application. Metadata and artwork availability depends on network connectivity and TMDB access.
