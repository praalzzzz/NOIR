package tv.noirstream;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class TmdbSeriesParser {
    private TmdbSeriesParser() { }

    static List<Season> parseSeasons(JSONObject show) {
        List<Season> seasons = new ArrayList<>();
        JSONArray values = show == null ? null : show.optJSONArray("seasons");
        if (values == null) return seasons;
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value == null) continue;
            int number = value.optInt("season_number", -1);
            if (number <= 0 || !seen.add(number)) continue;
            String name = value.optString("name", "").trim();
            if (name.isEmpty()) name = "Season " + number;
            seasons.add(new Season(number, name));
        }
        return seasons;
    }

    static List<Episode> parseEpisodes(JSONObject response, int requestedSeason) {
        List<Episode> episodes = new ArrayList<>();
        if (response == null || requestedSeason <= 0) return episodes;
        int responseSeason = response.optInt("season_number", requestedSeason);
        if (responseSeason != requestedSeason) return episodes;
        JSONArray values = response.optJSONArray("episodes");
        if (values == null) return episodes;

        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < values.length(); i++) {
            JSONObject value = values.optJSONObject(i);
            if (value == null) continue;
            int number = value.optInt("episode_number", -1);
            if (number <= 0 || !seen.add(number)) continue;
            String title = value.optString("name", "").trim();
            if (title.isEmpty()) title = "Episode " + number;
            episodes.add(new Episode(responseSeason, number, title,
                    value.optString("overview", ""),
                    value.optString("air_date", ""),
                    value.optString("still_path", "")));
        }
        return episodes;
    }

    static final class Season {
        final int number;
        final String name;

        Season(int number, String name) {
            this.number = number;
            this.name = name;
        }
    }

    static final class Episode {
        final int seasonNumber;
        final int episodeNumber;
        final String title;
        final String overview;
        final String airDate;
        final String stillPath;

        Episode(int seasonNumber, int episodeNumber, String title,
                String overview, String airDate, String stillPath) {
            this.seasonNumber = seasonNumber;
            this.episodeNumber = episodeNumber;
            this.title = title;
            this.overview = overview;
            this.airDate = airDate;
            this.stillPath = stillPath;
        }
    }
}
