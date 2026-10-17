package tv.noirstream;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AddonRegistry {
    private final Map<String, Boolean> sources = new LinkedHashMap<>();

    boolean install(String source) {
        if (sources.containsKey(source)) return false;
        sources.put(source, true);
        return true;
    }

    boolean setEnabled(String source, boolean enabled) {
        if (!sources.containsKey(source)) return false;
        sources.put(source, enabled);
        return true;
    }

    boolean isInstalled(String source) {
        return sources.containsKey(source);
    }

    boolean isEnabled(String source) {
        return Boolean.TRUE.equals(sources.get(source));
    }

    boolean remove(String source) {
        return sources.remove(source) != null;
    }

    void enableAll() {
        for (String source : sources.keySet()) sources.put(source, true);
    }

    List<String> installedSources() {
        return new ArrayList<>(sources.keySet());
    }

    List<String> disabledSources() {
        List<String> disabled = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : sources.entrySet()) {
            if (!entry.getValue()) disabled.add(entry.getKey());
        }
        return disabled;
    }
}
