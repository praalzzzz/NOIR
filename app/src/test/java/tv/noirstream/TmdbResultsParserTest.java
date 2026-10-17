package tv.noirstream;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class TmdbResultsParserTest {
    @Test
    public void parsesMovieSearchResults() throws Exception {
        JSONObject movie = new JSONObject()
                .put("id", 11)
                .put("title", "A Movie")
                .put("release_date", "2024-05-01")
                .put("poster_path", "/poster.jpg")
                .put("genre_ids", new JSONArray().put(18));

        List<MainActivity.Title> titles = TmdbResultsParser.parse(new JSONArray().put(movie), "movie");

        assertEquals(1, titles.size());
        assertEquals("movie", titles.get(0).type);
        assertEquals("A Movie", titles.get(0).name);
        assertEquals("2024", titles.get(0).year);
        assertEquals("Drama", titles.get(0).genres);
    }

    @Test
    public void parsesTvResultsWithoutMovieTitleField() throws Exception {
        JSONObject show = new JSONObject()
                .put("id", 22)
                .put("name", "A Series")
                .put("first_air_date", "2021-10-10")
                .put("overview", "A valid show")
                .put("genre_ids", new JSONArray().put(18));

        List<MainActivity.Title> titles = TmdbResultsParser.parse(new JSONArray().put(show), "tv");

        assertEquals(1, titles.size());
        assertEquals("tv", titles.get(0).type);
        assertEquals("A Series", titles.get(0).name);
        assertEquals("2021", titles.get(0).year);
        assertEquals("A valid show", titles.get(0).overview);
        assertEquals("", titles.get(0).posterPath);
    }

    @Test
    public void parsesMixedResultsAndIgnoresPeopleAndMalformedRows() throws Exception {
        JSONObject movie = new JSONObject().put("id", 1).put("title", "Movie").put("media_type", "movie");
        JSONObject show = new JSONObject().put("id", 2).put("name", "Show").put("media_type", "tv");
        JSONObject person = new JSONObject().put("id", 3).put("name", "Person").put("media_type", "person");

        List<MainActivity.Title> titles = TmdbResultsParser.parse(
                new JSONArray().put(JSONObject.NULL).put(movie).put(show).put(person), "multi");

        assertEquals(2, titles.size());
        assertEquals("movie", titles.get(0).type);
        assertEquals("tv", titles.get(1).type);
    }

    @Test
    public void returnsEmptyForMissingResultsAndDeduplicatesIds() throws Exception {
        assertTrue(TmdbResultsParser.parse(null, "tv").isEmpty());

        JSONObject first = new JSONObject().put("id", 7).put("name", "Show");
        JSONObject duplicate = new JSONObject().put("id", 7).put("name", "Show renamed");
        List<MainActivity.Title> titles = TmdbResultsParser.parse(
                new JSONArray().put(first).put(duplicate), "tv");

        assertEquals(1, titles.size());
        assertEquals("Show renamed", titles.get(0).name);
        assertNull(titles.get(0).rating);
    }
}
