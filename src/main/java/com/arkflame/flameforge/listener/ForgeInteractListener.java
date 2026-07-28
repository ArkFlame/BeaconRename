package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.menu.ForgeMenuService;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.model.StationProfile;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.station.ForgeStationService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

public final class ForgeInteractListener implements Listener {

    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final ForgeStationService stationService;
    private final ForgeMenuService menuService;

    public ForgeInteractListener(JavaPlugin plugin, SchedulerBridge scheduler, ForgeStationService stationService,
                                ForgeMenuService menuService) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.stationService = stationService;
        this.menuService = menuService;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event == null) {
            return;
        }

        if (event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.isCancelled()) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        Optional<StationRepository.StationData> stationOpt = stationService.resolveStationFromClick(player);
        if (!stationOpt.isPresent()) {
            return;
        }

        StationRepository.StationData station = stationOpt.get();

        Optional<StationProfile> profileOpt = stationService.resolveProfile(station);
        if (!profileOpt.isPresent()) {
            return;
        }

        StationProfile profile = profileOpt.get();

        if (!stationService.hasPermission(player, station, profile)) {
            event.setCancelled(true);
            return;
        }

        if (!stationService.isTierAllowed(profile, 1)) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);

        openForgeMenu(player, station, profile);
    }

    private void openForgeMenu(Player player, StationRepository.StationData station, StationProfile profile) {
        scheduler.runEntity(player, () -> {
            if (player.isOnline()) {
                PlayerForgeState session = PlayerForgeState.of(player.getUniqueId().toString());
                PlayerForgeState sessionWithStation = session.withActiveStation(station.id, 1);
                menuService.open(player, sessionWithStation);
            }
        }, () -> {});
    }
}
