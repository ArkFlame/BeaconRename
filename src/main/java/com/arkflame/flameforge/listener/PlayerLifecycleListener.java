package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.command.CommandSuggestionIndex;
import com.arkflame.flameforge.command.ReadyServices;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.forge.DeliveryService;
import com.arkflame.flameforge.forge.ForgePowerService;
import com.arkflame.flameforge.menu.ForgeMenuContext;
import com.arkflame.flameforge.menu.ForgeMenuService;
import com.arkflame.flameforge.model.ForgeSessionState;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.persistence.PlayerStateRepository;
import com.arkflame.flameforge.station.ForgeStationService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class PlayerLifecycleListener implements Listener {

    private final JavaPlugin plugin;
    private final ForgeStationService stationService;
    private final PlayerStateRepository playerStateRepository;
    private final DeliveryService deliveryService;
    private final ForgePowerService powerService;
    private final ForgeMenuService menuService;
    private final SchedulerBridge scheduler;
    private final ReadyServices readyServices;
    private final CommandSuggestionIndex suggestionIndex;
    private final ConcurrentHashMap<UUID, PlayerForgeState> activeSessions = new ConcurrentHashMap<>();

    public PlayerLifecycleListener(JavaPlugin plugin, ForgeStationService stationService,
                                   PlayerStateRepository playerStateRepository,
                                   DeliveryService deliveryService,
                                   ForgePowerService powerService,
                                   ForgeMenuService menuService,
                                   SchedulerBridge scheduler,
                                   ReadyServices readyServices,
                                   CommandSuggestionIndex suggestionIndex) {
        this.plugin = plugin;
        this.stationService = stationService;
        this.playerStateRepository = playerStateRepository;
        this.deliveryService = deliveryService;
        this.powerService = powerService;
        this.menuService = menuService;
        this.scheduler = scheduler;
        this.readyServices = readyServices;
        this.suggestionIndex = suggestionIndex;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event == null) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();

        playerStateRepository.getOrLoad(uuid);

        scheduler.runAsync(plugin, () -> {
            deliveryService.processPlayerJoin(player);
        });

        PlayerForgeState forgeState = PlayerForgeState.of(uuid.toString());
        activeSessions.put(uuid, forgeState);

        updateSuggestionIndex();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event == null) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        UUID uuid = player.getUniqueId();

        powerService.clearCooldowns(uuid);

        returnMenuInput(uuid, player);

        PlayerForgeState session = activeSessions.remove(uuid);

        if (session != null) {
            settleSession(uuid, session);
        }

        playerStateRepository.saveAsync(uuid, playerStateRepository.getSnapshot(uuid));

        updateSuggestionIndex();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        if (event == null) {
            return;
        }

        String worldName = event.getWorld().getName();

        for (UUID uuid : activeSessions.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.getWorld().getName().equals(worldName)) {
                warnStationIfNeeded(player);
            }
        }
    }

    private void returnMenuInput(UUID uuid, Player player) {
        ForgeMenuContext ctx = menuService.getContext(uuid);
        if (ctx == null) {
            return;
        }

        Optional<ItemStack> extracted = ctx.retireAndExtract();
        if (extracted.isPresent() && player.isOnline()) {
            giveItemToPlayer(player, extracted.get());
        }

        menuService.close(player);
    }

    public void retireAllMenuInputs() {
        for (UUID uuid : menuService.getAllOpenPlayerIds()) {
            Player player = Bukkit.getPlayer(uuid);
            ForgeMenuContext ctx = menuService.getContext(uuid);
            if (ctx == null) {
                continue;
            }

            Optional<ItemStack> extracted = ctx.retireAndExtract();
            if (extracted.isPresent() && player != null && player.isOnline()) {
                giveItemToPlayer(player, extracted.get());
            }
        }
        menuService.closeAll();
    }

    private void giveItemToPlayer(Player player, ItemStack item) {
        if (item == null || item.getType() == org.bukkit.Material.AIR) {
            return;
        }
        java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack overflowItem : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflowItem);
        }
    }

    private void updateSuggestionIndex() {
        suggestionIndex.updateOnlinePlayers(
            Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toSet())
        );
        suggestionIndex.updateProfileIds(
            readyServices.getProfileIds()
        );
        suggestionIndex.updateStationIds(
            readyServices.getStationService().listStations().stream()
                .map(s -> s.id)
                .collect(Collectors.toList())
        );
    }

    private void settleSession(UUID uuid, PlayerForgeState session) {
        if (session == null) {
            return;
        }

        ForgeSessionState state = session.getSessionState();
        if (state == ForgeSessionState.PROCESSING) {
            PlayerForgeState updatedSession = session.withSessionState(ForgeSessionState.SETTLING);
            activeSessions.put(uuid, updatedSession);
            return;
        }

        if (state == ForgeSessionState.SETTLING || state == ForgeSessionState.OPEN) {
            PlayerForgeState closedSession = session.withSessionState(ForgeSessionState.CLOSED);
            activeSessions.put(uuid, closedSession);
            savePlayerState(uuid, closedSession);
            return;
        }
    }

    private void savePlayerState(UUID uuid, PlayerForgeState forgeState) {
        if (forgeState == null) {
            return;
        }

        PlayerStateRepository.PlayerState oldState = playerStateRepository.getSnapshot(uuid);
        if (oldState == null) {
            return;
        }

        int tier = forgeState.getActiveTierLevel();
        long pityCooldown = System.currentTimeMillis();

        PlayerStateRepository.PlayerState newState = oldState.withTier(tier).withPityCooldown(pityCooldown);
        playerStateRepository.updateAndSave(uuid, current -> newState);
    }

    private void warnStationIfNeeded(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        stationService.resolveRegisteredForgeFromTarget(player).thenAccept(stationOpt -> {
            if (!stationOpt.isPresent()) {
                return;
            }

            stationService.resolveProfile(stationOpt.get()).ifPresent(profile -> {
                if (!stationService.hasPermission(player, stationOpt.get(), profile)) {
                    return;
                }
            });
        });
    }

    public Optional<PlayerForgeState> getActiveSession(UUID uuid) {
        return Optional.ofNullable(activeSessions.get(uuid));
    }

    public void updateSession(UUID uuid, PlayerForgeState newState) {
        if (newState == null) {
            activeSessions.remove(uuid);
        } else {
            activeSessions.put(uuid, newState);
        }
    }

    public boolean hasActiveSession(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }
}
