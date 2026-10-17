package tv.noirstream;

import java.net.URI;
import java.util.Locale;

final class MediaSourceValidator {
    private MediaSourceValidator() { }

    static boolean isSafeHttpUrl(String address) {
        if (address == null || address.isEmpty()) return false;
        try {
            URI uri = URI.create(address);
            String scheme = uri.getScheme();
            if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                    || uri.getHost() == null || uri.getUserInfo() != null) return false;
            String query = uri.getQuery();
            if (query != null) {
                for (String parameter : query.toLowerCase(Locale.ROOT).split("[&;]")) {
                    int equals = parameter.indexOf('=');
                    String name = equals < 0 ? parameter : parameter.substring(0, equals);
                    if (name.contains("pass") || name.contains("token") || name.contains("secret")
                            || name.contains("key") || name.contains("auth") || name.contains("sig")
                            || name.contains("credential") || name.contains("session")
                            || name.contains("cookie")) return false;
                }
            }
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static boolean isSafeArtwork(String value) {
        return value != null && (value.startsWith("/") || isSafeHttpUrl(value));
    }

    static boolean isValidSubtitleSource(String address) {
        if (!isSafeHttpUrl(address)) return false;
        String path = URI.create(address).getPath().toLowerCase(Locale.ROOT);
        return path.endsWith(".vtt") || path.endsWith(".srt");
    }

    static boolean isValidMediaSource(String address, String format) {
        if (!isSafeHttpUrl(address)) return false;
        String path = URI.create(address).getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".torrent")) return false;
        if (format != null && !format.isEmpty()) {
            return "hls".equals(format) || "dash".equals(format) || "progressive".equals(format);
        }
        return path.endsWith(".m3u8") || path.endsWith(".mpd") || path.endsWith(".mp4")
                || path.endsWith(".m4v") || path.endsWith(".webm") || path.endsWith(".mkv")
                || path.endsWith(".mov") || path.endsWith(".ts");
    }
}
