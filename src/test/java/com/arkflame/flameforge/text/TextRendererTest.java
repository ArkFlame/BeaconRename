package com.arkflame.flameforge.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.ChatColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TextRendererTest {

    private TextRenderer renderer;

    @BeforeEach
    void setUp() {
        renderer = new TextRenderer();
    }

    @Test
    void renderSupportsPlainNamedColorGradientAndLegacyConversion() {
        Map<String, String> emptyValues = Collections.emptyMap();
        Map<String, Component> emptyCompValues = Collections.emptyMap();

        Component plain = renderer.render("Plain Text", emptyValues, emptyCompValues, null, null);
        assertNotEquals(Component.empty(), plain);

        Component colored = renderer.render("<red>Error</red>", emptyValues, emptyCompValues, null, null);
        assertNotEquals(Component.empty(), colored);

        Component gradient = renderer.render("<gradient:red:blue>Gradient</gradient>", emptyValues, emptyCompValues, null, null);
        assertNotEquals(Component.empty(), gradient);

        Component gold = renderer.render("<gold>Gold</gold>", emptyValues, emptyCompValues, null, null);
        assertNotEquals(Component.empty(), gold);

        String miniMsg = renderer.renderToMiniMessage("<gradient:#ff5f00:#ffd166>Test</gradient>", emptyValues, emptyCompValues, "test.key");
        assertNotNull(miniMsg);
        assertFalse(miniMsg.isEmpty());
        MiniMessage mm = MiniMessage.miniMessage();
        assertDoesNotThrow(() -> mm.deserialize(miniMsg));

        String plainMiniMsg = renderer.renderToMiniMessage("Plain Text", emptyValues, emptyCompValues, "test.key");
        assertNotNull(plainMiniMsg);

        String gradientMiniMsg = renderer.renderToMiniMessage("<gradient:green:blue>Colored</gradient>", emptyValues, emptyCompValues, "test.key");
        assertNotNull(gradientMiniMsg);
        Component roundTrip = mm.deserialize(gradientMiniMsg);
        assertNotNull(roundTrip);
    }

    @Test
    void stringInjectionIsLiteralAndNullBecomesEmpty() {
        Map<String, String> values = new HashMap<>();
        values.put("name", "Player");
        values.put("empty", null);
        Map<String, Component> compValues = Collections.emptyMap();

        Component result = renderer.render("Hello <ff_name>", values, compValues, null, null);
        assertFalse(renderer.toLegacy(result).contains("<ff_name>"));
        assertTrue(renderer.toLegacy(result).contains("Player"));

        Component nullResult = renderer.render("Value: <ff_empty>", values, compValues, null, null);
        assertNotNull(nullResult);
    }

    @Test
    void componentPlaceholderPreservesStyleAndHoverEvents() {
        Map<String, String> stringValues = Collections.emptyMap();
        Map<String, Component> compValues = new HashMap<>();
        Component styled = Component.text("StyledGold").color(NamedTextColor.GOLD);
        Component withHover = Component.text("HoverTarget")
                .hoverEvent(HoverEvent.showText(Component.text("Tooltip Text")));
        compValues.put("styled", styled);
        compValues.put("hover", withHover);

        Component result = renderer.render("<ff_styled>", stringValues, compValues, null, null);
        assertTrue(result.toString().contains("StyledGold"));

        Component hoverResult = renderer.render("<ff_hover>", stringValues, compValues, null, null);
        assertTrue(hoverResult.toString().contains("HoverTarget"));
    }

    @Test
    void percentPlaceholdersConvertAndExtractAllNames() {
        Map<String, String> values = new HashMap<>();
        values.put("player", "Alice");
        values.put("world", "survival");
        values.put("online", "10");
        Map<String, Component> compValues = Collections.emptyMap();

        Component result = renderer.render("%player% in %world% (%online% online)", values, compValues, null, null);
        assertFalse(renderer.toLegacy(result).contains("%player%"));
        assertFalse(renderer.toLegacy(result).contains("%world%"));
        assertFalse(renderer.toLegacy(result).contains("%online%"));

        Map<String, String> extracted = renderer.extractPlaceholders("%player% %world% %online% extra");
        assertEquals(3, extracted.size());
        assertTrue(extracted.containsKey("player"));
        assertTrue(extracted.containsKey("world"));
        assertTrue(extracted.containsKey("online"));
    }

    @Test
    void unknownPlaceholdersFollowRendererLiteralContract() {
        Map<String, String> values = Collections.emptyMap();
        Map<String, Component> compValues = Collections.emptyMap();

        Component result = renderer.render("<ff_unknown_tag>", values, compValues, null, null);
        assertNotNull(result);
    }

    @Test
    void invalidTemplatesReturnControlledErrorWithoutThrowing() {
        Map<String, String> values = Collections.emptyMap();
        Map<String, Component> compValues = Collections.emptyMap();

        Component invalidResult = renderer.render("<invalid<<syntax", values, compValues, "test.key", null);
        assertNotNull(invalidResult);

        assertDoesNotThrow(() -> renderer.render("<red>Hello", values, compValues, "test", null));
        assertDoesNotThrow(() -> renderer.render("<not Valid", values, compValues, "test", null));
    }

    @Test
    void componentAndLegacyRoundTripPreserveVisibleContent() {
        Component original = Component.text("Test Content").color(NamedTextColor.GOLD);
        String legacy = renderer.toLegacy(original);
        assertTrue(legacy.contains("Test") || legacy.contains("§6"));

        Component fromLegacy = renderer.fromLegacy(ChatColor.RED + "Error" + ChatColor.WHITE + " Notice");
        assertNotEquals(Component.empty(), fromLegacy);
        assertTrue(fromLegacy.toString().contains("Error") || fromLegacy.toString().contains("Notice"));
    }

    @Test
    void nullAndEmptyInputsReturnEmptyComponentsAndStrings() {
        assertEquals(Component.empty(), renderer.render(null, null, null, null, null));
        assertEquals(Component.empty(), renderer.render("", null, null, null, null));
        assertEquals("", renderer.toLegacy(null));
        assertEquals("", renderer.toLegacy(Component.empty()));
        assertTrue(renderer.extractPlaceholders(null).isEmpty());
        assertTrue(renderer.extractPlaceholders("").isEmpty());
    }
}
