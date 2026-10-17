package tv.noirstream;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AddonRegistryTest {
    @Test
    public void installsDisablesEnablesAndRemovesSources() {
        AddonRegistry registry = new AddonRegistry();
        String source = "https://example.org/addon.json";

        assertTrue(registry.install(source));
        assertFalse(registry.install(source));
        assertTrue(registry.isInstalled(source));
        assertTrue(registry.isEnabled(source));

        assertTrue(registry.setEnabled(source, false));
        assertFalse(registry.isEnabled(source));
        assertEquals(Arrays.asList(source), registry.disabledSources());

        assertTrue(registry.setEnabled(source, true));
        assertTrue(registry.isEnabled(source));
        assertTrue(registry.disabledSources().isEmpty());

        assertTrue(registry.remove(source));
        assertFalse(registry.isInstalled(source));
        assertFalse(registry.remove(source));
    }

    @Test
    public void enableAllPreservesInstalledSources() {
        AddonRegistry registry = new AddonRegistry();
        String first = "https://example.org/first.json";
        String second = "https://example.org/second.json";
        registry.install(first);
        registry.install(second);
        registry.setEnabled(first, false);
        registry.setEnabled(second, false);

        registry.enableAll();

        assertEquals(Arrays.asList(first, second), registry.installedSources());
        assertTrue(registry.isEnabled(first));
        assertTrue(registry.isEnabled(second));
    }
}
