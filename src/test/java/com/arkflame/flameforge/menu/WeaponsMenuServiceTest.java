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
import com.arkflame.flameforge.testfakes.TaskHandleStub;
import com.arkflame.flameforge.text.MessageArguments;
import com.arkflame.flameforge.text.MessageService;
import com.arkflame.flameforge.text.TextRenderer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeaponsMenuServiceTest {

    private TierRepository tierRepository;
    private EquipmentCatalog catalog;
    private ForgeExampleItemService exampleItemService;
    private ConfigService configService;
    private ConfigSnapshot snapshot;
    private InventoryFactory inventoryFactory;
    private MenuItemFactory menuItemFactory;
    private TextRenderer textRenderer;
    private MenuInputReturnService inputReturnService;
    private ForgePowerService forgePowerService;
    private MessageService messageService;
    private SchedulerBridge scheduler;
    private Inventory inventory;
    private Map<Integer, ItemStack> visibleSlots;
    private AtomicReference<WeaponsMenuHolder> holderRef;
    private Player player;
    private WeaponsMenuService menuService;

    @BeforeEach
    void setUp() {
        tierRepository = mock(TierRepository.class);
        installCatalog(7, 3);

        configService = mock(ConfigService.class);
        snapshot = mock(ConfigSnapshot.class);
        when(configService.getCurrentSnapshot()).thenReturn(snapshot);
        when(snapshot.getMenuSettings("weapons-preview")).thenReturn(menuConfig());

        inventoryFactory = mock(InventoryFactory.class);
        visibleSlots = new HashMap<>();
        holderRef = new AtomicReference<>();
        inventory = mock(Inventory.class);
        when(inventory.getSize()).thenReturn(WeaponsMenuService.SIZE);
        when(inventory.getHolder()).thenAnswer(invocation -> holderRef.get());
        when(inventory.getItem(anyInt())).thenAnswer(invocation -> visibleSlots.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            visibleSlots.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(inventory).setItem(anyInt(), any());
        when(inventoryFactory.create(any(), eq(WeaponsMenuService.SIZE), anyString())).thenAnswer(invocation -> {
            holderRef.set(invocation.getArgument(0));
            return inventory;
        });

        menuItemFactory = mock(MenuItemFactory.class);
        when(menuItemFactory.background(anyList(), anyString())).thenReturn(new ItemStack(Material.STONE, 1));
        when(menuItemFactory.build(anyList(), anyString(), anyList(), any(), anyBoolean(), nullable(String.class)))
            .thenReturn(new ItemStack(Material.ARROW, 1));

        textRenderer = new TextRenderer();
        exampleItemService = mock(ForgeExampleItemService.class);
        when(exampleItemService.createDefault(any(TierDefinition.class), any(ForgeVariant.class), any(UUID.class)))
            .thenReturn(ForgeExampleItemService.ExampleResult.success(
                new ItemStack(Material.DIAMOND_SWORD, 1), Material.DIAMOND_SWORD));

        inputReturnService = mock(MenuInputReturnService.class);
        forgePowerService = mock(ForgePowerService.class);
        messageService = mock(MessageService.class);

        scheduler = mock(SchedulerBridge.class);
        player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getName()).thenReturn("Tester");
        when(player.isOnline()).thenReturn(true);
        when(scheduler.runEntity(eq(player), any(Runnable.class), any(Runnable.class))).thenAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return TaskHandleStub.INSTANCE;
        });

        menuService = new WeaponsMenuService(
            tierRepository, exampleItemService, configService, inventoryFactory, menuItemFactory,
            textRenderer, inputReturnService, forgePowerService, messageService, scheduler,
            Logger.getLogger("WeaponsMenuServiceTest"));
    }

    private void installCatalog(int tierCount, int variantsPerTier) {
        List<String> progression = new ArrayList<>();
        for (int i = 1; i <= tierCount; i++) {
            progression.add("weapon_tier" + i);
        }
        catalog = new EquipmentCatalog(Collections.singletonList(
            new EquipmentCatalog.Category("weapon", false,
                Collections.singletonList("DIAMOND_SWORD"), progression)));
        when(tierRepository.getEquipmentCatalog()).thenReturn(catalog);
        for (int i = 1; i <= tierCount; i++) {
            when(tierRepository.findById("weapon_tier" + i)).thenReturn(Optional.of(tier(i, variantsPerTier)));
        }
    }

    private TierDefinition tier(int level, int variantCount) {
        List<ForgeVariant> variants = new ArrayList<>();
        for (int i = 0; i < variantCount; i++) {
            variants.add(new ForgeVariant("v" + i, "Variant " + i, Collections.emptyList(), 1.0, null,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList()));
        }
        return new TierDefinition("weapon_tier" + level, level, true, null, null, 0L,
            Collections.emptyList(), Collections.emptyList(), null, null, null, null, null, variants);
    }

    private UUID previewForgeId(String tierId, String variantId) {
        return UUID.nameUUIDFromBytes(
            ("flameforge:weaponsmenu:" + tierId + ":" + variantId).getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, Object> menuConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("title", "<gold><bold>Weapons</bold> <gray>%page%/%pages%");
        config.put("size", 54);
        config.put("background", map("materials", Arrays.asList("GRAY_STAINED_GLASS_PANE", "GLASS_PANE"), "name", " "));
        config.put("previous", map("materials", Collections.singletonList("ARROW"),
            "name", "<yellow><bold>Previous Page</bold>",
            "lore", Collections.singletonList("<gray>Go to page <white>%target_page%")));
        config.put("next", map("materials", Collections.singletonList("ARROW"),
            "name", "<yellow><bold>Next Page</bold>",
            "lore", Collections.singletonList("<gray>Go to page <white>%target_page%")));
        config.put("page-info", map("materials", Collections.singletonList("PAPER"),
            "name", "<gold>Weapon Catalog</gold>",
            "lore", Collections.singletonList("<gray>Page <white>%page%<gray>/<white>%pages%")));
        return config;
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            result.put((String) values[i], values[i + 1]);
        }
        return result;
    }

    private int filledContentSlots() {
        int filled = 0;
        for (int slot : WeaponsMenuService.CONTENT_SLOTS) {
            ItemStack item = inventory.getItem(slot);
            if (item != null && item.getType() != Material.STONE) {
                filled++;
            }
        }
        return filled;
    }

    @Test
    void weaponCatalogPreservesProgressionThenVariantOrder() {
        menuService.open(player, 0);

        ArgumentCaptor<TierDefinition> tierCaptor = ArgumentCaptor.forClass(TierDefinition.class);
        ArgumentCaptor<ForgeVariant> variantCaptor = ArgumentCaptor.forClass(ForgeVariant.class);
        verify(exampleItemService, times(21))
            .createDefault(tierCaptor.capture(), variantCaptor.capture(), any(UUID.class));

        List<TierDefinition> tiers = tierCaptor.getAllValues();
        List<ForgeVariant> variants = variantCaptor.getAllValues();
        assertEquals(21, tiers.size());
        for (int i = 0; i < 21; i++) {
            assertEquals("weapon_tier" + (i / 3 + 1), tiers.get(i).getId());
            assertEquals("v" + (i % 3), variants.get(i).getId());
        }
    }

    @Test
    void twentyOneCurrentStyleEntriesFitFirstPage() {
        menuService.open(player, 0);

        assertEquals(21, filledContentSlots());
        assertEquals(Material.STONE, inventory.getItem(WeaponsMenuService.SLOT_PREVIOUS).getType());
        assertEquals(Material.STONE, inventory.getItem(WeaponsMenuService.SLOT_NEXT).getType());
        assertEquals(Material.ARROW, inventory.getItem(WeaponsMenuService.SLOT_PAGE_INFO).getType());
        assertEquals(0, holderRef.get().getPage());
    }

    @Test
    void syntheticOverTwentyEightEntriesMapsPageTwoAndClamps() {
        installCatalog(15, 2);
        menuService.open(player, 1);

        assertEquals(1, holderRef.get().getPage());
        assertEquals(Material.DIAMOND_SWORD, inventory.getItem(10).getType());
        assertEquals(Material.DIAMOND_SWORD, inventory.getItem(11).getType());
        assertEquals(Material.STONE, inventory.getItem(12).getType());
        assertEquals(Material.ARROW, inventory.getItem(WeaponsMenuService.SLOT_PREVIOUS).getType());
        assertEquals(Material.STONE, inventory.getItem(WeaponsMenuService.SLOT_NEXT).getType());

        ArgumentCaptor<TierDefinition> tierCaptor = ArgumentCaptor.forClass(TierDefinition.class);
        ArgumentCaptor<ForgeVariant> variantCaptor = ArgumentCaptor.forClass(ForgeVariant.class);
        verify(exampleItemService, times(2))
            .createDefault(tierCaptor.capture(), variantCaptor.capture(), any(UUID.class));
        assertEquals("weapon_tier15", tierCaptor.getAllValues().get(0).getId());
        assertEquals("weapon_tier15", tierCaptor.getAllValues().get(1).getId());
        assertEquals("v0", variantCaptor.getAllValues().get(0).getId());
        assertEquals("v1", variantCaptor.getAllValues().get(1).getId());

        clearInvocations(exampleItemService);
        menuService.open(player, 99);
        assertEquals(1, holderRef.get().getPage());

        clearInvocations(exampleItemService);
        menuService.open(player, -1);
        assertEquals(0, holderRef.get().getPage());
    }

    @Test
    void deterministicPreviewForgeIdRemainsStableAcrossReopens() {
        menuService.open(player, 0);
        menuService.open(player, 0);

        ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(exampleItemService, times(42))
            .createDefault(any(TierDefinition.class), any(ForgeVariant.class), uuidCaptor.capture());
        List<UUID> uuids = uuidCaptor.getAllValues();
        assertEquals(42, uuids.size());
        for (int i = 0; i < 21; i++) {
            assertEquals(uuids.get(i), uuids.get(i + 21));
            assertEquals(previewForgeId("weapon_tier" + (i / 3 + 1), "v" + (i % 3)), uuids.get(i));
        }
    }

    @Test
    void clickedGrantRequestsFreshIdentityAndReturnsNewlyGeneratedStack() {
        ItemStack previewItem = new ItemStack(Material.DIAMOND_SWORD, 1);
        ItemStack grantedItem = new ItemStack(Material.DIAMOND_SWORD, 1);
        when(exampleItemService.createDefault(any(TierDefinition.class), any(ForgeVariant.class), any(UUID.class)))
            .thenAnswer(invocation -> {
                TierDefinition tier = invocation.getArgument(0);
                ForgeVariant variant = invocation.getArgument(1);
                UUID forgeId = invocation.getArgument(2);
                if (previewForgeId(tier.getId(), variant.getId()).equals(forgeId)) {
                    return ForgeExampleItemService.ExampleResult.success(previewItem, Material.DIAMOND_SWORD);
                }
                return ForgeExampleItemService.ExampleResult.success(grantedItem, Material.DIAMOND_SWORD);
            });

        menuService.open(player, 0);
        menuService.handleClick(player, 0, WeaponsMenuService.CONTENT_SLOTS[0]);

        ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        verify(exampleItemService, times(22))
            .createDefault(any(TierDefinition.class), any(ForgeVariant.class), uuidCaptor.capture());
        UUID grantUuid = uuidCaptor.getAllValues().get(21);
        assertNotEquals(previewForgeId("weapon_tier1", "v0"), grantUuid);

        ArgumentCaptor<ItemStack> delivered = ArgumentCaptor.forClass(ItemStack.class);
        verify(inputReturnService).returnToPlayer(delivered.capture(), eq(player));
        assertSame(grantedItem, delivered.getValue());
        verify(inputReturnService, never()).returnToPlayer(same(previewItem), eq(player));
    }

    @Test
    void previousAndNextNavigationClampsAndHidesUnavailableDirections() {
        menuService.open(player, 0);
        assertEquals(Material.STONE, inventory.getItem(WeaponsMenuService.SLOT_PREVIOUS).getType());
        assertEquals(Material.STONE, inventory.getItem(WeaponsMenuService.SLOT_NEXT).getType());

        menuService.handleClick(player, 0, WeaponsMenuService.SLOT_NEXT);
        menuService.handleClick(player, 0, WeaponsMenuService.SLOT_PREVIOUS);
        verify(scheduler, times(1)).runEntity(eq(player), any(Runnable.class), any(Runnable.class));

        installCatalog(15, 2);
        menuService.open(player, 1);
        assertEquals(Material.ARROW, inventory.getItem(WeaponsMenuService.SLOT_PREVIOUS).getType());
        assertEquals(Material.STONE, inventory.getItem(WeaponsMenuService.SLOT_NEXT).getType());

        menuService.handleClick(player, 1, WeaponsMenuService.SLOT_PREVIOUS);
        verify(scheduler, times(3)).runEntity(eq(player), any(Runnable.class), any(Runnable.class));
        assertEquals(0, holderRef.get().getPage());

        menuService.handleClick(player, 0, WeaponsMenuService.SLOT_NEXT);
        verify(scheduler, times(4)).runEntity(eq(player), any(Runnable.class), any(Runnable.class));
        assertEquals(1, holderRef.get().getPage());
    }

    @Test
    void failedPreviewLeavesBackgroundWithoutBogusSubstitute() {
        when(exampleItemService.createDefault(any(TierDefinition.class), any(ForgeVariant.class), any(UUID.class)))
            .thenAnswer(invocation -> {
                TierDefinition tier = invocation.getArgument(0);
                if ("weapon_tier1".equals(tier.getId())) {
                    return ForgeExampleItemService.ExampleResult.mutationFailed(Material.DIAMOND_SWORD);
                }
                return ForgeExampleItemService.ExampleResult.success(
                    new ItemStack(Material.DIAMOND_SWORD, 1), Material.DIAMOND_SWORD);
            });

        menuService.open(player, 0);

        assertEquals(Material.STONE, inventory.getItem(10).getType());
        assertEquals(Material.STONE, inventory.getItem(11).getType());
        assertEquals(Material.STONE, inventory.getItem(12).getType());
        assertEquals(Material.DIAMOND_SWORD, inventory.getItem(13).getType());
    }

    @Test
    void successfulGrantRefreshesPassivesAndSendsGivenMessage() {
        menuService.open(player, 0);
        menuService.handleClick(player, 0, 10);

        verify(forgePowerService).refreshPassivePowers(player);
        verify(inputReturnService).returnToPlayer(any(ItemStack.class), eq(player));
        ArgumentCaptor<MessageArguments> argsCaptor = ArgumentCaptor.forClass(MessageArguments.class);
        verify(messageService).send(eq(player), eq("weaponsmenu.given"), argsCaptor.capture());
        Map<String, String> values = argsCaptor.getValue().getStringValues();
        assertEquals("weapon_tier1", values.get("tier_id"));
        assertEquals("v0", values.get("variant_id"));
    }

    @Test
    void failedGrantSendsFailureMessageWithoutGivingItem() {
        when(exampleItemService.createDefault(any(TierDefinition.class), any(ForgeVariant.class), any(UUID.class)))
            .thenAnswer(invocation -> {
                UUID forgeId = invocation.getArgument(2);
                if (previewForgeId("weapon_tier1", "v0").equals(forgeId)) {
                    return ForgeExampleItemService.ExampleResult.success(
                        new ItemStack(Material.DIAMOND_SWORD, 1), Material.DIAMOND_SWORD);
                }
                return ForgeExampleItemService.ExampleResult.mutationFailed(Material.DIAMOND_SWORD);
            });

        menuService.open(player, 0);
        menuService.handleClick(player, 0, 10);

        verify(messageService).send(eq(player), eq("weaponsmenu.give-failed"), any(MessageArguments.class));
        verify(inputReturnService, never()).returnToPlayer(any(ItemStack.class), eq(player));
        verify(forgePowerService, never()).refreshPassivePowers(player);
    }

    @Test
    void openRoutesPlayerOpenInventoryThroughSchedulerEntity() {
        menuService.open(player, 0);
        verify(scheduler).runEntity(eq(player), any(Runnable.class), any(Runnable.class));
        assertNotNull(holderRef.get());
        assertEquals(player.getUniqueId(), holderRef.get().getViewerId());
        assertEquals(0, holderRef.get().getPage());
    }
}
