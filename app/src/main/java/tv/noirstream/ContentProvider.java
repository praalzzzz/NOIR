package tv.noirstream;

import java.util.List;

public interface ContentProvider {
    String getId();

    String getDisplayName();

    ProviderStatus getStatus();

    List<PlaybackSource> getPlaybackSources(String contentId);

    final class ProviderStatus {
        public final boolean available;
        public final String message;

        public ProviderStatus(boolean available, String message) {
            this.available = available;
            this.message = message;
        }
    }

    final class PlaybackSource {
        public final String title;
        public final String uri;

        public PlaybackSource(String title, String uri) {
            this.title = title;
            this.uri = uri;
        }
    }
}
