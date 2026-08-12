package com.arkflame.flameforge.command;

import com.arkflame.flameforge.FlameForgePlugin;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.station.ForgeStationService;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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

    private CommandSender permittedSender() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
        return sender;
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
