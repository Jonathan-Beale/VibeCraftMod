package com.example.vibecraftmod.ui;

/**
 * Resolves data bindings for overlays (e.g., player.armor, player.health).
 * Extend this class to support more data sources.
 */
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class OverlayDataBinding {
    private static final Map<String, List<DataListener>> listeners = new ConcurrentHashMap<>();
    private static final Map<String, Object> data = new ConcurrentHashMap<>();

    public interface DataListener {
        void onDataChanged(String binding, Object value);
    }

    public static void subscribe(String binding, DataListener listener) {
        listeners.computeIfAbsent(binding, k -> new CopyOnWriteArrayList<>()).add(listener);
        // Immediately notify with current value if available
        if (data.containsKey(binding)) {
            listener.onDataChanged(binding, data.get(binding));
        }
    }

    public static void update(String binding, Object value) {
        data.put(binding, value);
        List<DataListener> bindingListeners = listeners.get(binding);
        if (bindingListeners != null) {
            for (DataListener l : bindingListeners) {
                l.onDataChanged(binding, value);
            }
        }
    }

    public static Object resolve(String binding) {
        return data.get(binding);
    }
}
