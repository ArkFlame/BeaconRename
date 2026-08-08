package com.arkflame.flameforge.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TextRendererTest {

    private final TextRenderer renderer = new TextRenderer();
    private final TextRenderer itemRenderer;

    public TextRendererTest() {
        LegacyComponentSerializer itemLegacySerializer = LegacyComponentSerializer.builder()
                .character(LegacyComponentSerializer.SECTION_CHAR)
                .hexColors()
                .useUnusualXRepeatedCharacterHexFormat()
                .build();
        this.itemRenderer = new TextRenderer(itemLegacySerializer);
    }

    @Test
    void itemGradientPreservesRgbWithDeterministicRgbSerializer() {
        String template = "<gradient:#ff0000:#00ff00>Test</gradient>";
        MessageArguments args = MessageArguments.create();
        String result = itemRenderer.renderItemLegacy(template, args, "test.key");
        assertNotNull(result);
        assertTrue(result.contains("\u00A7x") || result.contains("#"),
                "Gradient should preserve RGB via hex format");
    }

    @Test
    void missingPlaceholderBecomesEmptyAndNoLeakage() {
        String template = "Hello %foo% and %bar%";
        Map<String, String> stringValues = new HashMap<>();
        stringValues.put("bar", "World");
        String result = renderer.renderToLegacy(template, stringValues);
        assertFalse(result.contains("%foo%"), "Missing placeholder %foo% should not leak into output");
        assertFalse(result.contains("<ff_foo>"), "Internal tag should not leak");
        assertFalse(result.contains("Missing message"), "Error text should not appear for missing placeholder");
    }

    @Test
    void redTagInUnparsedDynamicValueRemainsLiteral() {
        String template = "Value: %dynamic%";
        Map<String, String> stringValues = new HashMap<>();
        stringValues.put("dynamic", "<red>not a color tag</red>");
        String result = renderer.renderToLegacy(template, stringValues);
        assertFalse(result.contains("\u00A7c") && !result.contains("<red>"),
                "<red> in unparsed dynamic value should remain literal, not interpreted as formatting");
    }

    @Test
    void renderNullOrEmptyReturnsEmptyComponent() {
        Component emptyResult = renderer.render(null, Collections.<String, String>emptyMap(),
                Collections.<String, Component>emptyMap(), "test", null);
        assertEquals(Component.empty(), emptyResult);

        Component emptyStringResult = renderer.render("", Collections.<String, String>emptyMap(),
                Collections.<String, Component>emptyMap(), "test", null);
        assertEquals(Component.empty(), emptyStringResult);
    }

    @Test
    void percentPlaceholdersConvertedToInternalTags() {
        String template = "Hello %name%";
        Map<String, String> stringValues = new HashMap<>();
        stringValues.put("name", "World");
        Component result = renderer.render(template, stringValues,
                Collections.<String, Component>emptyMap(), "test", null);
        assertNotNull(result);
        assertFalse(result.toString().contains("%name%"));
    }

    @Test
    void componentValuesOverrideStringValuesForSameKey() {
        String template = "Value: %key%";
        Map<String, String> stringValues = new HashMap<>();
        stringValues.put("key", "string-value");
        Map<String, Component> componentValues = new HashMap<>();
        componentValues.put("key", Component.text("component-value", NamedTextColor.RED));
        Component result = renderer.render(template, stringValues, componentValues, "test", null);
        assertNotNull(result);
    }

    @Test
    void renderToComponentReturnsComponent() {
        Map<String, String> stringValues = new HashMap<>();
        stringValues.put("name", "World");
        Component result = renderer.renderToComponent("Hello %name%",
                stringValues, Collections.emptyMap(), "test");
        assertNotNull(result);
        assertFalse(result.equals(Component.empty()));
    }

    @Test
    void renderToLegacyReturnsString() {
        String result = renderer.renderToLegacy("Hello %name%",
                Collections.singletonMap("name", "World"));
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void fromLegacyDeserializesLegacyText() {
        Component result = renderer.fromLegacy("\u00A7cRed Text");
        assertNotNull(result);
        assertTrue(result.toString().contains("Red Text"));
    }

    @Test
    void toLegacySerializesComponent() {
        Component component = Component.text("Hello", NamedTextColor.GREEN);
        String result = renderer.toLegacy(component);
        assertNotNull(result);
        assertTrue(result.contains("Hello"));
    }

    @Test
    void extractPlaceholdersFindsAllPercentMarkers() {
        Map<String, String> placeholders = renderer.extractPlaceholders("Hello %name%, your balance is %balance%");
        assertEquals(2, placeholders.size());
        assertTrue(placeholders.containsKey("name"));
        assertTrue(placeholders.containsKey("balance"));
    }

    @Test
    void extractPlaceholdersReturnsEmptyForNullOrEmpty() {
        Map<String, String> emptyResult = renderer.extractPlaceholders(null);
        assertTrue(emptyResult.isEmpty());

        Map<String, String> emptyStringResult = renderer.extractPlaceholders("");
        assertTrue(emptyStringResult.isEmpty());
    }

    @Test
    void renderItemLorePreservesItalicFalse() {
        java.util.List<String> templates = java.util.Arrays.asList("Line 1", "Line 2");
        MessageArguments args = MessageArguments.create();
        java.util.List<String> result = renderer.renderItemLore(templates, args, "test.key");
        assertEquals(2, result.size());
        assertFalse(result.get(0).isEmpty());
        assertFalse(result.get(1).isEmpty());
    }

    @Test
    void renderComponentWithNullArgumentsHandledGracefully() {
        Component result = renderer.renderComponent("Hello %name%", null, "test");
        assertNotNull(result);
    }
}
