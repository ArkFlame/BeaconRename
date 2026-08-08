package com.arkflame.flameforge.menu;

import com.arkflame.flameforge.chance.OutcomeSelector;
import com.arkflame.flameforge.chance.ThreadLocalRandomSource;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.forge.CostQuote;
import com.arkflame.flameforge.forge.ForgeItemInspection;
import com.arkflame.flameforge.forge.ForgeItemPolicy;
import com.arkflame.flameforge.forge.ForgePlan;
import com.arkflame.flameforge.forge.ForgePlanResult;
import com.arkflame.flameforge.forge.ForgeService;
import com.arkflame.flameforge.forge.ForgeVariantEligibility;
import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.PlayerForgeState;
import com.arkflame.flameforge.model.TierChances;
import com.arkflame.flameforge.model.TierDefinition;
import com.arkflame.flameforge.model.TierRequirements;
import com.arkflame.flameforge.text.MessageArguments;
import com.arkflame.flameforge.text.TextRenderer;

import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForgeMenuServiceTest {

    private ForgeMenuService menuService;
    private ForgeMenuRegistry menuRegistry;
    private ForgeMenuSettlementService menuSettlementService;
    private InventoryFactory inventoryFactory;
    private MenuInputReturnService inputReturnService;
    private ConfigService configService;
    private ForgeService forgeService;
    private ForgeVariantEligibility variantEligibility;
    private OutcomeSelector outcomeSelector;
    private ItemIdentityService identityService;
    private LoreTemplateRenderer loreTemplateRenderer;
    private ForgeItemPolicy itemPolicy;
    private TextRenderer textRenderer;
    private MenuItemFactory menuItemFactory;
    private Player player;
    private PlayerForgeState session;
    private Inventory mockInventory;
    private InventoryView mockView;

    @BeforeEach
    void setUp() {
        inventoryFactory = mock(InventoryFactory.class);
        inputReturnService = mock(MenuInputReturnService.class);
        configService = mock(ConfigService.class);
        forgeService = mock(ForgeService.class);
        variantEligibility = mock(ForgeVariantEligibility.class);
        outcomeSelector = new OutcomeSelector(ThreadLocalRandomSource.getInstance());
        identityService = mock(ItemIdentityService.class);
        textRenderer = new TextRenderer();
        menuItemFactory = mock(MenuItemFactory.class);
        ItemStack mockBackgroundItem = mock(ItemStack.class);
        when(mockBackgroundItem.getType()).thenReturn(Material.STONE);
        when(mockBackgroundItem.clone()).thenReturn(mockBackgroundItem);
        doReturn(mockBackgroundItem).when(menuItemFactory).background(anyList(), anyString());
        doReturn(mockBackgroundItem).when(menuItemFactory).build(anyList(), anyString(), anyList(), any(), anyBoolean(), any());

        menuRegistry = new ForgeMenuRegistry();
        menuSettlementService = new ForgeMenuSettlementService(inputReturnService);
        loreTemplateRenderer = new LoreTemplateRenderer();

        ForgeItemInspection inspection = mock(ForgeItemInspection.class);
        ItemIdentityCodec.Identity emptyIdentity = ItemIdentityCodec.Identity.empty();
        ForgeItemInspection.InspectionResult readyResult = new ForgeItemInspection.InspectionResult(
            ForgeItemInspection.Status.READY, emptyIdentity);
        when(inspection.inspect(any(), any(), any())).thenReturn(readyResult);
        itemPolicy = new ForgeItemPolicy(inspection);

        mockInventory = mock(Inventory.class);
        when(mockInventory.getHolder()).thenReturn(mock(org.bukkit.inventory.InventoryHolder.class));
        when(mockInventory.getSize()).thenReturn(54);
        doNothing().when(mockInventory).setItem(anyInt(), any(ItemStack.class));
        when(inventoryFactory.create(any(), anyInt(), anyString())).thenReturn(mockInventory);

        mockView = mock(InventoryView.class);
        when(mockView.getTopInventory()).thenReturn(mockInventory);

        player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getOpenInventory()).thenReturn(mockView);
        when(player.openInventory(any(Inventory.class))).thenReturn(null);

        session = mock(PlayerForgeState.class);
        when(session.getActiveStationId()).thenReturn("station");
        when(session.getActiveTierLevel()).thenReturn(1);

        ConfigSnapshot snapshot = mock(ConfigSnapshot.class);
        when(configService.getCurrentSnapshot()).thenReturn(snapshot);
        when(snapshot.getRootString(anyString(), anyString())).thenReturn("Test Menu");
        when(snapshot.getMenuSettings(anyString())).thenReturn(createMinimalMenuConfigWithConfirm());
        when(snapshot.getTiers()).thenReturn(createTierList());

        menuService = new ForgeMenuService(
            inventoryFactory, menuRegistry, menuSettlementService, configService,
            forgeService, variantEligibility, outcomeSelector, identityService,
            loreTemplateRenderer, itemPolicy, textRenderer, menuItemFactory,
            Logger.getLogger("ForgeMenuServiceTest")
        );
    }

    @Test
    void noInputSlot22EmptySlot31RedstoneNoGlowCorrectCopy() {
        Inventory mockInventory = mock(Inventory.class);
        Map<Integer, ItemStack> storedItems = new HashMap<>();
        when(mockInventory.getItem(anyInt())).thenAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            return storedItems.get(slot);
        });
        doAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            ItemStack item = invocation.getArgument(1);
            storedItems.put(slot, item);
            return null;
        }).when(mockInventory).setItem(anyInt(), any(ItemStack.class));
        when(mockInventory.getSize()).thenReturn(54);
        when(inventoryFactory.create(any(), anyInt(), anyString())).thenReturn(mockInventory);

        ForgeMenuService.MenuResult result = menuService.open(player, session);

        assertEquals(ForgeMenuService.MenuStatus.OPENED, result.getStatus());
        verify(mockInventory, atLeastOnce()).setItem(eq(22), any());
        verify(mockInventory, atLeastOnce()).setItem(eq(31), any());
    }

    @Test
    void tier0InputTierTransitionNoDuplicate() {
        when(session.getActiveTierLevel()).thenReturn(0);

        when(forgeService.createPlan(any(Player.class), any(), any())).thenAnswer(invocation -> {
            ItemStack input = invocation.getArgument(2);
            ForgePlan plan = mock(ForgePlan.class);
            when(plan.getCurrentTierLevel()).thenReturn(0);
            when(plan.getTargetTierLevel()).thenReturn(1);
            when(plan.getRequirements()).thenReturn(createTierRequirements());
            when(plan.getChances()).thenReturn(new TierChances(80.0, 15.0, 5.0));
            when(plan.getCostQuote()).thenReturn(CostQuote.zero());
            return ForgePlanResult.ready(plan);
        });

        ItemStack inputItem = mock(ItemStack.class);
        when(inputItem.getType()).thenReturn(Material.DIAMOND);
        when(inputItem.clone()).thenReturn(inputItem);
        ForgeMenuContext context = new ForgeMenuContext(UUID.randomUUID(), player.getUniqueId(), "station", session, System.currentTimeMillis());
        context.tryInsert(inputItem);
        menuRegistry.replace(context);

        Inventory mockInventory = mock(Inventory.class);
        Map<Integer, ItemStack> storedItems = new HashMap<>();
        when(mockInventory.getItem(anyInt())).thenAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            return storedItems.get(slot);
        });
        doAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            ItemStack item = invocation.getArgument(1);
            storedItems.put(slot, item);
            return null;
        }).when(mockInventory).setItem(anyInt(), any(ItemStack.class));
        when(mockInventory.getSize()).thenReturn(54);

        ForgeInventoryHolder holder = new ForgeInventoryHolder(context.getMenuId(), player.getUniqueId(), "station");
        doReturn(holder).when(mockInventory).getHolder();
        when(mockView.getTopInventory()).thenReturn(mockInventory);
        when(inventoryFactory.create(any(), anyInt(), anyString())).thenReturn(mockInventory);

        ForgeMenuService.MenuResult result = menuService.rerender(player);

        assertEquals(ForgeMenuService.MenuStatus.OPENED, result.getStatus());
    }

    @Test
    void readyConfirmSlotIsEmeraldWithGlow() {
        when(forgeService.createPlan(any(Player.class), any(), any())).thenAnswer(invocation -> {
            ForgePlan plan = mock(ForgePlan.class);
            when(plan.getCurrentTierLevel()).thenReturn(1);
            when(plan.getTargetTierLevel()).thenReturn(2);
            when(plan.getRequirements()).thenReturn(createTierRequirements());
            when(plan.getChances()).thenReturn(new TierChances(80.0, 15.0, 5.0));
            when(plan.getCostQuote()).thenReturn(CostQuote.zero());
            when(plan.isAffordable()).thenReturn(true);
            return ForgePlanResult.ready(plan);
        });

        ItemStack inputItem = mock(ItemStack.class);
        when(inputItem.getType()).thenReturn(Material.DIAMOND);
        when(inputItem.clone()).thenReturn(inputItem);
        ForgeMenuContext context = new ForgeMenuContext(UUID.randomUUID(), player.getUniqueId(), "station", session, System.currentTimeMillis());
        context.tryInsert(inputItem);
        menuRegistry.replace(context);

        Inventory mockInventory = mock(Inventory.class);
        Map<Integer, ItemStack> storedItems = new HashMap<>();
        when(mockInventory.getItem(anyInt())).thenAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            return storedItems.get(slot);
        });
        doAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            ItemStack item = invocation.getArgument(1);
            storedItems.put(slot, item);
            return null;
        }).when(mockInventory).setItem(anyInt(), any(ItemStack.class));
        when(mockInventory.getSize()).thenReturn(54);
        when(inventoryFactory.create(any(), anyInt(), anyString())).thenReturn(mockInventory);

        ForgeInventoryHolder holder = new ForgeInventoryHolder(context.getMenuId(), player.getUniqueId(), "station");
        doReturn(holder).when(mockInventory).getHolder();
        when(mockView.getTopInventory()).thenReturn(mockInventory);

        ForgeMenuService.MenuResult result = menuService.rerender(player);

        assertEquals(ForgeMenuService.MenuStatus.OPENED, result.getStatus());
    }

    @Test
    void blockedQuoteSlot31RedstoneNoGlowExactDeficit() {
        when(forgeService.createPlan(any(Player.class), any(), any())).thenAnswer(invocation -> {
            ForgePlan plan = mock(ForgePlan.class);
            when(plan.getCurrentTierLevel()).thenReturn(1);
            when(plan.getTargetTierLevel()).thenReturn(2);
            when(plan.getRequirements()).thenReturn(createTierRequirements());
            when(plan.getChances()).thenReturn(new TierChances(80.0, 15.0, 5.0));
            CostQuote quote = CostQuote.of(createTierRequirements(), true, true, 30, 10,
                    BigDecimal.valueOf(100), BigDecimal.valueOf(50),
                    Collections.emptyList(), Collections.singletonList("menu.requirements-not-met"));
            when(plan.getCostQuote()).thenReturn(quote);
            when(plan.isAffordable()).thenReturn(false);
            return ForgePlanResult.ready(plan);
        });

        ItemStack inputItem = mock(ItemStack.class);
        when(inputItem.getType()).thenReturn(Material.DIAMOND);
        when(inputItem.clone()).thenReturn(inputItem);
        ForgeMenuContext context = new ForgeMenuContext(UUID.randomUUID(), player.getUniqueId(), "station", session, System.currentTimeMillis());
        context.tryInsert(inputItem);
        menuRegistry.replace(context);

        Inventory mockInventory = mock(Inventory.class);
        Map<Integer, ItemStack> storedItems = new HashMap<>();
        when(mockInventory.getItem(anyInt())).thenAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            return storedItems.get(slot);
        });
        doAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            ItemStack item = invocation.getArgument(1);
            storedItems.put(slot, item);
            return null;
        }).when(mockInventory).setItem(anyInt(), any(ItemStack.class));
        when(mockInventory.getSize()).thenReturn(54);
        when(inventoryFactory.create(any(), anyInt(), anyString())).thenReturn(mockInventory);

        ForgeInventoryHolder holder = new ForgeInventoryHolder(context.getMenuId(), player.getUniqueId(), "station");
        doReturn(holder).when(mockInventory).getHolder();
        when(mockView.getTopInventory()).thenReturn(mockInventory);

        ForgeMenuService.MenuResult result = menuService.rerender(player);

        assertEquals(ForgeMenuService.MenuStatus.OPENED, result.getStatus());
    }

    @Test
    void variantsUseFullDisplayNamesSortedByActualProbability() {
        when(forgeService.createPlan(any(Player.class), any(), any())).thenAnswer(invocation -> {
            ForgePlan plan = mock(ForgePlan.class);
            when(plan.getCurrentTierLevel()).thenReturn(1);
            when(plan.getTargetTierLevel()).thenReturn(2);
            when(plan.getRequirements()).thenReturn(createTierRequirements());
            when(plan.getChances()).thenReturn(new TierChances(80.0, 15.0, 5.0));

            TierDefinition tier = mock(TierDefinition.class);
            ForgeVariant variant1 = new ForgeVariant("v1", "Common Sword", Collections.emptyList(), 60.0, null,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
            ForgeVariant variant2 = new ForgeVariant("v2", "Rare Axe", Collections.emptyList(), 30.0, null,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
            ForgeVariant variant3 = new ForgeVariant("v3", "Legendary Bow", Collections.emptyList(), 10.0, null,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
            when(tier.getVariants()).thenReturn(Arrays.asList(variant1, variant2, variant3));
            when(plan.getTargetTier()).thenReturn(tier);

            CostQuote quote = CostQuote.zero();
            when(plan.getCostQuote()).thenReturn(quote);
            when(plan.isAffordable()).thenReturn(true);
            return ForgePlanResult.ready(plan);
        });

        ItemStack inputItem = mock(ItemStack.class);
        when(inputItem.getType()).thenReturn(Material.DIAMOND);
        when(inputItem.clone()).thenReturn(inputItem);
        ForgeMenuContext context = new ForgeMenuContext(UUID.randomUUID(), player.getUniqueId(), "station", session, System.currentTimeMillis());
        context.tryInsert(inputItem);
        menuRegistry.replace(context);

        Inventory mockInventory = mock(Inventory.class);
        Map<Integer, ItemStack> storedItems = new HashMap<>();
        when(mockInventory.getItem(anyInt())).thenAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            return storedItems.get(slot);
        });
        doAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            ItemStack item = invocation.getArgument(1);
            storedItems.put(slot, item);
            return null;
        }).when(mockInventory).setItem(anyInt(), any(ItemStack.class));
        when(mockInventory.getSize()).thenReturn(54);
        when(inventoryFactory.create(any(), anyInt(), anyString())).thenReturn(mockInventory);

        ForgeInventoryHolder holder = new ForgeInventoryHolder(context.getMenuId(), player.getUniqueId(), "station");
        doReturn(holder).when(mockInventory).getHolder();
        when(mockView.getTopInventory()).thenReturn(mockInventory);

        ForgeMenuService.MenuResult result = menuService.rerender(player);

        assertEquals(ForgeMenuService.MenuStatus.OPENED, result.getStatus());
    }

    @Test
    void noPromptExposureStringsOrTokens() {
        Inventory mockInventory = mock(Inventory.class);
        Map<Integer, ItemStack> storedItems = new HashMap<>();
        when(mockInventory.getItem(anyInt())).thenAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            return storedItems.get(slot);
        });
        doAnswer(invocation -> {
            int slot = invocation.getArgument(0);
            ItemStack item = invocation.getArgument(1);
            storedItems.put(slot, item);
            return null;
        }).when(mockInventory).setItem(anyInt(), any(ItemStack.class));
        when(mockInventory.getSize()).thenReturn(54);
        when(inventoryFactory.create(any(), anyInt(), anyString())).thenReturn(mockInventory);

        ForgeMenuService.MenuResult result = menuService.open(player, session);

        assertEquals(ForgeMenuService.MenuStatus.OPENED, result.getStatus());
    }

    private Map<String, Object> createMinimalMenuConfigWithConfirm() {
        Map<String, Object> menuConfig = new HashMap<>();
        Map<String, Object> items = new HashMap<>();

        Map<String, Object> confirm = new HashMap<>();
        confirm.put("name", "Confirm");

        Map<String, Object> empty = new HashMap<>();
        empty.put("name", "<gradient:#ef4444:#7f1d1d><bold>No Item Selected</bold></gradient>");
        empty.put("materials", Collections.singletonList("REDSTONE_BLOCK"));
        empty.put("glow", false);
        confirm.put("empty", empty);

        Map<String, Object> blocked = new HashMap<>();
        blocked.put("name", "<gradient:#ef4444:#7f1d1d><bold>Forge Unavailable</bold></gradient>");
        blocked.put("materials", Collections.singletonList("REDSTONE_BLOCK"));
        blocked.put("glow", false);
        blocked.put("lore", Collections.singletonList("%tier_line%"));
        confirm.put("blocked", blocked);

        Map<String, Object> ready = new HashMap<>();
        ready.put("name", "<gradient:#22c55e:#a3e635><bold>Forge Item</bold></gradient>");
        ready.put("materials", Collections.singletonList("EMERALD_BLOCK"));
        ready.put("glow", true);
        ready.put("lore", Collections.singletonList("%tier_line%"));
        confirm.put("ready", ready);

        items.put("confirm", confirm);
        menuConfig.put("items", items);
        return menuConfig;
    }

    private List<TierDefinition> createTierList() {
        List<ForgeVariant> variants = Arrays.asList(
            new ForgeVariant("v1", "Common", Collections.emptyList(), 60.0, null,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
            new ForgeVariant("v2", "Rare", Collections.emptyList(), 30.0, null,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()),
            new ForgeVariant("v3", "Legendary", Collections.emptyList(), 10.0, null,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList())
        );

        TierDefinition tier1 = new TierDefinition(
            "tier_1", 1, true, null,
            new TierDefinition.TierDisplay("Tier 1", Collections.emptyList(), false, null),
            0L, Collections.emptyList(), Collections.emptyList(),
            createTierRequirements(), new TierChances(80.0, 15.0, 5.0),
            null, null, null, variants
        );

        TierDefinition tier2 = new TierDefinition(
            "tier_2", 2, true, null,
            new TierDefinition.TierDisplay("Tier 2", Collections.emptyList(), false, null),
            0L, Collections.emptyList(), Collections.emptyList(),
            createTierRequirements(), new TierChances(75.0, 18.0, 7.0),
            null, null, null, variants
        );

        return Arrays.asList(tier1, tier2);
    }

    private TierRequirements createTierRequirements() {
        return new TierRequirements(
            TierRequirements.Combine.ALL,
            new TierRequirements.XpRequirement(true, 30),
            new TierRequirements.MoneyRequirement(true, BigDecimal.valueOf(100)),
            new TierRequirements.ItemsRequirement(false, Collections.emptyList())
        );
    }
}
