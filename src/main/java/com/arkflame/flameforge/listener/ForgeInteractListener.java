package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.ForgeAccessService;
import com.arkflame.flameforge.compat.interaction.InteractionHandBridge;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.station.ForgeStationService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;

public final class ForgeInteractListener implements Listener {

    private final JavaPlugin plugin;
    private final ForgeAccessService accessService;
    private final ForgeStationService stationService;
    private final InteractionHandBridge handBridge;

    public ForgeInteractListener(JavaPlugin plugin, ForgeAccessService accessService,
                                 ForgeStationService stationService, InteractionHandBridge handBridge) {
        this.plugin = plugin;
        this.accessService = accessService;
        this.stationService = stationService;
        this.handBridge = handBridge;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event == null) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.isCancelled()) {
            return;
        }

        if (event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) {
            return;
        }

        if (!handBridge.isPrimary(event)) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        org.bukkit.block.Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        java.util.Optional<StationRepository.StationData> stationOpt = stationService.resolveStationAt(clickedBlock);
        if (!stationOpt.isPresent()) {
            return;
        }

        event.setCancelled(true);
        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);

        Player playerRef = player;
        String stationId = stationOpt.get().id;
        accessService.openForgeFromId(playerRef, stationId).thenAccept(result -> {
            if (result.getStatus() != ForgeAccessService.OpenStatus.OPENED) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!playerRef.isOnline()) {
                        return;
                    }
                    playerRef.sendMessage("Failed to open forge: " + result.getStatus().name()
                        + (result.getStationId() != null ? " [" + result.getStationId() + "]" : ""));
                });
            }
        });
    }
}
