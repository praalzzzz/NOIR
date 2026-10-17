package tv.noirstream;

import java.util.Collections;
import java.util.List;

public final class FilmanProvider implements ContentProvider {
    @Override
    public String getId() {
        return "filman";
    }

    @Override
    public String getDisplayName() {
        return "Filman";
    }

    @Override
    public ProviderStatus getStatus() {
        return new ProviderStatus(false,
                "No supported, documented Filman integration is configured. This app does not collect account credentials or expose web sources.");
    }

    @Override
    public List<PlaybackSource> getPlaybackSources(String contentId) {
        return Collections.emptyList();
    }
}
