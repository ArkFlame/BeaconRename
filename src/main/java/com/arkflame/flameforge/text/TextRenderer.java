package com.arkflame.flameforge.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextRenderer {
    private static final Pattern PERCENT_PATTERN = Pattern.compile("%([A-Za-z0-9_-]+)%");
    private static final String INTERNAL_TAG_PREFIX = "ff_";

    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer legacySerializer;

    public TextRenderer() {
        this.miniMessage = MiniMessage.miniMessage();
        this.legacySerializer = LegacyComponentSerializer.legacySection();
    }

    public Component render(final String input, final Map<String, String> stringValues,
                           final Map<String, Component> componentValues, final String messageKey,
                           final Logger logger) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        String normalized = normalizePercentMarkers(input);

        TagResolver.Builder resolverBuilder = TagResolver.builder();

        if (stringValues != null) {
            for (Map.Entry<String, String> entry : stringValues.entrySet()) {
                String normalizedKey = normalizePlaceholderKey(entry.getKey());
                String tagName = INTERNAL_TAG_PREFIX + normalizedKey;
                String value = entry.getValue() != null ? entry.getValue() : "";
                resolverBuilder.resolver(Placeholder.unparsed(tagName, value));
            }
        }

        if (componentValues != null) {
            for (Map.Entry<String, Component> entry : componentValues.entrySet()) {
                String normalizedKey = normalizePlaceholderKey(entry.getKey());
                String tagName = INTERNAL_TAG_PREFIX + normalizedKey;
                Component value = entry.getValue() != null ? entry.getValue() : Component.empty();
                resolverBuilder.resolver(Placeholder.component(tagName, value));
            }
        }

        TagResolver resolver = resolverBuilder.build();

        try {
            return miniMessage.deserialize(normalized, resolver);
        } catch (Exception e) {
            logInvalidMiniMessage(messageKey, e, logger);
            return errorComponent("Message format error");
        }
    }

    public Component renderLegacy(final String input, final Map<String, String> stringValues,
                                  final Map<String, Component> componentValues, final String messageKey,
                                  final Logger logger) {
        Component component = render(input, stringValues, componentValues, messageKey, logger);
        return component;
    }

    public Component fromLegacy(final String legacyText) {
        if (legacyText == null || legacyText.isEmpty()) {
            return Component.empty();
        }
        return legacySerializer.deserialize(legacyText);
    }

    public String toLegacy(final Component component) {
        if (component == null) {
            return "";
        }
        return legacySerializer.serialize(component);
    }

    public Map<String, String> extractPlaceholders(final String input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> placeholders = new HashMap<>();
        Matcher matcher = PERCENT_PATTERN.matcher(input);
        while (matcher.find()) {
            placeholders.put(matcher.group(1), "");
        }
        return Collections.unmodifiableMap(placeholders);
    }

    private String normalizePercentMarkers(final String input) {
        Matcher matcher = PERCENT_PATTERN.matcher(input);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String placeholderName = matcher.group(1);
            matcher.appendReplacement(result, "<" + INTERNAL_TAG_PREFIX + placeholderName + ">");
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String normalizePlaceholderKey(final String key) {
        if (key == null) {
            return "";
        }
        if (key.startsWith("%") && key.endsWith("%")) {
            return key.substring(1, key.length() - 1);
        }
        return key;
    }

    private void logInvalidMiniMessage(final String messageKey, final Exception e, final Logger logger) {
        if (logger != null) {
            logger.warning("Invalid MiniMessage for key '" + messageKey + "': " + e.getMessage());
        }
    }

    private Component errorComponent(final String text) {
        return Component.text()
                .content(text)
                .color(NamedTextColor.RED)
                .build();
    }

    public String renderToLegacy(final String template, final Map<String, String> stringValues,
                                final Map<String, Component> componentValues, final String messageKey) {
        Component component = render(template, stringValues, componentValues, messageKey, null);
        return legacySerializer.serialize(component);
    }

    public String renderToLegacy(final String template, final Map<String, String> placeholders) {
        return renderToLegacy(template, placeholders, Collections.emptyMap(), null);
    }

    public String renderToLegacy(final String template) {
        return renderToLegacy(template, Collections.emptyMap());
    }

    public Component renderToComponent(final String template, final Map<String, String> stringValues,
                                     final Map<String, Component> componentValues, final String messageKey) {
        return render(template, stringValues, componentValues, messageKey, null);
    }

    public Component renderToComponent(final String template) {
        return renderToComponent(template, Collections.emptyMap(), Collections.emptyMap(), null);
    }

    public String renderToMiniMessage(final String template, final Map<String, String> stringValues,
                                     final Map<String, Component> componentValues, final String messageKey) {
        Component component = render(template, stringValues, componentValues, messageKey, null);
        return miniMessage.serialize(component);
    }

    public String renderToMiniMessage(final String template, final Map<String, String> stringValues,
                                     final String messageKey) {
        return renderToMiniMessage(template, stringValues, Collections.emptyMap(), messageKey);
    }
}
