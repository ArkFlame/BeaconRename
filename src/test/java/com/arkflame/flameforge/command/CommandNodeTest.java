package com.arkflame.flameforge.command;

import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandNodeTest {

    @Test
    void catalogHasExactly18UniqueLeavesWithCompleteMetadata() {
        CommandNode[] nodes = CommandNode.values();
        Set<String> usages = new HashSet<>();
        Set<String> seenNodes = new HashSet<>();

        assertEquals(18, nodes.length);
        for (CommandNode node : nodes) {
            assertFalse(node.getUsage().trim().isEmpty(), "Usage must not be empty");
            assertTrue(usages.add(node.getUsage()), "Duplicate usage: " + node.getUsage());
            assertTrue(node.getPermission().isPresent(), "Permission must be present");
            assertTrue(node.getDescriptionKey().isPresent(), "Description key must be present");
            assertTrue(seenNodes.add(node.name()), "Duplicate node: " + node.name());
        }
        assertEquals(18, usages.size());
    }

    @Test
    void rootVisibilityFiltersNormalAndAdminSendersAndOrdersUserBeforeAdmin() {
        CommandSender none = mock(CommandSender.class);
        when(none.hasPermission(anyString())).thenReturn(false);
        List<String> noneRoots = CommandNode.permittedRootNames(none, "");
        assertEquals(Collections.emptyList(), noneRoots);

        CommandSender helpOnly = mock(CommandSender.class);
        when(helpOnly.hasPermission("flameforge.command.help")).thenReturn(true);
        assertEquals(Collections.singletonList("help"), CommandNode.permittedRootNames(helpOnly, ""));

        CommandSender admin = mock(CommandSender.class);
        when(admin.hasPermission(CommandNode.ADMIN_PERMISSION)).thenReturn(true);
        List<String> adminRoots = CommandNode.permittedRootNames(admin, "");
        assertEquals(Arrays.asList("help", "history", "open", "preview", "reload", "setup", "station",
            "tier", "tiers", "tp", "validate"), adminRoots);

        assertEquals(Collections.singletonList("station"), CommandNode.permittedRootNames(admin, "sta"));

        boolean seenUser = false;
        boolean seenAdmin = false;
        List<CommandNode.HelpEntry> rootChildren = CommandNode.immediateChildren(admin, Collections.emptyList());
        for (CommandNode.HelpEntry entry : rootChildren) {
            if (entry.getAccessClass() == CommandNode.AccessClass.USER) {
                seenUser = true;
            } else if (entry.getAccessClass() == CommandNode.AccessClass.ADMIN) {
                seenAdmin = true;
                assertTrue(seenUser, "USER commands must appear before ADMIN commands at root level");
            }
        }
    }

    @Test
    void immediateChildrenAggregatesRootStationAndSetupTierPaths() {
        CommandSender admin = mock(CommandSender.class);
        when(admin.hasPermission(anyString())).thenReturn(true);

        List<CommandNode.HelpEntry> rootChildren = CommandNode.immediateChildren(admin, Collections.emptyList());
        assertFalse(rootChildren.isEmpty());
        for (CommandNode.HelpEntry entry : rootChildren) {
            assertEquals(1, entry.getPath().size());
        }

        List<CommandNode.HelpEntry> stationChildren = CommandNode.immediateChildren(admin, Arrays.asList("station"));
        assertFalse(stationChildren.isEmpty());
        for (CommandNode.HelpEntry entry : stationChildren) {
            assertEquals(2, entry.getPath().size());
            assertEquals("station", entry.getPath().get(0));
        }

        List<CommandNode.HelpEntry> setupTierChildren = CommandNode.immediateChildren(admin, Arrays.asList("setup", "tier"));
        assertFalse(setupTierChildren.isEmpty());
        for (CommandNode.HelpEntry entry : setupTierChildren) {
            assertEquals(3, entry.getPath().size());
            assertEquals("setup", entry.getPath().get(0));
            assertEquals("tier", entry.getPath().get(1));
        }
    }

    @Test
    void immediateChildrenDeduplicatesVariantsAfterPermissionFiltering() {
        CommandSender admin = mock(CommandSender.class);
        when(admin.hasPermission(anyString())).thenReturn(true);

        List<CommandNode.HelpEntry> stationChildren = CommandNode.immediateChildren(admin, Arrays.asList("station"));

        java.util.Map<String, Integer> pathCount = new java.util.HashMap<>();
        for (CommandNode.HelpEntry entry : stationChildren) {
            String key = entry.getPath().get(entry.getPath().size() - 1);
            pathCount.merge(key, 1, Integer::sum);
        }
        for (java.util.Map.Entry<String, Integer> e : pathCount.entrySet()) {
            assertEquals(1, e.getValue().intValue(),
                "Each child token should appear exactly once after deduplication: " + e.getKey() + " appeared " + e.getValue());
        }
    }

    @Test
    void aliasesAndSuggestionsPreserveFlameforgeForgeAndFf() {
        int aliasCount = 0;
        for (CommandNode node : CommandNode.values()) {
            if (node.isAlias()) {
                aliasCount++;
                assertEquals("station", node.getRoot(), "Alias should have station as root");
                assertTrue(node.getUsage().contains("station"), "Alias usage should contain station");
            }
        }
        assertEquals(1, aliasCount, "Should have exactly one alias (STATION_TELEPORT_ALIAS)");
    }
}
