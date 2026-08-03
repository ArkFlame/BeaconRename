package com.arkflame.flameforge.text;

import com.arkflame.flameforge.testfakes.FakeTextBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TextBridgeTest {

    @Test
    void renderSupportsNullEmptyAndValidMiniMessageInputs() {
        FakeTextBridge bridge = new FakeTextBridge();

        Component nullResult = bridge.render(null);
        assertEquals(Component.empty(), nullResult);

        Component emptyResult = bridge.render("");
        assertEquals(Component.empty(), emptyResult);

        Component validResult = bridge.render("<red>Hello</red>");
        assertNotNull(validResult);
    }

    @Test
    void sendBroadcastTitleAndActionBarDelegateWhileOpen() {
        FakeTextBridge bridge = new FakeTextBridge();
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());

        assertDoesNotThrow(() -> bridge.broadcast(Component.text("Broadcast")));
        assertDoesNotThrow(() -> bridge.sendAll(Component.text("SendAll")));
        assertDoesNotThrow(() -> bridge.send(player, Component.text("Send")));
        assertDoesNotThrow(() -> bridge.sendTitle(player, Component.text("Title"), Component.text("Subtitle"), 10, 70, 20));
        assertDoesNotThrow(() -> bridge.sendActionBar(player, Component.text("ActionBar")));
        assertDoesNotThrow(() -> bridge.clearTitle(player));
    }

    @Test
    void closeIsIdempotentAndSubsequentSendsAreSafeNoOps() {
        FakeTextBridge bridge = new FakeTextBridge();

        assertFalse(bridge.isClosed());
        bridge.close();
        assertTrue(bridge.isClosed());
        bridge.close();
        assertTrue(bridge.isClosed());

        Player player = mock(Player.class);
        assertDoesNotThrow(() -> bridge.send(player, Component.text("After close")));
        assertDoesNotThrow(() -> bridge.broadcast(Component.text("After close")));
        assertDoesNotThrow(() -> bridge.sendAll(Component.text("After close")));
        assertDoesNotThrow(() -> bridge.sendTitle(player, Component.text("Title"), Component.text("Subtitle"), 10, 70, 20));
        assertDoesNotThrow(() -> bridge.sendActionBar(player, Component.text("ActionBar")));
    }
}
