package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.chance.ChanceEntry;
import com.arkflame.flameforge.chance.ChanceTable;
import com.arkflame.flameforge.chance.OutcomeSelector;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.forge.CostQuote;
import com.arkflame.flameforge.forge.CostService;
import com.arkflame.flameforge.model.ForgeSessionState;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.model.TierDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ForgeMenuService {
    private final ConfigService configService;
    private final CostService costService;
    private final OutcomeSelector outcomeSelector;

    private final Map<UUID, ForgeMenuContext> openMenus = new HashMap<>();

    public ForgeMenuService(ConfigService configService, CostService costService, OutcomeSelector outcomeSelector) {
        this.configService = Objects.requireNonNull(configService);
        this.costService = Objects.requireNonNull(costService);
        this.outcomeSelector = Objects.requireNonNull(outcomeSelector);
    }

    public void open(Player player, PlayerForgeState session) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(session);

        int page = 0;
        ForgeMenuContext context = new ForgeMenuContext(player.getUniqueId(), session, page);
        openMenus.put(player.getUniqueId(), context);

        render(player, session, page);
    }

    public void render(Player player, PlayerForgeState session, int page) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(session);

        int totalTiers = configService.getAllTiers().size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalTiers / MenuLayout.TIERS_PER_PAGE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        ForgeInventoryHolder holder = new ForgeInventoryHolder(player, session, page);
        Inventory inventory = Bukkit.createInventory(holder, MenuLayout.SIZE, "FlameForge");

        List<TierDefinition> allTiers = configService.getAllTiers();
        int tierIndex = page * MenuLayout.TIERS_PER_PAGE;
        int selectedTierLevel = session.getActiveTierLevel();

        for (int slot = 0; slot < MenuLayout.SIZE; slot++) {
            if (slot == MenuLayout.SLOT_INFO) {
                inventory.setItem(slot, MenuItemFactory.createInfoItem());
            } else if (slot == MenuLayout.SLOT_CATALYST) {
                inventory.setItem(slot, MenuItemFactory.createCatalystPlaceholder());
            } else if (slot == MenuLayout.SLOT_INPUT) {
                inventory.setItem(slot, MenuItemFactory.createInputPlaceholder());
            } else if (slot == MenuLayout.SLOT_WARD) {
                inventory.setItem(slot, MenuItemFactory.createWardPlaceholder());
            } else if (slot == MenuLayout.SLOT_CONFIRM) {
                TierDefinition selectedTier = findTierByLevel(allTiers, selectedTierLevel);
                if (selectedTier != null) {
                    ChanceTable chanceTable = buildChanceTable(selectedTier);
                    CostQuote costQuote = costService.quote(player, selectedTier.getCost());
                    boolean canAfford = costQuote.isAffordable();
                    inventory.setItem(slot, MenuItemFactory.createConfirmItem(selectedTier, chanceTable, costQuote, canAfford));
                } else {
                    inventory.setItem(slot, MenuItemFactory.createConfirmItem(null, null, null, false));
                }
            } else if (slot == MenuLayout.SLOT_PREVIOUS) {
                inventory.setItem(slot, MenuItemFactory.createPreviousButton(page > 0));
            } else if (slot >= MenuLayout.SLOT_TIER_START && slot <= MenuLayout.SLOT_TIER_END) {
                int tierSlotIndex = slot - MenuLayout.SLOT_TIER_START;
                int tierListIndex = tierIndex + tierSlotIndex;
                if (tierListIndex < allTiers.size()) {
                    TierDefinition tier = allTiers.get(tierListIndex);
                    if (tier.getTierLevel() == selectedTierLevel) {
                        inventory.setItem(slot, MenuItemFactory.createTierSelected(tier));
                    } else {
                        inventory.setItem(slot, MenuItemFactory.createTierAvailable(tier));
                    }
                } else {
                    inventory.setItem(slot, MenuItemFactory.createFiller());
                }
            } else if (slot == MenuLayout.SLOT_NEXT) {
                inventory.setItem(slot, MenuItemFactory.createNextButton(page < totalPages - 1));
            } else if (slot == MenuLayout.SLOT_PITY_HISTORY) {
                inventory.setItem(slot, MenuItemFactory.createPityHistoryItem(session, session.getActiveStationId()));
            } else if (slot == MenuLayout.SLOT_CLOSE) {
                inventory.setItem(slot, MenuItemFactory.createCloseButton());
            } else if (MenuLayout.isFillerSlot(slot)) {
                inventory.setItem(slot, MenuItemFactory.createFiller());
            }
        }

        holder.setInventory(inventory);
        player.openInventory(inventory);
    }

    public void rerender(Player player) {
        Objects.requireNonNull(player);
        ForgeMenuContext context = openMenus.get(player.getUniqueId());
        if (context == null) {
            return;
        }
        render(player, context.session, context.page);
    }

    public void page(Player player, int direction) {
        Objects.requireNonNull(player);
        ForgeMenuContext context = openMenus.get(player.getUniqueId());
        if (context == null) {
            return;
        }

        int newPage = context.page + direction;
        if (newPage < 0) {
            return;
        }

        int totalTiers = configService.getAllTiers().size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalTiers / MenuLayout.TIERS_PER_PAGE));
        if (newPage >= totalPages) {
            return;
        }

        ForgeMenuContext newContext = new ForgeMenuContext(player.getUniqueId(), context.session, newPage);
        openMenus.put(player.getUniqueId(), newContext);

        render(player, context.session, newPage);
    }

    public void selectTier(Player player, int slot) {
        Objects.requireNonNull(player);

        ForgeMenuContext context = openMenus.get(player.getUniqueId());
        if (context == null) {
            return;
        }

        if (!MenuLayout.isTierSlot(slot)) {
            return;
        }

        int tierIndex = context.page * MenuLayout.TIERS_PER_PAGE + (slot - MenuLayout.SLOT_TIER_START);
        List<TierDefinition> allTiers = configService.getAllTiers();
        if (tierIndex < 0 || tierIndex >= allTiers.size()) {
            return;
        }

        TierDefinition tier = allTiers.get(tierIndex);
        PlayerForgeState newSession = context.session.withActiveStation(
            context.session.getActiveStationId(),
            tier.getTierLevel()
        );

        ForgeMenuContext newContext = new ForgeMenuContext(player.getUniqueId(), newSession, context.page);
        openMenus.put(player.getUniqueId(), newContext);

        render(player, newSession, context.page);
    }

    public void refresh(Player player, PlayerForgeState newSession) {
        Objects.requireNonNull(player);
        Objects.requireNonNull(newSession);

        ForgeMenuContext context = openMenus.get(player.getUniqueId());
        int page = context != null ? context.page : 0;

        ForgeMenuContext newContext = new ForgeMenuContext(player.getUniqueId(), newSession, page);
        openMenus.put(player.getUniqueId(), newContext);

        render(player, newSession, page);
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

    private TierDefinition findTierByLevel(List<TierDefinition> tiers, int level) {
        for (TierDefinition tier : tiers) {
            if (tier.getTierLevel() == level) {
                return tier;
            }
        }
        return null;
    }

    private ChanceTable buildChanceTable(TierDefinition tier) {
        if (tier == null || tier.getOutcomes() == null || tier.getOutcomes().isEmpty()) {
            return null;
        }
        try {
            return outcomeSelector.buildChanceTable(tier.getOutcomes());
        } catch (Exception e) {
            return null;
        }
    }

    public static final class ForgeMenuContext {
        private final UUID playerId;
        private final PlayerForgeState session;
        private final int page;

        public ForgeMenuContext(UUID playerId, PlayerForgeState session, int page) {
            this.playerId = playerId;
            this.session = session;
            this.page = page;
        }

        public UUID getPlayerId() {
            return playerId;
        }

        public PlayerForgeState getSession() {
            return session;
        }

        public int getPage() {
            return page;
        }
    }
}
