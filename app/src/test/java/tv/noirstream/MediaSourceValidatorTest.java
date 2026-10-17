package tv.noirstream;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MediaSourceValidatorTest {
    @Test
    public void acceptsSupportedDirectMedia() {
        assertTrue(MediaSourceValidator.isValidMediaSource("https://media.example/movie.m3u8", ""));
        assertTrue(MediaSourceValidator.isValidMediaSource("https://media.example/manifest", "dash"));
        assertTrue(MediaSourceValidator.isValidMediaSource("https://media.example/video", "progressive"));
    }

    @Test
    public void rejectsUnsafeAndUnsupportedSources() {
        assertFalse(MediaSourceValidator.isValidMediaSource("magnet:?xt=urn:btih:sample", "hls"));
        assertFalse(MediaSourceValidator.isValidMediaSource("https://media.example/file.torrent", "progressive"));
        assertFalse(MediaSourceValidator.isValidMediaSource("https://media.example/video.mp4?token=secret", ""));
        assertFalse(MediaSourceValidator.isValidMediaSource("https://user:pass@media.example/video.mp4", ""));
        assertFalse(MediaSourceValidator.isValidMediaSource("https://media.example/video.mp4", "unknown"));
        assertFalse(MediaSourceValidator.isValidMediaSource("https://media.example/video.unknown", ""));
    }

    @Test
    public void acceptsOnlySupportedSubtitleExtensions() {
        assertTrue(MediaSourceValidator.isValidSubtitleSource("https://media.example/subtitle.vtt"));
        assertTrue(MediaSourceValidator.isValidSubtitleSource("https://media.example/subtitle.srt"));
        assertFalse(MediaSourceValidator.isValidSubtitleSource("https://media.example/subtitle.ass"));
    }
}
