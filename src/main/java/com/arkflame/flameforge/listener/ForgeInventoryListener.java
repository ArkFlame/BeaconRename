package com.arkflame.flameforge.listener;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.menu.ForgeInventoryHolder;
import com.arkflame.flameforge.menu.ForgeMenuService;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.persistence.PlayerStateRepository;
import com.arkflame.flameforge.persistence.StationRepository;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ForgeInventoryListener implements Listener {

    private static final int SLOT_CATALYST = 11;
    private static final int SLOT_INPUT = 13;
    private static final int SLOT_WARD = 15;
    private static final int SLOT_CONFIRM = 22;
    private static final int SLOT_PREVIOUS = 27;
    private static final int SLOT_NEXT = 35;
    private static final int SLOT_CLOSE = 49;
    private static final int SLOT_TIER_START = 28;
    private static final int SLOT_TIER_END = 34;

    private static final int INPUT_REQUIRED_AMOUNT = 1;

    private static final Set<Integer> REAL_ITEM_SLOTS;
    private static final Set<Integer> NAVIGATION_SLOTS;
    private static final Set<Integer> ALL_MENU_SLOTS;

    static {
        REAL_ITEM_SLOTS = new HashSet<>();
        REAL_ITEM_SLOTS.add(SLOT_CATALYST);
        REAL_ITEM_SLOTS.add(SLOT_INPUT);
        REAL_ITEM_SLOTS.add(SLOT_WARD);

        NAVIGATION_SLOTS = new HashSet<>();
        NAVIGATION_SLOTS.add(SLOT_CONFIRM);
        NAVIGATION_SLOTS.add(SLOT_PREVIOUS);
        NAVIGATION_SLOTS.add(SLOT_NEXT);
        NAVIGATION_SLOTS.add(SLOT_CLOSE);
        for (int i = SLOT_TIER_START; i <= SLOT_TIER_END; i++) {
            NAVIGATION_SLOTS.add(i);
        }

        ALL_MENU_SLOTS = new HashSet<>(REAL_ITEM_SLOTS);
        ALL_MENU_SLOTS.addAll(NAVIGATION_SLOTS);
    }

    private final JavaPlugin plugin;
    private final ForgeMenuService menuService;
    private final SchedulerBridge scheduler;
    private final PlayerStateRepository playerStateRepository;
    private final ConcurrentHashMap<UUID, Boolean> menuOpen = new ConcurrentHashMap<>();

    public ForgeInventoryListener(JavaPlugin plugin, ForgeMenuService menuService,
                                  SchedulerBridge scheduler, PlayerStateRepository playerStateRepository) {
        this.plugin = plugin;
        this.menuService = menuService;
        this.scheduler = scheduler;
        this.playerStateRepository = playerStateRepository;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event == null) {
            return;
        }

        if (event.isCancelled()) {
            return;
        }

        Inventory inventory = event.getInventory();
        if (inventory == null) {
            return;
        }

        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof ForgeInventoryHolder)) {
            return;
        }

        ForgeInventoryHolder menuHolder = (ForgeInventoryHolder) holder;
        Player player = event.getWhoClicked() instanceof Player ? (Player) event.getWhoClicked() : null;
        if (player == null) {
            event.setCancelled(true);
            return;
        }

        if (!menuOpen.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        int rawSlot = event.getRawSlot();

        if (event.getInventory().getType() == InventoryType.PLAYER) {
            if (event.getClick() == ClickType.NUMBER_KEY) {
                event.setCancelled(true);
                return;
            }
            return;
        }

        if (rawSlot < 0 || rawSlot >= inventory.getSize()) {
            event.setCancelled(true);
            return;
        }

        InventoryAction action = event.getAction();
        ClickType click = event.getClick();

        if (click == ClickType.CREATIVE) {
            event.setCancelled(true);
            return;
        }

        if (click == ClickType.DOUBLE_CLICK && action == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        if (click.isShiftClick()) {
            event.setCancelled(true);
            handleShiftClick(player, menuHolder, rawSlot, event);
            return;
        }

        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            return;
        }

        if (!ALL_MENU_SLOTS.contains(rawSlot)) {
            event.setCancelled(true);
            return;
        }

        if (REAL_ITEM_SLOTS.contains(rawSlot)) {
            if (rawSlot == SLOT_INPUT) {
                ItemStack cursor = event.getCursor();
                if (cursor != null && cursor.getType() != Material.AIR) {
                    if (cursor.getAmount() != INPUT_REQUIRED_AMOUNT) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }
            event.setCancelled(true);
            handleRealSlotClick(player, menuHolder, rawSlot, event);
            return;
        }

        if (NAVIGATION_SLOTS.contains(rawSlot)) {
            event.setCancelled(true);
            handleNavigationClick(player, menuHolder, rawSlot, event);
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event == null) {
            return;
        }

        if (event.isCancelled()) {
            return;
        }

        Inventory inventory = event.getInventory();
        if (inventory == null) {
            return;
        }

        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof ForgeInventoryHolder)) {
            return;
        }

        Player player = event.getWhoClicked() instanceof Player ? (Player) event.getWhoClicked() : null;
        if (player == null) {
            event.setCancelled(true);
            return;
        }

        if (!menuOpen.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        for (int slot : event.getRawSlots()) {
            if (slot >= 0 && slot < inventory.getSize()) {
                if (!REAL_ITEM_SLOTS.contains(slot)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event == null) {
            return;
        }

        Inventory inventory = event.getInventory();
        if (inventory == null) {
            return;
        }

        InventoryHolder holder = inventory.getHolder();
        if (!(holder instanceof ForgeInventoryHolder)) {
            return;
        }

        Player player = event.getPlayer() instanceof Player ? (Player) event.getPlayer() : null;
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        Boolean wasOpen = menuOpen.remove(playerId);
        if (wasOpen == null) {
            return;
        }

        ForgeInventoryHolder menuHolder = (ForgeInventoryHolder) holder;
        settleSession(player, menuHolder);
    }

    private void handleShiftClick(Player player, ForgeInventoryHolder menuHolder, int rawSlot, InventoryClickEvent event) {
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        if (REAL_ITEM_SLOTS.contains(rawSlot)) {
            return;
        }

        event.setCancelled(true);
    }

    private void handleRealSlotClick(Player player, ForgeInventoryHolder menuHolder, int rawSlot, InventoryClickEvent event) {
        scheduler.runEntity(player, () -> rerenderMenu(player, menuHolder), () -> {});
    }

    private void handleNavigationClick(Player player, ForgeInventoryHolder menuHolder, int rawSlot, InventoryClickEvent event) {
        if (rawSlot == SLOT_CONFIRM) {
            dispatchConfirm(player, menuHolder);
            return;
        }

        if (rawSlot == SLOT_PREVIOUS) {
            dispatchPrevious(player, menuHolder);
            return;
        }

        if (rawSlot == SLOT_NEXT) {
            dispatchNext(player, menuHolder);
            return;
        }

        if (rawSlot == SLOT_CLOSE) {
            dispatchClose(player, menuHolder);
            return;
        }

        if (rawSlot >= SLOT_TIER_START && rawSlot <= SLOT_TIER_END) {
            dispatchTierSelect(player, menuHolder, rawSlot);
            return;
        }
    }

    private void dispatchConfirm(Player player, ForgeInventoryHolder menuHolder) {
        scheduler.runEntity(player, () -> {
            if (player.isOnline()) {
                rerenderMenu(player, menuHolder);
            }
        }, () -> {});
    }

    private void dispatchPrevious(Player player, ForgeInventoryHolder menuHolder) {
        scheduler.runEntity(player, () -> {
            if (player.isOnline()) {
                menuService.page(player, -1);
            }
        }, () -> {});
    }

    private void dispatchNext(Player player, ForgeInventoryHolder menuHolder) {
        scheduler.runEntity(player, () -> {
            if (player.isOnline()) {
                menuService.page(player, 1);
            }
        }, () -> {});
    }

    private void dispatchClose(Player player, ForgeInventoryHolder menuHolder) {
        menuOpen.remove(player.getUniqueId());
        settleSession(player, menuHolder);
        menuService.close(player);
        player.closeInventory();
    }

    private void dispatchTierSelect(Player player, ForgeInventoryHolder menuHolder, int rawSlot) {
        scheduler.runEntity(player, () -> {
            if (player.isOnline()) {
                menuService.selectTier(player, rawSlot);
            }
        }, () -> {});
    }

    private void rerenderMenu(Player player, ForgeInventoryHolder menuHolder) {
        if (!player.isOnline()) {
            return;
        }
        PlayerForgeState session = menuHolder.getSession();
        if (session != null) {
            menuService.refresh(player, session);
        }
    }

    private void settleSession(Player player, ForgeInventoryHolder menuHolder) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID uuid = player.getUniqueId();
        PlayerForgeState session = menuHolder.getSession();

        playerStateRepository.updateAndSave(uuid, current -> {
            int tier = session != null ? session.getActiveTierLevel() : current.tier;
            return current.withTier(tier);
        });
    }

    public void markMenuOpen(UUID playerId) {
        menuOpen.put(playerId, Boolean.TRUE);
    }

    public void markMenuClosed(UUID playerId) {
        menuOpen.remove(playerId);
    }

    public boolean isMenuOpen(UUID playerId) {
        return menuOpen.containsKey(playerId);
    }
}
