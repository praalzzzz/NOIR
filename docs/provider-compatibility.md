# Provider compatibility and availability

Checked 18 October 2026. Availability is time-sensitive and can vary by title, rights window, device, and region. This report distinguishes a service that is reachable in a browser from a source that NOIR can search and play.

## What NOIR currently supports

| Integration | What works in NOIR | Authentication | Playback status |
|---|---|---|---|
| TMDB | Search and movie/series metadata, artwork, seasons, and episodes when a user supplies a TMDB key | TMDB key only | Metadata only; TMDB does not supply video sources |
| User-installed JSON catalog | Reads a static catalog manifest, indexes its listed movies/series locally, and reads declared episode/source metadata | None | Direct HTTP(S) HLS, DASH, or progressive streams are handed to Media3; actual playback still depends on host access and device codecs |
| Filman | No supported integration | None | Not available; the registered `FilmanProvider` is a deliberate unavailable stub and returns no sources |
| TVP VOD, Rakuten TV, Plex, South Park Studios, JustWatch, CDA, Bajeczki.org, Paramount+ | External links only | Handled by the service outside NOIR | NOIR does not search these catalogs, import their sessions, resolve their web players, or claim their content is playable |

The custom JSON catalog is the only user-defined in-app source mechanism. It is not an executable provider plugin, a website scraper, or a remote search API. The existing format is documented in [addon-development.md](addon-development.md): up to 100 catalog entries in a 1 MB manifest, with explicit episode records and direct stream URLs. An ordinary home-page URL does not describe search, seasons, authentication, or media formats and cannot be treated as an integration.

Installing a catalog now shows the publisher, version, item count, supported direct-stream mode, and a trust/rights notice before saving it. Remote manifests have a 1 MB response cap, bounded connect/read timeouts, no automatic redirects, and reject credentials, local hostnames, and DNS results in private/reserved address ranges. This reduces accidental access to local services; it is not a guarantee against DNS rebinding. User-supplied media URLs are validated separately and are not fetched as manifests. NOIR does not run addon code.

There is no supported sign-in/OAuth flow for third-party streaming sites. NOIR does not collect passwords or copy browser cookies/tokens into Media3. Where a service needs a website login, DRM, a proprietary app, or a region-specific session, use its official app/site. Do not paste session URLs or tokens into manifests.

## Requested services

### CDA

