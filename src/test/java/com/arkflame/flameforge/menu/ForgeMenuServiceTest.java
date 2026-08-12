package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.chance.OutcomeSelector;
import com.arkflame.flameforge.chance.ThreadLocalRandomSource;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.forge.CostQuote;
import com.arkflame.flameforge.forge.ForgeItemPolicy;
import com.arkflame.flameforge.forge.ForgePlan;
import com.arkflame.flameforge.forge.ForgePlanResult;
import com.arkflame.flameforge.forge.ForgeService;
import com.arkflame.flameforge.forge.ForgeVariantEligibility;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.model.TierChances;
import com.arkflame.flameforge.model.TierDefinition;
import com.arkflame.flameforge.model.TierRequirements;
import com.arkflame.flameforge.text.TextRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeMenuServiceTest {
    private ForgeMenuService menuService;
    private ForgeMenuRegistry registry;
    private InventoryFactory inventoryFactory;
    private ForgeService forgeService;
    private ForgeVariantEligibility variantEligibility;
    private ConfigService configService;
    private Inventory inventory;
    private Map<Integer, ItemStack> visibleSlots;
    private Player player;
    private PlayerForgeState session;

    @BeforeEach
    void setUp() {
        inventoryFactory = mock(InventoryFactory.class);
        registry = new ForgeMenuRegistry();
        ConfigSnapshot snapshot = mock(ConfigSnapshot.class);
        configService = mock(ConfigService.class);
        forgeService = mock(ForgeService.class);
        variantEligibility = mock(ForgeVariantEligibility.class);
        when(configService.getCurrentSnapshot()).thenReturn(snapshot);
        when(snapshot.getRootString(anyString(), anyString())).thenReturn("default");
        when(snapshot.getMenuSettings(anyString())).thenReturn(menuConfig());

        visibleSlots = new HashMap<>();
        AtomicReference<ForgeInventoryHolder> holder = new AtomicReference<>();
        inventory = mock(Inventory.class);
        when(inventory.getSize()).thenReturn(MenuLayout.SIZE);
        when(inventory.getHolder()).thenAnswer(invocation -> holder.get());
        when(inventory.getItem(anyInt())).thenAnswer(invocation -> visibleSlots.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            ItemStack item = invocation.getArgument(1);
            visibleSlots.put(slot, item);
            return null;
        }).when(inventory).setItem(anyInt(), any(ItemStack.class));
        when(inventoryFactory.create(any(), eq(MenuLayout.SIZE), anyString())).thenAnswer(invocation -> {
            holder.set(invocation.getArgument(0));
            return inventory;
        });

        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(inventory);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getOpenInventory()).thenReturn(view);

        session = mock(PlayerForgeState.class);
        when(session.getActiveStationId()).thenReturn("station");
        when(session.getActiveTierLevel()).thenReturn(1);

        TextRenderer textRenderer = new TextRenderer();
        MenuItemFactory menuItemFactory = mock(MenuItemFactory.class);
        when(menuItemFactory.background(anyList(), anyString())).thenAnswer(invocation -> menuItem(Collections.emptyList()));
        when(menuItemFactory.build(anyList(), anyString(), anyList(), any(), anyBoolean(), nullable(String.class)))
                .thenAnswer(invocation -> menuItem(textRenderer.renderItemLore(
                        invocation.getArgument(2), invocation.getArgument(3), invocation.getArgument(5))));
        menuService = new ForgeMenuService(
                inventoryFactory, registry, mock(ForgeMenuSettlementService.class), configService,
                forgeService, variantEligibility,
                new OutcomeSelector(ThreadLocalRandomSource.getInstance()), mock(ItemIdentityService.class),
                new LoreTemplateRenderer(), mock(ForgeItemPolicy.class), textRenderer, menuItemFactory,
                Logger.getLogger("ForgeMenuServiceTest"));
    }

    private ItemStack menuItem(List<String> lore) {
        ItemStack item = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        when(item.clone()).thenReturn(item);
        when(item.getItemMeta()).thenReturn(meta);
        when(meta.getLore()).thenReturn(new ArrayList<>(lore));
        when(item.isSimilar(any(ItemStack.class))).thenReturn(true);
        return item;
    }

    @Test
    void menuShowsNoInputBlockedAndReadyPlayerStates() {
        ForgeMenuService.MenuResult opened = menuService.open(player, session);
        assertTrue(opened.isOpened());
        assertNull(inventory.getItem(MenuLayout.SLOT_INPUT));
        assertNotNull(inventory.getItem(MenuLayout.SLOT_CONFIRM));

        ForgeMenuContext context = registry.get(player.getUniqueId()).get();
        context.tryInsert(new ItemStack(Material.DIAMOND, 1));
        ForgePlan blockedPlan = plan(new ItemStack(Material.DIAMOND, 1), false, Collections.emptyList());
        when(forgeService.createPlan(eq(player), eq(session), any(ItemStack.class)))
                .thenReturn(ForgePlanResult.ready(blockedPlan));

        ForgeMenuService.MenuResult blocked = menuService.rerender(player);
        assertTrue(blocked.isOpened());
        assertNotNull(inventory.getItem(MenuLayout.SLOT_INPUT));
        assertNotNull(inventory.getItem(MenuLayout.SLOT_CONFIRM));

        ForgePlan readyPlan = plan(new ItemStack(Material.DIAMOND, 1), true, Collections.emptyList());
        when(forgeService.createPlan(eq(player), eq(session), any(ItemStack.class)))
                .thenReturn(ForgePlanResult.ready(readyPlan));
        ForgeMenuService.MenuResult ready = menuService.rerender(player);
        assertTrue(ready.isOpened());
        assertNotNull(inventory.getItem(MenuLayout.SLOT_INPUT));
        assertNotNull(inventory.getItem(MenuLayout.SLOT_CONFIRM));
    }

    @Test
    void confirmLoreRendersRequirementsChancesAndVariantsWithoutRawTokensOrFormatError() {
        List<ForgeVariant> variants = Arrays.asList(
                variant("Alpha Variant", 70.0), variant("Beta Variant", 30.0));
        ForgePlan plan = plan(new ItemStack(Material.DIAMOND, 1), true, variants);
        when(forgeService.createPlan(eq(player), eq(session), any(ItemStack.class)))
                .thenReturn(ForgePlanResult.ready(plan));
        when(variantEligibility.eligibleVariants(any(ItemStack.class), anyList())).thenReturn(variants);

        ForgeMenuService.MenuResult opened = menuService.open(player, session);
        ForgeMenuContext context = registry.get(player.getUniqueId()).get();
        context.tryInsert(new ItemStack(Material.DIAMOND, 1));
        ForgeMenuService.MenuResult rendered = menuService.rerender(player);

        assertTrue(opened.isOpened());
        assertTrue(rendered.isOpened());
        List<String> lore = inventory.getItem(MenuLayout.SLOT_CONFIRM).getItemMeta().getLore();
        assertNotNull(lore);
        assertFalse(lore.isEmpty());
        String renderedLore = String.join("\n", lore);
        assertTrue(renderedLore.contains("Tier"));
        assertTrue(renderedLore.contains("XP"));
        assertTrue(renderedLore.contains("Money"));
        assertTrue(renderedLore.contains("Success"));
        assertTrue(renderedLore.contains("Alpha Variant"));
        assertTrue(renderedLore.contains("Beta Variant"));
        assertFalse(renderedLore.matches("(?s).*%[A-Za-z0-9_-]+%.*"));
        assertFalse(renderedLore.contains("Message format error"));
    }

    @Test
    void rerenderPreservesTheCurrentInputAndActionSlots() {
        ForgePlan plan = plan(new ItemStack(Material.DIAMOND, 1), true, Collections.emptyList());
        when(forgeService.createPlan(eq(player), eq(session), any(ItemStack.class)))
                .thenReturn(ForgePlanResult.ready(plan));

        menuService.open(player, session);
        ForgeMenuContext context = registry.get(player.getUniqueId()).get();
        context.tryInsert(new ItemStack(Material.DIAMOND, 3));
        menuService.rerender(player);

        ItemStack inputBefore = inventory.getItem(MenuLayout.SLOT_INPUT);
        ItemStack actionBefore = inventory.getItem(MenuLayout.SLOT_CONFIRM);
        menuService.rerender(player);

        ItemStack inputAfter = inventory.getItem(MenuLayout.SLOT_INPUT);
        ItemStack actionAfter = inventory.getItem(MenuLayout.SLOT_CONFIRM);
        assertNotNull(inputBefore);
        assertEquals(Material.DIAMOND, inputAfter.getType());
        assertEquals(3, inputAfter.getAmount());
        assertNotNull(actionBefore);
        assertNotNull(actionAfter);
        assertEquals(actionBefore.getItemMeta().getLore(), actionAfter.getItemMeta().getLore());
    }

    private ForgePlan plan(ItemStack input, boolean affordable, List<ForgeVariant> variants) {
        TierRequirements requirements = new TierRequirements(
                TierRequirements.Combine.ALL,
                new TierRequirements.XpRequirement(true, 30),
                new TierRequirements.MoneyRequirement(true, new BigDecimal("100.00")),
                new TierRequirements.ItemsRequirement(true, Collections.singletonList(
                        new TierRequirements.ItemRequirement(Collections.singletonList("GOLD_INGOT"), 2, "Gold"))));
        TierDefinition targetTier = new TierDefinition("target", 2, true, null, null, 0L,
                Collections.emptyList(), Collections.emptyList(), requirements,
                new TierChances(80.0, 15.0, 5.0), null, null, null, variants);
        CostQuote quote = CostQuote.of(requirements, affordable, true, 30, 40,
                new BigDecimal("100.00"), new BigDecimal("250.00"),
                Collections.singletonList(CostQuote.ItemRequirementQuote.available(
                        Collections.singletonList("GOLD_INGOT"), 2, 4, "Gold")),
                affordable ? Collections.emptyList() : Collections.singletonList("menu.requirements-not-met"));
        return ForgePlan.createWithTier(input, 1, targetTier, requirements,
                new TierChances(80.0, 15.0, 5.0), quote,
                "station", null, null, null, 0, 0, 0);
    }

    private ForgeVariant variant(String name, double weight) {
        return new ForgeVariant(name.toLowerCase().replace(' ', '_'), name,
                Collections.emptyList(), weight, null, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private Map<String, Object> menuConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("title", "<gold><bold>FlameForge</bold>");
        config.put("background", map("materials", Collections.singletonList("STONE"), "name", " "));

        Map<String, Object> confirm = new HashMap<>();
        confirm.put("empty", map("materials", Collections.singletonList("REDSTONE_BLOCK"), "glow", false,
                "name", "<red>No Item Selected</red>", "lore", Collections.singletonList("<red>No item inserted.</red>")));
        List<String> lore = Arrays.asList("%tier_line%", " ", "Requirements", "%requirements%", " ",
                "Chances", "%chances%", " ", "Possible Variants", "%variants%");
        confirm.put("blocked", map("materials", Collections.singletonList("REDSTONE_BLOCK"), "glow", false,
                "name", "<red>Forge Unavailable</red>", "lore", lore));
        confirm.put("ready", map("materials", Collections.singletonList("EMERALD_BLOCK"), "glow", true,
                "name", "<green>Forge Item</green>", "lore", lore));
        config.put("items", Collections.singletonMap("confirm", confirm));
        config.put("dynamic-lines", map(
                "tier", "<gray>Tier: <white>%current_tier% <dark_gray>to <white>%target_tier%",
                "chance-success", "<green>Success <white>%success_chance%%",
                "chance-break", "<red>Break <white>%break_chance%%",
                "chance-curse", "<dark_purple>Curse <white>%curse_chance%%",
                "variant-entry", "<gray>%variant_name% <white>%variant_chance%%"));
        return config;
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put((String) values[i], values[i + 1]);
        }
        return result;
    }
}
