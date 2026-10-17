package tv.noirstream;

import java.text.Normalizer;
import java.util.Locale;

final class SearchText {
    private SearchText() { }

    static String normalize(String value) {
        if (value == null) return "";
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replace('ł', 'l');
        return normalized.replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }
}
