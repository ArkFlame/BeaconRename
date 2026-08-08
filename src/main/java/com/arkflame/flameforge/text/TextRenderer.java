package com.arkflame.flameforge.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextRenderer {
    private static final Pattern PERCENT_PATTERN = Pattern.compile("%([A-Za-z0-9_-]+)%");
    private static final String INTERNAL_TAG_PREFIX = "ff_";

    private final MiniMessage miniMessage;
    private final LegacyComponentSerializer legacySerializer;
    private final LegacyComponentSerializer itemLegacySerializer;

    public TextRenderer() {
        this.miniMessage = MiniMessage.miniMessage();
        this.legacySerializer = LegacyComponentSerializer.legacySection();
        this.itemLegacySerializer = this.legacySerializer;
    }

    public TextRenderer(final LegacyComponentSerializer itemLegacySerializer) {
        this.miniMessage = MiniMessage.miniMessage();
        this.legacySerializer = LegacyComponentSerializer.legacySection();
        this.itemLegacySerializer = itemLegacySerializer;
    }

    public Component render(final String input, final Map<String, String> stringValues,
                           final Map<String, Component> componentValues, final String messageKey,
                           final Logger logger) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }

        String normalized = normalizePercentMarkers(input);

        TagResolver.Builder resolverBuilder = TagResolver.builder();

        Set<String> referencedPlaceholders = extractPlaceholderNames(input);

        if (stringValues != null) {
            for (Map.Entry<String, String> entry : stringValues.entrySet()) {
                String normalizedKey = normalizePlaceholderKey(entry.getKey());
                String tagName = INTERNAL_TAG_PREFIX + normalizedKey;
                String value = entry.getValue() != null ? entry.getValue() : "";
                resolverBuilder.resolver(Placeholder.unparsed(tagName, value));
                referencedPlaceholders.remove(normalizedKey);
            }
        }

        if (componentValues != null) {
            for (Map.Entry<String, Component> entry : componentValues.entrySet()) {
                String normalizedKey = normalizePlaceholderKey(entry.getKey());
                String tagName = INTERNAL_TAG_PREFIX + normalizedKey;
                Component value = entry.getValue() != null ? entry.getValue() : Component.empty();
                resolverBuilder.resolver(Placeholder.component(tagName, value));
                referencedPlaceholders.remove(normalizedKey);
            }
        }

        for (String missingKey : referencedPlaceholders) {
            String tagName = INTERNAL_TAG_PREFIX + missingKey;
            resolverBuilder.resolver(Placeholder.unparsed(tagName, ""));
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

    private Set<String> extractPlaceholderNames(final String input) {
        Set<String> names = new HashSet<>();
        Matcher matcher = PERCENT_PATTERN.matcher(input);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
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

    public String renderItemLegacy(final String template, final MessageArguments arguments, final String messageKey) {
        if (template == null) {
            return "";
        }
        Component component = renderComponent(template, arguments, messageKey);
        component = component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        return itemLegacySerializer.serialize(component);
    }

    public List<String> renderItemLore(final List<String> templates, final MessageArguments arguments, final String messageKey) {
        if (templates == null || templates.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (String line : templates) {
            if (line == null) {
                continue;
            }
            Component component = renderComponent(line, arguments, messageKey);
            component = component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
            result.add(itemLegacySerializer.serialize(component));
        }
        return result;
    }

    public Component renderComponent(final String template, final MessageArguments arguments, final String messageKey) {
        if (template == null || template.isEmpty()) {
            return Component.empty();
        }
        Map<String, String> stringValues = arguments != null ? arguments.getStringValues() : Collections.emptyMap();
        Map<String, Component> componentValues = arguments != null ? arguments.getComponentValues() : Collections.emptyMap();
        return render(template, stringValues, componentValues, messageKey, null);
    }
}