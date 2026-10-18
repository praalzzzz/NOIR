package tv.noirstream;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/** Fixture-only regression coverage; it does not assert a live catalog or playable source. */
public final class SouthParkMetadataRegressionTest {
    @Test
    public void retainsSouthParkSeriesIdentityAndRequestedEpisodeNumbers() throws Exception {
        JSONObject show = new JSONObject()
                .put("id", 2190)
                .put("name", "South Park")
                .put("original_name", "South Park")
                .put("first_air_date", "1997-08-13");
        List<MainActivity.Title> titles = TmdbResultsParser.parse(new JSONArray().put(show), "tv");

        assertEquals(1, titles.size());
        assertEquals("tv", titles.get(0).type);
        assertEquals(2190, titles.get(0).id);
        assertEquals("South Park", titles.get(0).name);
        assertEquals("1997", titles.get(0).year);

        JSONObject season = new JSONObject().put("season_number", 2)
                .put("episodes", new JSONArray()
                        .put(new JSONObject().put("episode_number", 1).put("name", "Fixture Episode 1"))
                        .put(new JSONObject().put("episode_number", 2).put("name", "Fixture Episode 2")));
        List<TmdbSeriesParser.Episode> episodes = TmdbSeriesParser.parseEpisodes(season, 2);

        assertEquals(2, episodes.size());
        assertEquals(2, episodes.get(1).seasonNumber);
        assertEquals(2, episodes.get(1).episodeNumber);
        assertEquals("Fixture Episode 2", episodes.get(1).title);
    }
}
