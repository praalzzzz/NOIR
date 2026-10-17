package tv.noirstream;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class AddonManifestParser {
    private AddonManifestParser() { }

    static Manifest parse(JSONObject manifest, String source) {
        String suppliedName = manifest.optString("name", "").trim();
        String name = suppliedName.isEmpty() ? "Community add-on" : suppliedName;
        if (name.length() > 120) throw new IllegalArgumentException("The add-on name is too long.");
        String description = manifest.optString("description", "").trim();
        String author = manifest.optString("author", "").trim();
        String version = manifest.optString("version", "").trim();
        if (description.length() > 500 || author.length() > 120 || version.length() > 64) {
            throw new IllegalArgumentException("Add-on description, author, or version exceeds the supported length.");
        }
        String addonId = manifest.optString("id", "").trim();
        if (!addonId.isEmpty() && !addonId.matches("[A-Za-z0-9._-]{1,100}")) {
            throw new IllegalArgumentException("The add-on id may contain only letters, numbers, dot, underscore, and hyphen.");
        }

        JSONArray items = manifest.optJSONArray("items");
        if (items == null || items.length() == 0 || items.length() > 100) {
            throw new IllegalArgumentException("The manifest must contain 1–100 items.");
        }

        List<Item> parsedItems = new ArrayList<>();
        Set<String> itemIds = new HashSet<>();
        for (int i = 0; i < items.length(); i++) {
            JSONObject entry = items.optJSONObject(i);
            if (entry == null) throw new IllegalArgumentException("Item " + (i + 1) + " must be a JSON object.");

            String title = entry.optString("title", "").trim();
            if (title.isEmpty() || title.length() > 200) {
                throw new IllegalArgumentException("Item " + (i + 1) + " needs a title of at most 200 characters.");
            }
            String type = entry.optString("type", "movie").toLowerCase(Locale.ROOT);
            if (!("movie".equals(type) || "tv".equals(type))) {
                throw new IllegalArgumentException("Item " + (i + 1) + " type must be movie or tv.");
            }
            String stream = entry.optString("stream", "");
            String format = entry.optString("format", "").trim().toLowerCase(Locale.ROOT);
            if (!MediaSourceValidator.isValidMediaSource(stream, format)) {
                throw new IllegalArgumentException("Item " + (i + 1) + " needs a supported direct HTTP(S) media URL and format.");
            }

            String itemId = entry.optString("id", String.valueOf(i)).trim();
            if (!itemId.matches("[A-Za-z0-9._-]{1,100}") || !itemIds.add(itemId)) {
                throw new IllegalArgumentException("Item " + (i + 1) + " has a missing, invalid, or duplicate id.");
            }

            List<String> genres = parseGenres(entry.optJSONArray("genres"), i + 1);
            String poster = entry.optString("poster", "");
            String backdrop = entry.optString("backdrop", poster);
            if (!poster.isEmpty() && !MediaSourceValidator.isSafeArtwork(poster)) {
                throw new IllegalArgumentException("Item " + (i + 1) + " has an invalid poster URL.");
            }
            if (!backdrop.isEmpty() && !MediaSourceValidator.isSafeArtwork(backdrop)) {
                throw new IllegalArgumentException("Item " + (i + 1) + " has an invalid backdrop URL.");
            }

            List<Subtitle> subtitles = parseSubtitles(entry.optJSONArray("subtitles"), i + 1);
            long generatedId = Integer.toUnsignedLong((source + ":" + itemId).hashCode()) + 1;
            parsedItems.add(new Item(generatedId, type, title, entry.optString("year", "—"),
                    entry.optString("overview", ""), poster, backdrop, genres, stream, format, subtitles));
        }
        return new Manifest(name, addonId, description, author, version, parsedItems);
    }

    private static List<String> parseGenres(JSONArray values, int itemNumber) {
        List<String> genres = new ArrayList<>();
        if (values == null) return genres;
        if (values.length() > 30) {
            throw new IllegalArgumentException("Item " + itemNumber + " has too many genre labels.");
        }
        for (int i = 0; i < values.length(); i++) {
            Object value = values.opt(i);
            if (!(value instanceof String) || ((String) value).trim().isEmpty()) {
                throw new IllegalArgumentException("Item " + itemNumber + " genre labels must be non-empty strings.");
            }
            genres.add(((String) value).trim());
        }
        return genres;
    }

    private static List<Subtitle> parseSubtitles(JSONArray values, int itemNumber) {
        List<Subtitle> subtitles = new ArrayList<>();
        if (values == null) return subtitles;
        for (int i = 0; i < values.length(); i++) {
            JSONObject subtitle = values.optJSONObject(i);
            String url = subtitle == null ? "" : subtitle.optString("url", "");
            if (subtitle == null || !MediaSourceValidator.isValidSubtitleSource(url)) {
                throw new IllegalArgumentException("Item " + itemNumber + " has an invalid subtitle entry.");
            }
            String language = subtitle.optString("language", subtitle.optString("lang", "und"));
            String label = subtitle.optString("label", language);
            subtitles.add(new Subtitle(language, label, url));
        }
        return subtitles;
    }

    static final class Manifest {
        final String name;
        final String id;
        final String description;
        final String author;
        final String version;
        final List<Item> items;

        Manifest(String name, String id, String description, String author, String version, List<Item> items) {
            this.name = name;
            this.id = id;
            this.description = description;
            this.author = author;
            this.version = version;
            this.items = items;
        }
    }

    static final class Item {
        final long id;
        final String type;
        final String title;
        final String year;
        final String overview;
        final String poster;
        final String backdrop;
        final List<String> genres;
        final String stream;
        final String format;
        final List<Subtitle> subtitles;

        Item(long id, String type, String title, String year, String overview, String poster,
             String backdrop, List<String> genres, String stream, String format, List<Subtitle> subtitles) {
            this.id = id;
            this.type = type;
            this.title = title;
            this.year = year;
            this.overview = overview;
            this.poster = poster;
            this.backdrop = backdrop;
            this.genres = genres;
            this.stream = stream;
            this.format = format;
            this.subtitles = subtitles;
        }
    }

    static final class Subtitle {
        final String language;
        final String label;
        final String url;

        Subtitle(String language, String label, String url) {
            this.language = language;
            this.label = label;
            this.url = url;
        }
    }
}
