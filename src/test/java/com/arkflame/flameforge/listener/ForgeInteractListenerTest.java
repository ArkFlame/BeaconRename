package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.ForgeAccessService;
import com.arkflame.flameforge.compat.interaction.InteractionHandBridge;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.station.ForgeStationService;
import com.arkflame.flameforge.text.MessageService;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ForgeInteractListenerTest {

    @Test
    void registeredForgeInteractionOpensForge() {
        ForgeAccessService accessService = mock(ForgeAccessService.class);
        ForgeStationService stationService = mock(ForgeStationService.class);
        InteractionHandBridge handBridge = mock(InteractionHandBridge.class);
        MessageService messageService = mock(MessageService.class);
        ForgeInteractListener listener = new ForgeInteractListener(
            accessService, stationService, handBridge, messageService
        );

        Player player = mock(Player.class);
        Block block = mock(Block.class);
        PlayerInteractEvent event = new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, null, block, null
        );
        StationRepository.StationData station = new StationRepository.StationData(
            "station", "world", 0, 0, 0, "default"
        );

        when(handBridge.isPrimary(event)).thenReturn(true);
        when(stationService.resolveStationAt(block)).thenReturn(Optional.of(station));
        when(accessService.openForgeFromId(player, station.id)).thenReturn(
            CompletableFuture.completedFuture(ForgeAccessService.OpenResult.opened("open"))
        );

        listener.onPlayerInteract(event);

        assertTrue(event.isCancelled());
        assertEquals(Event.Result.DENY, event.useInteractedBlock());
        assertEquals(Event.Result.DENY, event.useItemInHand());
        verify(accessService).openForgeFromId(player, station.id);
    }

    @Test
    void unrelatedOrIgnoredInteractionDoesNothing() {
        ForgeAccessService accessService = mock(ForgeAccessService.class);
        ForgeStationService stationService = mock(ForgeStationService.class);
        InteractionHandBridge handBridge = mock(InteractionHandBridge.class);
        MessageService messageService = mock(MessageService.class);
        ForgeInteractListener listener = new ForgeInteractListener(
            accessService, stationService, handBridge, messageService
        );

        Player player = mock(Player.class);
        Block block = mock(Block.class);
        PlayerInteractEvent unrelated = new PlayerInteractEvent(
            player, Action.LEFT_CLICK_BLOCK, null, block, null
        );
        PlayerInteractEvent cancelled = new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, null, block, null
        );
        cancelled.setCancelled(true);
        PlayerInteractEvent offHand = new PlayerInteractEvent(
            player, Action.RIGHT_CLICK_BLOCK, null, block, null
        );
        when(handBridge.isPrimary(offHand)).thenReturn(false);

        listener.onPlayerInteract(unrelated);
        listener.onPlayerInteract(cancelled);
        listener.onPlayerInteract(offHand);

        assertFalse(unrelated.isCancelled());
        assertTrue(cancelled.isCancelled());
        assertFalse(offHand.isCancelled());
        verifyNoInteractions(accessService);
        verifyNoInteractions(stationService);
    }
}
