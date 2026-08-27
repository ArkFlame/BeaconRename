package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.config.EquipmentCatalog;
import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.forge.ForgePowerService;
import com.arkflame.flameforge.item.ForgeExampleItemService;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.TierDefinition;
import com.arkflame.flameforge.text.MessageArguments;
import com.arkflame.flameforge.text.MessageService;
import com.arkflame.flameforge.text.TextRenderer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WeaponsMenuService {

    public static final int SIZE = 54;
    public static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    public static final int SLOT_PREVIOUS = 45;
    public static final int SLOT_PAGE_INFO = 49;
    public static final int SLOT_NEXT = 53;

    private static final int ENTRIES_PER_PAGE = 28;
    private static final int MAX_LEGACY_INVENTORY_TITLE_LENGTH = 32;
    private static final String SAFE_LEGACY_TITLE = "\u00A76\u00A7lWeapons";
    private static final String MENU_CONFIG_KEY = "weapons-preview";
    private static final String PREVIEW_ID_PREFIX = "flameforge:weaponsmenu:";
    private static final Runnable RETIRED_NOOP = () -> {
    };

    private static final class CatalogEntry {
        final TierDefinition tier;
        final ForgeVariant variant;
        final String tierId;
        final String variantId;

        CatalogEntry(TierDefinition tier, ForgeVariant variant) {
            this.tier = tier;
            this.variant = variant;
            this.tierId = tier.getId();
            this.variantId = variant.getId();
        }
    }

    private final TierRepository tierRepository;
    private final ForgeExampleItemService exampleItemService;
    private final ConfigService configService;
    private final InventoryFactory inventoryFactory;
    private final MenuItemFactory menuItemFactory;
    private final TextRenderer textRenderer;
    private final MenuInputReturnService inputReturnService;
    private final ForgePowerService forgePowerService;
    private final MessageService messageService;
    private final SchedulerBridge scheduler;
    private final Logger logger;

    public WeaponsMenuService(TierRepository tierRepository,
                              ForgeExampleItemService exampleItemService,
                              ConfigService configService,
                              InventoryFactory inventoryFactory,
                              MenuItemFactory menuItemFactory,
                              TextRenderer textRenderer,
                              MenuInputReturnService inputReturnService,
                              ForgePowerService forgePowerService,
                              MessageService messageService,
                              SchedulerBridge scheduler,
                              Logger logger) {
        this.tierRepository = Objects.requireNonNull(tierRepository, "tierRepository");
        this.exampleItemService = Objects.requireNonNull(exampleItemService, "exampleItemService");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.inventoryFactory = Objects.requireNonNull(inventoryFactory, "inventoryFactory");
        this.menuItemFactory = Objects.requireNonNull(menuItemFactory, "menuItemFactory");
        this.textRenderer = Objects.requireNonNull(textRenderer, "textRenderer");
        this.inputReturnService = Objects.requireNonNull(inputReturnService, "inputReturnService");
        this.forgePowerService = Objects.requireNonNull(forgePowerService, "forgePowerService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void open(Player player, int requestedPage) {
        if (player == null) {
            return;
        }
        scheduler.runEntity(player, () -> openOnEntity(player, requestedPage), RETIRED_NOOP);
    }

    private void openOnEntity(Player player, int requestedPage) {
        List<CatalogEntry> entries = resolveEntries();
        int pages = pageCount(entries);
        int page = clampPage(requestedPage, pages);
        try {
            renderPage(player, entries, pages, page);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to render weapons menu for player " + player.getName(), e);
        }
    }

    public void handleClick(Player player, int page, int rawSlot) {
        if (player == null || page < 0) {
            return;
        }
        if (rawSlot == SLOT_PREVIOUS) {
            if (page > 0) {
                open(player, page - 1);
            }
            return;
        }
        if (rawSlot == SLOT_NEXT) {
            List<CatalogEntry> entries = resolveEntries();
            if (page < pageCount(entries) - 1) {
                open(player, page + 1);
            }
            return;
        }
        if (rawSlot == SLOT_PAGE_INFO) {
            return;
        }
        int slotIndex = indexOfContentSlot(rawSlot);
        if (slotIndex < 0) {
            return;
        }
        List<CatalogEntry> entries = resolveEntries();
        int entryIndex = page * ENTRIES_PER_PAGE + slotIndex;
        if (entryIndex < 0 || entryIndex >= entries.size()) {
            return;
        }
        grant(player, entries.get(entryIndex));
    }

    private void grant(Player player, CatalogEntry entry) {
        ForgeExampleItemService.ExampleResult result =
            exampleItemService.createDefault(entry.tier, entry.variant, UUID.randomUUID());
        MessageArguments arguments = MessageArguments.create()
            .string("tier_id", entry.tierId)
            .string("variant_id", entry.variantId);
        if (result.getStatus() == ForgeExampleItemService.Status.SUCCESS && result.getItem().isPresent()) {
            inputReturnService.returnToPlayer(result.getItem().get(), player);
            forgePowerService.refreshPassivePowers(player);
            messageService.send(player, "weaponsmenu.given", arguments);
        } else {
            messageService.send(player, "weaponsmenu.give-failed", arguments);
        }
    }

    private void renderPage(Player player, List<CatalogEntry> entries, int pages, int page) {
        Map<String, Object> menuConfig = readMenuConfig();
        WeaponsMenuHolder holder = new WeaponsMenuHolder(player.getUniqueId(), page);
        String legacyTitle = renderTitle(menuConfig, pages, page);

        InventoryMenuBuilder builder = new InventoryMenuBuilder(inventoryFactory, holder, SIZE, legacyTitle);
        builder.background(menuItemFactory.background(
            readMaterials(menuConfig, "background"),
            readName(menuConfig, "background", " ")));

        int start = page * ENTRIES_PER_PAGE;
        int end = Math.min(start + ENTRIES_PER_PAGE, entries.size());
        int failedPreviews = 0;
        for (int i = start; i < end; i++) {
            CatalogEntry entry = entries.get(i);
            Optional<ItemStack> preview = buildPreview(entry);
            if (preview.isPresent()) {
                builder.slot(CONTENT_SLOTS[i - start], preview.get());
            } else {
                failedPreviews++;
            }
        }

        if (page > 0) {
            builder.slot(SLOT_PREVIOUS,
                buildNavigationItem(menuConfig, "previous", pages, page, page));
        }
        builder.slot(SLOT_PAGE_INFO,
            buildNavigationItem(menuConfig, "page-info", pages, page, page + 1));
        if (page < pages - 1) {
            builder.slot(SLOT_NEXT,
                buildNavigationItem(menuConfig, "next", pages, page, page + 2));
        }

        if (failedPreviews > 0) {
            logger.warning("Weapons menu: " + failedPreviews
                + " preview(s) could not be built for page " + (page + 1));
        }

        Inventory inventory = builder.build();
        holder.setInventory(inventory);
        player.openInventory(inventory);
    }

    private Optional<ItemStack> buildPreview(CatalogEntry entry) {
        UUID previewForgeId = UUID.nameUUIDFromBytes(
            (PREVIEW_ID_PREFIX + entry.tierId + ":" + entry.variantId).getBytes(StandardCharsets.UTF_8));
        ForgeExampleItemService.ExampleResult result =
            exampleItemService.createDefault(entry.tier, entry.variant, previewForgeId);
        if (result.getStatus() == ForgeExampleItemService.Status.SUCCESS && result.getItem().isPresent()) {
            return result.getItem();
        }
        return Optional.empty();
    }

    private ItemStack buildNavigationItem(Map<String, Object> menuConfig, String nodeKey,
                                          int pages, int page, int targetPage) {
        MessageArguments arguments = MessageArguments.create()
            .string("page", String.valueOf(page + 1))
            .string("pages", String.valueOf(pages))
            .string("target_page", String.valueOf(targetPage));
        return menuItemFactory.build(
            readMaterials(menuConfig, nodeKey),
            readName(menuConfig, nodeKey, ""),
            readLore(menuConfig, nodeKey),
            arguments,
            false,
            MENU_CONFIG_KEY);
    }

    private List<CatalogEntry> resolveEntries() {
        List<CatalogEntry> entries = new ArrayList<>();
        Optional<EquipmentCatalog.Category> weapon =
            tierRepository.getEquipmentCatalog().findCategory("weapon");
        if (!weapon.isPresent()) {
            return entries;
        }
        for (String progressionId : weapon.get().getProgression()) {
            Optional<TierDefinition> tier = tierRepository.findById(progressionId);
            if (!tier.isPresent()) {
                continue;
            }
            for (ForgeVariant variant : tier.get().getVariants()) {
                entries.add(new CatalogEntry(tier.get(), variant));
            }
        }
        return entries;
    }

    private int pageCount(List<CatalogEntry> entries) {
        return Math.max(1, (int) Math.ceil(entries.size() / (double) ENTRIES_PER_PAGE));
    }

    private int clampPage(int requestedPage, int pages) {
        if (requestedPage < 0) {
            return 0;
        }
        return Math.min(requestedPage, pages - 1);
    }

    private int indexOfContentSlot(int rawSlot) {
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            if (CONTENT_SLOTS[i] == rawSlot) {
                return i;
            }
        }
        return -1;
    }

    private Map<String, Object> readMenuConfig() {
        ConfigSnapshot snapshot = configService.getCurrentSnapshot();
        if (snapshot == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> settings = snapshot.getMenuSettings(MENU_CONFIG_KEY);
        return settings != null ? settings : Collections.emptyMap();
    }

    private String renderTitle(Map<String, Object> menuConfig, int pages, int page) {
        Object titleValue = menuConfig.get("title");
        String template = titleValue instanceof String ? (String) titleValue : SAFE_LEGACY_TITLE;
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("page", String.valueOf(page + 1));
        placeholders.put("pages", String.valueOf(pages));
        try {
            String legacyTitle = textRenderer.renderToLegacy(template, placeholders);
            if (legacyTitle == null || legacyTitle.isEmpty()
                    || legacyTitle.length() > MAX_LEGACY_INVENTORY_TITLE_LENGTH) {
                return SAFE_LEGACY_TITLE;
            }
            return legacyTitle;
        } catch (Exception e) {
            return SAFE_LEGACY_TITLE;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> readMaterials(Map<String, Object> menuConfig, String nodeKey) {
        Object value = subConfig(menuConfig, nodeKey).get("materials");
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    private String readName(Map<String, Object> menuConfig, String nodeKey, String def) {
        Object value = subConfig(menuConfig, nodeKey).get("name");
        return value instanceof String ? (String) value : def;
    }

    @SuppressWarnings("unchecked")
    private List<String> readLore(Map<String, Object> menuConfig, String nodeKey) {
        Object value = subConfig(menuConfig, nodeKey).get("lore");
        if (value instanceof List) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> subConfig(Map<String, Object> menuConfig, String nodeKey) {
        Object value = menuConfig.get(nodeKey);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Collections.emptyMap();
    }
}
