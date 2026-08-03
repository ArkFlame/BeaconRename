package com.arkflame.flameforge.text;

import net.kyori.adventure.text.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public final class MessageArguments {
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z0-9_-]+");

    private final Map<String, String> stringValues = new LinkedHashMap<>();
    private final Map<String, Component> componentValues = new LinkedHashMap<>();

    private MessageArguments() {
    }

    public static MessageArguments create() {
        return new MessageArguments();
    }

    public MessageArguments string(final String key, final String value) {
        if (isValidKey(key) && value != null) {
            stringValues.put(key, value);
            componentValues.remove(key);
        }
        return this;
    }

    public MessageArguments component(final String key, final Component value) {
        if (isValidKey(key) && value != null) {
            componentValues.put(key, value);
            stringValues.remove(key);
        }
        return this;
    }

    public Map<String, String> getStringValues() {
        return Collections.unmodifiableMap(stringValues);
    }

    public Map<String, Component> getComponentValues() {
        return Collections.unmodifiableMap(componentValues);
    }

    private boolean isValidKey(final String key) {
        return key != null && KEY_PATTERN.matcher(key).matches();
    }
}
