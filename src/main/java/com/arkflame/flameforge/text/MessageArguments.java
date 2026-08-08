package com.arkflame.flameforge.text;

import net.kyori.adventure.text.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class MessageArguments {
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    private final Map<String, String> stringValues = new LinkedHashMap<>();
    private final Map<String, Component> componentValues = new LinkedHashMap<>();
    private final Map<String, List<String>> lineValues = new LinkedHashMap<>();

    private MessageArguments() {
    }

    public static MessageArguments create() {
        return new MessageArguments();
    }

    public MessageArguments string(final String key, final String value) {
        if (isValidKey(key) && value != null) {
            stringValues.put(key, value);
            componentValues.remove(key);
            lineValues.remove(key);
        }
        return this;
    }

    public MessageArguments component(final String key, final Component value) {
        if (isValidKey(key) && value != null) {
            componentValues.put(key, value);
            stringValues.remove(key);
            lineValues.remove(key);
        }
        return this;
    }

    public MessageArguments lines(final String key, final List<String> value) {
        if (isValidKey(key)) {
            List<String> safeValue = value != null ? new java.util.ArrayList<>(value) : Collections.emptyList();
            lineValues.put(key, safeValue);
            stringValues.remove(key);
            componentValues.remove(key);
        }
        return this;
    }

    public Map<String, String> getStringValues() {
        return Collections.unmodifiableMap(stringValues);
    }

    public Map<String, Component> getComponentValues() {
        return Collections.unmodifiableMap(componentValues);
    }

    public Map<String, List<String>> getLineValues() {
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : lineValues.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    private boolean isValidKey(final String key) {
        return key != null && KEY_PATTERN.matcher(key).matches();
    }
}