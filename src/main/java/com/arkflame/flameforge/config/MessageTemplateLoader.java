package com.arkflame.flameforge.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MessageTemplateLoader {
    public static Map<String, Map<String, Object>> flatten(final ConfigurationSection section) {
        Map<String, Map<String, Object>> flattened = new LinkedHashMap<>();
        if (section != null) {
            flatten(section, "", flattened);
        }
        return immutableMessages(flattened);
    }

    private static void flatten(final ConfigurationSection section, final String prefix,
                                final Map<String, Map<String, Object>> flattened) {
        for (String key : section.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = section.get(key);
            if (value instanceof ConfigurationSection) {
                flatten((ConfigurationSection) value, path, flattened);
            } else if (value instanceof List) {
                List<String> lines = new ArrayList<>();
                for (Object line : (List<?>) value) {
                    if (line != null) {
                        lines.add(String.valueOf(line));
                    }
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("lines", Collections.unmodifiableList(lines));
                flattened.put(path, Collections.unmodifiableMap(entry));
            } else if (value instanceof Number || value instanceof Boolean || value instanceof String) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("message", String.valueOf(value));
                flattened.put(path, Collections.unmodifiableMap(entry));
            }
        }
    }

    private static Map<String, Map<String, Object>> immutableMessages(
            final Map<String, Map<String, Object>> flattened) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(flattened));
    }
}
