package com.arkflame.flameforge.text;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextRendererTest {

    private final TextRenderer renderer = new TextRenderer();

    @Test
    void renderHandlesMiniMessageLegacyAndPercentPlaceholders() {
        Component rendered = renderer.render("<green>Hello %name%</green>",
            Collections.singletonMap("name", "World"), Collections.emptyMap(), "test", null);
        String legacy = renderer.toLegacy(rendered);

        assertNotNull(rendered);
        assertTrue(legacy.contains("World"));
        assertFalse(legacy.contains("%name%"));
    }

    @Test
    void dynamicValuesRemainLiteralAndMissingValuesDoNotLeakTokens() {
        Map<String, String> values = new HashMap<>();
        values.put("dynamic", "<red>literal value</red>");

        String result = renderer.renderToLegacy("Value %dynamic% and %missing%", values);

        assertTrue(result.contains("<red>literal value</red>"));
        assertFalse(result.contains("%missing%"));
    }

    @Test
    void itemLoreRenderingProducesUsableLegacyOutput() {
        MessageArguments arguments = MessageArguments.create().string("value", "text");
        List<String> rendered = renderer.renderItemLore(
            Arrays.asList("First %value%", "<blue>Second"), arguments, "test");

        assertFalse(rendered.isEmpty());
        assertTrue(rendered.stream().allMatch(line -> line != null && !line.isEmpty()));
        assertTrue(rendered.stream().noneMatch(line -> line.contains("%value%")));
    }
}
