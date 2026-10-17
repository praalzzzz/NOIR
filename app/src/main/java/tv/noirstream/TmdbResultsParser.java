package tv.noirstream;

import org.json.JSONArray;
import org.json.JSONObject;

import androidx.media3.common.util.UnstableApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@UnstableApi
final class TmdbResultsParser {
    private static final Map<Integer, String> GENRES = createGenres();

    private TmdbResultsParser() { }

    static List<MainActivity.Title> parse(JSONArray results, String expectedType) {
        if (!"movie".equals(expectedType) && !"tv".equals(expectedType) && !"multi".equals(expectedType)) {
            throw new IllegalArgumentException("Unsupported TMDB result type: " + expectedType);
        }

        Map<String, MainActivity.Title> output = new LinkedHashMap<>();
        if (results == null) return new ArrayList<>();

        for (int i = 0; i < results.length(); i++) {
            JSONObject object = results.optJSONObject(i);
            if (object == null) continue;

            String type = "multi".equals(expectedType)
                    ? object.optString("media_type", "")
                    : expectedType;
            if (!"movie".equals(type) && !"tv".equals(type)) continue;

            String name = object.optString("tv".equals(type) ? "name" : "title", "").trim();
            long id = object.optLong("id", 0);
            if (name.isEmpty() || id <= 0) continue;

            String date = object.optString("tv".equals(type) ? "first_air_date" : "release_date", "");
            String year = date.length() >= 4 ? date.substring(0, 4) : "—";
            String poster = object.optString("poster_path", "");
            String backdrop = object.optString("backdrop_path", "");
            Double rating = object.has("vote_average")
                    ? object.optDouble("vote_average", 0)
                    : 0;
            MainActivity.Title item = new MainActivity.Title(id, type, name, year,
                    object.optString("overview", ""), poster, backdrop,
                    genres(object.optJSONArray("genre_ids")),
                    rating > 0 && Double.isFinite(rating) ? rating : null, null);
            output.put(type + ":" + id, item);
        }
        return new ArrayList<>(output.values());
    }

    private static String genres(JSONArray ids) {
        List<String> names = new ArrayList<>();
        if (ids != null) {
            for (int i = 0; i < Math.min(ids.length(), 3); i++) {
                String name = GENRES.get(ids.optInt(i));
                if (name != null) names.add(name);
            }
        }
        StringBuilder joined = new StringBuilder();
        for (String name : names) {
            if (joined.length() > 0) joined.append(" / ");
            joined.append(name);
        }
        return joined.toString();
    }

    private static Map<Integer, String> createGenres() {
        Map<Integer, String> names = new LinkedHashMap<>();
        names.put(28, "Action"); names.put(12, "Adventure"); names.put(16, "Animation");
        names.put(35, "Comedy"); names.put(80, "Crime"); names.put(99, "Documentary");
        names.put(18, "Drama"); names.put(10751, "Family"); names.put(14, "Fantasy");
        names.put(36, "History"); names.put(27, "Horror"); names.put(10402, "Music");
        names.put(9648, "Mystery"); names.put(10749, "Romance"); names.put(878, "Sci-Fi");
        names.put(10770, "TV Movie"); names.put(53, "Thriller"); names.put(10752, "War");
        names.put(37, "Western"); names.put(10759, "Action & Adventure"); names.put(10762, "Kids");
        names.put(10763, "News"); names.put(10764, "Reality"); names.put(10765, "Sci-Fi & Fantasy");
        names.put(10766, "Soap"); names.put(10767, "Talk"); names.put(10768, "War & Politics");
        return names;
    }
}
