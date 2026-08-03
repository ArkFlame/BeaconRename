package com.arkflame.flameforge.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEvent.Action;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TextPlaceholdersTest {

    private TextPlaceholders placeholders;

    @BeforeEach
    void setUp() {
        placeholders = new TextPlaceholders();
    }

    private Player createMockPlayer(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        org.bukkit.World mockWorld = mock(org.bukkit.World.class);
        when(mockWorld.getName()).thenReturn("world");
        when(player.getWorld()).thenReturn(mockWorld);
        org.bukkit.Server mockServer = mock(org.bukkit.Server.class);
        when(mockServer.getOnlinePlayers()).thenReturn(new java.util.ArrayList<>());
        when(mockServer.getMaxPlayers()).thenReturn(20);
        when(player.getServer()).thenReturn(mockServer);
        return player;
    }

    @Test
    void defaultAndRegisteredStringPlaceholdersResolveForPlayer() {
        Player player = createMockPlayer("TestPlayer");
        Map<String, String> resolved = placeholders.resolveStringValues(player);

        assertEquals("TestPlayer", resolved.get("%player_name%"));
        assertEquals("TestPlayer", resolved.get("%player%"));
        assertEquals("world", resolved.get("%world%"));

        placeholders.registerReplacement("%custom%", "customValue");
        placeholders.registerReplacement("%dynamic%", p -> p != null ? p.getName() + "_suffix" : "???");

        Map<String, String> resolvedAfter = placeholders.resolveStringValues(player);
        assertEquals("customValue", resolvedAfter.get("%custom%"));
        assertEquals("TestPlayer_suffix", resolvedAfter.get("%dynamic%"));
    }

    @Test
    void registeredComponentPlaceholdersPreserveComponents() {
        Component goldComponent = Component.text("GoldText").color(net.kyori.adventure.text.format.NamedTextColor.GOLD);
        Component hoverComponent = Component.text("HoverMe")
                .hoverEvent(HoverEvent.showText(Component.text("Tooltip")));

        placeholders.registerComponent("%gold%", goldComponent);
        placeholders.registerComponent("%hover%", hoverComponent);

        Map<String, Component> resolved = placeholders.resolveComponentValues(null);
        assertEquals(goldComponent, resolved.get("%gold%"));

        Component resolvedHover = resolved.get("%hover%");
        assertNotNull(resolvedHover);
        assertTrue(resolvedHover.toString().contains("HoverMe"));
    }

    @Test
    void unregisterAndClearRemoveBothAuthorities() {
        placeholders.registerReplacement("%test%", "value");
        placeholders.registerComponent("%comp%", Component.text("test"));

        assertTrue(placeholders.getRegisteredNames().contains("%test%"));
        assertTrue(placeholders.getRegisteredNames().contains("%comp%"));

        placeholders.unregisterReplacement("%test%");
        placeholders.unregisterComponent("%comp%");

        assertFalse(placeholders.getRegisteredNames().contains("%test%"));
        assertFalse(placeholders.getRegisteredNames().contains("%comp%"));

        placeholders.registerReplacement("%another%", "value2");
        placeholders.registerComponent("%another_comp%", Component.text("test2"));
        placeholders.clearAll();

        assertFalse(placeholders.getRegisteredNames().contains("%another%"));
        assertFalse(placeholders.getRegisteredNames().contains("%another_comp%"));
    }

    @Test
    void nullRegistrationInputsAreIgnoredAndNullPlayerOmitsDynamicValues() {
        placeholders.registerReplacement(null, "ignored");
        placeholders.registerReplacement(null, p -> "ignored");
        placeholders.registerReplacement("%valid%", (String) null);
        placeholders.registerComponent(null, Component.text("ignored"));
        placeholders.registerComponent(null, p -> Component.text("ignored"));
        placeholders.registerComponent("%valid%", (Component) null);

        Map<String, String> stringResolved = placeholders.resolveStringValues(null);
        assertNull(stringResolved.get("%player_name%"));
        assertNull(stringResolved.get("%world%"));

        Map<String, Component> compResolved = placeholders.resolveComponentValues(null);
        assertNull(compResolved.get("%health%"));
        assertNull(compResolved.get("%food%"));
    }
}
