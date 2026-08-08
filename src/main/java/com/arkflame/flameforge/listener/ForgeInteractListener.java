package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.ForgeAccessService;
import com.arkflame.flameforge.compat.interaction.InteractionHandBridge;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.station.ForgeStationService;
import com.arkflame.flameforge.text.MessageArguments;
import com.arkflame.flameforge.text.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public final class ForgeInteractListener implements Listener {

    private final ForgeAccessService accessService;
    private final ForgeStationService stationService;
    private final InteractionHandBridge handBridge;
    private final MessageService messageService;

    public ForgeInteractListener(ForgeAccessService accessService,
                                 ForgeStationService stationService, InteractionHandBridge handBridge,
                                 MessageService messageService) {
        this.accessService = accessService;
        this.stationService = stationService;
        this.handBridge = handBridge;
        this.messageService = messageService;
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
            if (!result.getStatus().equals(ForgeAccessService.OpenStatus.OPENED)) {
                if (!playerRef.isOnline()) {
                    return;
                }
                MessageArguments args = MessageArguments.create()
                    .string("station_id", result.getStationId() != null ? result.getStationId() : "")
                    .string("reason", result.getReason() != null ? result.getReason() : "")
                    .string("reference", result.getReference() != null ? result.getReference() : "");
                String messageKey = mapStatusToMessageKey(result.getStatus());
                messageService.send(playerRef, messageKey, args);
            }
        });
    }

    private String mapStatusToMessageKey(ForgeAccessService.OpenStatus status) {
        switch (status) {
            case FORGE_NOT_FOUND:
                return "open.forge-not-found";
            case PROFILE_NOT_FOUND:
                return "open.profile-not-found";
            case PERMISSION_REQUIRED:
                return "open.station-permission-required";
            case NO_ALLOWED_TIER:
                return "open.no-allowed-tier";
            case SCHEDULER_REJECTED:
                return "open.scheduler-rejected";
            case MENU_OPEN_FAILED:
                return "open.menu-open-failed";
            default:
                return "open.forge-not-found";
        }
    }
}
