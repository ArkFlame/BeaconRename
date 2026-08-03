package com.arkflame.flameforge.text;

import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.testfakes.FakeTextBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageServiceTest {

    @Mock
    private ConfigService configService;

    private TextRenderer renderer;
    private FakeTextBridge textBridge;
    private TextPlaceholders placeholders;
    private MessageService messageService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.initMocks(this);
        renderer = new TextRenderer();
        textBridge = new FakeTextBridge();
        placeholders = new TextPlaceholders();
        messageService = MessageService.create(configService, renderer, textBridge, placeholders, null);
    }

    private Player createMockPlayer() {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("TestPlayer");
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
    void snapshotOverridesBundledFallbackAndMissingKeyReturnsControlledError() {
        ConfigSnapshot snapshotWithKey = ConfigSnapshot.builder()
                .putMessage("test.key", Collections.singletonMap("message", "Snapshot Value"))
                .build();
        when(configService.getCurrentSnapshot()).thenReturn(snapshotWithKey);

        Component result = messageService.renderToComponent("test.key", (CommandSender) null);
        assertEquals("Snapshot Value", renderer.toLegacy(result));

        when(configService.getCurrentSnapshot()).thenReturn(ConfigSnapshot.builder().build());

        Component errorResult = messageService.renderToComponent("nonexistent.key", (CommandSender) null);
        assertTrue(renderer.toLegacy(errorResult).contains("Missing message key"));
    }

    @Test
    void stringAndComponentPlaceholdersRenderSafelyAndUnknownPlaceholdersAreSuppressed() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .putMessage("test", Collections.singletonMap("message", "Hello %name% see %comp%"))
                .build();
        when(configService.getCurrentSnapshot()).thenReturn(snapshot);

        Component result = messageService.renderToComponent("test", (CommandSender) null,
                Collections.singletonMap("name", "Player"),
                Collections.singletonMap("comp", Component.text("Component").color(NamedTextColor.GOLD)));

        String legacy = renderer.toLegacy(result);
        assertTrue(legacy.contains("Hello Player"));
        assertTrue(legacy.contains("Component"));
    }

    @Test
    void validGradientAndLineTemplatesRenderWithoutFormatError() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .putMessage("gradient", Collections.singletonMap("message", "<gradient:#ff0000:#00ff00>Forge</gradient>"))
                .putMessage("lines", Collections.singletonMap("lines", java.util.Arrays.asList("Line 1", "Line 2", "Line 3")))
                .build();
        when(configService.getCurrentSnapshot()).thenReturn(snapshot);

        Component gradientResult = messageService.renderToComponent("gradient", (CommandSender) null);
        assertFalse(renderer.toLegacy(gradientResult).contains("Message format error"));

        java.util.List<Component> lineResults = messageService.renderLinesToComponents("lines", (CommandSender) null);
        assertEquals(3, lineResults.size());
    }

    @Test
    void sendAndSendComponentDeliverThroughTextBridgeForPlayerAndConsole() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .putMessage("test", Collections.singletonMap("message", "Test Message"))
                .build();
        when(configService.getCurrentSnapshot()).thenReturn(snapshot);

        Player player = createMockPlayer();
        CommandSender console = mock(CommandSender.class);

        messageService.send(player, "test");
        messageService.send(console, "test");
        messageService.sendComponent(player, Component.text("Direct"));
        messageService.sendComponent(console, Component.text("Direct"));

        assertNotNull(messageService.getRenderer());
        assertNotNull(messageService.getTextBridge());
    }

    @Test
    void nullRecipientsAndOptionalTitleActionBarCallsAreSafeNoOps() {
        assertDoesNotThrow(() -> messageService.send(null, "any.key"));
        assertDoesNotThrow(() -> messageService.sendComponent((Player) null, Component.text("test")));
        assertDoesNotThrow(() -> messageService.sendComponent((CommandSender) null, Component.text("test")));
        assertDoesNotThrow(() -> messageService.sendTitle(null, "any.key"));
        assertDoesNotThrow(() -> messageService.sendActionBar(null, "any.key"));
    }

    @Test
    void serviceExposesInjectedRendererAndTextBridge() {
        assertSame(renderer, messageService.getRenderer());
        assertSame(textBridge, messageService.getTextBridge());
    }
}
