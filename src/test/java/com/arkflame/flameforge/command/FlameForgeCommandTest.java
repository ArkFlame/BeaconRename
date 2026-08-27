package com.arkflame.flameforge.command;

import com.arkflame.flameforge.FlameForgePlugin;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.EquipmentCatalog;
import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.forge.ForgePowerService;
import com.arkflame.flameforge.forge.ForgeVariantEligibility;
import com.arkflame.flameforge.item.ForgeExampleItemService;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.item.ItemMutationService;
import com.arkflame.flameforge.menu.MenuInputReturnService;
import com.arkflame.flameforge.menu.WeaponsMenuService;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.TierDefinition;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.station.ForgeStationService;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlameForgeCommandTest {

    private FlameForgeCommand command;
    private FlameForgePlugin plugin;
    private SchedulerBridge scheduler;
    private ConfigService configService;
    private TierRepository tierRepository;
    private MessageServiceStub messages;

    @BeforeEach
    void setUp() {
        plugin = mock(FlameForgePlugin.class);
        PluginDescriptionFile description = mock(PluginDescriptionFile.class);
        when(description.getName()).thenReturn("FlameForge");
        when(description.getVersion()).thenReturn("1.0.2");
        when(description.getAuthors()).thenReturn(Collections.singletonList("ArkFlame Studios"));
        when(plugin.getDescription()).thenReturn(description);
        scheduler = mock(SchedulerBridge.class);
        configService = mock(ConfigService.class);
        tierRepository = mock(TierRepository.class);
        messages = new MessageServiceStub();

        command = new FlameForgeCommand(plugin, scheduler, messages.service, configService,
            tierRepository, new CommandSuggestionIndex(tierRepository));
    }

    @Test
    void helpAndUnknownCommandRenderWithoutThrowing() {
        CommandSender sender = permittedSender();

        assertTrue(command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"help"}));
        assertTrue(command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"not-a-command"}));
        assertTrue(messages.keys.contains("help.root-header"));
        assertTrue(messages.keys.contains("command.unknown"));
    }

    @Test
    void playerConsoleAndPermissionRoutingDispatchesExpectedCommandFamilies() {
        Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(true);
        CommandSender console = permittedSender();
        CommandSender denied = mock(CommandSender.class);

        assertTrue(command.onCommand(player, mock(Command.class), "flameforge", new String[]{"open"}));
        assertTrue(command.onCommand(console, mock(Command.class), "flameforge", new String[]{"station", "list"}));
        assertTrue(command.onCommand(denied, mock(Command.class), "flameforge", new String[]{"reload"}));
        assertTrue(messages.keys.contains("startup.loading"));
        assertTrue(messages.keys.contains("reload.no-permission"));
    }

    @Test
    void reloadStationAndForgeCommandsReturnStableSuccessContract() {
        ForgeStationService stationService = mock(ForgeStationService.class);
        when(stationService.listStations()).thenReturn(Collections.emptyList());
        when(stationService.resolveRegisteredForgeFromTarget(any(Player.class)))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        ReadyServices readyServices = mock(ReadyServices.class);
        when(readyServices.getStationService()).thenReturn(stationService);
        command.markReady(readyServices);

        when(configService.reloadAsync()).thenReturn(
            CompletableFuture.completedFuture(ConfigService.ReloadResult.alreadyRunning()));
        runCallbacksImmediately();

        CommandSender console = permittedSender();
        Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(true);

        assertTrue(command.onCommand(console, mock(Command.class), "flameforge", new String[]{"reload"}));
        assertTrue(command.onCommand(console, mock(Command.class), "flameforge", new String[]{"station", "list"}));
        assertTrue(command.onCommand(player, mock(Command.class), "flameforge", new String[]{"open"}));
        assertTrue(messages.keys.contains("reload.started"));
        assertTrue(messages.keys.contains("station-list.empty"));
        assertTrue(messages.keys.contains("open.no-forge-target"));
    }

    @Test
    void testItemDelegatesToSharedExampleItemServiceAndDeliversExactItem() {
        ForgeVariant crit = variant("crit_variant", "ANY");
        TierDefinition tier = tierWithVariants("weapon_tier3", crit);
        ItemMutationService mutation = mock(ItemMutationService.class);
        MenuInputReturnService returns = mock(MenuInputReturnService.class);
        ForgePowerService powers = mock(ForgePowerService.class);
        ReadyServices ready = readyWith(weaponCatalog(), tier, mutation, returns, powers);

        ItemStack resultItem = new ItemStack(Material.IRON_SWORD, 1);
        when(mutation.mutateSuccess(any(ItemStack.class), eq(tier), eq(crit),
            any(com.arkflame.flameforge.item.ItemIdentityCodec.Identity.class), any(UUID.class)))
            .thenReturn(ItemMutationService.MutationResult.success(resultItem));

        Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(true);
        command.onCommand(player, mock(Command.class), "flameforge",
            new String[]{"testitem", "weapon_tier3", "crit_variant", "iron_sword"});

        verify(ready).getForgeExampleItemService();
        ArgumentCaptor<ItemStack> delivered = ArgumentCaptor.forClass(ItemStack.class);
        verify(returns).returnToPlayer(delivered.capture(), eq(player));
        assertSame(resultItem, delivered.getValue());
        verify(powers).refreshPassivePowers(player);
        verify(ready, never()).getForgeService();
        assertTrue(messages.keys.contains("testitem.success"));
    }

    @Test
    void testItemFallsBackToCategorySafeMaterial() {
        ForgeVariant crit = variant("crit_variant", "ANY");
        TierDefinition tier = tierWithVariants("weapon_tier3", crit);
        ItemMutationService mutation = mock(ItemMutationService.class);
        MenuInputReturnService returns = mock(MenuInputReturnService.class);
        ForgePowerService powers = mock(ForgePowerService.class);
        readyWith(weaponCatalog(), tier, mutation, returns, powers);

        when(mutation.mutateSuccess(any(ItemStack.class), eq(tier), eq(crit),
            any(com.arkflame.flameforge.item.ItemIdentityCodec.Identity.class), any(UUID.class)))
            .thenReturn(ItemMutationService.MutationResult.success(new ItemStack(Material.IRON_SWORD, 1)));

        Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(true);
        command.onCommand(player, mock(Command.class), "flameforge",
            new String[]{"testitem", "weapon_tier3", "crit_variant"});

        ArgumentCaptor<ItemStack> input = ArgumentCaptor.forClass(ItemStack.class);
        verify(mutation).mutateSuccess(input.capture(), eq(tier), eq(crit),
            any(com.arkflame.flameforge.item.ItemIdentityCodec.Identity.class), any(UUID.class));
        Material chosen = input.getValue().getType();
        assertTrue(chosen == Material.DIAMOND_SWORD || chosen == Material.IRON_SWORD,
            "fallback must be a runtime-present weapon material, was " + chosen);
        verify(returns).returnToPlayer(any(ItemStack.class), eq(player));
        assertTrue(messages.keys.contains("testitem.success"));
    }

    @Test
    void testItemRequiresReadyServices() {
        Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(true);
        command.onCommand(player, mock(Command.class), "flameforge",
            new String[]{"testitem", "weapon_tier3", "crit_variant"});
        assertTrue(messages.keys.contains("startup.loading"));
    }

    @Test
    void testItemRequiresPlayer() {
        ReadyServices ready = mock(ReadyServices.class);
        command.markReady(ready);
        command.onCommand(permittedSender(), mock(Command.class), "flameforge",
            new String[]{"testitem", "weapon_tier3", "crit_variant"});
        assertTrue(messages.keys.contains("testitem.player-only"));
    }

    @Test
    void testItemRejectsIneligibleVariant() {
        ForgeVariant helmetOnly = variant("helmet_only", "helmet");
        TierDefinition tier = tierWithVariants("weapon_tier3", helmetOnly);
        ItemMutationService mutation = mock(ItemMutationService.class);
        readyWith(weaponCatalog(), tier, mutation,
            mock(MenuInputReturnService.class), mock(ForgePowerService.class));

        Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(true);
        command.onCommand(player, mock(Command.class), "flameforge",
            new String[]{"testitem", "weapon_tier3", "helmet_only", "iron_sword"});
        assertTrue(messages.keys.contains("testitem.variant-ineligible"));
        verifyNoInteractions(mutation);
    }

    @Test
    void testItemRejectsMaterialCategoryMismatch() {
        ForgeVariant crit = variant("crit_variant", "ANY");
        TierDefinition tier = tierWithVariants("weapon_tier3", crit);
        ItemMutationService mutation = mock(ItemMutationService.class);
        readyWith(weaponCatalog(), tier, mutation,
            mock(MenuInputReturnService.class), mock(ForgePowerService.class));

        Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(true);
        command.onCommand(player, mock(Command.class), "flameforge",
            new String[]{"testitem", "weapon_tier3", "crit_variant", "iron_chestplate"});
        assertTrue(messages.keys.contains("testitem.material-category-mismatch"));
        verifyNoInteractions(mutation);
    }

    @Test
    void weaponsMenuDelegatesOpenWhenPlayerPermittedAndReady() {
        WeaponsMenuService weaponsMenu = mock(WeaponsMenuService.class);
        ReadyServices ready = mock(ReadyServices.class);
        when(ready.getWeaponsMenuService()).thenReturn(weaponsMenu);
        command.markReady(ready);

        Player player = mock(Player.class);
        when(player.hasPermission(anyString())).thenReturn(true);
        assertTrue(command.onCommand(player, mock(Command.class), "flameforge",
            new String[]{"weaponsmenu"}));
        verify(weaponsMenu).open(player, 0);
    }

    @Test
    void weaponsMenuRejectsConsole() {
        ReadyServices ready = mock(ReadyServices.class);
        command.markReady(ready);
        command.onCommand(permittedSender(), mock(Command.class), "flameforge",
            new String[]{"weaponsmenu"});
        assertTrue(messages.keys.contains("weaponsmenu.player-only"));
        verify(ready, never()).getWeaponsMenuService();
    }

    @Test
    void weaponsMenuRequiresPermission() {
        CommandSender denied = mock(CommandSender.class);
        command.onCommand(denied, mock(Command.class), "flameforge",
            new String[]{"weaponsmenu"});
        assertTrue(messages.keys.contains("weaponsmenu.no-permission"));
    }

    private CommandSender permittedSender() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
        return sender;
    }

    private EquipmentCatalog weaponCatalog() {
        return new EquipmentCatalog(Collections.singletonList(new EquipmentCatalog.Category(
            "weapon", false,
            Arrays.asList("IRON_SWORD", "DIAMOND_SWORD", "NETHERITE_SWORD"),
            Collections.singletonList("weapon_tier3"))));
    }

    private TierDefinition tierWithVariants(String id, ForgeVariant... variants) {
        return new TierDefinition(id, 3, true, "",
            new TierDefinition.TierDisplay("", Collections.emptyList(), false, "AIR"), 0L,
            Collections.singletonList("ANY"), Collections.emptyList(),
            null, null, null, null, null, Arrays.asList(variants));
    }

    private ForgeVariant variant(String id, String... groups) {
        return new ForgeVariant(id, id, Collections.emptyList(), 1.0, "STICK",
            Arrays.asList(groups), Collections.emptyList(),
            Collections.emptyList(), Collections.emptyList());
    }

    private ReadyServices readyWith(EquipmentCatalog catalog, TierDefinition tier,
                                    ItemMutationService mutation, MenuInputReturnService returns,
                                    ForgePowerService powers) {
        when(tierRepository.getEquipmentCatalog()).thenReturn(catalog);
        when(tierRepository.findById(tier.getId())).thenReturn(Optional.of(tier));
        when(tierRepository.findEquipmentCategory(any(Material.class))).thenReturn(Optional.of("weapon"));
        ReadyServices ready = mock(ReadyServices.class);
        ForgeVariantEligibility eligibility =
            new ForgeVariantEligibility(ItemIdentityService.getInstance(), tierRepository);
        when(ready.getForgeVariantEligibility()).thenReturn(eligibility);
        when(ready.getItemIdentityService()).thenReturn(ItemIdentityService.getInstance());
        when(ready.getItemMutationService()).thenReturn(mutation);
        when(ready.getMenuInputReturnService()).thenReturn(returns);
        when(ready.getForgePowerService()).thenReturn(powers);
        when(ready.getForgeExampleItemService()).thenReturn(new ForgeExampleItemService(
            tierRepository, com.arkflame.flameforge.compat.material.MaterialResolver.getInstance(),
            eligibility, ItemIdentityService.getInstance(), mutation));
        command.markReady(ready);
        return ready;
    }

    private void runCallbacksImmediately() {
        when(scheduler.runGlobal(eq(plugin), any(Runnable.class))).thenAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return TaskHandleStub.INSTANCE;
        });
        when(scheduler.runEntity(any(Entity.class), any(Runnable.class), any(Runnable.class)))
            .thenAnswer(invocation -> {
                ((Runnable) invocation.getArgument(1)).run();
                return TaskHandleStub.INSTANCE;
            });
    }

    private enum TaskHandleStub implements TaskHandle {
        INSTANCE;

        @Override public void cancel() {}
        @Override public boolean isCancelled() { return false; }
    }

    private static final class MessageServiceStub {
        private final List<String> keys = new ArrayList<>();
        private final com.arkflame.flameforge.text.MessageService service = mock(
            com.arkflame.flameforge.text.MessageService.class);

        private MessageServiceStub() {
            when(service.findMessageString(anyString())).thenReturn(Optional.of("description"));
            when(service.renderToComponent(anyString(), any(CommandSender.class)))
                .thenReturn(Component.text("text"));
            when(service.renderToComponent(anyString(), any(CommandSender.class), any(
                com.arkflame.flameforge.text.MessageArguments.class)))
                .thenReturn(Component.text("entry"));
            doAnswer(invocation -> {
                keys.add(invocation.getArgument(1));
                return null;
            }).when(service).send(any(CommandSender.class), anyString());
            doAnswer(invocation -> {
                keys.add(invocation.getArgument(1));
                return null;
            }).when(service).send(any(CommandSender.class), anyString(), any(
                com.arkflame.flameforge.text.MessageArguments.class));
        }
    }
}
