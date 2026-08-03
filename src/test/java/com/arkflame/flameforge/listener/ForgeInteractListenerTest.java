package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.ForgeAccessService;
import com.arkflame.flameforge.compat.interaction.InteractionHandBridge;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.station.ForgeStationService;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeInteractListenerTest {

    private ForgeInteractListener listener;
    private ForgeAccessService accessService;
    private ForgeStationService stationService;
    private InteractionHandBridge handBridge;
    private JavaPlugin fakePlugin;

    @BeforeEach
    void setUp() throws Exception {
        fakePlugin = mock(JavaPlugin.class);
        when(fakePlugin.getLogger()).thenReturn(Logger.getLogger("ForgeInteractListenerTest"));
        accessService = mock(ForgeAccessService.class);
        stationService = mock(ForgeStationService.class);
        handBridge = new InteractionHandBridge(Logger.getLogger("ForgeInteractListenerTest"));
        listener = new ForgeInteractListener(fakePlugin, accessService, stationService, handBridge);
    }

    @Test
    void registeredInteractiveAndOrdinaryMaterialsAreCancelledDeniedAndOpenedOnce() {
        Material[] materials = {Material.BEACON, Material.ANVIL, Material.CHEST, Material.STONE};
        for (Material material : materials) {
            Player player = mock(Player.class);
            when(player.isOnline()).thenReturn(true);

            Block block = mock(Block.class);
            when(block.getType()).thenReturn(material);

            PlayerInteractEvent event = new PlayerInteractEvent(
                player, Action.RIGHT_CLICK_BLOCK, null, block, null
            );

            StationRepository.StationData stationData = new StationRepository.StationData(
                "forge-1", "world", 0, 0, 0, "default"
            );
            when(stationService.resolveStationAt(block)).thenReturn(Optional.of(stationData));
            when(accessService.openForgeFromId(any(Player.class), eq("forge-1"))).thenReturn(
    CompletableFuture.completedFuture(ForgeAccessService.OpenResult.opened("test-id"))
);

            listener.onPlayerInteract(event);

            assertTrue(event.isCancelled(), "Event should be cancelled for " + material);
            assertEquals(Event.Result.DENY, event.useInteractedBlock(), "Interacted block should be denied for " + material);
            assertEquals(Event.Result.DENY, event.useItemInHand(), "Item in hand should be denied for " + material);
            verify(accessService, times(1)).openForgeFromId(eq(player), eq("forge-1"));
        }
    }

    @Test
    void unregisteredBlockLeavesVanillaInteractionUntouched() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);

        Block block = mock(Block.class);

        PlayerInteractEvent event = new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, null, block, null
        );

        when(stationService.resolveStationAt(block)).thenReturn(Optional.empty());

        listener.onPlayerInteract(event);

        assertFalse(event.isCancelled(), "Event should NOT be cancelled for unregistered block");
        verify(accessService, never()).openForgeFromId(any(), any());
    }

    @Test
    void cancelledOrDeniedEventIsNotModified() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);

        Block block = mock(Block.class);

        PlayerInteractEvent cancelledEvent = new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, null, block, null
        );
        cancelledEvent.setCancelled(true);

        StationRepository.StationData stationData = new StationRepository.StationData(
            "forge-1", "world", 0, 0, 0, "default"
        );
        when(stationService.resolveStationAt(block)).thenReturn(Optional.of(stationData));

        listener.onPlayerInteract(cancelledEvent);

        assertTrue(cancelledEvent.isCancelled(), "Pre-cancelled event should remain cancelled");
        verify(accessService, never()).openForgeFromId(any(), any());

        Player player2 = mock(Player.class);
        when(player2.isOnline()).thenReturn(true);
        Block block2 = mock(Block.class);

        PlayerInteractEvent deniedEvent = new PlayerInteractEvent(
            player2, Action.RIGHT_CLICK_BLOCK, null, block2, null
        );
        deniedEvent.setUseInteractedBlock(Event.Result.DENY);

        StationRepository.StationData stationData2 = new StationRepository.StationData(
            "forge-2", "world", 1, 1, 1, "default"
        );
        when(stationService.resolveStationAt(block2)).thenReturn(Optional.of(stationData2));

        listener.onPlayerInteract(deniedEvent);

        assertEquals(Event.Result.DENY, deniedEvent.useInteractedBlock(), "Deny should remain deny");
        verify(accessService, never()).openForgeFromId(any(), any());
    }

    @Test
    void offHandInteractionIsIgnored() {
        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);

        Block block = mock(Block.class);

        PlayerInteractEvent event = new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, null, block, null
        );

        StationRepository.StationData stationData = new StationRepository.StationData(
            "forge-1", "world", 0, 0, 0, "default"
        );
        when(stationService.resolveStationAt(block)).thenReturn(Optional.of(stationData));

        InteractionHandBridge offHandBridge = mock(InteractionHandBridge.class);
        when(offHandBridge.isPrimary(event)).thenReturn(false);

        ForgeInteractListener offHandListener = new ForgeInteractListener(
            fakePlugin, accessService, stationService, offHandBridge
        );
        offHandListener.onPlayerInteract(event);

        assertFalse(event.isCancelled(), "Off-hand click should not cancel event");
        verify(accessService, never()).openForgeFromId(any(), any());
    }

    @Test
    void legacyEventWithoutHandIsAccepted() {
        InteractionHandBridge legacyBridge = new InteractionHandBridge(
            Logger.getLogger("LegacyTest")
        );

        ForgeInteractListener legacyListener = new ForgeInteractListener(
            fakePlugin, accessService, stationService, legacyBridge
        );

        Player player = mock(Player.class);
        when(player.isOnline()).thenReturn(true);

        Block block = mock(Block.class);
        when(block.getType()).thenReturn(Material.BEACON);

        PlayerInteractEvent event = new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, null, block, null
        );

        StationRepository.StationData stationData = new StationRepository.StationData(
            "forge-1", "world", 0, 0, 0, "default"
        );
        when(stationService.resolveStationAt(block)).thenReturn(Optional.of(stationData));
        when(accessService.openForgeFromId(any(Player.class), eq("forge-1"))).thenReturn(
    CompletableFuture.completedFuture(ForgeAccessService.OpenResult.opened("test-id"))
);

        legacyListener.onPlayerInteract(event);

        assertTrue(event.isCancelled(), "Event should be cancelled for legacy player");
        verify(accessService, times(1)).openForgeFromId(eq(player), eq("forge-1"));
    }
}
