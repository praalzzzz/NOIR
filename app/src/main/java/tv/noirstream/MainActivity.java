package tv.noirstream;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.app.AlertDialog;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.LruCache;
import android.util.Log;
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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;
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
    private static final int REQUEST_PICK_ADDON_MANIFEST = 101;
    private static final int BG = 0x08090B;
    private static final int PANEL = 0x0D0F12;
    private static final int TEXT = 0xF1EDE4;
    private static final int MUTED = 0x8C9099;
    private static final int GOLD = 0xD8B579;
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
    private final AddonRegistry addonRegistry = new AddonRegistry();
    private final Map<String, AddonInfo> addonInfo = new ConcurrentHashMap<>();
    private final Set<String> detailsRequested = new HashSet<>();
    private final Set<String> detailFailures = new HashSet<>();
    private final Set<String> seasonsRequested = new HashSet<>();
    private final Map<String, Integer> activeSeasonRequests = new ConcurrentHashMap<>();
    private final List<ContentProvider> providers = Arrays.asList(new FilmanProvider());
    private SharedPreferences preferences;
    private LinearLayout page;
    private LinearLayout content;
    private ScrollView pageScroll;
    private String currentRoute = "home";
    private String query = "";
    private String searchType = "all";
    private String catalogMessage = "";
    private boolean loadingSearch;
    private boolean liveCatalogLoaded;
    private int tmdbCatalogGeneration;
    private ExoPlayer player;
    private PlayerView playerView;
    private WebView playerWebView;
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
        loadAddonManifestUrls();
        loadContinuePositions();
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
                    if (isSafeAddonManifestSource(url)) addonRegistry.install(url);
                }
                JSONArray disabled = new JSONArray(preferences.getString("disabled_addon_manifest_urls", "[]"));
                for (int i = 0; i < disabled.length(); i++) {
                    String url = disabled.optString(i, "");
                    if (addonRegistry.isInstalled(url)) addonRegistry.setEnabled(url, false);
                }
            } catch (Exception ignored) {
                preferences.edit().remove("addon_manifest_urls").apply();
                addonRegistry.enableAll();
            }
        }

    private void loadSavedAddonManifests() {
            for (String url : addonRegistry.installedSources()) {
                if (addonRegistry.isEnabled(url)) fetchAddonManifest(url, false);
            }
        }

    private void persistAddonManifestUrls() {
            JSONArray urls = new JSONArray();
            for (String url : addonRegistry.installedSources()) urls.put(url);
            preferences.edit().putString("addon_manifest_urls", urls.toString()).apply();
        }

    private void persistDisabledAddonManifestUrls() {
            JSONArray urls = new JSONArray();
            for (String url : addonRegistry.disabledSources()) urls.put(url);
            preferences.edit().putString("disabled_addon_manifest_urls", urls.toString()).apply();
        }

    private void addAddonManifest(String url) {
            if (!isSafeAddonManifestSource(url)) {
                addonMessage = "Choose a local JSON file or enter a valid HTTPS manifest URL without embedded credentials.";
                render();
                return;
            }
            if (isSafeHttpUrl(url) && !url.regionMatches(true, 0, "https://", 0, 8)) {
                addonMessage = "Use HTTPS for new remote catalogs. Existing HTTP catalogs can still be refreshed.";
                render();
                return;
            }
            if (addonRegistry.isInstalled(url)) {
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
                    AddonManifestParser.Manifest parsed = AddonManifestParser.parse(manifest, manifestUrl);
                    List<Title> titles = new ArrayList<>();
                    for (AddonManifestParser.Item entry : parsed.items) {
                        Title item = new Title(entry.id, entry.type, entry.title, entry.year,
                                entry.overview, entry.poster, entry.backdrop, join(entry.genres, " / "),
                                null, null);
                        item.streamUri = entry.stream;
                        item.streamFormat = entry.format;
                        item.addonName = parsed.name;
                        item.manifestUrl = manifestUrl;
                        item.addonEpisodes = entry.episodes;
                        for (AddonManifestParser.Subtitle subtitle : entry.subtitles) {
                            item.subtitles.add(new SubtitleTrack(subtitle.language, subtitle.label, subtitle.url));
                        }
                        titles.add(item);
                    }
                    runOnUiThread(() -> {
                        if (!userInitiated && !addonRegistry.isInstalled(manifestUrl)) return;
                        if (userInitiated) {
                            showAddonInstallReview(manifestUrl, parsed, titles);
                            return;
                        }
                        addonInfo.put(manifestUrl, new AddonInfo(parsed.name, parsed.description, parsed.author,
                                parsed.version, parsed.items.size(), parsed.id));
                        for (Map.Entry<String, Title> entry : new ArrayList<>(addonTitles.entrySet())) {
                            if (manifestUrl.equals(entry.getValue().manifestUrl)) addonTitles.remove(entry.getKey());
                        }
                        if (addonRegistry.isEnabled(manifestUrl)) {
                            for (Title item : titles) {
                                knownTitles.put(item.key(), item);
                                addonTitles.put(item.key(), item);
                            }
                        }
                        addonMessage = (userInitiated ? "Added " : "Validated ") + parsed.name + " · "
                                + parsed.items.size() + " validated item(s).";
                        if ("addons".equals(currentRoute) || "home".equals(currentRoute)) render();
                    });
                } catch (Exception exception) {
                    Log.w("NOIR", "Add-on manifest failed validation (" + exception.getClass().getSimpleName() + ").");
                    if (userInitiated && manifestUrl.startsWith("content:")) {
                        releaseLocalManifestPermission(manifestUrl);
                    }
                    runOnUiThread(() -> {
                        addonMessage = "Add-on not loaded: " + safeErrorMessage(exception);
                        if ("addons".equals(currentRoute)) render();
                    });
                }
            });
        }

    private void showAddonInstallReview(String manifestUrl, AddonManifestParser.Manifest parsed,
                                       List<Title> titles) {
        String publisher = parsed.author.isEmpty() ? "Publisher not supplied" : "Publisher: " + parsed.author;
        String version = parsed.version.isEmpty() ? "Version not supplied" : "Version: " + parsed.version;
        new AlertDialog.Builder(this)
                .setTitle("Review custom catalog")
                .setMessage(parsed.name + "\n" + publisher + "\n" + version
                        + "\nCatalog: " + parsed.items.size() + " item(s); direct HTTP(S) streams only."
                        + "\n\nNOIR reads catalog data and declared media URLs. It does not run code, sign in, or scrape the publisher site. Install only sources you trust and are authorized to use.")
                .setNegativeButton("Cancel", (dialog, which) -> {
                    addonMessage = "Installation cancelled; the catalog was not saved.";
                    if ("addons".equals(currentRoute)) render();
                    if (manifestUrl.startsWith("content:")) releaseLocalManifestPermission(manifestUrl);
                })
                .setPositiveButton("Install catalog", (dialog, which) -> {
                    if (!addonRegistry.install(manifestUrl)) {
                        addonMessage = "That catalog is already installed.";
                        render();
                        return;
                    }
                    addonRegistry.setEnabled(manifestUrl, true);
                    persistAddonManifestUrls();
                    persistDisabledAddonManifestUrls();
                    applyAddonManifest(manifestUrl, parsed, titles, true);
                })
                .setOnCancelListener(dialog -> {
                    if (manifestUrl.startsWith("content:")) releaseLocalManifestPermission(manifestUrl);
                })
                .show();
    }

    private void applyAddonManifest(String manifestUrl, AddonManifestParser.Manifest parsed,
                                    List<Title> titles, boolean newlyInstalled) {
        addonInfo.put(manifestUrl, new AddonInfo(parsed.name, parsed.description, parsed.author,
                parsed.version, parsed.items.size(), parsed.id));
        for (Map.Entry<String, Title> entry : new ArrayList<>(addonTitles.entrySet())) {
            if (manifestUrl.equals(entry.getValue().manifestUrl)) addonTitles.remove(entry.getKey());
        }
        if (addonRegistry.isEnabled(manifestUrl)) {
            for (Title item : titles) {
                knownTitles.put(item.key(), item);
                addonTitles.put(item.key(), item);
            }
        }
        addonMessage = (newlyInstalled ? "Added " : "Validated ") + parsed.name + " Â· "
                + parsed.items.size() + " validated item(s).";
        if ("addons".equals(currentRoute) || "home".equals(currentRoute)) render();
    }

    private void setAddonEnabled(String manifestUrl, boolean enabled) {
        if (!addonRegistry.isInstalled(manifestUrl)) return;
        if (enabled) {
            addonRegistry.setEnabled(manifestUrl, true);
            addonMessage = "Enabling add-on…";
        } else {
            addonRegistry.setEnabled(manifestUrl, false);
            for (Map.Entry<String, Title> entry : new ArrayList<>(addonTitles.entrySet())) {
                if (manifestUrl.equals(entry.getValue().manifestUrl)) addonTitles.remove(entry.getKey());
            }
            addonMessage = "Add-on disabled.";
        }
        persistDisabledAddonManifestUrls();
        render();
        if (enabled) fetchAddonManifest(manifestUrl, false);
    }

    private JSONObject fetchJsonUrl(String address) throws Exception {
            HttpURLConnection connection = null;
            try {
                InputStream stream;
                URI source = URI.create(address);
                if ("content".equalsIgnoreCase(source.getScheme())) {
                    stream = getContentResolver().openInputStream(Uri.parse(address));
                    if (stream == null) throw new IllegalStateException("The selected local manifest could not be opened.");
                } else {
                    ProviderUrlPolicy.requirePublicHttpUrl(address);
                    connection = (HttpURLConnection) new URL(address).openConnection();
                    connection.setConnectTimeout(10000);
                    connection.setReadTimeout(12000);
                    connection.setInstanceFollowRedirects(false);
                    connection.setRequestProperty("Accept", "application/json");
                    int status = connection.getResponseCode();
                    if (status < 200 || status >= 300) {
                        throw new IllegalStateException("HTTP " + status + " from the manifest host.");
                    }
                    stream = connection.getInputStream();
                }
                try (InputStream input = stream; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (bytes.size() + count > 1_000_000) {
                            throw new IllegalArgumentException("Manifest exceeds the 1 MB limit.");
                        }
                        bytes.write(buffer, 0, count);
                    }
                    return new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
                }
            } finally {
                if (connection != null) connection.disconnect();
            }
        }

    private boolean isSafeAddonManifestSource(String address) {
        if (isSafeHttpUrl(address)) return true;
        if (address == null || address.isEmpty()) return false;
        try {
            URI uri = URI.create(address);
            return "content".equalsIgnoreCase(uri.getScheme())
                    && uri.getAuthority() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void pickLocalAddonManifest() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/json", "text/plain"});
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_PICK_ADDON_MANIFEST);
        } catch (ActivityNotFoundException exception) {
            showMessage("File picker unavailable", "This device does not provide a document picker. Install a manifest using its HTTP(S) URL instead.");
        }
    }

    private void releaseLocalManifestPermission(String manifestUrl) {
        try {
            getContentResolver().releasePersistableUriPermission(
                    Uri.parse(manifestUrl), Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException exception) {
            Log.w("NOIR", "Could not release local add-on document permission.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_ADDON_MANIFEST || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException exception) {
            showMessage("File access unavailable", "The selected document provider did not grant persistent read access.");
            return;
        }
        addAddonManifest(uri.toString());
    }

    private void removeAddonManifest(String url) {
            if (url.startsWith("content:")) releaseLocalManifestPermission(url);
            addonRegistry.remove(url);
            addonInfo.remove(url);
            addonMessage = "Removed add-on manifest.";
            persistAddonManifestUrls();
            persistDisabledAddonManifestUrls();
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
            return MediaSourceValidator.isSafeHttpUrl(address);
        }

    private boolean isValidSubtitleSource(String address) {
            return MediaSourceValidator.isValidSubtitleSource(address);
        }

    private boolean isValidMediaSource(String address) {
            return MediaSourceValidator.isValidMediaSource(address, "");
        }

    private boolean isValidMediaSource(String address, String format) {
            return MediaSourceValidator.isValidMediaSource(address, format);
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
        boolean animatePage = motionEnabled();
        content.setAlpha(animatePage ? 0f : 1f);
        pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(true);
        pageScroll.setClipToPadding(false);
        pageScroll.addView(content);
        page.addView(pageScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        if (animatePage) content.animate().alpha(1f).setDuration(220).start();

        switch (currentRoute) {
            case "search": renderSearch(); break;
            case "movies": renderMediaCollection(false); break;
            case "series": renderMediaCollection(true); break;
            case "list": renderMyList(); break;
            case "continue": renderContinueWatching(); break;
            case "providers": renderProviders(); break;
            case "addons": renderAddons(); break;
            case "addon-guide": renderAddonGuide(); break;
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

        String[][] nav = {{"Home", "home"}, {"Movies", "movies"}, {"Series", "series"},
                {"Search", "search"}, {"Continue", "continue"},
                {"My List", "list"}, {"Providers", "providers"}, {"Add-ons", "addons"}};
        for (String[] item : nav) {
            boolean selected = currentRoute.equals(item[1])
                    || ("addon-guide".equals(currentRoute) && "addons".equals(item[1]));
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
        TextView live = text(tmdbApiKey().isEmpty() ? "CURATED EDITION" : "LIVE DISCOVERY",
                10, TEXT, true);
        live.setLetterSpacing(.08f);
        LinearLayout.LayoutParams liveParams = new LinearLayout.LayoutParams(-2, -2);
        liveParams.leftMargin = dp(8);
        status.addView(live, liveParams);
        status.setFocusable(true);
        status.setBackground(focusBackground(18, Color.rgb(23, 25, 27)));
        addFocusAnimation(status);
        status.setOnClickListener(v -> showTmdbKeyDialog());
        status.setContentDescription("TMDB API settings");
        bar.addView(status, new LinearLayout.LayoutParams(-2, dp(36)));
        return bar;
    }

    private String tmdbApiKey() {
        if (preferences.contains("tmdb_api_key")) {
            return preferences.getString("tmdb_api_key", "").trim();
        }
        return BuildConfig.TMDB_API_KEY.trim();
    }

    private void showTmdbKeyDialog() {
        EditText input = new EditText(this);
        configureInput(input, "Paste your TMDB v3 API key");
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(24), dp(8), dp(24), 0);
        wrapper.addView(input, new LinearLayout.LayoutParams(-1, dp(56)));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("TMDB setup")
                .setMessage("A personal key enables live catalogs, search, title details, and series metadata. It is saved in this app's private preferences and sent directly to TMDB over HTTPS. It remains a client key and can be extracted by someone with access to the device.")
                .setView(wrapper)
                .setPositiveButton("Save and connect", null)
                .setNeutralButton("Clear key", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String key = input.getText().toString().trim();
                if (key.isEmpty() || key.length() > 256 || key.matches(".*\\s+.*")) {
                    input.setError("Enter a valid TMDB API key without spaces.");
                    return;
                }
                preferences.edit().putString("tmdb_api_key", key).apply();
                dialog.dismiss();
                refreshTmdbCatalog();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                preferences.edit().putString("tmdb_api_key", "").apply();
                dialog.dismiss();
                refreshTmdbCatalog();
            });
            input.requestFocus();
        });
        dialog.show();
    }

    private void refreshTmdbCatalog() {
        detailsRequested.clear();
        detailFailures.clear();
        seasonsRequested.clear();
        activeSeasonRequests.clear();
        liveCatalogLoaded = false;
        trending = selectType(false);
        popular = selectType(false);
        series = selectType(true);
        newTitles = selectType(false);
        animation = selectAnimation();
        catalogMessage = tmdbApiKey().isEmpty()
                ? "TMDB key cleared · showing the starter collection"
                : "Connecting to TMDB for live discovery…";
        loadLiveCatalog();
        render();
    }

    private void renderHome() {
        content.setPadding(0, 0, 0, dp(42));
        content.addView(buildHero(featured()), new LinearLayout.LayoutParams(-1, heroHeight()));
        TextView status = text(catalogMessage.isEmpty()
                ? (tmdbApiKey().isEmpty()
                    ? "A hand-picked starter collection · Select the catalog status above to configure TMDB"
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

        boolean curatedPreview = !liveCatalogLoaded || trending.isEmpty();
        TextView index = text(curatedPreview ? "01  /  CURATED PREVIEW" : "01  /  FEATURED",
                11, 0xFFE9E9E1, true);
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
        TextView eyebrow = text(curatedPreview ? "STARTER COLLECTION" : "TONIGHT'S PREMIERE",
                11, GOLD, true);
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
            TextView empty = text("No titles are available in this collection right now.", 14, MUTED, false);
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
            boolean animateCards = motionEnabled();
            for (int i = 0; i < items.size(); i++) {
                View card = buildCard(items.get(i));
                row.addView(card, new LinearLayout.LayoutParams(dp(184), dp(318)));
                if (animateCards) animateEntry(card, Math.min(i * 28, 224));
            }
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
        TextView hint = text(tmdbApiKey().isEmpty()
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
                if (!tmdbApiKey().isEmpty() && query.trim().length() >= 2) {
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
                if (!tmdbApiKey().isEmpty() && query.trim().length() >= 2) searchLive(query, results);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                hideKeyboard(input);
                if (!tmdbApiKey().isEmpty()) searchLive(query, results);
                return true;
            }
            return false;
        });
        input.requestFocus();
    }

    private void renderMediaCollection(boolean tv) {
        content.setPadding(dp(64), dp(40), dp(64), dp(48));
        content.addView(text(tv ? "Series" : "Movies", 34, TEXT, true));
        TextView intro = text("Browse the live TMDB catalog when configured, with your saved and installed add-on titles alongside it.",
                15, MUTED, false);
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(-1, -2);
        introParams.topMargin = dp(8);
        content.addView(intro, introParams);
        List<Title> results = new ArrayList<>(tv ? series : popular);
        for (Title item : savedTitles()) if (item.type.equals(tv ? "tv" : "movie")) results.add(item);
        for (Title item : addonTitles.values()) if (item.type.equals(tv ? "tv" : "movie")) results.add(item);
        if (results.isEmpty()) {
            content.addView(text("No titles are available yet. Configure TMDB or install a compatible catalog add-on.",
                    16, MUTED, false));
        } else {
            addSection(tv ? "Discover series" : "Discover movies", results, tv ? "series" : "movies");
        }
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
        String selectedType = searchType;
        io.execute(() -> {
            try {
                String encoded = URLEncoder.encode(term.trim(), StandardCharsets.UTF_8.name());
                String endpoint = "movie".equals(selectedType) ? "movie"
                        : "tv".equals(selectedType) ? "tv" : "multi";
                String expectedType = "all".equals(selectedType) ? "multi" : selectedType;
                Map<String, Title> deduplicated = new java.util.LinkedHashMap<>();
                Exception lastFailure = null;
                for (String language : new String[]{"pl-PL", "en-US"}) {
                    try {
                        JSONObject response = requestJson("/search/" + endpoint + "?query=" + encoded
                                + "&include_adult=false&language=" + language);
                        for (Title item : TmdbResultsParser.parse(response.optJSONArray("results"), expectedType)) {
                            String key = item.type + ":" + item.id;
                            if (!deduplicated.containsKey(key)) deduplicated.put(key, item);
                        }
                    } catch (Exception exception) {
                        lastFailure = exception;
                    }
                }
                if (deduplicated.isEmpty() && lastFailure != null) throw lastFailure;
                List<Title> found = new ArrayList<>(deduplicated.values());
                runOnUiThread(() -> {
                    if (!"search".equals(currentRoute) || !query.equals(term) || !searchType.equals(selectedType)) return;
                    results.removeAllViews();
                    results.addView(text(found.isEmpty() ? "No TMDB matches in Polish or English."
                            : "Results from TMDB  ·  Polski + English", 20, TEXT, true));
                    for (Title item : found) results.addView(searchResult(item));
                    loadingSearch = false;
                });
            } catch (Exception exception) {
                Log.w("NOIR", "TMDB search request failed (" + exception.getClass().getSimpleName() + ").");
                runOnUiThread(() -> {
                    if ("search".equals(currentRoute) && query.equals(term) && searchType.equals(selectedType)) {
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
        content.addView(text("Services & sources", 34, TEXT, true));
        TextView intro = text("These links open outside NOIR. Availability, price, login, language, and rights vary by title and region. External pages are not searchable or playable inside NOIR.", 16, MUTED, false);
        intro.setMaxWidth(dp(1000));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(1020), -2);
        ip.topMargin = dp(12);
        content.addView(intro, ip);
        TextView customSources = actionButton("Add a custom catalog by URL", true,
                () -> {
                    history.clear();
                    currentRoute = "addons";
                    query = "";
                    render();
                });
        LinearLayout.LayoutParams customSourcesParams = new LinearLayout.LayoutParams(dp(360), dp(48));
        customSourcesParams.topMargin = dp(14);
        content.addView(customSources, customSourcesParams);
        addOfficialService("TVP VOD", "Polish public broadcaster · selected free titles and live channels; other content may be paid",
                "https://vod.tvp.pl/");
        addOfficialService("Rakuten TV Free", "Ad-supported selection · app/device and account requirements vary",
                "https://www.rakuten.tv/pl/free");
        addOfficialService("Plex", "Free ad-supported catalog · free account and availability may vary",
                "https://watch.plex.tv/");
        addOfficialService("South Park Studios", "Official site · selected full episodes and clips; regional catalog varies; opens in browser",
                "https://southpark.cc.com/");
        addOfficialService("JustWatch Polska", "Find where a title is legally available in Poland · discovery guide, not a player",
                "https://www.justwatch.com/pl");
        addOfficialService("CDA", "Official site · partner, user-uploaded, and paid Premium catalog; NOIR cannot verify individual upload rights",
                "https://www.cda.pl/");
        addOfficialService("Bajeczki.org", "External catalog says no fee or login; third-party video embeds and distribution permissions are not verified by NOIR",
                "https://bajeczki.org/");
        addOfficialService("Paramount+ · South Park", "Regional Paramount+ availability varies; not integrated in NOIR",
                "https://www.paramountplus.com/shows/south-park/");

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
        TextView summary = text("Add a custom catalog by entering its NOIR-compatible JSON manifest URL or choosing a local file. A website URL by itself is not a searchable provider: the manifest must list metadata and direct media URLs. NOIR does not execute add-on code, scrape websites, or handle provider sign-in.", 15, MUTED, false);
        summary.setMaxWidth(dp(900));
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(dp(920), -2);
        summaryParams.topMargin = dp(10);
        content.addView(summary, summaryParams);
        TextView guide = actionButton("Add-on developer guide   ›", false, () -> push("addon-guide", ""));
        LinearLayout.LayoutParams guideParams = new LinearLayout.LayoutParams(dp(300), dp(48));
        guideParams.topMargin = dp(14);
        content.addView(guide, guideParams);

        EditText manifestUrl = new EditText(this);
        configureInput(manifestUrl, "https://example.org/noir-catalog.json");
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(dp(820), dp(56));
        inputParams.topMargin = dp(14);
        content.addView(manifestUrl, inputParams);
        TextView add = actionButton("Validate and review catalog", true,
                () -> addAddonManifest(manifestUrl.getText().toString().trim()));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(280), dp(50));
        addParams.topMargin = dp(12);
        content.addView(add, addParams);
        TextView local = actionButton("Choose local JSON manifest", false, this::pickLocalAddonManifest);
        LinearLayout.LayoutParams localParams = new LinearLayout.LayoutParams(dp(280), dp(46));
        localParams.topMargin = dp(8);
        content.addView(local, localParams);

        TextView rule = text("Accepted streams: direct HTTP(S) HLS (.m3u8), DASH (.mpd), or progressive media. Subtitles: WebVTT (.vtt) or SubRip (.srt). No torrent/magnet sources or credential-bearing URLs. HTTPS is recommended.", 14, MUTED, false);
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
        if (!addonRegistry.installedSources().isEmpty()) {
            TextView installed = text("Installed manifests", 20, TEXT, true);
            LinearLayout.LayoutParams installedParams = new LinearLayout.LayoutParams(-1, -2);
            installedParams.topMargin = dp(28);
            content.addView(installed, installedParams);
            for (String url : addonRegistry.installedSources()) {
                AddonInfo info = addonInfo.get(url);
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(dp(18), dp(14), dp(18), dp(14));
                card.setBackground(rounded(PANEL, 14));
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(900), -2);
                cardParams.topMargin = dp(10);
                content.addView(card, cardParams);
                card.addView(text(info == null ? url : info.name, 18, TEXT, true));
                boolean enabled = addonRegistry.isEnabled(url);
                if (info != null) {
                    if (!info.description.isEmpty()) {
                        TextView description = text(info.description, 13, MUTED, false);
                        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
                        descriptionParams.topMargin = dp(6);
                        card.addView(description, descriptionParams);
                    }
                    String authorVersion = (info.author.isEmpty() ? "" : info.author)
                            + (!info.author.isEmpty() && !info.version.isEmpty() ? "  ·  " : "")
                            + (info.version.isEmpty() ? "" : "v" + info.version);
                    String addonId = info.id.isEmpty() ? "" : "  ·  " + info.id;
                    TextView meta = text((enabled ? "Enabled" : "Disabled") + "  ·  "
                            + info.itemCount + " catalog item(s)  ·  direct media"
                            + (authorVersion.isEmpty() ? "" : "  ·  " + authorVersion) + addonId,
                            12, MUTED, false);
                    LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
                    metaParams.topMargin = dp(6);
                    card.addView(meta, metaParams);
                } else {
                    TextView status = text("Not loaded yet · refresh to check this manifest.", 12, MUTED, false);
                    LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
                    statusParams.topMargin = dp(6);
                    card.addView(status, statusParams);
                }
                LinearLayout controls = new LinearLayout(this);
                controls.setGravity(Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(-1, dp(46));
                controlsParams.topMargin = dp(10);
                card.addView(controls, controlsParams);
                controls.addView(actionButton(enabled ? "Disable" : "Enable", false,
                        () -> setAddonEnabled(url, !enabled)), new LinearLayout.LayoutParams(dp(120), dp(42)));
                controls.addView(actionButton("Refresh", false,
                        () -> fetchAddonManifest(url, false)), new LinearLayout.LayoutParams(dp(120), dp(42)));
                controls.addView(actionButton("Remove", false, () -> new AlertDialog.Builder(this)
                        .setTitle("Remove add-on?")
                        .setMessage("Remove this manifest and its cached catalog?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove", (dialog, which) -> removeAddonManifest(url))
                        .show()), new LinearLayout.LayoutParams(dp(120), dp(42)));
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

    private void renderAddonGuide() {
        content.setPadding(dp(64), dp(38), dp(64), dp(54));
        content.addView(text("Build a NOIR add-on", 34, TEXT, true));
        TextView intro = text("NOIR add-ons are data-only JSON catalogs of direct media URLs you own or are authorized to use. The app never loads executable add-on code.", 15, MUTED, false);
        intro.setMaxWidth(dp(980));
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(dp(1000), -2);
        introParams.topMargin = dp(10);
        content.addView(intro, introParams);
        TextView back = actionButton("‹  Back to Add-ons", false, this::onBackPressed);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(dp(210), dp(46));
        backParams.topMargin = dp(14);
        content.addView(back, backParams);

        List<View> sections = new ArrayList<>();
        sections.add(addGuideSection("01  Getting started",
                "Create a UTF-8 JSON file and host it at an HTTP(S) URL reachable from your TV. This format describes titles and direct streams; it is not a JavaScript, binary, scraping, or executable provider SDK. TV items can declare their own season and episode numbers with a direct stream URL for each episode. TMDB metadata does not provide playable streams."));
        sections.add(addGuideSection("02  Manifest fields",
                "Root: name (optional, max 120 characters), id (optional stable identifier containing letters, numbers, dot, underscore or hyphen), description (optional, max 500), author (optional, max 120), version (optional, max 64), and items (required, 1–100 objects). Item: id (unique within the manifest), type (movie or tv; defaults to movie), title (required, max 200), year, overview, poster, backdrop, genres (string array), stream (required for movies; optional for series with episodes), format (hls, dash, progressive, or omitted when a supported extension identifies it), and subtitles (WebVTT/SRT URLs with optional language and label). TV items can declare 1–500 episodes with season, episode, title, stream, optional format, overview, and subtitles. The manifest is limited to 1 MB. Unknown fields are ignored; there are no permission, entry-point, registry, or NOIR-version fields."));
        String example = "{\n"
                + "  \"id\": \"example.library\",\n"
                + "  \"name\": \"Example authorized library\",\n"
                + "  \"description\": \"A fictional test catalog\",\n"
                + "  \"author\": \"Example Author\",\n"
                + "  \"version\": \"1.0.0\",\n"
                + "  \"items\": [{\n"
                + "    \"id\": \"sample-film\",\n"
                + "    \"type\": \"movie\",\n"
                + "    \"title\": \"Sample Film\",\n"
                + "    \"year\": \"2025\",\n"
                + "    \"overview\": \"Fictional metadata for testing.\",\n"
                + "    \"poster\": \"https://images.example.org/poster.jpg\",\n"
                + "    \"genres\": [\"Drama\"],\n"
                + "    \"stream\": \"https://media.example.org/sample.m3u8\",\n"
                + "    \"format\": \"hls\",\n"
                + "    \"subtitles\": [{\"language\": \"en\", \"label\": \"English\", \"url\": \"https://media.example.org/sample.vtt\"}]\n"
                + "  }, {\n"
                + "    \"id\": \"sample-series\",\n"
                + "    \"type\": \"tv\",\n"
                + "    \"title\": \"Sample Series\",\n"
                + "    \"episodes\": [{\"season\": 1, \"episode\": 1, \"title\": \"Pilot\", \"stream\": \"https://media.example.org/series-s01e01.m3u8\", \"format\": \"hls\"}]\n"
                + "  }]\n"
                + "}";
        addGuideCode(example);
        sections.add(addGuideSection("03  Test and debug",
                "Install a remote manifest by URL or choose a local JSON document from Add-ons. NOIR checks JSON shape, item limits, stream and artwork URLs, and subtitle extensions; the Add-ons screen reports validation errors. A structurally valid URL is not proof it is reachable or decodable. Use “Try stream in NOIR” to verify the source with Media3 on the device. Run .\\\\gradlew.bat testDebugUnitTest assembleDebug lintDebug. For device logs, use adb logcat -s NOIR."));
        sections.add(addGuideSection("04  Package, update & security",
                "There is no public registry configured. Host the JSON file on a trusted HTTPS server and share its URL, or select a local JSON file with the system picker. Use stable unique item IDs and increment version when publishing an update; Refresh downloads the current remote manifest. Installed add-ons can be enabled, disabled, refreshed, or removed. NOIR does not execute remote code, collect provider passwords, resolve unsupported websites, or bypass DRM/access controls. Credential-like URL parameters, embedded URL credentials, torrent/magnet sources, and unsupported subtitle extensions are rejected. The example domains are placeholders and are not playable."));

        HorizontalScrollView tocScroll = new HorizontalScrollView(this);
        tocScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout toc = new LinearLayout(this);
        toc.setOrientation(LinearLayout.HORIZONTAL);
        String[] tocNames = {"Getting started", "Manifest", "Testing", "Security"};
        for (int i = 0; i < sections.size(); i++) {
            final View target = sections.get(i);
            TextView link = actionButton(tocNames[i], false,
                    () -> pageScroll.post(() -> pageScroll.smoothScrollTo(0, target.getTop())));
            LinearLayout.LayoutParams linkParams = new LinearLayout.LayoutParams(-2, dp(42));
            linkParams.rightMargin = dp(8);
            toc.addView(link, linkParams);
        }
        tocScroll.addView(toc);
        content.addView(tocScroll, 3, new LinearLayout.LayoutParams(-1, dp(50)));
    }

    private LinearLayout addGuideSection(String title, String description) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, dp(22), 0, dp(8));
        section.addView(text(title, 22, TEXT, true));
        TextView body = text(description, 15, MUTED, false);
        body.setGravity(Gravity.TOP);
        body.setTextIsSelectable(true);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(dp(980), -2);
        bodyParams.topMargin = dp(10);
        section.addView(body, bodyParams);
        content.addView(section);
        return section;
    }

    private void addGuideCode(String code) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(14), dp(18), dp(14));
        panel.setBackground(rounded(Color.rgb(14, 16, 19), 12));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(dp(980), -2);
        panelParams.topMargin = dp(8);
        content.addView(panel, panelParams);
        panel.addView(actionButton("Copy example", false, () -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("NOIR add-on manifest", code));
                Toast.makeText(this, "Example copied", Toast.LENGTH_SHORT).show();
            }
        }), new LinearLayout.LayoutParams(dp(150), dp(40)));
        TextView source = text(code, 13, 0xFFE2E6E2, false);
        source.setTypeface(Typeface.MONOSPACE);
        source.setGravity(Gravity.TOP | Gravity.START);
        source.setTextIsSelectable(true);
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(-1, -2);
        sourceParams.topMargin = dp(10);
        panel.addView(source, sourceParams);
    }

    private void renderPlayerScreen() {
        Title item = findTitle(query);
        if (!hasAvailableSource(item)) {
            showMessage("Playback unavailable", "This item does not contain a valid, authorized HTTP(S) media URL.");
            currentRoute = history.isEmpty() ? "home" : history.removeLast().name;
            render();
            return;
        }

        releasePlayer();
        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.BLACK);
        setContentView(screen);

        // Shared Header
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

        // Track Selection (Only for DIRECT playback as WebView tracks are hard to control)
        LinearLayout tracks = new LinearLayout(this);
        tracks.setPadding(0, 0, dp(34), dp(34));
        FrameLayout.LayoutParams tracksParams = new FrameLayout.LayoutParams(-2, dp(58),
                Gravity.BOTTOM | Gravity.END);
        screen.addView(tracks, tracksParams);
        if (item.playbackType == PlaybackType.DIRECT) {
            TextView captions = actionButton("CC  Captions", false, () -> showTrackPicker(C.TRACK_TYPE_TEXT));
            tracks.addView(captions, new LinearLayout.LayoutParams(dp(176), dp(46)));
            View gap = new View(this);
            tracks.addView(gap, new LinearLayout.LayoutParams(dp(9), 1));
            TextView audio = actionButton("♫  Audio", false, () -> showTrackPicker(C.TRACK_TYPE_AUDIO));
            tracks.addView(audio, new LinearLayout.LayoutParams(dp(148), dp(46)));
        }

        playingTitle = item;

        if (item.playbackType == PlaybackType.EMBED) {
            // EMBED playback via WebView
            playerWebView = new WebView(this);
            playerWebView.setWebChromeClient(new android.webkit.WebChromeClient());
            playerWebView.setWebViewClient(new android.webkit.WebViewClient());
            WebSettings settings = playerWebView.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setMediaPlaybackRequiresUserGesture(false);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            playerWebView.loadUrl(item.streamUri);
            screen.addView(playerWebView, new FrameLayout.LayoutParams(-1, -1));
        } else {
            // DIRECT playback via ExoPlayer
            playerView = new PlayerView(this);
            playerView.setUseController(true);
            playerView.setControllerAutoShow(true);
            playerView.setControllerShowTimeoutMs(5000);
            playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
            playerView.setKeepScreenOn(true);
            screen.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

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
                    String errorName = PlaybackException.getErrorCodeName(error.errorCode);
                    Log.w("NOIR", "Media3 playback failed (" + errorName + ").");
                    String message = "Playback failed (" + errorName
                            + "). Verify that the direct HTTP(S) stream is reachable and uses a supported format and codec.";
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
        }

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
        if (!hasAvailableSource(item)) {
            showMessage("Playback unavailable", "This stream is invalid or its add-on is disabled.");
            return;
        }
        if (item.playbackType == PlaybackType.DIRECT && (item.streamUri == null || item.streamUri.isEmpty())) {
            item.playbackType = PlaybackType.EMBED;
            if (item.type.equals("tv")) {
                item.streamUri = "https://vidlink.pro/tv/" + item.id;
            } else {
                item.streamUri = "https://vidlink.pro/movie/" + item.id;
            }
        }
        knownTitles.put(item.key(), item);
        push("player", item.key());
    }

    private void savePlaybackProgress() {
        if (player == null || !hasAvailableSource(playingTitle)) return;
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
            object.put("playbackType", item.playbackType.name());
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
            if (hasAvailableSource(item)) titles.add(item);
        }
        return titles;
    }

    private boolean hasAvailableSource(Title item) {
        if (item == null) return false;
        if (item.playbackType == PlaybackType.EMBED) {
            return item.manifestUrl == null || addonRegistry.isEnabled(item.manifestUrl);
        }
        return item != null && isValidMediaSource(item.streamUri, item.streamFormat)
                && (item.manifestUrl == null || addonRegistry.isEnabled(item.manifestUrl));
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
        if (playerWebView != null) {
            playerWebView.stopLoading();
            playerWebView.destroy();
            playerWebView = null;
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
        if (hasAvailableSource(item)) {
            buttons.addView(actionButton("Try stream in NOIR   ▶", true, () -> startPlayback(item)),
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

        if (item.type.equals("tv") && !item.addonEpisodes.isEmpty()) {
            addAddonEpisodes(item);
        } else if (item.manifestUrl == null && item.type.equals("tv") && item.id > 0
                && !tmdbApiKey().isEmpty()
                && seasonsRequested.add(item.key())) {
            loadSeasons(item);
        }
        if (item.manifestUrl == null && item.id > 0 && !tmdbApiKey().isEmpty()
                && detailsRequested.add(item.key())) loadDetails(item);
        if (detailFailures.contains(item.key())) {
            TextView error = text("Live title details could not be loaded. The available starter information is shown.", 14, MUTED, false);
            error.setPadding(dp(64), dp(12), dp(64), dp(6));
            content.addView(error);
        }
        if (!hasAvailableSource(item) && item.addonEpisodes.isEmpty()) addProviderAvailability(item);
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
                item.similar = TmdbResultsParser.parse(related.optJSONArray("results"), kind);
                runOnUiThread(() -> {
                    if ("detail".equals(currentRoute) && query.equals(item.key())) render();
                });
            } catch (Exception ignored) {
                Log.w("NOIR", "TMDB title details request failed (" + ignored.getClass().getSimpleName() + ").");
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
                List<TmdbSeriesParser.Season> seasons = TmdbSeriesParser.parseSeasons(show);
                runOnUiThread(() -> {
                    if (!"detail".equals(currentRoute) || !query.equals(item.key())) return;
                    LinearLayout section = new LinearLayout(this);
                    section.setOrientation(LinearLayout.VERTICAL);
                    section.setPadding(dp(64), dp(24), dp(48), dp(10));
                    section.addView(text("Seasons & episodes", 23, TEXT, true));
                    if (seasons.isEmpty()) {
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
                    for (int index = 0; index < seasons.size(); index++) {
                        TmdbSeriesParser.Season season = seasons.get(index);
                        int number = season.number;
                        String label = season.name;
                        TextView choice = actionButton(label, index == 0, () -> loadEpisodes(item, number, section));
                        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(48));
                        p.rightMargin = dp(10);
                        choices.addView(choice, p);
                    }
                    content.addView(section);
                    loadEpisodes(item, seasons.get(0).number, section);
                });
            } catch (Exception exception) {
                Log.w("NOIR", "TMDB seasons request failed (" + exception.getClass().getSimpleName() + ").");
                runOnUiThread(() -> {
                    if (!"detail".equals(currentRoute) || !query.equals(item.key())) return;
                    TextView error = text("Seasons could not be loaded. Check the TMDB connection and try again.", 14, MUTED, false);
                    error.setPadding(dp(64), dp(14), dp(24), dp(8));
                    LinearLayout row = new LinearLayout(this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.addView(error, new LinearLayout.LayoutParams(0, -2, 1f));
                    row.addView(actionButton("Retry", false, () -> {
                        seasonsRequested.remove(item.key());
                        render();
                    }), new LinearLayout.LayoutParams(dp(120), dp(44)));
                    content.addView(row);
                });
            }
        });
    }

    private void loadEpisodes(Title item, int season, LinearLayout section) {
        activeSeasonRequests.put(item.key(), season);
        while (section.getChildCount() > 2) section.removeViewAt(2);
        TextView loading = text("Loading episodes…", 14, MUTED, false);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(-1, dp(48));
        loadingParams.topMargin = dp(8);
        section.addView(loading, loadingParams);
        io.execute(() -> {
            try {
                JSONObject response = requestJson("/tv/" + item.id + "/season/" + season);
                List<TmdbSeriesParser.Episode> values = TmdbSeriesParser.parseEpisodes(response, season);
                runOnUiThread(() -> {
                    if (!"detail".equals(currentRoute) || !query.equals(item.key())
                            || !Integer.valueOf(season).equals(activeSeasonRequests.get(item.key()))) return;
                    while (section.getChildCount() > 2) section.removeViewAt(2);
                    if (values.isEmpty()) {
                        TextView empty = text("No episode entries are listed for this season.", 14, MUTED, false);
                        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, dp(50));
                        ep.topMargin = dp(8);
                        section.addView(empty, ep);
                    }
                    for (TmdbSeriesParser.Episode episode : values) {
                        int number = episode.episodeNumber;
                        String watchedKey = "watched_" + item.id + "_" + season + "_" + number;
                        boolean watched = preferences.getBoolean(watchedKey, false);
                        String heading = String.format(Locale.US, "S%02d E%02d   %s",
                                episode.seasonNumber, number, episode.title);
                        LinearLayout episodeCard = new LinearLayout(this);
                        episodeCard.setOrientation(LinearLayout.VERTICAL);
                        episodeCard.setPadding(dp(15), dp(10), dp(15), dp(10));
                        episodeCard.setBackground(rounded(PANEL, 12));
                        if (!episode.stillPath.isEmpty()) {
                            ImageView still = new ImageView(this);
                            still.setScaleType(ImageView.ScaleType.CENTER_CROP);
                            still.setBackground(posterPlaceholder());
                            still.setClipToOutline(true);
                            String stillUrl = episode.stillPath.startsWith("http")
                                    ? episode.stillPath : TMDB_IMAGE + "w300" + episode.stillPath;
                            loadImage(stillUrl, still);
                            LinearLayout.LayoutParams stillParams = new LinearLayout.LayoutParams(dp(260), dp(146));
                            stillParams.bottomMargin = dp(8);
                            episodeCard.addView(still, stillParams);
                        }
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
                        String overview = episode.overview;
                        if (!overview.isEmpty()) {
                            TextView description = text(overview, 13, MUTED, false);
                            description.setMaxLines(3);
                            LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(-1, -2);
                            descriptionParams.topMargin = dp(5);
                            episodeCard.addView(description, descriptionParams);
                        }
                        String airDate = episode.airDate;
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
                Log.w("NOIR", "TMDB episode request failed (" + exception.getClass().getSimpleName() + ").");
                runOnUiThread(() -> {
                    if (!"detail".equals(currentRoute) || !query.equals(item.key())
                            || !Integer.valueOf(season).equals(activeSeasonRequests.get(item.key()))) return;
                    while (section.getChildCount() > 2) section.removeViewAt(2);
                    TextView message = text("Episodes could not be loaded. " + safeErrorMessage(exception),
                            14, MUTED, false);
                    LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(0, -2, 1f);
                    LinearLayout row = new LinearLayout(this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.addView(message, messageParams);
                    row.addView(actionButton("Retry", false, () -> loadEpisodes(item, season, section)),
                            new LinearLayout.LayoutParams(dp(120), dp(44)));
                    section.addView(row);
                });
            }
        });
    }

    private void addAddonEpisodes(Title item) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(dp(64), dp(24), dp(48), dp(10));
        section.addView(text("Episodes from " + item.addonName, 23, TEXT, true));
        List<Integer> seasons = new ArrayList<>();
        for (AddonManifestParser.Episode episode : item.addonEpisodes) {
            if (!seasons.contains(episode.season)) seasons.add(episode.season);
        }
        Collections.sort(seasons);
        LinearLayout choices = new LinearLayout(this);
        choices.setPadding(0, dp(14), 0, dp(8));
        HorizontalScrollView seasonScroll = new HorizontalScrollView(this);
        seasonScroll.setHorizontalScrollBarEnabled(false);
        seasonScroll.addView(choices);
        section.addView(seasonScroll);
        content.addView(section);
        for (Integer season : seasons) {
            TextView choice = actionButton(season == 0 ? "Specials" : "Season " + season,
                    season.equals(seasons.get(0)), () -> renderAddonEpisodesForSeason(item, season, section));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(48));
            params.rightMargin = dp(10);
            choices.addView(choice, params);
        }
        renderAddonEpisodesForSeason(item, seasons.get(0), section);
    }

    private void renderAddonEpisodesForSeason(Title seriesTitle, int season, LinearLayout section) {
        while (section.getChildCount() > 2) section.removeViewAt(2);
        for (AddonManifestParser.Episode episode : seriesTitle.addonEpisodes) {
            if (episode.season != season) continue;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(16), dp(10), dp(14), dp(10));
            card.setBackground(rounded(PANEL, 12));
            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            String label = String.format(Locale.US, "S%02d E%02d  ·  %s",
                    episode.season, episode.number, episode.title);
            copy.addView(text(label, 16, TEXT, true));
            if (!episode.overview.isEmpty()) {
                TextView overview = text(episode.overview, 13, MUTED, false);
                overview.setMaxLines(2);
                LinearLayout.LayoutParams overviewParams = new LinearLayout.LayoutParams(-1, -2);
                overviewParams.topMargin = dp(5);
                copy.addView(overview, overviewParams);
            }
            card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(actionButton("Play episode   ▶", true,
                    () -> startPlayback(createEpisodeTitle(seriesTitle, episode))),
                    new LinearLayout.LayoutParams(dp(190), dp(46)));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(dp(820), -2);
            cardParams.topMargin = dp(8);
            section.addView(card, cardParams);
        }
    }

    private Title createEpisodeTitle(Title seriesTitle, AddonManifestParser.Episode episode) {
        String name = String.format(Locale.US, "S%02d E%02d · %s",
                episode.season, episode.number, episode.title);
        Title item = new Title(seriesTitle.id, "tv", name, seriesTitle.year, episode.overview,
                seriesTitle.posterPath, seriesTitle.backdropPath, seriesTitle.genres, null, null);
        item.streamUri = episode.stream;
        item.streamFormat = episode.format;
        item.addonName = seriesTitle.addonName;
        item.manifestUrl = seriesTitle.manifestUrl;
        for (AddonManifestParser.Subtitle subtitle : episode.subtitles) {
            item.subtitles.add(new SubtitleTrack(subtitle.language, subtitle.label, subtitle.url));
        }
        return item;
    }

    private void loadLiveCatalog() {
        final int generation = ++tmdbCatalogGeneration;
        final String apiKey = tmdbApiKey();
        if (apiKey.isEmpty()) return;
        io.execute(() -> {
            try {
                JSONObject trendingResponse = requestJson("/trending/movie/week", apiKey);
                JSONObject popularResponse = requestJson("/movie/popular", apiKey);
                JSONObject seriesResponse = requestJson("/tv/popular", apiKey);
                JSONObject recentResponse = requestJson("/movie/now_playing", apiKey);
                JSONObject animationResponse = requestJson(
                        "/discover/movie?with_genres=16&sort_by=popularity.desc", apiKey);
                List<Title> liveTrending = TmdbResultsParser.parse(trendingResponse.optJSONArray("results"), "movie");
                List<Title> livePopular = TmdbResultsParser.parse(popularResponse.optJSONArray("results"), "movie");
                List<Title> liveSeries = TmdbResultsParser.parse(seriesResponse.optJSONArray("results"), "tv");
                List<Title> liveRecent = TmdbResultsParser.parse(recentResponse.optJSONArray("results"), "movie");
                List<Title> liveAnimation = TmdbResultsParser.parse(animationResponse.optJSONArray("results"), "movie");
                runOnUiThread(() -> {
                    if (generation != tmdbCatalogGeneration) return;
                    trending = liveTrending;
                    popular = livePopular;
                    series = liveSeries;
                    newTitles = liveRecent;
                    animation = liveAnimation;
                    liveCatalogLoaded = true;
                    catalogMessage = "Live catalog · powered by TMDB";
                    if ("home".equals(currentRoute)) render();
                });
            } catch (Exception exception) {
                Log.w("NOIR", "TMDB catalog request failed (" + exception.getClass().getSimpleName() + ").");
                runOnUiThread(() -> {
                    if (generation != tmdbCatalogGeneration) return;
                    catalogMessage = "Live catalog unavailable · showing the starter collection";
                    if ("home".equals(currentRoute)) render();
                });
            }
        });
    }

    private JSONObject requestJson(String path) throws Exception {
        return requestJson(path, tmdbApiKey());
    }

    private JSONObject requestJson(String path, String apiKey) throws Exception {
        String separator = path.contains("?") ? "&" : "?";
        URL url = new URL("https://api.themoviedb.org/3" + path + separator + "api_key="
                + URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name()));
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

    private Title featured() {
        if (!liveCatalogLoaded) {
            for (Title item : series) {
                if ("Stranger Things".equals(item.name)) return item;
            }
        }
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
        String lower = SearchText.normalize(term);
        if (lower.isEmpty()) return found;
        for (Title item : starter) {
            if ((type.equals("all") || item.type.equals(type))
                    && (SearchText.normalize(item.name).contains(lower)
                    || SearchText.normalize(item.genres).contains(lower)))
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
            if (motionEnabled()) {
                focusedView.animate().scaleX(focused ? 1.04f : 1f).scaleY(focused ? 1.04f : 1f)
                        .setDuration(130).start();
            } else {
                focusedView.setScaleX(1f);
                focusedView.setScaleY(1f);
            }
            focusedView.setElevation(focused ? dp(10) : 0);
        });
    }

    private void animateEntry(View view, long delay) {
        view.setAlpha(0f);
        view.setTranslationY(dp(12));
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(190).start();
    }

    private boolean motionEnabled() {
        if (android.os.Build.VERSION.SDK_INT >= 26) return ValueAnimator.areAnimatorsEnabled();
        return Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
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

    public enum PlaybackType { DIRECT, EMBED }

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

    private static final class AddonInfo {
        final String name;
        final String description;
        final String author;
        final String version;
        final int itemCount;
        final String id;

        AddonInfo(String name, String description, String author, String version, int itemCount, String id) {
            this.name = name;
            this.description = description;
            this.author = author;
            this.version = version;
            this.itemCount = itemCount;
            this.id = id;
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
        List<AddonManifestParser.Episode> addonEpisodes = Collections.emptyList();
        List<SubtitleTrack> subtitles = new ArrayList<>();
        List<Title> similar = Collections.emptyList();
        PlaybackType playbackType = PlaybackType.DIRECT;

        Title(long id, String type, String name, String year, String overview, String posterPath,
              String backdropPath, String genres, Double rating, Integer runtime) {
            this.id = id; this.type = type; this.name = name; this.year = year; this.overview = overview;
            this.posterPath = posterPath; this.backdropPath = backdropPath; this.genres = genres;
            this.rating = rating; this.runtime = runtime;
        }

        Title(long id, String type, String name, String year, String overview, String posterPath,
              String backdropPath, String genres, Double rating, Integer runtime, PlaybackType playbackType) {
            this(id, type, name, year, overview, posterPath, backdropPath, genres, rating, runtime);
            this.playbackType = playbackType;
        }
        String key() { return type + ":" + id + ":" + name; }
        String posterUrl(String size) {
            if (posterPath == null || posterPath.isEmpty()) return "";
            return posterPath.startsWith("http") ? posterPath : TMDB_IMAGE + size + posterPath;
        }
        String backdropUrl(String size) {
            String path = backdropPath == null || backdropPath.isEmpty() ? posterPath : backdropPath;
            if (path == null || path.isEmpty()) return "";
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
