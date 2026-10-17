package tv.noirstream;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class TmdbSeriesParserTest {
    @Test
    public void parsesRealSeasonsAndSkipsSpecialsAndDuplicates() throws Exception {
        JSONObject show = new JSONObject().put("seasons", new JSONArray()
                .put(new JSONObject().put("season_number", 0).put("name", "Specials"))
                .put(new JSONObject().put("season_number", 1).put("name", "First"))
                .put(new JSONObject().put("season_number", 1).put("name", "Duplicate"))
                .put(new JSONObject().put("season_number", 2)));

        List<TmdbSeriesParser.Season> seasons = TmdbSeriesParser.parseSeasons(show);

        assertEquals(2, seasons.size());
        assertEquals(1, seasons.get(0).number);
        assertEquals("First", seasons.get(0).name);
        assertEquals("Season 2", seasons.get(1).name);
    }

    @Test
    public void parsesEpisodesWithTheirSeasonAndArtworkMetadata() throws Exception {
        JSONObject response = new JSONObject()
                .put("season_number", 3)
                .put("episodes", new JSONArray()
                        .put(new JSONObject().put("episode_number", 1)
                                .put("name", "The Return")
                                .put("overview", "A fictional summary.")
                                .put("air_date", "2025-01-02")
                                .put("still_path", "/still.jpg"))
                        .put(JSONObject.NULL)
                        .put(new JSONObject().put("episode_number", 1).put("name", "Duplicate")));

        List<TmdbSeriesParser.Episode> episodes = TmdbSeriesParser.parseEpisodes(response, 3);

        assertEquals(1, episodes.size());
        assertEquals(3, episodes.get(0).seasonNumber);
        assertEquals(1, episodes.get(0).episodeNumber);
        assertEquals("The Return", episodes.get(0).title);
        assertEquals("A fictional summary.", episodes.get(0).overview);
        assertEquals("2025-01-02", episodes.get(0).airDate);
        assertEquals("/still.jpg", episodes.get(0).stillPath);
    }

    @Test
    public void safelyReturnsEmptyForMissingOrMismatchedSeasonData() throws Exception {
        assertTrue(TmdbSeriesParser.parseSeasons(new JSONObject()).isEmpty());
        assertTrue(TmdbSeriesParser.parseEpisodes(null, 1).isEmpty());
        assertTrue(TmdbSeriesParser.parseEpisodes(
                new JSONObject().put("season_number", 2)
                        .put("episodes", new JSONArray().put(new JSONObject().put("episode_number", 1))),
                1).isEmpty());
    }
}
