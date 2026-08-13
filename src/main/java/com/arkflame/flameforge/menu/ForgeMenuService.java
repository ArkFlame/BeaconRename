package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.chance.OutcomeSelector;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.forge.ForgeItemPolicy;
import com.arkflame.flameforge.forge.ForgePlan;
import com.arkflame.flameforge.forge.ForgePlanResult;
import com.arkflame.flameforge.forge.ForgeService;
import com.arkflame.flameforge.forge.ForgeVariantEligibility;
import com.arkflame.flameforge.forge.CostQuote;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.model.TierChances;
import com.arkflame.flameforge.model.TierRequirements;
import com.arkflame.flameforge.text.MessageArguments;
import com.arkflame.flameforge.text.TextRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ForgeMenuService {
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final int MAX_LEGACY_INVENTORY_TITLE_LENGTH = 32;
    private static final String SAFE_LEGACY_TITLE = "\u00A76\u00A7lFlameForge";

    public enum MenuStatus {
        OPENED,
        PLAYER_OFFLINE,
        NO_SESSION,
        CONTEXT_MISSING,
        CONTEXT_ROLLBACK,
        RENDER_FAILED,
        INVALID_SESSION
    }

    public static final class MenuResult {
        private final MenuStatus status;
        private final String reference;
        private final String reason;
        private final UUID menuId;

        private MenuResult(MenuStatus status, String reference, String reason, UUID menuId) {
            this.status = status;
            this.reference = reference;
            this.reason = reason;
            this.menuId = menuId;
        }

        public MenuStatus getStatus() { return status; }
        public String getReference() { return reference; }
        public String getReason() { return reason; }
        public UUID getMenuId() { return menuId; }

        public static MenuResult opened(UUID menuId) {
            return new MenuResult(MenuStatus.OPENED, null, null, menuId);
        }

        public static MenuResult playerOffline() {
            return new MenuResult(MenuStatus.PLAYER_OFFLINE, null, "player is offline", null);
        }

        public static MenuResult noSession(String playerId) {
            return new MenuResult(MenuStatus.NO_SESSION, playerId, "no session found", null);
        }

        public static MenuResult contextMissing(String playerId) {
            return new MenuResult(MenuStatus.CONTEXT_MISSING, playerId, "menu context not found", null);
        }

        public static MenuResult contextRollback(String reference, String reason) {
            return new MenuResult(MenuStatus.CONTEXT_ROLLBACK, reference, reason, null);
        }

        public static MenuResult renderFailed(String reference, String reason) {
            return new MenuResult(MenuStatus.RENDER_FAILED, reference, reason, null);
        }

        public static MenuResult invalidSession(String playerId, String reason) {
            return new MenuResult(MenuStatus.INVALID_SESSION, playerId, reason, null);
        }

        public boolean isOpened() {
            return status == MenuStatus.OPENED;
        }
    }

    private final InventoryFactory inventoryFactory;
    private final ForgeMenuRegistry registry;
    private final ForgeMenuSettlementService settlementService;
    private final ConfigService configService;
    private final ForgeService forgeService;
    private final ForgeVariantEligibility variantEligibility;
    private final OutcomeSelector outcomeSelector;
    private final ItemIdentityService identityService;
    private final LoreTemplateRenderer loreTemplateRenderer;
    private final ForgeItemPolicy itemPolicy;
    private final TextRenderer textRenderer;
    private final MenuItemFactory menuItemFactory;
    private final Logger logger;

    public ForgeMenuService(InventoryFactory inventoryFactory, ForgeMenuRegistry registry,
                           ForgeMenuSettlementService settlementService, ConfigService configService,
                           ForgeService forgeService, ForgeVariantEligibility variantEligibility,
                           OutcomeSelector outcomeSelector, ItemIdentityService identityService,
                           LoreTemplateRenderer loreTemplateRenderer, ForgeItemPolicy itemPolicy,
                           TextRenderer textRenderer, MenuItemFactory menuItemFactory, Logger logger) {
        this.inventoryFactory = Objects.requireNonNull(inventoryFactory);
        this.registry = Objects.requireNonNull(registry);
        this.settlementService = Objects.requireNonNull(settlementService);
        this.configService = Objects.requireNonNull(configService);
        this.forgeService = Objects.requireNonNull(forgeService);
        this.variantEligibility = Objects.requireNonNull(variantEligibility);
        this.outcomeSelector = Objects.requireNonNull(outcomeSelector);
        this.identityService = Objects.requireNonNull(identityService);
        this.loreTemplateRenderer = Objects.requireNonNull(loreTemplateRenderer);
        this.itemPolicy = Objects.requireNonNull(itemPolicy);
        this.textRenderer = Objects.requireNonNull(textRenderer);
        this.menuItemFactory = Objects.requireNonNull(menuItemFactory);
        this.logger = Objects.requireNonNull(logger);
    }

    public MenuResult open(Player player, PlayerForgeState session) {
        Objects.requireNonNull(player, "player cannot be null");
        Objects.requireNonNull(session, "session cannot be null");

        if (!player.isOnline()) {
            return MenuResult.playerOffline();
        }

        UUID playerId = player.getUniqueId();
        UUID menuId = UUID.randomUUID();
        ForgeMenuContext newContext = new ForgeMenuContext(
            menuId,
            playerId,
            session.getActiveStationId(),
            session,
            System.currentTimeMillis()
        );

        Optional<ForgeMenuContext> displaced = registry.replace(newContext);
        settlementService.settleOnlineOrQueue(displaced.orElse(null), player);

        ForgeInventoryHolder holder = new ForgeInventoryHolder(
            menuId,
            playerId,
            session.getActiveStationId()
        );

        try {
            InventoryMenuBuilder builder = createBuilder(player, newContext, holder);
            Inventory inventory = builder.build();
            holder.setInventory(inventory);
            player.openInventory(inventory);
            return MenuResult.opened(menuId);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to render menu for player " + player.getName(), e);
            registry.removeIfCurrent(playerId, menuId);
            return MenuResult.renderFailed(menuId.toString(), e.getMessage());
        }
    }

    public MenuResult rerender(Player player) {
        Objects.requireNonNull(player, "player cannot be null");

        UUID playerId = player.getUniqueId();
        ForgeMenuContext context = registry.get(playerId).orElse(null);
        if (context == null) {
            return MenuResult.contextMissing(playerId.toString());
        }

        Inventory topInventory = player.getOpenInventory().getTopInventory();
        if (!(topInventory.getHolder() instanceof ForgeInventoryHolder)) {
            return MenuResult.contextMissing(playerId.toString());
        }

        ForgeInventoryHolder holder = (ForgeInventoryHolder) topInventory.getHolder();
        if (!holder.getMenuId().equals(context.getMenuId())) {
            return MenuResult.contextMissing(playerId.toString());
        }

        try {
            InventoryMenuBuilder builder = createBuilder(player, context, holder);
            builder.applyTo(topInventory);
            player.updateInventory();
            return MenuResult.opened(context.getMenuId());
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to rerender menu for player " + player.getName(), e);
            return MenuResult.renderFailed(context.getMenuId().toString(), e.getMessage());
        }
    }

    private InventoryMenuBuilder createBuilder(Player player, ForgeMenuContext context, ForgeInventoryHolder holder) {
        Map<String, Object> menuConfig = new HashMap<>();
        String titleTemplate;
        Map<String, Object> backgroundConfig = new HashMap<>();
        try {
            ConfigSnapshot snapshot = configService.getCurrentSnapshot();
            menuConfig = getActiveMenuConfig(snapshot);
            titleTemplate = getMenuString(menuConfig, "title", SAFE_LEGACY_TITLE);
            backgroundConfig = getSubConfig(menuConfig, "background");
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to get menu config: " + e.getMessage());
            titleTemplate = SAFE_LEGACY_TITLE;
        }

        String legacyTitle = renderInventoryTitle(titleTemplate);

        PlayerForgeState session = context.getSession();

        List<String> bgMaterials = parseMaterialStrings(backgroundConfig.get("materials"));
        if (bgMaterials.isEmpty()) {
            bgMaterials = Collections.singletonList("GRAY_STAINED_GLASS_PANE");
        }
        String bgName = getMenuString(backgroundConfig, "name", " ");
        ItemStack background = menuItemFactory.background(bgMaterials, bgName);

        InventoryMenuBuilder builder = new InventoryMenuBuilder(inventoryFactory, holder, MenuLayout.SIZE, legacyTitle);
        builder.background(background);

        Optional<ItemStack> inputItem = context.peekInput();

        ForgePlanResult planResult = null;
        ForgePlan plan = null;
        if (inputItem.isPresent()) {
            planResult = forgeService.createPlan(player, session, inputItem.get());
            if (planResult.isReady()) {
                plan = planResult.plan;
            }
        }

        Map<String, Object> confirmConfig = getMenuItemConfig(menuConfig, "confirm");
        ItemStack confirmItem = createConfirmSlot(confirmConfig, player, context, planResult, plan, menuConfig);
        builder.slot(MenuLayout.SLOT_CONFIRM, confirmItem);

        if (inputItem.isPresent()) {
            builder.slot(MenuLayout.SLOT_INPUT, inputItem.get().clone());
        } else {
            builder.empty(MenuLayout.SLOT_INPUT);
        }

        return builder;
    }

    private ItemStack createConfirmSlot(Map<String, Object> confirmConfig, Player player,
                                       ForgeMenuContext context, ForgePlanResult planResult, ForgePlan plan,
                                       Map<String, Object> menuConfig) {
        Optional<ItemStack> inputOpt = context.peekInput();

        if (!inputOpt.isPresent()) {
            return createEmptyConfirmSlot(confirmConfig);
        }

        if (planResult == null || !planResult.isReady()) {
            return createBlockedConfirmSlot(confirmConfig, player, context, planResult, menuConfig);
        }

        if (!plan.isAffordable()) {
            return createBlockedConfirmSlot(confirmConfig, player, context, planResult, menuConfig);
        }

        return createReadyConfirmSlot(confirmConfig, player, context, plan, menuConfig);
    }

    private ItemStack createEmptyConfirmSlot(Map<String, Object> confirmConfig) {
        Map<String, Object> emptyConfig = getSubConfig(confirmConfig, "empty");
        List<String> materials = parseMaterialStrings(emptyConfig.get("materials"));
        boolean glow = getBoolean(emptyConfig, "glow", false);
        String name = getMenuString(emptyConfig, "name", "<gradient:#ef4444:#7f1d1d><bold>No Item Selected</bold></gradient>");
        List<String> lore = getMenuStringList(emptyConfig, "lore", Collections.singletonList("<red>No item inserted.</red>"));

        if (materials.isEmpty()) {
            materials = Collections.singletonList("REDSTONE_BLOCK");
        }
        return menuItemFactory.build(materials, name, lore, null, glow, null);
    }

    private ItemStack createBlockedConfirmSlot(Map<String, Object> confirmConfig, Player player,
                                              ForgeMenuContext context, ForgePlanResult planResult,
                                              Map<String, Object> menuConfig) {
        Map<String, Object> blockedConfig = getSubConfig(confirmConfig, "blocked");
        List<String> materials = parseMaterialStrings(blockedConfig.get("materials"));
        boolean glow = getBoolean(blockedConfig, "glow", false);
        String name = getMenuString(blockedConfig, "name", "<gradient:#ef4444:#7f1d1d><bold>Forge Unavailable</bold></gradient>");

        List<String> loreTemplate = getMenuStringList(blockedConfig, "lore", new ArrayList<String>());
        MessageArguments args = buildDynamicArgs(player, context, planResult, null, menuConfig);
        List<String> lore = loreTemplateRenderer.render(loreTemplate, args);

        if (materials.isEmpty()) {
            materials = Collections.singletonList("REDSTONE_BLOCK");
        }
        return menuItemFactory.build(materials, name, lore, args, glow, "menu.confirm.blocked");
    }

    private ItemStack createReadyConfirmSlot(Map<String, Object> confirmConfig, Player player,
                                            ForgeMenuContext context, ForgePlan plan,
                                            Map<String, Object> menuConfig) {
        Map<String, Object> readyConfig = getSubConfig(confirmConfig, "ready");
        List<String> materials = parseMaterialStrings(readyConfig.get("materials"));
        boolean glow = getBoolean(readyConfig, "glow", true);
        String name = getMenuString(readyConfig, "name", "<gradient:#22c55e:#a3e635><bold>Forge Item</bold></gradient>");

        List<String> loreTemplate = getMenuStringList(readyConfig, "lore", new ArrayList<String>());
        ForgePlanResult planResult = ForgePlanResult.ready(plan);
        MessageArguments args = buildDynamicArgs(player, context, planResult, plan, menuConfig);
        List<String> lore = loreTemplateRenderer.render(loreTemplate, args);

        if (materials.isEmpty()) {
            materials = Collections.singletonList("EMERALD_BLOCK");
        }
        return menuItemFactory.build(materials, name, lore, args, glow, "menu.confirm.ready");
    }

    private MessageArguments buildDynamicArgs(Player player, ForgeMenuContext context,
                                              ForgePlanResult planResult, ForgePlan plan,
                                              Map<String, Object> menuConfig) {
        MessageArguments args = MessageArguments.create();

        Component tierComponent = buildTierComponent(context, plan, menuConfig);
        args.component("tier_line", tierComponent);

        List<String> requirements = buildRequirementLines(plan);
        args.lines("requirements", requirements);

        List<String> chances = buildChanceLines(plan, menuConfig);
        args.lines("chances", chances);

        Optional<ItemStack> inputItem = context.peekInput();
        List<String> variants = buildVariantLines(inputItem.orElse(null), plan, menuConfig);
        args.lines("variants", variants);

        return args;
    }

    Component buildTierComponent(ForgeMenuContext context, ForgePlan plan, Map<String, Object> menuConfig) {
        int currentTier = 0;
        int targetTier = 0;

        if (plan != null) {
            currentTier = plan.getCurrentTierLevel();
            targetTier = plan.getTargetTierLevel();
        }

        Map<String, Object> dynamicLinesConfig = getSubConfig(menuConfig, "dynamic-lines");
        String tierTemplate = getMenuString(dynamicLinesConfig, "tier", "<gray>Tier: <white>%current_tier% <dark_gray>→ <white>%target_tier%");

        MessageArguments args = MessageArguments.create()
                .string("current_tier", String.valueOf(currentTier))
                .string("target_tier", String.valueOf(targetTier));

        return textRenderer.renderComponent(tierTemplate, args, "menu.tier");
    }

    List<String> buildRequirementLines(ForgePlan plan) {
        List<String> lines = new ArrayList<>();

        if (plan == null || plan.getCostQuote() == null || !plan.getCostQuote().isReady()) {
            lines.add("<red>No requirements data available.");
            return lines;
        }

        if (plan.getCostQuote().getRequirements() == null) {
            lines.add("<green>✔ <gray>No additional requirements.");
            return lines;
        }

        TierRequirements reqs = plan.getCostQuote().getRequirements();
        boolean hasAnyEnabled = (reqs.getXp().isEnabled() || reqs.getMoney().isEnabled()
                || (reqs.getItems().isEnabled() && !reqs.getItems().getItems().isEmpty()));

        if (!hasAnyEnabled) {
            lines.add("<green>✔ <gray>No additional requirements.");
            return lines;
        }

        TierRequirements.Combine combine = reqs.getCombine();
        if (combine == TierRequirements.Combine.ANY) {
            lines.add("<gray>Meet any one requirement:");
        }

        if (reqs.getXp().isEnabled()) {
            int required = reqs.getXp().getLevel();
            int available = plan.getCostQuote().getXpAvailable();
            if (available >= required) {
                lines.add("<green>✔ <gray>XP: <white>" + available + "/" + required);
            } else {
                lines.add("<red>✘ <gray>XP: <white>" + available + "/" + required);
            }
        }

        if (reqs.getMoney().isEnabled()) {
            BigDecimal required = reqs.getMoney().getAmount();
            BigDecimal available = plan.getCostQuote().getMoneyAvailable();
            if (available.compareTo(required) >= 0) {
                lines.add("<green>✔ <gray>Money: <white>" + available + "/" + required);
            } else {
                lines.add("<red>✘ <gray>Money: <white>" + available + "/" + required);
            }
        }

        if (reqs.getItems().isEnabled() && !reqs.getItems().getItems().isEmpty()) {
            for (CostQuote.ItemRequirementQuote itemQuote : plan.getCostQuote().getItemQuotes()) {
                String itemName = itemQuote.getDisplayName() != null ? itemQuote.getDisplayName() : "Item";
                int required = itemQuote.getAmount();
                int available = itemQuote.getAmountAvailable();
                if (available >= required) {
                    lines.add("<green>✔ <gray>" + itemName + ": <white>" + available + "/" + required);
                } else {
                    lines.add("<red>✘ <gray>" + itemName + ": <white>" + available + "/" + required);
                }
            }
        }

        return lines;
    }

    List<String> buildChanceLines(ForgePlan plan, Map<String, Object> menuConfig) {
        List<String> lines = new ArrayList<>();

        if (plan == null || plan.getChances() == null) {
            return lines;
        }

        TierChances chances = plan.getChances();
        Map<String, Object> dynamicLinesConfig = getSubConfig(menuConfig, "dynamic-lines");

        String successTemplate = getMenuString(dynamicLinesConfig, "chance-success", "<dark_gray>• <green>Success <white>%success_chance%%");
        String breakTemplate = getMenuString(dynamicLinesConfig, "chance-break", "<dark_gray>• <red>Break/reset <white>%break_chance%%");
        String curseTemplate = getMenuString(dynamicLinesConfig, "chance-curse", "<dark_gray>• <dark_purple>Curse <white>%curse_chance%%");

        MessageArguments args = MessageArguments.create()
                .string("success_chance", formatPercent(chances.getSuccessPercent()))
                .string("break_chance", formatPercent(chances.getBreakPercent()))
                .string("curse_chance", formatPercent(chances.getCursePercent()));

        Component successComp = textRenderer.renderComponent(successTemplate, args, "menu.chance-success");
        Component breakComp = textRenderer.renderComponent(breakTemplate, args, "menu.chance-break");
        Component curseComp = textRenderer.renderComponent(curseTemplate, args, "menu.chance-curse");

        lines.add(textRenderer.renderToMiniMessage(successTemplate, args.getStringValues(), "menu.chance-success"));
        lines.add(textRenderer.renderToMiniMessage(breakTemplate, args.getStringValues(), "menu.chance-break"));
        lines.add(textRenderer.renderToMiniMessage(curseTemplate, args.getStringValues(), "menu.chance-curse"));

        return lines;
    }

    List<String> buildVariantLines(ItemStack inputItem, ForgePlan plan, Map<String, Object> menuConfig) {
        List<String> lines = new ArrayList<>();

        if (plan == null || plan.getTargetTier() == null) {
            return lines;
        }

        List<ForgeVariant> allVariants = plan.getTargetTier().getVariants();
        if (allVariants == null || allVariants.isEmpty()) {
            return lines;
        }

        List<ForgeVariant> eligibleVariants = variantEligibility.eligibleVariants(inputItem, allVariants);
        if (eligibleVariants.isEmpty()) {
            return lines;
        }

        com.arkflame.flameforge.chance.ChanceTable chanceTable = outcomeSelector.buildVariantChanceTable(eligibleVariants);

        Map<String, Object> dynamicLinesConfig = getSubConfig(menuConfig, "dynamic-lines");
        String variantTemplate = getMenuString(dynamicLinesConfig, "variant-entry", "<dark_gray>• %variant_name% <gray>— <aqua>%variant_chance%%");

        BigDecimal tierSuccessPct = plan.getChances() != null ? plan.getChances().getSuccessPercent() : BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (ForgeVariant v : eligibleVariants) {
            totalWeight = totalWeight.add(BigDecimal.valueOf(v.getWeight()));
        }

        List<VariantWithActualPct> sortedVariants = new ArrayList<>();
        for (int i = 0; i < eligibleVariants.size(); i++) {
            ForgeVariant variant = eligibleVariants.get(i);
            BigDecimal variantWeight = BigDecimal.valueOf(variant.getWeight());
            BigDecimal normalizedWeight = totalWeight.compareTo(BigDecimal.ZERO) > 0
                    ? variantWeight.multiply(BigDecimal.valueOf(100)).divide(totalWeight, 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal actualPct = normalizedWeight.multiply(tierSuccessPct).divide(BigDecimal.valueOf(100), 1, RoundingMode.HALF_UP);
            sortedVariants.add(new VariantWithActualPct(variant, actualPct, i));
        }

        Collections.sort(sortedVariants, new Comparator<VariantWithActualPct>() {
            @Override
            public int compare(VariantWithActualPct a, VariantWithActualPct b) {
                int pctCmp = b.actualPct.compareTo(a.actualPct);
                if (pctCmp != 0) return pctCmp;
                return Integer.compare(a.yamlOrder, b.yamlOrder);
            }
        });

        for (VariantWithActualPct vwp : sortedVariants) {
            ForgeVariant variant = vwp.variant;
            String variantName = variant.getName() != null ? variant.getName() : "Unknown";

            MessageArguments args = MessageArguments.create()
                    .component("variant_name", textRenderer.renderComponent(variantName, null, "menu.variant-name"))
                    .string("variant_chance", formatPercent(vwp.actualPct));

            lines.add(textRenderer.renderToMiniMessage(variantTemplate, args.getStringValues(),
                    args.getComponentValues(), "menu.variant"));
        }

        return lines;
    }

    String formatPercent(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.setScale(1, RoundingMode.HALF_UP).toPlainString();
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> getSubConfig(Map<String, Object> parentConfig, String subKey) {
        Object subObj = parentConfig.get(subKey);
        if (subObj instanceof Map) {
            return (Map<String, Object>) subObj;
        }
        return new HashMap<>();
    }

    private String getMenuString(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value instanceof String ? (String) value : defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> getMenuStringList(Map<String, Object> config, String key, List<String> defaultValue) {
        Object value = config.get(key);
        if (value instanceof List) {
            return new ArrayList<>((List<String>) value);
        }
        return defaultValue;
    }

    private boolean getBoolean(Map<String, Object> config, String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private List<String> parseMaterialStrings(Object materialsObj) {
        List<String> materials = new ArrayList<>();
        if (materialsObj instanceof List) {
            List<?> list = (List<?>) materialsObj;
            for (Object item : list) {
                if (item instanceof String) {
                    materials.add((String) item);
                }
            }
        }
        return materials;
    }

    private String renderInventoryTitle(String titleTemplate) {
        try {
            Component titleComponent = textRenderer.renderToComponent(titleTemplate);
            String legacyTitle = LEGACY_SERIALIZER.serialize(titleComponent);
            if (legacyTitle == null || legacyTitle.isEmpty() || legacyTitle.length() > MAX_LEGACY_INVENTORY_TITLE_LENGTH) {
                return SAFE_LEGACY_TITLE;
            }
            return legacyTitle;
        } catch (Exception e) {
            return SAFE_LEGACY_TITLE;
        }
    }

    private static final class VariantWithActualPct {
        final ForgeVariant variant;
        final BigDecimal actualPct;
        final int yamlOrder;

        VariantWithActualPct(ForgeVariant variant, BigDecimal actualPct, int yamlOrder) {
            this.variant = variant;
            this.actualPct = actualPct;
            this.yamlOrder = yamlOrder;
        }
    }
}
