package tv.noirstream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.net.Uri;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.webkit.WebView;
import org.json.JSONArray;
import org.json.JSONObject;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;

@UnstableApi
public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(7, 8, 10);
    private static final int PANEL = Color.rgb(20, 22, 26);
    private static final int TEXT = Color.rgb(248, 249, 246);
    private static final int MUTED = Color.rgb(151, 156, 165);
    private static final int GOLD = Color.rgb(210, 246, 91);
    private static final String TMDB_IMAGE = "https://image.tmdb.org/t/p/";
    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final LruCache<String, Bitmap> imageCache =
            new LruCache<>((int) (Runtime.getRuntime().maxMemory() / 8)) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };
    private final Deque<Route> history = new ArrayDeque<>();
    private final Set<String> savedIds = new HashSet<>();
    private final Map<String, Title> savedCatalog = new HashMap<>();
    private final Map<String, Title> knownTitles = new ConcurrentHashMap<>();
    private final Map<String, Title> addonTitles = new ConcurrentHashMap<>();
    private final Map<String, Long> continuePositions = new ConcurrentHashMap<>();
    private final Set<String> addonManifestUrls = new HashSet<>();
    private final Set<String> detailsRequested = new HashSet<>();
    private final Set<String> detailFailures = new HashSet<>();
    private final Set<String> seasonsRequested = new HashSet<>();
    private final List<ContentProvider> providers = Arrays.asList(new FilmanProvider());
    private SharedPreferences preferences;
    private LinearLayout page;
    private LinearLayout content;
    private String currentRoute = "home";
    private String query = "";
    private String searchType = "all";
    private String catalogMessage = "";
    private boolean loadingSearch;
    private ExoPlayer player;
    private PlayerView playerView;
    private Title playingTitle;
    private Handler playbackHandler;
    private String addonMessage = "";
    private final Runnable playbackProgressSaver = new Runnable() {
        @Override
        public void run() {
            if (player != null && playingTitle != null && player.isPlaying()) savePlaybackProgress();
            if (player != null) playbackHandler.postDelayed(this, 5000);
        }
    };
    private List<Title> trending;
    private List<Title> popular;
    private List<Title> series;
    private List<Title> newTitles;
    private List<Title> animation;
    private final List<Title> starter = StarterCatalog.create();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        preferences = getSharedPreferences("noir_preferences", MODE_PRIVATE);
        for (Title item : starter) knownTitles.put(item.key(), item);
        loadSavedTitles();
        loadContinuePositions();
        loadAddonManifestUrls();
        playbackHandler = new Handler(Looper.getMainLooper());
        trending = selectType(false);
        popular = selectType(false);
        series = selectType(true);
        newTitles = selectType(false);
        animation = selectAnimation();
        loadLiveCatalog();
        render();
        loadSavedAddonManifests();
    }

    private LinearLayout findSearchResults() {
        if (content == null || content.getChildCount() < 5) return null;
        View candidate = content.getChildAt(4);
        return candidate instanceof LinearLayout ? (LinearLayout) candidate : null;
    }

    private void loadSavedTitles() {
        JSONArray stored;
        try {
            stored = new JSONArray(preferences.getString("saved_titles", "[]"));
        } catch (Exception exception) {
            return;
        }
        loadSavedTitlesEntries(stored);
    }

    private void loadContinuePositions() {
            try {
                JSONArray entries = new JSONArray(preferences.getString("continue_items", "[]"));
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject object = entries.optJSONObject(i);
                    if (object == null) continue;
                    Title item = titleFromJson(object);
                    long position = object.optLong("position", 0);
                    if (item == null || !isValidMediaSource(item.streamUri, item.streamFormat) || position < 10000) continue;
                    knownTitles.put(item.key(), item);
                    continuePositions.put(item.key(), position);
                }
            } catch (Exception ignored) {
                preferences.edit().remove("continue_items").apply();
            }
        }

    private Title titleFromJson(JSONObject object) {
            String name = object.optString("name", "");
            if (name.isEmpty()) return null;
            Title item = new Title(object.optLong("id"), object.optString("type", "movie"),
                    name, object.optString("year", "—"), object.optString("overview", ""),
                    object.optString("poster", ""), object.optString("backdrop", ""),
                    object.optString("genres", ""), object.has("rating") ? object.optDouble("rating") : null,
                    object.has("runtime") ? object.optInt("runtime") : null);
            item.streamUri = object.optString("stream", null);
            item.streamFormat = object.optString("format", "");
            item.addonName = object.optString("addon", null);
            item.subtitles = parseSubtitles(object.optJSONArray("subtitles"));
            return item;
        }

    private List<SubtitleTrack> parseSubtitles(JSONArray values) {
            List<SubtitleTrack> tracks = new ArrayList<>();
            if (values == null) return tracks;
            for (int i = 0; i < values.length(); i++) {
                JSONObject item = values.optJSONObject(i);
                if (item == null) continue;
                String uri = item.optString("url", "");
                String language = item.optString("language", item.optString("lang", "und"));
                String label = item.optString("label", language);
                if (isValidSubtitleSource(uri)) tracks.add(new SubtitleTrack(language, label, uri));
            }
            return tracks;
        }

    private void loadAddonManifestUrls() {
            try {
                JSONArray urls = new JSONArray(preferences.getString("addon_manifest_urls", "[]"));
                for (int i = 0; i < urls.length(); i++) {
                    String url = urls.optString(i, "");
                    if (isSafeHttpUrl(url)) addonManifestUrls.add(url);
                }
            } catch (Exception ignored) {
                preferences.edit().remove("addon_manifest_urls").apply();
            }
        }

    private void loadSavedAddonManifests() {
            for (String url : new ArrayList<>(addonManifestUrls)) fetchAddonManifest(url, false);
        }

    private void persistAddonManifestUrls() {
            JSONArray urls = new JSONArray();
            for (String url : addonManifestUrls) urls.put(url);
            preferences.edit().putString("addon_manifest_urls", urls.toString()).apply();
        }

    private void addAddonManifest(String url) {
            if (!isSafeHttpUrl(url)) {
                addonMessage = "Enter a valid HTTP(S) manifest URL without embedded credentials.";
                render();
                return;
            }
            if (addonManifestUrls.contains(url)) {
                addonMessage = "That manifest is already installed.";
                render();
                return;
            }
            addonMessage = "Loading and validating add-on…";
            render();
            fetchAddonManifest(url, true);
        }

    private void fetchAddonManifest(String manifestUrl, boolean userInitiated) {
            io.execute(() -> {
                try {
                    JSONObject manifest = fetchJsonUrl(manifestUrl);
                    String addonName = manifest.optString("name", "Community add-on");
                    JSONArray items = manifest.optJSONArray("items");
                    if (items == null || items.length() == 0 || items.length() > 100) {
                        throw new IllegalArgumentException("The manifest must contain 1–100 items.");
                    }
                    List<Title> parsed = new ArrayList<>();
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject entry = items.optJSONObject(i);
                        if (entry == null) continue;
                        String title = entry.optString("title", "").trim();
                        String stream = entry.optString("stream", "");
                        String format = entry.optString("format", "").toLowerCase(Locale.ROOT);
                        if (title.isEmpty() || !isValidMediaSource(stream, format)) {
                            throw new IllegalArgumentException("Item " + (i + 1) + " needs a title and a valid direct HTTP(S) stream URL.");
                        }
                        JSONArray rawGenres = entry.optJSONArray("genres");
                        List<String> genres = new ArrayList<>();
                        if (rawGenres != null) for (int n = 0; n < rawGenres.length(); n++) genres.add(rawGenres.optString(n));
                        String poster = entry.optString("poster", "");
                        String backdrop = entry.optString("backdrop", poster);
                        if (!poster.isEmpty() && !isSafeArtwork(poster)) {
                            throw new IllegalArgumentException("Item " + (i + 1) + " has an invalid poster URL.");
                        }
                        if (!backdrop.isEmpty() && !isSafeArtwork(backdrop)) {
                            throw new IllegalArgumentException("Item " + (i + 1) + " has an invalid backdrop URL.");
                        }
                        long id = Integer.toUnsignedLong((manifestUrl + ":" + entry.optString("id", String.valueOf(i))).hashCode()) + 1;
                        String type = "tv".equalsIgnoreCase(entry.optString("type", "movie")) ? "tv" : "movie";
                        Title item = new Title(id, type, title, entry.optString("year", "—"),
                                entry.optString("overview", ""), poster, backdrop, join(genres, " / "),
                                null, null);
                        item.streamUri = stream;
                        item.streamFormat = format;
                        item.addonName = addonName;
                        item.manifestUrl = manifestUrl;
                        item.subtitles = parseSubtitles(entry.optJSONArray("subtitles"));
                        parsed.add(item);
                    }
                    if (parsed.isEmpty()) throw new IllegalArgumentException("No playable items were found.");
                    runOnUiThread(() -> {
                        if (userInitiated) {
                            addonManifestUrls.add(manifestUrl);
                            persistAddonManifestUrls();
                        }
                        for (Title item : parsed) {
                            knownTitles.put(item.key(), item);
                            addonTitles.put(item.key(), item);
                        }
                        addonMessage = (userInitiated ? "Added " : "Loaded ") + addonName + " · "
                                + parsed.size() + " validated item(s).";
                        if ("addons".equals(currentRoute) || "home".equals(currentRoute)) render();
                    });
                } catch (Exception exception) {
                    runOnUiThread(() -> {
                        addonMessage = "Add-on not loaded: " + safeErrorMessage(exception);
                        if ("addons".equals(currentRoute)) render();
                    });
                }
            });
        }

    private JSONObject fetchJsonUrl(String address) throws Exception {
            HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            try {
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status + " from the manifest host.");
                InputStream stream = connection.getInputStream();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (bytes.size() + count > 1_000_000) throw new IllegalArgumentException("Manifest exceeds the 1 MB limit.");
                    bytes.write(buffer, 0, count);
                }
                stream.close();
                return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            } finally {
                connection.disconnect();
            }
        }

    private void removeAddonManifest(String url) {
            addonManifestUrls.remove(url);
            addonMessage = "Removed add-on manifest.";
            persistAddonManifestUrls();
            for (Map.Entry<String, Title> entry : new ArrayList<>(addonTitles.entrySet())) {
                if (url.equals(entry.getValue().manifestUrl)) addonTitles.remove(entry.getKey());
            }
            for (String key : new ArrayList<>(knownTitles.keySet())) {
                Title item = knownTitles.get(key);
                if (item != null && url.equals(item.manifestUrl)) {
                    continuePositions.remove(key);
                    savedCatalog.remove(key);
                    savedIds.remove(key);
                }
            }
            for (Map.Entry<String, Title> entry : new ArrayList<>(knownTitles.entrySet())) {
                if (url.equals(entry.getValue().manifestUrl)) knownTitles.remove(entry.getKey());
            }
            persistSavedTitles();
            persistContinuePositions();
            render();
        }

    private boolean isSafeHttpUrl(String address) {
            if (address == null || address.isEmpty()) return false;
            try {
                URI uri = URI.create(address);
                String scheme = uri.getScheme();
                if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                        || uri.getHost() == null || uri.getUserInfo() != null) return false;
                String query = uri.getQuery();
                if (query != null) {
                    for (String parameter : query.toLowerCase(Locale.ROOT).split("[&;]")) {
                        int equals = parameter.indexOf('=');
                        String name = equals < 0 ? parameter : parameter.substring(0, equals);
                        if (name.contains("pass") || name.contains("token") || name.contains("secret")
                                || name.contains("key") || name.contains("auth") || name.contains("sig")
                                || name.contains("credential") || name.contains("session")
                                || name.contains("cookie")) return false;
                    }
                }
                return true;
            } catch (Exception exception) {
                return false;
            }
        }

    private boolean isSafeArtwork(String value) {
            return value.startsWith("/") || isSafeHttpUrl(value);
        }

    private boolean isValidSubtitleSource(String address) {
            if (!isSafeHttpUrl(address)) return false;
            String path = URI.create(address).getPath().toLowerCase(Locale.ROOT);
            return path.endsWith(".vtt") || path.endsWith(".srt");
        }

    private boolean isValidMediaSource(String address) {
            return isValidMediaSource(address, "");
        }

    private boolean isValidMediaSource(String address, String format) {
            if (!isSafeHttpUrl(address)) return false;
            String path = URI.create(address).getPath().toLowerCase(Locale.ROOT);
            if (path.endsWith(".torrent")) return false;
            if ("hls".equals(format) || "dash".equals(format) || "progressive".equals(format)) return true;
            return path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".mp4")
                    || path.endsWith(".m4v") || path.endsWith(".webm") || path.endsWith(".mkv")
                    || path.endsWith(".mov") || path.endsWith(".ts");
        }

    private String safeErrorMessage(Exception exception) {
            String message = exception.getMessage();
            if (message == null || message.trim().isEmpty()) return "Check the URL and manifest format.";
            return message.length() > 180 ? message.substring(0, 180) : message;
    }

    private void loadSavedTitlesEntries(JSONArray stored) {
        for (int i = 0; i < stored.length(); i++) {
            JSONObject object = stored.optJSONObject(i);
            if (object == null) continue;
            Title item = new Title(object.optLong("id"), object.optString("type", "movie"),
                    object.optString("name", ""), object.optString("year", "—"),
                    object.optString("overview", ""), object.optString("poster", ""),
                    object.optString("backdrop", ""), object.optString("genres", ""),
                    object.has("rating") ? object.optDouble("rating") : null,
                    object.has("runtime") ? object.optInt("runtime") : null);
            item.streamUri = object.optString("stream", null);
            item.streamFormat = object.optString("format", "");
            item.addonName = object.optString("addon", null);
            item.manifestUrl = object.optString("manifest", null);
            item.subtitles = parseSubtitles(object.optJSONArray("subtitles"));
            if (item.name.isEmpty()) continue;
            savedCatalog.put(item.key(), item);
            savedIds.add(item.key());
            knownTitles.put(item.key(), item);
        }
    }

    private void persistSavedTitles() {
        JSONArray stored = new JSONArray();
        for (Title item : savedCatalog.values()) {
            JSONObject object = new JSONObject();
            try {
                object.put("id", item.id);
                object.put("type", item.type);
                object.put("name", item.name);
                object.put("year", item.year);
                object.put("overview", item.overview);
                object.put("poster", item.posterPath);
                object.put("backdrop", item.backdropPath);
                object.put("genres", item.genres);
                if (item.rating != null) object.put("rating", item.rating);
                if (item.runtime != null) object.put("runtime", item.runtime);
                if (item.streamUri != null) object.put("stream", item.streamUri);
                if (!item.streamFormat.isEmpty()) object.put("format", item.streamFormat);
                if (item.addonName != null) object.put("addon", item.addonName);
                if (item.manifestUrl != null) object.put("manifest", item.manifestUrl);
                JSONArray subtitles = new JSONArray();
                for (SubtitleTrack subtitle : item.subtitles) {
                    JSONObject sub = new JSONObject();
                    sub.put("language", subtitle.language);
                    sub.put("label", subtitle.label);
                    sub.put("url", subtitle.uri);
                    subtitles.put(sub);
                }
                object.put("subtitles", subtitles);
                stored.put(object);
            } catch (Exception exception) {
                showMessage("Unable to save title", "NOIR could not store this title in My List.");
                return;
            }
        }
        preferences.edit().putString("saved_titles", stored.toString()).apply();
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
        io.shutdownNow();
    }

    @Override
    public void onBackPressed() {
        if (!history.isEmpty()) {
            Route route = history.removeLast();
            currentRoute = route.name;
            query = route.query;
            render();
            return;
        }
        if (!"home".equals(currentRoute)) {
            currentRoute = "home";
            render();
            return;
        }
        super.onBackPressed();
    }

    private void push(String route, String search) {
        history.addLast(new Route(currentRoute, query));
        currentRoute = route;
        query = search == null ? "" : search;
        render();
    }

    private void render() {
        if ("player".equals(currentRoute)) {
            renderPlayerScreen();
            return;
        }
        releasePlayer();
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(BG);
        setContentView(page);
        page.addView(buildHeader(), new LinearLayout.LayoutParams(-1, dp(76)));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        switch (currentRoute) {
            case "search": renderSearch(); break;
            case "list": renderMyList(); break;
            case "continue": renderContinueWatching(); break;
            case "providers": renderProviders(); break;
            case "addons": renderAddons(); break;
            case "detail": renderDetail(findTitle(query)); break;
            default: renderHome();
        }
    }

    private View buildHeader() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(58), 0, dp(56), 0);
        bar.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Color.rgb(12, 13, 16), BG}));
        LinearLayout wordmark = new LinearLayout(this);
        wordmark.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("NOIR", 25, TEXT, true);
        brand.setLetterSpacing(.22f);
        wordmark.addView(brand);
        View brandDot = new View(this);
        brandDot.setBackground(rounded(GOLD, 5));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(7), dp(7));
        dotParams.leftMargin = dp(7);
        wordmark.addView(brandDot, dotParams);
        bar.addView(wordmark, new LinearLayout.LayoutParams(dp(138), -2));

        String[][] nav = {{"Home", "home"}, {"Search", "search"}, {"Continue", "continue"},
                {"My List", "list"}, {"Providers", "providers"}, {"Add-ons", "addons"}};
        for (String[] item : nav) {
            boolean selected = currentRoute.equals(item[1]);
            TextView tab = text(item[0], 15, selected ? TEXT : MUTED, selected);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(18), 0, dp(18), 0);
            tab.setFocusable(true);
            tab.setBackground(focusBackground(18, selected ? Color.rgb(34, 37, 39) : Color.TRANSPARENT));
            addFocusAnimation(tab);
            tab.setOnClickListener(v -> {
                history.clear();
                currentRoute = item[1];
                query = "";
                render();
            });
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(-2, dp(43));
            tabParams.rightMargin = dp(3);
            bar.addView(tab, tabParams);
        }
        View flexible = new View(this);
        bar.addView(flexible, new LinearLayout.LayoutParams(0, 1, 1));
        LinearLayout status = new LinearLayout(this);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(13), 0, dp(15), 0);
        status.setBackground(rounded(Color.rgb(23, 25, 27), 18));
        View statusDot = new View(this);
        statusDot.setBackground(rounded(GOLD, 5));
        status.addView(statusDot, new LinearLayout.LayoutParams(dp(7), dp(7)));
        TextView live = text(BuildConfig.TMDB_API_KEY.isEmpty() ? "CURATED EDITION" : "LIVE DISCOVERY",
                10, TEXT, true);
        live.setLetterSpacing(.08f);
        LinearLayout.LayoutParams liveParams = new LinearLayout.LayoutParams(-2, -2);
        liveParams.leftMargin = dp(8);
        status.addView(live, liveParams);
        bar.addView(status, new LinearLayout.LayoutParams(-2, dp(36)));
        return bar;
    }

    private void renderHome() {
        content.setPadding(0, 0, 0, dp(42));
        content.addView(buildHero(featured()), new LinearLayout.LayoutParams(-1, heroHeight()));
        TextView status = text(catalogMessage.isEmpty()
                ? (BuildConfig.TMDB_API_KEY.isEmpty()
                    ? "A hand-picked starter collection · Add your TMDB key for live discovery"
                    : "Connecting to TMDB for live discovery…")
                : catalogMessage, 13, MUTED, false);
        status.setPadding(dp(64), dp(8), dp(64), dp(2));
        content.addView(status);
        addSection("Trending now", trending, "trending");
        addSection("Popular films", popular, "popular");
        addSection("Series to discover", series, "series");
        addSection("Recently released", newTitles, "recent");
        addSection("Animation & anime", animation, "animation");
        addSection("Science fiction & fantasy", filterGenre("sci-fi", "fantasy"), "genre-sci-fi");
        addSection("Drama & mystery", filterGenre("drama", "mystery"), "genre-drama");
        if (!addonTitles.isEmpty()) addSection("Your add-ons", new ArrayList<>(addonTitles.values()), "addons");
        List<Title> saved = savedTitles();
        if (!saved.isEmpty()) addSection("My List", saved, "saved");
        List<Title> continueItems = continueWatchingTitles();
        if (!continueItems.isEmpty()) addSection("Continue watching", continueItems, "continue");
        TextView footer = text("This product uses the TMDB API but is not endorsed or certified by TMDB.", 12, MUTED, false);
        footer.setPadding(dp(64), dp(30), dp(64), dp(18));
        content.addView(footer);
    }

    private int heroHeight() {
        float screenHeightDp = getResources().getDisplayMetrics().heightPixels
                / getResources().getDisplayMetrics().density;
        return dp(Math.max(430, Math.min(560, (int) (screenHeightDp * .58f))));
    }

    private View buildHero(Title item) {
        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(45, 40, 34), Color.rgb(19, 21, 25), BG}));
        ImageView artwork = new ImageView(this);
        artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(artwork, new FrameLayout.LayoutParams(-1, -1));
        loadImage(item.backdropUrl("w1280"), artwork);

        View overlay = new View(this);
        overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF508090B, 0xA908090B, 0x0508090B}));
        hero.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        View bottomShade = new View(this);
        bottomShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{BG, 0x0008090B}));
        hero.addView(bottomShade, new FrameLayout.LayoutParams(-1, -1));

        TextView index = text("01  /  FEATURED", 11, 0xFFE9E9E1, true);
        index.setLetterSpacing(.12f);
        index.setPadding(dp(14), 0, dp(14), 0);
        index.setBackground(rounded(0x55242626, 18));
        FrameLayout.LayoutParams indexParams = new FrameLayout.LayoutParams(-2, dp(34), Gravity.TOP | Gravity.END);
        indexParams.setMargins(0, dp(30), dp(54), 0);
        hero.addView(index, indexParams);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.BOTTOM);
        copy.setPadding(dp(64), dp(20), 0, dp(48));
        FrameLayout.LayoutParams copyParams = new FrameLayout.LayoutParams(dp(790), -1, Gravity.START | Gravity.CENTER_VERTICAL);
        hero.addView(copy, copyParams);
        TextView eyebrow = text("TONIGHT'S PREMIERE", 11, GOLD, true);
        eyebrow.setLetterSpacing(.2f);
        eyebrow.setPadding(dp(12), dp(7), dp(12), dp(7));
        eyebrow.setBackground(rounded(0x3326F65B, 20));
        copy.addView(eyebrow);
        TextView title = text(item.name, 52, TEXT, true);
        title.setLetterSpacing(-.025f);
        title.setMaxLines(2);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(14);
        copy.addView(title, titleParams);
        String line = item.year + (item.genres.isEmpty() ? "" : "    /    " + item.genres)
                + (item.rating == null ? "" : "    /    " + String.format(Locale.US, "TMDB %.1f", item.rating));
        TextView meta = text(line, 15, 0xFFE6E8E3, false);
        meta.setLetterSpacing(.035f);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(11);
        copy.addView(meta, metaParams);
        TextView overview = text(item.overview, 16, 0xFFD0D1CF, false);
        overview.setMaxLines(2);
        LinearLayout.LayoutParams overviewParams = new LinearLayout.LayoutParams(-1, -2);
        overviewParams.topMargin = dp(13);
        copy.addView(overview, overviewParams);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(54));
        actionParams.topMargin = dp(19);
        copy.addView(actions, actionParams);
        actions.addView(actionButton("Explore title    ›", true, () -> openDetail(item)), new LinearLayout.LayoutParams(dp(218), dp(50)));
        View spacer = new View(this);
        actions.addView(spacer, new LinearLayout.LayoutParams(dp(14), 1));
        actions.addView(actionButton(savedIds.contains(item.key()) ? "✓  In My List" : "+  My List", false,
                () -> toggleSaved(item)), new LinearLayout.LayoutParams(dp(174), dp(50)));
        return hero;
    }

    private void addSection(String title, List<Title> items, String tag) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(64), dp(24), dp(48), dp(4));
        TextView heading = text(title, 25, TEXT, true);
        heading.setLetterSpacing(-.015f);
        section.addView(heading);
        if (items == null || items.isEmpty()) {
            TextView empty = text("Nothing here yet. Titles become available when a supported in-app source is connected.", 14, MUTED, false);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(72));
            ep.topMargin = dp(12);
            section.addView(empty, ep);
        } else {
            HorizontalScrollView horizontal = new HorizontalScrollView(this);
            horizontal.setHorizontalScrollBarEnabled(false);
            horizontal.setClipToPadding(false);
            horizontal.setPadding(dp(2), dp(17), dp(24), dp(16));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (Title item : items) row.addView(buildCard(item), new LinearLayout.LayoutParams(dp(184), dp(318)));
            horizontal.addView(row);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, dp(343));
            section.addView(horizontal, hp);
        }
        content.addView(section);
    }

    private View buildCard(Title item) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(dp(3), dp(3), dp(12), dp(4));
        wrapper.setFocusable(true);
        wrapper.setClickable(true);
        wrapper.setBackground(focusBackground(14));
        addFocusAnimation(wrapper);
        wrapper.setOnClickListener(v -> openDetail(item));

        FrameLayout posterFrame = new FrameLayout(this);
        posterFrame.setBackground(posterPlaceholder());
        posterFrame.setClipToOutline(true);
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterFrame.addView(poster, new FrameLayout.LayoutParams(-1, -1));
        loadImage(item.posterUrl("w342"), poster);
        String badgeText = item.rating != null && item.rating > 0
                ? String.format(Locale.US, "★  %.1f", item.rating)
                : (item.type.equals("tv") ? "SERIES" : "FILM");
        TextView badge = text(badgeText, 10, item.rating != null && item.rating > 0 ? TEXT : GOLD, true);
        badge.setGravity(Gravity.CENTER);
        badge.setLetterSpacing(.06f);
        badge.setPadding(dp(9), 0, dp(9), 0);
        badge.setBackground(rounded(0xE51A1C1E, 16));
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, dp(27), Gravity.TOP | Gravity.END);
        badgeParams.setMargins(0, dp(10), dp(9), 0);
        posterFrame.addView(badge, badgeParams);
        wrapper.addView(posterFrame, new LinearLayout.LayoutParams(-1, dp(264)));
        TextView label = text(item.name, 15, TEXT, true);
        label.setMaxLines(1);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(10);
        wrapper.addView(label, lp);
        TextView year = text(item.year + (item.type.equals("tv") ? "    SERIES" : "    FILM"), 10, MUTED, true);
        year.setLetterSpacing(.08f);
        LinearLayout.LayoutParams yp = new LinearLayout.LayoutParams(-1, -2);
        yp.topMargin = dp(3);
        wrapper.addView(year, yp);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(184), dp(318));
        params.rightMargin = dp(16);
        return wrapper;
    }

    private void renderSearch() {
        content.setPadding(dp(64), dp(40), dp(64), dp(46));
        content.addView(text("Find your next story", 32, TEXT, true));
        TextView hint = text(BuildConfig.TMDB_API_KEY.isEmpty()
                ? "Search the starter collection offline, or add a TMDB key for the full catalog."
                : "Search films and series across the live catalog.", 15, MUTED, false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(8);
        content.addView(hint, hintParams);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(query);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setTextSize(19);
        input.setHint("Search titles");
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setPadding(dp(18), 0, dp(18), 0);
        input.setBackground(rounded(PANEL, 14));
        input.setFocusable(true);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(dp(650), dp(58));
        inputParams.topMargin = dp(26);
        content.addView(input, inputParams);
        LinearLayout filters = new LinearLayout(this);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(-1, dp(52));
        filterParams.topMargin = dp(14);
        content.addView(filters, filterParams);
        String[][] options = {{"All", "all"}, {"Films", "movie"}, {"Series", "tv"}};
        for (String[] option : options) {
            TextView filter = actionButton(option[0], searchType.equals(option[1]), () -> {
                searchType = option[1];
                render();
                if (!BuildConfig.TMDB_API_KEY.isEmpty() && query.trim().length() >= 2) {
                    LinearLayout refreshedResults = findSearchResults();
                    if (refreshedResults != null) searchLive(query, refreshedResults);
                }
            });
            LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-2, dp(44));
            fp.rightMargin = dp(10);
            filters.addView(filter, fp);
        }
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams resultParams = new LinearLayout.LayoutParams(-1, -2);
        resultParams.topMargin = dp(20);
        content.addView(results, resultParams);
        renderSearchResults(results, filterStarter(query));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString();
                renderSearchResults(results, filterStarter(query));
                if (!BuildConfig.TMDB_API_KEY.isEmpty() && query.trim().length() >= 2) searchLive(query, results);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                hideKeyboard(input);
                if (!BuildConfig.TMDB_API_KEY.isEmpty()) searchLive(query, results);
                return true;
            }
            return false;
        });
        input.requestFocus();
    }

    private void renderSearchResults(LinearLayout target, List<Title> items) {
        target.removeAllViews();
        if (query.trim().isEmpty()) {
            target.addView(text("Popular searches", 19, TEXT, true));
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, dp(14), 0, 0);
            for (Title item : trending.subList(0, Math.min(4, trending.size()))) {
                TextView suggestion = actionButton(item.name, false, () -> {
                    query = item.name;
                    render();
                });
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(48));
                p.rightMargin = dp(10);
                row.addView(suggestion, p);
            }
            target.addView(row);
        } else if (items.isEmpty()) {
            TextView empty = text(loadingSearch ? "Searching TMDB…" : "No matching starter titles.", 16, MUTED, false);
            empty.setPadding(0, dp(12), 0, 0);
            target.addView(empty);
        } else {
            target.addView(text("Results", 20, TEXT, true));
            for (Title item : items) target.addView(searchResult(item));
        }
    }

    private View searchResult(Title item) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(18), dp(8));
        row.setFocusable(true);
        row.setBackground(focusBackground(13));
        row.setOnClickListener(v -> openDetail(item));
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(posterPlaceholder());
        poster.setClipToOutline(true);
        row.addView(poster, new LinearLayout.LayoutParams(dp(72), dp(104)));
        loadImage(item.posterUrl("w185"), poster);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(18), 0, 0, 0);
        TextView name = text(item.name, 18, TEXT, true);
        copy.addView(name);
        TextView sub = text(item.year + (item.genres.isEmpty() ? "" : "  ·  " + item.genres), 14, MUTED, false);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(8);
        copy.addView(sub, subParams);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text("›", 30, GOLD, false);
        row.addView(arrow);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(760), dp(124));
        params.topMargin = dp(8);
        return row;
    }

    private void searchLive(String term, LinearLayout results) {
        loadingSearch = true;
        renderSearchResults(results, filterStarter(term));
        io.execute(() -> {
            try {
                String encoded = URLEncoder.encode(term.trim(), StandardCharsets.UTF_8.name());
                String selectedType = searchType;
                String endpoint = "movie".equals(selectedType) ? "movie"
                        : "tv".equals(selectedType) ? "tv" : "multi";
                JSONObject response = requestJson("/search/" + endpoint + "?query=" + encoded + "&include_adult=false");
                List<Title> found = parseResults(response.optJSONArray("results"), "all".equals(selectedType));
                runOnUiThread(() -> {
                    if (!"search".equals(currentRoute) || !query.equals(term) || !searchType.equals(selectedType)) return;
                    results.removeAllViews();
                    results.addView(text(found.isEmpty() ? "No TMDB matches." : "Results from TMDB", 20, TEXT, true));
                    for (Title item : found) results.addView(searchResult(item));
                    loadingSearch = false;
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if ("search".equals(currentRoute)) {
                        loadingSearch = false;
                        renderSearchResults(results, filterStarter(term));
                        results.addView(text("Live search failed. Check your connection or TMDB key.", 14, MUTED, false));
                    }
                });
            }
        });
    }

    private void renderMyList() {
        content.setPadding(dp(64), dp(48), dp(64), dp(50));
        content.addView(text("My List", 34, TEXT, true));
        List<Title> items = savedTitles();
        if (items.isEmpty()) {
            TextView empty = text("Your saved titles will live here. Add something from any title page.", 17, MUTED, false);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.topMargin = dp(14);
            content.addView(empty, p);
            content.addView(actionButton("Browse titles", true, () -> {
                currentRoute = "home";
                history.clear();
                render();
            }), new LinearLayout.LayoutParams(dp(200), dp(54)));
        } else {
            addSection("Saved for later", items, "saved");
        }
    }

    private void renderContinueWatching() {
        content.setPadding(dp(64), dp(48), dp(64), dp(50));
        content.addView(text("Continue watching", 34, TEXT, true));
        TextView intro = text("Your in-progress streams are saved on this device.", 15, MUTED, false);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
        ip.topMargin = dp(8);
        content.addView(intro, ip);
        List<Title> items = continueWatchingTitles();
        if (items.isEmpty()) {
            TextView empty = text("Nothing in progress yet. Play an authorized stream from one of your add-ons and it will appear here.", 16, MUTED, false);
            empty.setMaxWidth(dp(800));
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(dp(820), -2);
            ep.topMargin = dp(22);
            content.addView(empty, ep);
            return;
        }
        for (Title item : items) {
            Long savedPosition = continuePositions.get(item.key());
            long position = savedPosition == null ? 0L : savedPosition;
            TextView resume = actionButton("Resume  ·  " + item.name + "  ·  " + formatTime(position),
                    true, () -> startPlayback(item));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(800), dp(58));
            rp.topMargin = dp(18);
            content.addView(resume, rp);
        }
    }

    private void renderProviders() {
        content.setPadding(dp(64), dp(48), dp(64), dp(48));
        content.addView(text("Watch legally", 34, TEXT, true));
        TextView intro = text("Official free/ad-supported catalogs and the official South Park episode site. Availability and language depend on your region; opening a service leaves NOIR.", 16, MUTED, false);
        intro.setMaxWidth(dp(1000));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(1020), -2);
        ip.topMargin = dp(12);
        content.addView(intro, ip);
        addOfficialService("TVP VOD", "Polish public broadcaster · free and paid titles; availability varies",
                "https://vod.tvp.pl/");
        addOfficialService("Rakuten TV Free", "Ad-supported selection · supported titles vary by country",
                "https://www.rakuten.tv/pl/free");
        addOfficialService("Plex", "Free ad-supported movies and series · catalog varies by region",
                "https://watch.plex.tv/");
        addOfficialService("South Park Studios", "Official South Park episodes · English audio; episode access varies by region",
                "https://www.southparkstudios.com/episodes");
        addOfficialService("JustWatch Polska", "Find where a title is legally available in Poland · discovery guide, not a player",
                "https://www.justwatch.com/pl");

        TextView integrations = text("In-app integrations", 22, TEXT, true);
        LinearLayout.LayoutParams integrationParams = new LinearLayout.LayoutParams(-1, -2);
        integrationParams.topMargin = dp(34);
        content.addView(integrations, integrationParams);
        for (ContentProvider provider : providers) {
            ContentProvider.ProviderStatus providerStatus = provider.getStatus();
            TextView status = text(provider.getDisplayName() + "  ·  " + providerStatus.message,
                    14, MUTED, false);
            status.setMaxWidth(dp(950));
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(dp(980), -2);
            statusParams.topMargin = dp(12);
            content.addView(status, statusParams);
        }
        TextView creditsHeading = text("Credits & data", 22, TEXT, true);
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(-1, -2);
        chp.topMargin = dp(38);
        content.addView(creditsHeading, chp);
        TextView tmdb = text("TMDB", 16, GOLD, true);
        LinearLayout.LayoutParams tmdbParams = new LinearLayout.LayoutParams(-1, -2);
        tmdbParams.topMargin = dp(12);
        content.addView(tmdb, tmdbParams);
        WebView tmdbLogo = new WebView(this);
        tmdbLogo.setBackgroundColor(Color.TRANSPARENT);
        tmdbLogo.setFocusable(false);
        tmdbLogo.setFocusableInTouchMode(false);
        tmdbLogo.setOverScrollMode(View.OVER_SCROLL_NEVER);
        tmdbLogo.loadDataWithBaseURL("https://www.themoviedb.org/",
                "<html><body style='margin:0;background:transparent'><img alt='TMDB' width='245' src='https://www.themoviedb.org/assets/v4/logos/v2/blue_long_2-9665a76b1ae401a510ec1e0ca40ddcb3b0cfe45f1d51b77a308fea0845885648.svg'></body></html>",
                "text/html", "UTF-8", null);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(250), dp(48));
        logoParams.topMargin = dp(8);
        content.addView(tmdbLogo, logoParams);
        TextView attribution = text("This product uses the TMDB API but is not endorsed or certified by TMDB. Metadata and artwork are provided by TMDB.", 14, MUTED, false);
        attribution.setMaxWidth(dp(800));
        LinearLayout.LayoutParams attributionParams = new LinearLayout.LayoutParams(dp(820), -2);
        attributionParams.topMargin = dp(8);
        content.addView(attribution, attributionParams);
        TextView guidelines = text("Approved logo and attribution guidelines: themoviedb.org/about/logos-attribution", 13, MUTED, false);
        LinearLayout.LayoutParams guidelinesParams = new LinearLayout.LayoutParams(dp(820), -2);
        guidelinesParams.topMargin = dp(8);
        content.addView(guidelines, guidelinesParams);
    }

    private void addOfficialService(String name, String description, String uri) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(20), dp(14), dp(20), dp(14));
        card.setBackground(rounded(PANEL, 16));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(930), dp(96));
        cp.topMargin = dp(11);
        content.addView(card, cp);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(name, 19, TEXT, true));
        TextView desc = text(description, 13, MUTED, false);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, -2);
        dp.topMargin = dp(7);
        copy.addView(desc, dp);
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView launch = actionButton("Open official service  ↗", false,
                () -> openOfficialService(name, uri));
        card.addView(launch, new LinearLayout.LayoutParams(dp(230), dp(46)));
    }

    private void openOfficialService(String name, String uri) {
        new AlertDialog.Builder(this)
                .setTitle("Leave NOIR?")
                .setMessage(name + " opens its official app or website outside NOIR. Catalog, account requirements, and availability are controlled by that service.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Continue", (dialog, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
                    } catch (Exception exception) {
                        showMessage("Service unavailable", "Fire TV could not open this service. Install its official app or use a supported browser.");
                    }
                })
                .show();
    }

    private void renderAddons() {
        content.setPadding(dp(64), dp(44), dp(64), dp(52));
        content.addView(text("Add-ons", 34, TEXT, true));
        TextView summary = text("Add a JSON manifest for streams you own or are authorized to watch. NOIR does not execute add-on code, scrape websites, or store provider passwords.", 15, MUTED, false);
        summary.setMaxWidth(dp(900));
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(dp(920), -2);
        summaryParams.topMargin = dp(10);
        content.addView(summary, summaryParams);

        EditText manifestUrl = new EditText(this);
        configureInput(manifestUrl, "HTTPS manifest URL");
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(dp(820), dp(56));
        inputParams.topMargin = dp(22);
        content.addView(manifestUrl, inputParams);
        TextView add = actionButton("Add and validate manifest", true,
                () -> addAddonManifest(manifestUrl.getText().toString().trim()));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(280), dp(50));
        addParams.topMargin = dp(12);
        content.addView(add, addParams);

        TextView rule = text("Accepted streams: direct HTTP(S) HLS (.m3u8), DASH (.mpd), or MP4/WebM media. Subtitles: WebVTT (.vtt) or SubRip (.srt). No torrent/magnet sources or credential-bearing URLs. HTTPS is recommended.", 14, MUTED, false);
        rule.setMaxWidth(dp(900));
        LinearLayout.LayoutParams ruleParams = new LinearLayout.LayoutParams(dp(920), -2);
        ruleParams.topMargin = dp(18);
        content.addView(rule, ruleParams);

        if (!addonMessage.isEmpty()) {
            TextView message = text(addonMessage, 14, addonMessage.startsWith("Added") ? GOLD : 0xFFE4A395, false);
            LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(dp(900), -2);
            messageParams.topMargin = dp(14);
            content.addView(message, messageParams);
        }
        if (!addonManifestUrls.isEmpty()) {
            TextView installed = text("Installed manifests", 20, TEXT, true);
            LinearLayout.LayoutParams installedParams = new LinearLayout.LayoutParams(-1, -2);
            installedParams.topMargin = dp(28);
            content.addView(installed, installedParams);
            for (String url : addonManifestUrls) {
                TextView entry = actionButton(url, false, () -> new AlertDialog.Builder(this)
                        .setTitle("Remove add-on?")
                        .setMessage("Remove this manifest and its cached catalog?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove", (dialog, which) -> removeAddonManifest(url))
                        .show());
                LinearLayout.LayoutParams entryParams = new LinearLayout.LayoutParams(dp(850), dp(48));
                entryParams.topMargin = dp(8);
                content.addView(entry, entryParams);
            }
        }
        if (!addonTitles.isEmpty()) {
            addSection("Available from your add-ons", new ArrayList<>(addonTitles.values()), "addon-titles");
        }
    }

    private void configureInput(EditText input, String hint) {
        input.setSingleLine(true);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setTextSize(16);
        input.setHint(hint);
        input.setPadding(dp(17), 0, dp(17), 0);
        input.setBackground(rounded(PANEL, 13));
        input.setFocusable(true);
    }

    private void renderPlayerScreen() {
        Title item = findTitle(query);
        if (item == null || !isValidMediaSource(item.streamUri, item.streamFormat)) {
            showMessage("Playback unavailable", "This item does not contain a valid, authorized HTTP(S) media URL.");
            currentRoute = history.isEmpty() ? "home" : history.removeLast().name;
            render();
            return;
        }
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.BLACK);
        setContentView(screen);
        playerView = new PlayerView(this);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setControllerShowTimeoutMs(5000);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setKeepScreenOn(true);
        screen.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(28), dp(22), dp(28), dp(12));
        top.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xDD07080A, 0x0007080A}));
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(-1, dp(92), Gravity.TOP);
        screen.addView(top, topParams);
        TextView back = actionButton("‹  Back", false, this::onBackPressed);
        top.addView(back, new LinearLayout.LayoutParams(dp(120), dp(44)));
        TextView name = text(item.name, 18, TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
        nameParams.leftMargin = dp(18);
        top.addView(name, nameParams);
        TextView source = text(item.addonName == null ? "AUTHORIZED STREAM" : item.addonName.toUpperCase(Locale.ROOT),
                10, GOLD, true);
        source.setLetterSpacing(.08f);
        top.addView(source, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout tracks = new LinearLayout(this);
        tracks.setPadding(0, 0, dp(34), dp(34));
        FrameLayout.LayoutParams tracksParams = new FrameLayout.LayoutParams(-2, dp(58),
                Gravity.BOTTOM | Gravity.END);
        screen.addView(tracks, tracksParams);
        TextView captions = actionButton("CC  Captions", false, () -> showTrackPicker(C.TRACK_TYPE_TEXT));
        tracks.addView(captions, new LinearLayout.LayoutParams(dp(176), dp(46)));
        View gap = new View(this);
        tracks.addView(gap, new LinearLayout.LayoutParams(dp(9), 1));
        TextView audio = actionButton("♫  Audio", false, () -> showTrackPicker(C.TRACK_TYPE_AUDIO));
        tracks.addView(audio, new LinearLayout.LayoutParams(dp(148), dp(46)));

        playingTitle = item;
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    continuePositions.remove(item.key());
                    persistContinuePositions();
                    showMessage("Finished", "Playback reached the end of this stream.");
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                String message = "The stream could not be played. Verify that its HTTP(S) URL points directly to a supported video and is reachable on this device.";
                showMessage("Playback error", message);
            }
        });
        Long savedPosition = continuePositions.get(item.key());
        long resumePosition = savedPosition == null ? 0L : savedPosition;
        MediaItem mediaItem = buildMediaItem(item);
        if (resumePosition > 0) player.setMediaItem(mediaItem, resumePosition);
        else player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
        playbackHandler.removeCallbacks(playbackProgressSaver);
        playbackHandler.postDelayed(playbackProgressSaver, 5000);
        back.requestFocus();
    }

    private MediaItem buildMediaItem(Title item) {
        MediaItem.Builder builder = new MediaItem.Builder()
                .setUri(item.streamUri)
                .setMediaId(item.key())
                .setMediaMetadata(new androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(item.name).build());
        String mimeType = streamMimeType(item.streamUri, item.streamFormat);
        if (!mimeType.isEmpty()) builder.setMimeType(mimeType);
        List<MediaItem.SubtitleConfiguration> subtitles = new ArrayList<>();
        for (SubtitleTrack subtitle : item.subtitles) {
            String path = URI.create(subtitle.uri).getPath().toLowerCase(Locale.ROOT);
            String subtitleMime = path.endsWith(".vtt") ? MimeTypes.TEXT_VTT : MimeTypes.APPLICATION_SUBRIP;
            subtitles.add(new MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.uri))
                    .setMimeType(subtitleMime)
                    .setLanguage(subtitle.language)
                    .setLabel(subtitle.label)
                    .setRoleFlags(C.ROLE_FLAG_SUBTITLE)
                    .build());
        }
        if (!subtitles.isEmpty()) builder.setSubtitleConfigurations(subtitles);
        return builder.build();
    }

    private String streamMimeType(String address, String format) {
        if ("hls".equals(format)) return MimeTypes.APPLICATION_M3U8;
        if ("dash".equals(format)) return MimeTypes.APPLICATION_MPD;
        if ("progressive".equals(format)) return "";
        String path = URI.create(address).getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".m3u8")) return MimeTypes.APPLICATION_M3U8;
        if (path.endsWith(".mpd")) return MimeTypes.APPLICATION_MPD;
        if (path.endsWith(".webm")) return MimeTypes.VIDEO_WEBM;
        if (path.endsWith(".mp4") || path.endsWith(".m4v")) return MimeTypes.VIDEO_MP4;
        if (path.endsWith(".mkv")) return MimeTypes.VIDEO_MATROSKA;
        return "";
    }

    private void showTrackPicker(int trackType) {
        if (player == null) return;
        List<TrackChoice> choices = new ArrayList<>();
        if (trackType == C.TRACK_TYPE_TEXT) choices.add(new TrackChoice(null, -1, "Off"));
        else choices.add(new TrackChoice(null, -1, "Auto"));
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != trackType) continue;
            for (int index = 0; index < group.length; index++) {
                if (!group.isTrackSupported(index)) continue;
                androidx.media3.common.Format format = group.getTrackFormat(index);
                String language = format.language == null || "und".equals(format.language) ? "" : format.language;
                String label = format.label == null ? "" : format.label;
                String display = label.isEmpty() ? language : (language.isEmpty() ? label : label + "  ·  " + language);
                if (display.isEmpty()) display = trackType == C.TRACK_TYPE_TEXT ? "Subtitle track" : "Audio track";
                choices.add(new TrackChoice(group, index, display));
            }
        }
        String[] labels = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) labels[i] = choices.get(i).label;
        new AlertDialog.Builder(this)
                .setTitle(trackType == C.TRACK_TYPE_TEXT ? "Captions & subtitles" : "Audio language")
                .setItems(labels, (dialog, which) -> selectTrack(trackType, choices.get(which)))
                .setNegativeButton("Close", null)
                .show();
    }

    private void selectTrack(int trackType, TrackChoice choice) {
        if (player == null) return;
        androidx.media3.common.TrackSelectionParameters.Builder selection =
                player.getTrackSelectionParameters().buildUpon();
        selection.clearOverridesOfType(trackType);
        selection.setTrackTypeDisabled(trackType, choice.group == null && trackType == C.TRACK_TYPE_TEXT);
        if (choice.group != null) {
            selection.setTrackTypeDisabled(trackType, false);
            selection.addOverride(new TrackSelectionOverride(
                    choice.group.getMediaTrackGroup(), Collections.singletonList(choice.index)));
        }
        player.setTrackSelectionParameters(selection.build());
    }

    private void startPlayback(Title item) {
        if (!isValidMediaSource(item.streamUri, item.streamFormat)) {
            showMessage("Playback unavailable", "This add-on entry has no valid direct HTTP(S) video source.");
            return;
        }
        knownTitles.put(item.key(), item);
        push("player", item.key());
    }

    private void savePlaybackProgress() {
        if (player == null || playingTitle == null || !isValidMediaSource(playingTitle.streamUri, playingTitle.streamFormat)) return;
        long position = player.getCurrentPosition();
        long duration = player.getDuration();
        if (position >= 10000 && (duration <= 0 || position < duration - 15000)) {
            continuePositions.put(playingTitle.key(), position);
        } else if (duration > 0 && position >= duration - 15000) {
            continuePositions.remove(playingTitle.key());
        }
        persistContinuePositions();
    }

    private void persistContinuePositions() {
        JSONArray entries = new JSONArray();
        for (Map.Entry<String, Long> entry : continuePositions.entrySet()) {
            Title item = knownTitles.get(entry.getKey());
            if (item == null || !isValidMediaSource(item.streamUri, item.streamFormat)) continue;
            JSONObject object = titleToJson(item);
            try {
                object.put("position", entry.getValue());
                entries.put(object);
            } catch (Exception ignored) {
                // Invalid progress entries are skipped; valid entries remain available.
            }
        }
        preferences.edit().putString("continue_items", entries.toString()).apply();
    }

    private JSONObject titleToJson(Title item) {
        JSONObject object = new JSONObject();
        try {
            object.put("id", item.id);
            object.put("type", item.type);
            object.put("name", item.name);
            object.put("year", item.year);
            object.put("overview", item.overview);
            object.put("poster", item.posterPath);
            object.put("backdrop", item.backdropPath);
            object.put("genres", item.genres);
            object.put("stream", item.streamUri);
            object.put("format", item.streamFormat);
            object.put("addon", item.addonName);
            object.put("manifest", item.manifestUrl);
            JSONArray subtitles = new JSONArray();
            for (SubtitleTrack subtitle : item.subtitles) {
                JSONObject track = new JSONObject();
                track.put("language", subtitle.language);
                track.put("label", subtitle.label);
                track.put("url", subtitle.uri);
                subtitles.put(track);
            }
            object.put("subtitles", subtitles);
        } catch (Exception ignored) {
            // The item is validated before persistence.
        }
        return object;
    }

    private List<Title> continueWatchingTitles() {
        List<Title> titles = new ArrayList<>();
        for (String key : continuePositions.keySet()) {
            Title item = knownTitles.get(key);
            if (item != null && isValidMediaSource(item.streamUri, item.streamFormat)) titles.add(item);
        }
        return titles;
    }

    private String formatTime(long milliseconds) {
        long totalSeconds = Math.max(0, milliseconds / 1000);
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return hours > 0
                ? String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    private void releasePlayer() {
        if (playbackHandler != null) playbackHandler.removeCallbacks(playbackProgressSaver);
        if (player != null) {
            savePlaybackProgress();
            playerView = null;
            player.release();
            player = null;
        }
        playingTitle = null;
    }

    private void renderDetail(Title item) {
        if (item == null) {
            content.setPadding(dp(64), dp(40), dp(64), dp(40));
            content.addView(text("Title not found", 26, TEXT, true));
            return;
        }
        content.setPadding(0, 0, 0, dp(42));
        FrameLayout hero = new FrameLayout(this);
        hero.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(41, 37, 32), BG}));
        ImageView backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        loadImage(item.backdropUrl("w1280"), backdrop);
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF508090B, 0xBE08090B, 0x2608090B}));
        hero.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setPadding(dp(64), dp(30), dp(60), dp(30));
        hero.addView(body, new FrameLayout.LayoutParams(-1, -1));
        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.setBackground(posterPlaceholder());
        poster.setClipToOutline(true);
        body.addView(poster, new LinearLayout.LayoutParams(dp(230), dp(340)));
        loadImage(item.posterUrl("w500"), poster);
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(38), 0, 0, 0);
        body.addView(copy, new LinearLayout.LayoutParams(dp(760), -2));
        copy.addView(text(item.type.equals("tv") ? "SERIES" : "FILM", 12, GOLD, true));
        TextView title = text(item.name, 38, TEXT, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(10);
        copy.addView(title, tp);
        String metaText = item.year + (item.genres.isEmpty() ? "" : "   ·   " + item.genres)
                + (item.rating == null ? "" : "   ·   " + String.format(Locale.US, "TMDB %.1f", item.rating))
                + (item.runtime == null ? "" : "   ·   " + item.runtime + " min");
        TextView meta = text(metaText, 15, 0xFFE1DED7, false);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.topMargin = dp(12);
        copy.addView(meta, mp);
        TextView overview = text(item.overview.isEmpty() ? "Synopsis unavailable in the starter catalog." : item.overview,
                16, 0xFFD0D0CE, false);
        overview.setMaxLines(4);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(-1, -2);
        op.topMargin = dp(14);
        copy.addView(overview, op);
        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(54));
        bp.topMargin = dp(20);
        copy.addView(buttons, bp);
        if (isValidMediaSource(item.streamUri, item.streamFormat)) {
            buttons.addView(actionButton("Play in NOIR   ▶", true, () -> startPlayback(item)),
                    new LinearLayout.LayoutParams(dp(220), dp(50)));
        } else {
            TextView unavailable = text("NO IN-APP SOURCE", 12, MUTED, true);
            unavailable.setLetterSpacing(.08f);
            unavailable.setPadding(dp(12), 0, dp(12), 0);
            buttons.addView(unavailable, new LinearLayout.LayoutParams(dp(220), dp(52)));
        }
        View gap = new View(this);
        buttons.addView(gap, new LinearLayout.LayoutParams(dp(12), 1));
        buttons.addView(actionButton(savedIds.contains(item.key()) ? "✓  In My List" : "+  My List", true,
                () -> toggleSaved(item)), new LinearLayout.LayoutParams(dp(180), dp(52)));
        content.addView(hero, new LinearLayout.LayoutParams(-1, dp(475)));

        if (item.type.equals("tv") && item.id > 0 && !BuildConfig.TMDB_API_KEY.isEmpty()
                && seasonsRequested.add(item.key())) {
            loadSeasons(item);
        }
        if (item.id > 0 && !BuildConfig.TMDB_API_KEY.isEmpty() && detailsRequested.add(item.key())) loadDetails(item);
        if (detailFailures.contains(item.key())) {
            TextView error = text("Live title details could not be loaded. The available starter information is shown.", 14, MUTED, false);
            error.setPadding(dp(64), dp(12), dp(64), dp(6));
            content.addView(error);
        }
        if (!isValidMediaSource(item.streamUri, item.streamFormat)) addProviderAvailability(item);
        if (!item.similar.isEmpty()) addSection("More like this", item.similar, "similar");
    }

    private void addProviderAvailability(Title item) {
        TextView note = text("Playback is not available for this title until a supported provider supplies an in-app source.", 14, MUTED, false);
        note.setPadding(dp(64), dp(12), dp(64), dp(4));
        content.addView(note);
    }

    private void loadDetails(Title item) {
        io.execute(() -> {
            try {
                String kind = item.type.equals("tv") ? "tv" : "movie";
                JSONObject details = requestJson("/" + kind + "/" + item.id);
                JSONObject related = requestJson("/" + kind + "/" + item.id + "/similar");
                item.mergeDetails(details);
                item.similar = parseResults(related.optJSONArray("results"), false);
                runOnUiThread(() -> {
                    if ("detail".equals(currentRoute) && query.equals(item.key())) render();
                });
            } catch (Exception ignored) {
                runOnUiThread(() -> {
                    detailFailures.add(item.key());
                    if ("detail".equals(currentRoute) && query.equals(item.key())) render();
                });
            }
        });
    }

    private void loadSeasons(Title item) {
        io.execute(() -> {
            try {
                JSONObject show = requestJson("/tv/" + item.id);
                JSONArray seasons = show.optJSONArray("seasons");
                List<Integer> seasonNumbers = new ArrayList<>();
                List<String> seasonNames = new ArrayList<>();
                if (seasons != null) for (int i = 0; i < seasons.length(); i++) {
                    JSONObject season = seasons.optJSONObject(i);
                    if (season == null || season.optInt("season_number", 0) == 0) continue;
                    seasonNumbers.add(season.optInt("season_number"));
                    seasonNames.add(season.optString("name", "Season " + season.optInt("season_number")));
                }
                runOnUiThread(() -> {
                    if (!"detail".equals(currentRoute) || !query.equals(item.key())) return;
                    LinearLayout section = new LinearLayout(this);
                    section.setOrientation(LinearLayout.VERTICAL);
                    section.setPadding(dp(64), dp(24), dp(48), dp(10));
                    section.addView(text("Seasons & episodes", 23, TEXT, true));
                    if (seasonNumbers.isEmpty()) {
                        TextView unavailable = text("No season information is available for this series.", 14, MUTED, false);
                        LinearLayout.LayoutParams up = new LinearLayout.LayoutParams(-1, dp(50));
                        up.topMargin = dp(10);
                        section.addView(unavailable, up);
                        content.addView(section);
                        return;
                    }
                    LinearLayout choices = new LinearLayout(this);
                    choices.setPadding(0, dp(14), 0, dp(8));
                    HorizontalScrollView seasonScroll = new HorizontalScrollView(this);
                    seasonScroll.setHorizontalScrollBarEnabled(false);
                    seasonScroll.addView(choices);
                    section.addView(seasonScroll);
                    for (int index = 0; index < seasonNumbers.size(); index++) {
                        int number = seasonNumbers.get(index);
                        String label = seasonNames.get(index);
                        TextView choice = actionButton(label, index == 0, () -> loadEpisodes(item, number, section));
                        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(48));
                        p.rightMargin = dp(10);
                        choices.addView(choice, p);
                    }
                    content.addView(section);
                    loadEpisodes(item, seasonNumbers.get(0), section);
                });
            } catch (Exception ignored) {
                runOnUiThread(() -> {
                    if (!"detail".equals(currentRoute) || !query.equals(item.key())) return;
                    TextView error = text("Episodes could not be loaded. Check the TMDB connection and try again.", 14, MUTED, false);
                    error.setPadding(dp(64), dp(14), dp(64), dp(8));
                    content.addView(error);
                });
            }
        });
    }

    private void loadEpisodes(Title item, int season, LinearLayout section) {
        io.execute(() -> {
            try {
                JSONObject response = requestJson("/tv/" + item.id + "/season/" + season);
                JSONArray episodes = response.optJSONArray("episodes");
                List<JSONObject> values = new ArrayList<>();
                if (episodes != null) for (int i = 0; i < episodes.length(); i++) {
                    JSONObject episode = episodes.optJSONObject(i);
                    if (episode != null) values.add(episode);
                }
                runOnUiThread(() -> {
                    if (!"detail".equals(currentRoute) || !query.equals(item.key())) return;
                    while (section.getChildCount() > 2) section.removeViewAt(2);
                    if (values.isEmpty()) {
                        TextView empty = text("No episode entries are listed for this season.", 14, MUTED, false);
                        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(50));
                        ep.topMargin = dp(8);
                        section.addView(empty, ep);
                    }
                    for (JSONObject episode : values) {
                        int number = episode.optInt("episode_number");
                        String watchedKey = "watched_" + item.id + "_" + season + "_" + number;
                        boolean watched = preferences.getBoolean(watchedKey, false);
                        String heading = String.format(Locale.US, "S%02d E%02d   %s",
                                season, number, episode.optString("name", "Episode"));
                        LinearLayout episodeCard = new LinearLayout(this);
                        episodeCard.setOrientation(LinearLayout.VERTICAL);
                        episodeCard.setPadding(dp(15), dp(10), dp(15), dp(10));
                        episodeCard.setBackground(rounded(PANEL, 12));
                        LinearLayout topRow = new LinearLayout(this);
                        topRow.setGravity(Gravity.CENTER_VERTICAL);
                        TextView row = text(heading, 16, TEXT, true);
                        topRow.addView(row, new LinearLayout.LayoutParams(0, dp(36), 1));
                        TextView markWatched = actionButton(watched ? "✓  Watched" : "Mark watched", false, () -> {
                            preferences.edit().putBoolean(watchedKey, !preferences.getBoolean(watchedKey, false)).apply();
                            loadEpisodes(item, season, section);
                        });
                        topRow.addView(markWatched, new LinearLayout.LayoutParams(dp(150), dp(38)));
                        episodeCard.addView(topRow);
                        String overview = episode.optString("overview", "");
                        if (!overview.isEmpty()) {
                            TextView description = text(overview, 13, MUTED, false);
                            description.setMaxLines(3);
                            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
                            descriptionParams.topMargin = dp(5);
                            episodeCard.addView(description, descriptionParams);
                        }
                        String airDate = episode.optString("air_date", "");
                        if (!airDate.isEmpty()) {
                            TextView date = text(airDate, 11, MUTED, false);
                            LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-1, -2);
                            dateParams.topMargin = dp(5);
                            episodeCard.addView(date, dateParams);
                        }
                        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(dp(820), -2);
                        rp.topMargin = dp(7);
                        section.addView(episodeCard, rp);
                    }
                });
            } catch (Exception exception) {
                runOnUiThread(() -> showMessage("Episodes unavailable", "TMDB could not load this season. Check the connection and try again."));
            }
        });
    }

    private void loadLiveCatalog() {
        if (BuildConfig.TMDB_API_KEY.isEmpty()) return;
        io.execute(() -> {
            try {
                JSONObject trendingResponse = requestJson("/trending/movie/week");
                JSONObject popularResponse = requestJson("/movie/popular");
                JSONObject seriesResponse = requestJson("/tv/popular");
                JSONObject recentResponse = requestJson("/movie/now_playing");
                JSONObject animationResponse = requestJson("/discover/movie?with_genres=16&sort_by=popularity.desc");
                List<Title> liveTrending = parseResults(trendingResponse.optJSONArray("results"), false);
                List<Title> livePopular = parseResults(popularResponse.optJSONArray("results"), false);
                List<Title> liveSeries = parseResults(seriesResponse.optJSONArray("results"), false);
                List<Title> liveRecent = parseResults(recentResponse.optJSONArray("results"), false);
                List<Title> liveAnimation = parseResults(animationResponse.optJSONArray("results"), false);
                runOnUiThread(() -> {
                    trending = nonEmpty(liveTrending, trending);
                    popular = nonEmpty(livePopular, popular);
                    series = nonEmpty(liveSeries, series);
                    newTitles = nonEmpty(liveRecent, newTitles);
                    animation = nonEmpty(liveAnimation, animation);
                    catalogMessage = "Live catalog · powered by TMDB";
                    if ("home".equals(currentRoute)) render();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    catalogMessage = "Live catalog unavailable · showing the starter collection";
                    if ("home".equals(currentRoute)) render();
                });
            }
        });
    }

    private JSONObject requestJson(String path) throws Exception {
        String separator = path.contains("?") ? "&" : "?";
        URL url = new URL("https://api.themoviedb.org/3" + path + separator + "api_key="
                + URLEncoder.encode(BuildConfig.TMDB_API_KEY, StandardCharsets.UTF_8.name()));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            if (status < 200 || status >= 300) throw new IllegalStateException("TMDB returned HTTP " + status);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = stream.read(buffer)) != -1) bytes.write(buffer, 0, count);
            stream.close();
            return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
        } finally {
            connection.disconnect();
        }
    }

    private List<Title> parseResults(JSONArray results, boolean mixed) {
        List<Title> output = new ArrayList<>();
        if (results == null) return output;
        for (int i = 0; i < results.length(); i++) {
            JSONObject object = results.optJSONObject(i);
            if (object == null) continue;
            String type = mixed ? object.optString("media_type", "") : "";
            if (type.isEmpty() || "movie".equals(type)) {
                if (object.has("title") || !mixed) type = "movie";
                else continue;
            } else if (!"tv".equals(type)) continue;
            String name = object.optString(type.equals("tv") ? "name" : "title", "");
            if (name.isEmpty()) continue;
            String date = object.optString(type.equals("tv") ? "first_air_date" : "release_date", "");
            String year = date.length() >= 4 ? date.substring(0, 4) : "—";
            String poster = object.optString("poster_path", "");
            if (poster.isEmpty()) continue;
            List<String> genres = genreNames(object.optJSONArray("genre_ids"));
            Double rating = object.has("vote_average") ? object.optDouble("vote_average", 0) : 0;
            Title item = new Title(object.optLong("id"), type, name, year,
                    object.optString("overview", ""), poster, object.optString("backdrop_path", ""),
                    join(genres, " / "), rating > 0 ? rating : null, null);
            output.add(item);
            knownTitles.put(item.key(), item);
        }
        return output;
    }

    private List<String> genreNames(JSONArray ids) {
        Map<Integer, String> names = new HashMap<>();
        names.put(28, "Action"); names.put(12, "Adventure"); names.put(16, "Animation"); names.put(35, "Comedy");
        names.put(80, "Crime"); names.put(99, "Documentary"); names.put(18, "Drama"); names.put(10751, "Family");
        names.put(14, "Fantasy"); names.put(36, "History"); names.put(27, "Horror"); names.put(10402, "Music");
        names.put(9648, "Mystery"); names.put(10749, "Romance"); names.put(878, "Sci-Fi"); names.put(10770, "TV Movie");
        names.put(53, "Thriller"); names.put(10752, "War"); names.put(37, "Western"); names.put(10759, "Action & Adventure");
        names.put(10762, "Kids"); names.put(10763, "News"); names.put(10764, "Reality"); names.put(10765, "Sci-Fi & Fantasy");
        names.put(10766, "Soap"); names.put(10767, "Talk"); names.put(10768, "War & Politics");
        List<String> output = new ArrayList<>();
        if (ids != null) for (int i = 0; i < Math.min(ids.length(), 3); i++) {
            String name = names.get(ids.optInt(i));
            if (name != null) output.add(name);
        }
        return output;
    }

    private void loadImage(String url, ImageView target) {
        if (url == null || url.isEmpty()) return;
        target.setTag(url);
        Bitmap cached = imageCache.get(url);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        io.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(true);
                try (InputStream stream = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(stream);
                    if (bitmap == null) return;
                    imageCache.put(url, bitmap);
                    runOnUiThread(() -> {
                        if (url.equals(target.getTag())) target.setImageBitmap(bitmap);
                    });
                }
            } catch (Exception ignored) {
                // Keep the polished poster placeholder if artwork is temporarily unavailable.
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private List<Title> selectType(boolean tv) {
        List<Title> result = new ArrayList<>();
        for (Title item : starter) if (item.type.equals(tv ? "tv" : "movie")) result.add(item);
        return result;
    }

    private List<Title> selectAnimation() {
        List<Title> output = new ArrayList<>();
        for (Title item : starter) if (item.genres.toLowerCase(Locale.ROOT).contains("animation")
                || item.genres.toLowerCase(Locale.ROOT).contains("anime")) output.add(item);
        return output;
    }

    private List<Title> nonEmpty(List<Title> preferred, List<Title> fallback) {
        return preferred == null || preferred.isEmpty() ? fallback : preferred;
    }

    private Title featured() {
        for (Title item : trending) {
            if (item.backdropPath != null && !item.backdropPath.isEmpty()
                    && !item.backdropPath.equals(item.posterPath)) return item;
        }
        return starter.get(0);
    }

    private List<Title> filterStarter(String term) {
        return filterStarter(term, searchType);
    }

    private List<Title> filterStarter(String term, String type) {
        List<Title> found = new ArrayList<>();
        String lower = term.trim().toLowerCase(Locale.ROOT);
        if (lower.isEmpty()) return found;
        for (Title item : starter) {
            if ((type.equals("all") || item.type.equals(type))
                    && (item.name.toLowerCase(Locale.ROOT).contains(lower) || item.genres.toLowerCase(Locale.ROOT).contains(lower)))
                found.add(item);
        }
        return found;
    }

    private List<Title> filterGenre(String... terms) {
        List<Title> matches = new ArrayList<>();
        for (Title item : allTitlesWithoutSaved()) {
            String genres = item.genres.toLowerCase(Locale.ROOT);
            for (String term : terms) if (genres.contains(term)) {
                matches.add(item);
                break;
            }
        }
        return matches;
    }

    private Title findTitle(String key) {
        Title known = knownTitles.get(key);
        if (known != null) return known;
        for (Title item : allTitles()) {
            knownTitles.put(item.key(), item);
            if (item.key().equals(key)) return item;
        }
        return null;
    }

    private List<Title> allTitles() {
        List<Title> all = new ArrayList<>();
        all.addAll(trending); all.addAll(popular); all.addAll(series); all.addAll(newTitles); all.addAll(animation);
        all.addAll(starter); all.addAll(savedTitles());
        Map<String, Title> distinct = new HashMap<>();
        for (Title item : all) distinct.put(item.key(), item);
        return new ArrayList<>(distinct.values());
    }

    private List<Title> savedTitles() {
        return new ArrayList<>(savedCatalog.values());
    }

    private List<Title> allTitlesWithoutSaved() {
        List<Title> all = new ArrayList<>();
        if (trending != null) all.addAll(trending);
        if (popular != null) all.addAll(popular);
        if (series != null) all.addAll(series);
        if (newTitles != null) all.addAll(newTitles);
        if (animation != null) all.addAll(animation);
        all.addAll(starter);
        Map<String, Title> distinct = new HashMap<>();
        for (Title item : all) distinct.put(item.key(), item);
        return new ArrayList<>(distinct.values());
    }

    private void toggleSaved(Title item) {
        if (!savedIds.add(item.key())) {
            savedIds.remove(item.key());
            savedCatalog.remove(item.key());
        } else {
            savedCatalog.put(item.key(), item);
        }
        persistSavedTitles();
        render();
    }

    private void openDetail(Title item) {
        knownTitles.put(item.key(), item);
        push("detail", item.key());
    }

    private void hideKeyboard(View view) {
        InputMethodManager manager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showMessage(String title, String message) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setPositiveButton("OK", null).show();
    }

    private TextView actionButton(String label, boolean primary, Runnable action) {
        TextView button = text(label, 15, primary ? BG : TEXT, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(16), 0, dp(16), 0);
        button.setFocusable(true);
        button.setClickable(true);
        button.setBackground(focusBackground(12, primary ? GOLD : PANEL));
        addFocusAnimation(button);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        return view;
    }

    private GradientDrawable posterPlaceholder() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(45, 46, 49), Color.rgb(23, 25, 29), Color.rgb(15, 17, 20)});
        drawable.setCornerRadius(dp(12));
        return drawable;
    }

    private android.graphics.drawable.Drawable focusBackground(int radius) {
        return focusBackground(radius, PANEL);
    }

    private android.graphics.drawable.Drawable focusBackground(int radius, int color) {
        android.graphics.drawable.StateListDrawable states = new android.graphics.drawable.StateListDrawable();
        GradientDrawable focused = rounded(color, radius);
        focused.setStroke(dp(2), color == GOLD ? TEXT : GOLD);
        states.addState(new int[]{android.R.attr.state_focused}, focused);
        states.addState(new int[]{android.R.attr.state_pressed}, focused);
        states.addState(new int[]{}, rounded(color, radius));
        return states;
    }

    private void addFocusAnimation(View view) {
        view.setOnFocusChangeListener((focusedView, focused) -> {
            focusedView.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f)
                    .setDuration(130).start();
            focusedView.setElevation(focused ? dp(10) : 0);
        });
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private static final class Route {
        final String name;
        final String query;
        Route(String name, String query) { this.name = name; this.query = query; }
    }

    private static final class TrackChoice {
        final Tracks.Group group;
        final int index;
        final String label;
        TrackChoice(Tracks.Group group, int index, String label) {
            this.group = group;
            this.index = index;
            this.label = label;
        }
    }

    private static final class SubtitleTrack {
        final String language;
        final String label;
        final String uri;
        SubtitleTrack(String language, String label, String uri) {
            this.language = language;
            this.label = label;
            this.uri = uri;
        }
    }

    static final class Title {
        final long id;
        final String type, name, year, overview, posterPath, backdropPath;
        String genres;
        Double rating;
        Integer runtime;
        String streamUri;
        String streamFormat = "";
        String addonName;
        String manifestUrl;
        List<SubtitleTrack> subtitles = new ArrayList<>();
        List<Title> similar = Collections.emptyList();

        Title(long id, String type, String name, String year, String overview, String posterPath,
              String backdropPath, String genres, Double rating, Integer runtime) {
            this.id = id; this.type = type; this.name = name; this.year = year; this.overview = overview;
            this.posterPath = posterPath; this.backdropPath = backdropPath; this.genres = genres;
            this.rating = rating; this.runtime = runtime;
        }
        String key() { return type + ":" + id + ":" + name; }
        String posterUrl(String size) { return posterPath.startsWith("http") ? posterPath : TMDB_IMAGE + size + posterPath; }
        String backdropUrl(String size) {
            String path = backdropPath == null || backdropPath.isEmpty() ? posterPath : backdropPath;
            return path.startsWith("http") ? path : TMDB_IMAGE + size + path;
        }
        void mergeDetails(JSONObject details) {
            JSONArray genreArray = details.optJSONArray("genres");
            if (genreArray != null) {
                List<String> names = new ArrayList<>();
                for (int i = 0; i < genreArray.length(); i++) {
                    JSONObject genre = genreArray.optJSONObject(i);
                    if (genre != null) names.add(genre.optString("name"));
                }
                if (!names.isEmpty()) genres = join(names, " / ");
            }
            int minutes = details.optInt(type.equals("tv") ? "episode_run_time" : "runtime", 0);
            if (minutes == 0 && type.equals("tv")) {
                JSONArray runTimes = details.optJSONArray("episode_run_time");
                if (runTimes != null && runTimes.length() > 0) minutes = runTimes.optInt(0);
            }
            if (minutes > 0) runtime = minutes;
        }
    }

    private static String join(List<String> values, String separator) {
        StringBuilder joined = new StringBuilder();
        for (String value : values) {
            if (joined.length() > 0) joined.append(separator);
            joined.append(value);
        }
        return joined.toString();
    }
}
