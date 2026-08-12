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
}
