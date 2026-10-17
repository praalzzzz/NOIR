package tv.noirstream;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class SearchNormalizationTest {
    @Test
    public void foldsDiacriticsAndPunctuationForLocalSearch() {
        assertEquals("zabki z lodzi", SearchText.normalize("Żąbki z Łodzi!"));
        assertEquals("spider man", SearchText.normalize("Spider-Man"));
        assertEquals("", SearchText.normalize(null));
    }
}
