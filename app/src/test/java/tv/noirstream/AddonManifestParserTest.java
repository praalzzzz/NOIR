package tv.noirstream;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public final class AddonManifestParserTest {
    @Test
    public void parsesSupportedMetadataItemsAndSubtitles() throws Exception {
        JSONObject manifest = new JSONObject()
                .put("id", "sample.library")
                .put("name", "Sample library")
                .put("description", "A fictional catalog")
                .put("author", "Example author")
                .put("version", "1.0.0")
                .put("unknownField", true)
                .put("items", new JSONArray().put(new JSONObject()
                        .put("id", "sample-film")
                        .put("type", "movie")
                        .put("title", "Sample Film")
                        .put("year", "2025")
                        .put("overview", "Fictional description")
                        .put("poster", "https://images.example.org/poster.jpg")
                        .put("genres", new JSONArray().put("Drama"))
                        .put("stream", "https://media.example.org/sample.m3u8")
                        .put("format", "HLS")
                        .put("subtitles", new JSONArray().put(new JSONObject()
                                .put("language", "en")
                                .put("label", "English")
                                .put("url", "https://media.example.org/sample.vtt")))));

        AddonManifestParser.Manifest parsed = AddonManifestParser.parse(manifest, "https://example.org/addon.json");

        assertEquals("Sample library", parsed.name);
        assertEquals("sample.library", parsed.id);
        assertEquals("Example author", parsed.author);
        assertEquals("1.0.0", parsed.version);
        assertEquals(1, parsed.items.size());
        assertEquals("movie", parsed.items.get(0).type);
        assertEquals("hls", parsed.items.get(0).format);
        assertEquals("Drama", parsed.items.get(0).genres.get(0));
        assertEquals("en", parsed.items.get(0).subtitles.get(0).language);
    }

    @Test
    public void acceptsMinimalManifestWithDefaultMetadata() throws Exception {
        JSONObject manifest = new JSONObject().put("items", new JSONArray()
                .put(new JSONObject()
                        .put("title", "Sample")
                        .put("stream", "https://media.example.org/sample.mp4")
                        .put("format", "progressive")));

        AddonManifestParser.Manifest parsed = AddonManifestParser.parse(manifest, "https://example.org/minimal.json");

        assertEquals("Community add-on", parsed.name);
        assertEquals("", parsed.id);
        assertEquals("movie", parsed.items.get(0).type);
        assertEquals("Sample", parsed.items.get(0).title);
        assertTrue(parsed.items.get(0).subtitles.isEmpty());
    }

    @Test
    public void rejectsMissingItemsMalformedEntriesAndDuplicateIds() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> AddonManifestParser.parse(new JSONObject(), "https://example.org/addon.json"));
        assertThrows(IllegalArgumentException.class,
                () -> AddonManifestParser.parse(new JSONObject().put("items", new JSONArray()),
                        "https://example.org/addon.json"));
        assertThrows(IllegalArgumentException.class,
                () -> AddonManifestParser.parse(new JSONObject().put("items", new JSONArray().put(JSONObject.NULL)),
                        "https://example.org/addon.json"));
        JSONObject duplicateIds = new JSONObject().put("items", new JSONArray()
                .put(item("same", "https://media.example.org/first.mp4"))
                .put(item("same", "https://media.example.org/second.mp4")));
        assertThrows(IllegalArgumentException.class,
                () -> AddonManifestParser.parse(duplicateIds, "https://example.org/addon.json"));
    }

    @Test
    public void rejectsInvalidTypesSourcesAndSubtitles() throws Exception {
        JSONObject invalidType = new JSONObject().put("items", new JSONArray()
                .put(item("sample", "https://media.example.org/sample.mp4").put("type", "person")));
        assertThrows(IllegalArgumentException.class,
                () -> AddonManifestParser.parse(invalidType, "https://example.org/addon.json"));

        JSONObject credentialUrl = new JSONObject().put("items", new JSONArray()
                .put(item("sample", "https://media.example.org/sample.mp4?token=secret")));
        assertThrows(IllegalArgumentException.class,
                () -> AddonManifestParser.parse(credentialUrl, "https://example.org/addon.json"));

        JSONObject invalidSubtitle = new JSONObject().put("items", new JSONArray()
                .put(item("sample", "https://media.example.org/sample.mp4")
                        .put("subtitles", new JSONArray().put(new JSONObject()
                                .put("url", "https://media.example.org/captions.ass")))));
        assertThrows(IllegalArgumentException.class,
                () -> AddonManifestParser.parse(invalidSubtitle, "https://example.org/addon.json"));

        JSONObject invalidArtwork = new JSONObject().put("items", new JSONArray()
                .put(item("sample", "https://media.example.org/sample.mp4")
                        .put("poster", "javascript:alert(1)")));
        assertThrows(IllegalArgumentException.class,
                () -> AddonManifestParser.parse(invalidArtwork, "https://example.org/addon.json"));

        JSONObject invalidGenre = new JSONObject().put("items", new JSONArray()
                .put(item("sample", "https://media.example.org/sample.mp4")
                        .put("genres", new JSONArray().put(123))));
        assertThrows(IllegalArgumentException.class,
                () -> AddonManifestParser.parse(invalidGenre, "https://example.org/addon.json"));
    }

    private JSONObject item(String id, String stream) throws Exception {
        return new JSONObject().put("id", id).put("title", "Sample").put("stream", stream);
    }
}
