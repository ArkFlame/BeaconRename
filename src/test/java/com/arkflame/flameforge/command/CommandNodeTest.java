package com.arkflame.flameforge.command;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandNodeTest {

    @Test
    void catalogRoutesVisibleCommandsAndPermissionFilteredSuggestions() {
        CommandSender helpOnly = mock(CommandSender.class);
        when(helpOnly.hasPermission("flameforge.command.help")).thenReturn(true);
        assertEquals(Collections.singletonList("help"), CommandNode.permittedRootNames(helpOnly, ""));

        CommandSender admin = mock(CommandSender.class);
        when(admin.hasPermission(anyString())).thenReturn(true);
        List<String> roots = CommandNode.permittedRootNames(admin, "sta");
        assertTrue(roots.contains("station"));
        assertFalse(roots.contains("reload"));

        List<CommandNode.HelpEntry> rootChildren = CommandNode.immediateChildren(admin, Collections.emptyList());
        List<CommandNode.HelpEntry> stationChildren = CommandNode.immediateChildren(admin, Arrays.asList("station"));
        assertFalse(rootChildren.isEmpty());
        assertFalse(stationChildren.isEmpty());
        assertTrue(stationChildren.stream().allMatch(entry ->
            entry.getPath().size() == 2 && "station".equals(entry.getPath().get(0))));

        boolean userSeen = false;
        for (CommandNode.HelpEntry entry : rootChildren) {
            if (entry.getAccessClass() == CommandNode.AccessClass.USER) {
                userSeen = true;
            } else {
                assertTrue(userSeen);
            }
        }
    }

    @Test
    void testItemNodeIsAdminChildWithExactUsage() {
        CommandNode testItem = null;
        for (CommandNode node : CommandNode.values()) {
            if ("testitem".equals(node.getRoot())
                && node.getPermission().isPresent()
                && "flameforge.command.testitem".equals(node.getPermission().get())) {
                testItem = node;
            }
        }
        assertNotNull(testItem);
        assertEquals("testitem <tier> <variant> [material]", testItem.getUsage());
        assertEquals("testitem", testItem.getSuggestion());
        assertEquals("flameforge.command.testitem", testItem.getPermission().get());
        assertEquals("help.descriptions.testitem", testItem.getDescriptionKey().get());
        assertEquals(CommandNode.Category.ADMINISTRATION, testItem.getCategory());
        assertEquals(CommandNode.AccessClass.ADMIN, testItem.getAccessClass());
        assertTrue(testItem.isReadyOnly());
        assertFalse(testItem.isAlias());
    }

    @Test
    void setupTierUsagesUseLevelNotPriority() {
        assertEquals("setup tier create <id> <level>", CommandNode.SETUP_TIER_CREATE.getUsage());
        assertEquals("setup tier clone <source> <id> <level>", CommandNode.SETUP_TIER_CLONE.getUsage());
        assertFalse(CommandNode.SETUP_TIER_CREATE.getUsage().contains("<priority>"));
        assertFalse(CommandNode.SETUP_TIER_CLONE.getUsage().contains("<priority>"));
    }

    @Test
    void weaponsMenuNodeIsAdminChildVisibleOnlyWithPermission() {
        CommandNode weaponsMenu = null;
        for (CommandNode node : CommandNode.values()) {
            if ("weaponsmenu".equals(node.getRoot())
                && node.getPermission().isPresent()
                && "flameforge.command.weaponsmenu".equals(node.getPermission().get())) {
                weaponsMenu = node;
            }
        }
        assertNotNull(weaponsMenu);
        assertEquals("weaponsmenu", weaponsMenu.getUsage());
        assertEquals("weaponsmenu", weaponsMenu.getSuggestion());
        assertEquals("flameforge.command.weaponsmenu", weaponsMenu.getPermission().get());
        assertEquals("help.descriptions.weaponsmenu", weaponsMenu.getDescriptionKey().get());
        assertEquals(CommandNode.Category.ADMINISTRATION, weaponsMenu.getCategory());
        assertEquals(CommandNode.AccessClass.ADMIN, weaponsMenu.getAccessClass());
        assertTrue(weaponsMenu.isReadyOnly());
        assertFalse(weaponsMenu.isAlias());

        CommandSender denied = mock(CommandSender.class);
        when(denied.hasPermission(anyString())).thenReturn(false);
        assertFalse(weaponsMenu.isPermitted(denied));
        assertFalse(weaponsMenu.visibleTo(denied));

        CommandSender admin = mock(CommandSender.class);
        when(admin.hasPermission(anyString())).thenReturn(true);
        assertTrue(weaponsMenu.isPermitted(admin));
        assertTrue(weaponsMenu.visibleTo(admin));
        assertTrue(CommandNode.permittedRootNames(admin, "weap").contains("weaponsmenu"));
    }
}
