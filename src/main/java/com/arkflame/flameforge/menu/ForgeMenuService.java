package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.forge.CostQuote;
import com.arkflame.flameforge.forge.CostService;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.model.TierDefinition;
import com.arkflame.flameforge.text.TextRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ForgeMenuService {
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private final InventoryFactory inventoryFactory;
    private final MenuInputReturnService inputReturnService;
    private final ConfigService configService;
    private final CostService costService;
    private final TextRenderer textRenderer;

    private final Map<UUID, ForgeMenuContext> openMenus = new HashMap<>();

    public ForgeMenuService(InventoryFactory inventoryFactory, MenuInputReturnService inputReturnService,
                           ConfigService configService, CostService costService, TextRenderer textRenderer) {
        this.inventoryFactory = Objects.requireNonNull(inventoryFactory);
        this.inputReturnService = Objects.requireNonNull(inputReturnService);
        this.configService = Objects.requireNonNull(configService);
        this.costService = Objects.requireNonNull(costService);
        this.textRenderer = Objects.requireNonNull(textRenderer);
    }

    public void open(Player player, PlayerForgeState session) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(session);

        UUID menuId = UUID.randomUUID();
        ForgeMenuContext context = new ForgeMenuContext(
            menuId,
            player.getUniqueId(),
            session.getActiveStationId(),
            session,
            System.currentTimeMillis()
        );
        openMenus.put(player.getUniqueId(), context);

        render(player, context);
    }

    public void render(Player player, ForgeMenuContext context) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(context);

        ConfigSnapshot snapshot = configService.getCurrentSnapshot();
        Map<String, Object> menuConfig = getActiveMenuConfig(snapshot);

        String titleTemplate = getMenuString(menuConfig, "title", "FlameForge");
        Component titleComponent = textRenderer.renderToComponent(titleTemplate);
        String legacyTitle = LEGACY_SERIALIZER.serialize(titleComponent);

        PlayerForgeState session = context.getSession();
        ForgeInventoryHolder holder = new ForgeInventoryHolder(
            context.getMenuId(),
            context.getPlayerId(),
            context.getStationId()
        );

        MenuItemFactory menuItemFactory = new MenuItemFactory(
            com.arkflame.flameforge.compat.material.MaterialResolver.getInstance(),
            textRenderer
        );

        ItemStack background = menuItemFactory.background();

        InventoryMenuBuilder builder = new InventoryMenuBuilder(inventoryFactory, holder, MenuLayout.SIZE, legacyTitle);
        builder.background(background);

        builder.slot(MenuLayout.SLOT_INFO, createInfoSlot(menuConfig, session, menuItemFactory));
        builder.slot(MenuLayout.SLOT_INPUT, createInputSlot(menuConfig, context, menuItemFactory));
        builder.slot(MenuLayout.SLOT_CONFIRM, createConfirmSlot(menuConfig, session, menuItemFactory, player));
        builder.slot(MenuLayout.SLOT_CLOSE, createCloseSlot(menuConfig, menuItemFactory));

        Inventory inventory = builder.build();
        holder.setInventory(inventory);
        player.openInventory(inventory);
    }

    private ItemStack createInfoSlot(Map<String, Object> menuConfig, PlayerForgeState session, MenuItemFactory factory) {
        Map<String, Object> infoConfig = getMenuItemConfig(menuConfig, "info");
        String name = getMenuString(infoConfig, "name", "<gold><bold>FlameForge");
        List<String> lore = getMenuStringList(infoConfig, "lore");
        return factory.info(name, lore);
    }

    private ItemStack createInputSlot(Map<String, Object> menuConfig, ForgeMenuContext context, MenuItemFactory factory) {
        Map<String, Object> inputConfig = getMenuItemConfig(menuConfig, "input");
        String name = getMenuString(inputConfig, "name", "<dark_gray><bold>Input Item");
        List<String> lore = getMenuStringList(inputConfig, "lore");
        return factory.inputEmpty(name, lore);
    }

    private ItemStack createConfirmSlot(Map<String, Object> menuConfig, PlayerForgeState session, MenuItemFactory factory, Player player) {
        Map<String, Object> confirmConfig = getMenuItemConfig(menuConfig, "confirm");

        int selectedTierLevel = session.getActiveTierLevel();
        List<TierDefinition> allTiers = configService.getAllTiers();
        TierDefinition selectedTier = findTierByLevel(allTiers, selectedTierLevel);

        if (selectedTier == null) {
            String name = getMenuString(confirmConfig, "name-empty", "<gray><bold>No Tier Selected");
            List<String> lore = Collections.singletonList("<red>Select a tier first");
            return factory.confirmEmpty(name, lore);
        }

        List<String> lore = buildConfirmLore(selectedTier);
        String nameAvailable = getMenuString(confirmConfig, "name-when-available", "<green><bold>Confirm Forge");
        String nameUnavailable = getMenuString(confirmConfig, "name-when-unavailable", "<red><bold>Cannot Afford");

        CostQuote costQuote = costService.quote(player, selectedTier.getCost());
        boolean canAfford = costQuote.isAffordable();

        if (canAfford) {
            return factory.confirmReady(nameAvailable, lore);
        } else {
            return factory.confirmBlocked(nameUnavailable, lore);
        }
    }

    private ItemStack createCloseSlot(Map<String, Object> menuConfig, MenuItemFactory factory) {
        Map<String, Object> closeConfig = getMenuItemConfig(menuConfig, "close");
        String name = getMenuString(closeConfig, "name", "<red><bold>Close");
        String lore = getMenuString(closeConfig, "lore", "<gray>Click to close the menu");
        return factory.close(name, lore);
    }

    private Map<String, Object> getActiveMenuConfig(ConfigSnapshot snapshot) {
        String menuProfile = snapshot.getRootString("menu-profile", "default");
        Map<String, Object> menuConfig = snapshot.getMenuSettings(menuProfile);
        if (menuConfig == null) {
            menuConfig = snapshot.getMenuSettings("default");
        }
        if (menuConfig == null) {
            menuConfig = new HashMap<>();
        }
        return menuConfig;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMenuItemConfig(Map<String, Object> menuConfig, String itemKey) {
        Object itemsObj = menuConfig.get("items");
        if (itemsObj instanceof Map) {
            Map<String, Object> items = (Map<String, Object>) itemsObj;
            Object itemConfig = items.get(itemKey);
            if (itemConfig instanceof Map) {
                return (Map<String, Object>) itemConfig;
            }
        }
        return new HashMap<>();
    }

    private String getMenuString(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> getMenuStringList(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value instanceof List) {
            return new ArrayList<>((List<String>) value);
        }
        return Collections.emptyList();
    }

    private List<String> buildConfirmLore(TierDefinition tier) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Tier Level: <yellow>" + tier.getTierLevel());
        lore.add("<gray>&m----------------------------");
        lore.add("<e>Place item to forge");
        return lore;
    }

    public void rerender(Player player) {
        Objects.requireNonNull(player);
        ForgeMenuContext context = openMenus.get(player.getUniqueId());
        if (context == null) {
            return;
        }
        render(player, context);
    }

    public void refresh(Player player, PlayerForgeState newSession) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(newSession);

        ForgeMenuContext context = openMenus.get(player.getUniqueId());
        if (context == null) {
            return;
        }

        ForgeMenuContext newContext = new ForgeMenuContext(
            context.getMenuId(),
            context.getPlayerId(),
            context.getStationId(),
            newSession,
            context.getGeneration()
        );
        openMenus.put(player.getUniqueId(), newContext);

        render(player, newContext);
    }

    public void close(Player player) {
        if (player != null) {
            openMenus.remove(player.getUniqueId());
        }
    }

    public ForgeMenuContext getContext(UUID playerId) {
        return openMenus.get(playerId);
    }

    public boolean hasOpenMenu(UUID playerId) {
        return openMenus.containsKey(playerId);
    }

    public java.util.Set<UUID> getAllOpenPlayerIds() {
        return new java.util.HashSet<>(openMenus.keySet());
    }

    public void closeAll() {
        openMenus.clear();
    }

    public Optional<ForgeMenuContext> closeIfCurrent(UUID playerId, ForgeInventoryHolder holder) {
        if (playerId == null || holder == null) {
            return Optional.empty();
        }
        ForgeMenuContext context = openMenus.get(playerId);
        if (context == null) {
            return Optional.empty();
        }
        String contextStationId = context.getStationId();
        String holderStationId = holder.getStationId();
        if (!contextStationId.equals(holderStationId)) {
            return Optional.empty();
        }
        openMenus.remove(playerId);
        return Optional.of(context);
    }

    public boolean isCurrentMenu(Player player, ForgeInventoryHolder holder) {
        if (player == null || holder == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (!openMenus.containsKey(playerId)) {
            return false;
        }
        ForgeMenuContext context = openMenus.get(playerId);
        String contextStationId = context.getStationId();
        String holderStationId = holder.getStationId();
        if (!contextStationId.equals(holderStationId)) {
            return false;
        }
        Inventory topInventory = player.getOpenInventory().getTopInventory();
        return topInventory.getHolder() == holder;
    }

    private TierDefinition findTierByLevel(List<TierDefinition> tiers, int level) {
        for (TierDefinition tier : tiers) {
            if (tier.getTierLevel() == level) {
                return tier;
            }
        }
        return null;
    }
}