- The official site exposes search, user videos, partner channels, premium films/series, and account sign-in. Its own [legal information](https://www.cda.pl/legal) distinguishes licensed CDA Premium partner titles from partner and user-uploaded material; the site says uploads without rights violate its rules. Individual listings therefore cannot be presumed authorized from the presence of a player.
- CDA Premium is a paid subscription and its terms limit the license to personal viewing on the CDA portal; they prohibit retransmission/ripping. See the [CDA Premium terms](https://www.cda.pl/regulamin/premium).
- I found no public, official CDA catalog/playback API or third-party-player integration contract in CDA's user-facing developer/legal pages. Unofficial libraries that scrape pages or extract direct playback URLs are not a documented authorization path and are not used by NOIR.
- Result: external browser/app link only. No CDA search, login, episode resolver, stream extraction, or Media3 playback was tested or implemented.
- Research limitation: the public homepage/legal page were readable, but the browser research tool could not open the constructed South Park search URL. No live CDA query result was recorded.

### Bajeczki.org

- At the time checked, the public homepage exposed search, film and cartoon/series categories, season pages, episode entries, and a registration/login menu. Its own information text says access is free and does not require login/payment. The homepage also links a legal notice, privacy policy, and DMCA reporting page.
- The observed catalog includes third-party titles and pages that link to video items, but this check did not establish title-by-title distribution rights or permission to embed/extract its video hosts. The legal-notice request was rate-limited during this research, and I found no documented public API or third-party playback permission.
- Result: not integrated into NOIR and not described as a verified licensed source. The app can open the site's homepage externally; its catalog is not imported and its streams are not resolved.
- Research limitation: the homepage was readable, but its legal-notice endpoint returned HTTP 429 and the constructed search URL was inaccessible to the browser research tool. No episode page or video host was tested.

### South Park Studios, Paramount+, and Polish availability

- `https://southpark.cc.com/` is an official South Park site. A browser fetch redirected to a regional South Park Studios/Nordic site whose public page advertises full episodes and clips. This confirms the official site is live, not that all episodes are available from Poland or that its web player can be embedded.
- The requested marketing/reference site `https://noir-player.vercel.app/` could not be opened with the available browser research tool. The supplied `toinspire` React source and interactive TV mockup were inspected locally; they were already reflected in the existing Android TV design, so this change did not replace the app UI with the website.
- The official [South Park viewing FAQ](https://help.southpark.cc.com/s/article/SP-Where-can-I-watch-South-Park) says availability depends on location. The [JustWatch Poland series page](https://www.justwatch.com/pl/serial/miasteczko-south-park), checked 18 October 2026, reported no streaming option for the series in Poland. JustWatch listed the 1999 film separately on paid services; film availability does not establish series availability.
- Paramount+'s U.S. page advertises seasons and full episodes, but that is not evidence of a Polish Paramount+ subscription or Polish playback rights. SkyShowtime's Polish catalogue pages surfaced South Park specials; I did not find evidence there that the regular series is included.
- Result: the app opens the official South Park website in the system browser. NOIR has no South Park provider, source resolver, WebView playback, or authenticated/DRM-capable playback route.

### Other services usable for discovery in Poland

| Service | Publicly documented offer | NOIR status |
|---|---|---|
| [TVP VOD](https://vod.tvp.pl/) | Polish broadcaster; its catalogue mixes selected free programmes/films and paid/premium items. The catalogue and rights windows change. | External link only; no public integration contract was configured |
| [Rakuten TV Free](https://support.rakuten.tv/hc/pl/articles/360036407474-Czym-jest-darmowy-serwis-Rakuten-TV-ZA-DARMO) | Ad-supported selection of films/series. Rakuten says requirements differ by device: compatible Smart TVs can use it without an account; web/other devices require sign-in/registration. | External link only; no in-app catalog API/player integration |
| [Plex](https://support.plex.tv/articles/free-live-tv-streaming-overview/) | Free ad-supported on-demand/live catalog; catalogue varies by region. A free Plex account may be used for personalization and watchlist. | External link only |
| [JustWatch Polska](https://www.justwatch.com/pl/) | Search/discovery guide for services and current title availability; it is not a video source/player. | External link only |
| [Polsat Box Go](https://polsatboxgo.pl/) | Polish catalogue with both free and paid/packaged material; account and licensing conditions vary. | Not currently linked or integrated |
| Public-domain collections | Rights are item-specific. A platform label or free download alone does not establish the status of every film, soundtrack, subtitle, or restoration. | No broad collection is imported; a user may add a direct stream only when they have rights to use it |

The existing Providers screen also links TVP VOD, Rakuten TV, Plex, JustWatch, the official South Park site, CDA, Bajeczki.org, and Paramount+ as external destinations. The labels identify when NOIR has not verified rights, price, login, or in-app compatibility.

## South Park end-to-end test status

| Step | Verified status |
|---|---|
| Search `South Park` / `Miasteczko South Park` in NOIR | Not run on an Android device; TMDB search code queries Polish and English locales when configured, but a live result is not recorded here |
| Series/season/episode metadata | TMDB parser unit tests exist for generic TV seasons and episode numbering; they are not South Park-specific and do not prove live availability |
| South Park official site reachable | Browser-side page fetch succeeded and redirected to a regional official site; not tested from a Polish Android TV device |
| Source URL resolved by NOIR | No |
| Playback in NOIR/Media3 | Not tested; no source is available to hand off |
| Polish series availability | JustWatch Poland reported no streaming option for the series on the date checked |

Therefore the requested `search → series → season → episode → source → playback` acceptance path is **not passed**. The app's external-browser link is a fallback only. No claim is made that a full episode plays inside NOIR.

## Integration blockers and next steps

1. A provider owner must publish a documented API/plugin contract or authorize an adapter, and document how its content is licensed for the intended use.
2. A compatible provider must return metadata, season/episode identifiers, and supported direct playback URLs (or a documented standard NOIR can implement). DRM-protected web players remain external.
3. If an official OAuth flow is available, implement its registered client/redirect and secure token storage separately. Do not collect passwords or import site cookies.
4. Add fixtures and provider-specific tests, then run a real device test from Poland for search, each episode step, source validation, and actual playback.
