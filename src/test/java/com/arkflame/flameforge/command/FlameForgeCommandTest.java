package com.arkflame.flameforge.command;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.station.ForgeStationService;
import com.arkflame.flameforge.testfakes.FakeTextBridge;
import com.arkflame.flameforge.testfakes.TaskHandleStub;
import com.arkflame.flameforge.text.MessageArguments;
import com.arkflame.flameforge.text.MessageService;
import com.arkflame.flameforge.text.TextBridge;
import com.arkflame.flameforge.text.TextPlaceholders;
import com.arkflame.flameforge.text.TextRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlameForgeCommandTest {

    private FlameForgeCommand command;
    private TextBridge textBridge;
    private ConfigService configService;
    private TierRepository tierRepository;
    private JavaPlugin fakePlugin;
    private SchedulerBridge schedulerBridge;
    private MessageService messageService;
    private CommandSuggestionIndex suggestionIndex;
    private final List<String> sentMessageKeys = new ArrayList<>();
    private final Map<String, List<MessageArguments>> sentArguments = new HashMap<>();
    private final List<Component> sentComponents = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        fakePlugin = mock(JavaPlugin.class);
        textBridge = mock(TextBridge.class);
        configService = mock(ConfigService.class);
        tierRepository = mock(TierRepository.class);
        schedulerBridge = mock(SchedulerBridge.class);
        messageService = mock(MessageService.class);
        suggestionIndex = new CommandSuggestionIndex(tierRepository);

        when(configService.getAllTiers()).thenReturn(Collections.emptyList());
        when(messageService.findMessageString(anyString())).thenReturn(Optional.of("description"));
        when(messageService.renderToComponent(anyString(), any(CommandSender.class)))
            .thenAnswer(invocation -> Component.text(invocation.<String>getArgument(0)));
        when(messageService.renderToComponent(anyString(), any(CommandSender.class), any(MessageArguments.class)))
            .thenAnswer(invocation -> Component.text(invocation.<String>getArgument(0)));
        doAnswer(invocation -> {
            sentMessageKeys.add(invocation.getArgument(1));
            return null;
        }).when(messageService).send(any(CommandSender.class), anyString());
        doAnswer(invocation -> {
            String key = invocation.getArgument(1);
            sentMessageKeys.add(key);
            sentArguments.computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(invocation.getArgument(2));
            return null;
        }).when(messageService).send(any(CommandSender.class), anyString(), any(MessageArguments.class));
        doAnswer(invocation -> {
            sentComponents.add(invocation.getArgument(1));
            return null;
        }).when(messageService).sendComponent(any(CommandSender.class), any(Component.class));

        command = new FlameForgeCommand(
            fakePlugin, schedulerBridge, messageService, configService, tierRepository, suggestionIndex
        );
    }

    @Test
    void rootAndHelpRenderOnePagelessHeaderWithDynamicDescriptorMetadata() {
        sentComponents.clear();
        sentMessageKeys.clear();
        CommandSender sender = adminSender();

        assertTrue(command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"help"}));
        assertTrue(sentMessageKeys.contains("help.root-header"));

        long headerCount = sentMessageKeys.stream()
            .filter(key -> key.equals("help.root-header"))
            .count();
        assertEquals(1, headerCount, "Root help should render exactly one header");

        for (String key : sentMessageKeys) {
            assertFalse(key.contains("page"), "Help should not contain page counter: " + key);
            assertFalse(key.contains("total_pages"), "Help should not contain total_pages: " + key);
            assertFalse(key.contains("previous"), "Help should not contain previous control: " + key);
            assertFalse(key.contains("next"), "Help should not contain next control: " + key);
        }
    }

    @Test
    void rootHelpShowsImmediatePermittedCommandsUserBeforeAdmin() {
        sentComponents.clear();
        sentMessageKeys.clear();
        CommandSender sender = adminSender();

        assertTrue(command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"help"}));

        long entryCount = sentComponents.stream()
            .filter(c -> c.hoverEvent() != null)
            .count();
        assertTrue(entryCount > 0, "Help should show entries with hover events");

        boolean seenAdmin = false;
        boolean seenUser = false;
        for (Component entry : sentComponents) {
            if (entry.hoverEvent() != null) {
                String text = entry.toString();
                if (text.contains("ADMIN")) {
                    seenAdmin = true;
                    assertFalse(seenUser, "USER entries should appear before ADMIN entries");
                } else if (text.contains("USER") || text.contains("General") || text.contains("Forging")) {
                    seenUser = true;
                }
            }
        }
    }

    @Test
    void groupHelpShowsOnlyImmediateStationTierSetupAndSetupTierChildren() {
        sentComponents.clear();
        sentMessageKeys.clear();
        CommandSender sender = adminSender();

        assertTrue(command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"help", "station"}));
        assertTrue(sentMessageKeys.contains("help.group-header"));

        sentComponents.clear();
        sentMessageKeys.clear();
        assertTrue(command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"help", "setup"}));
        assertTrue(sentMessageKeys.contains("help.group-header"));

        sentComponents.clear();
        sentMessageKeys.clear();
        assertTrue(command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"help", "setup", "tier"}));
        assertTrue(sentMessageKeys.contains("help.group-header"));

        boolean foundCreate = false;
        boolean foundClone = false;
        for (Component component : sentComponents) {
            String text = component.toString();
            if (text.contains("create") || text.contains("Create")) foundCreate = true;
            if (text.contains("clone") || text.contains("Clone")) foundClone = true;
        }
        assertTrue(foundCreate, "Setup tier help should show create");
        assertTrue(foundClone, "Setup tier help should show clone");
    }

    @Test
    void helpUsesTypedAliasClickSuggestionHoverAndBundledFallbackBeforeReady() {
        sentComponents.clear();
        sentMessageKeys.clear();
        CommandSender sender = adminSender();

        assertTrue(command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"help"}));

        for (Component component : sentComponents) {
            if (component.hoverEvent() != null) {
                assertNotNull(component.clickEvent(), "Entry with hover should have click event");
                assertEquals(ClickEvent.Action.SUGGEST_COMMAND, component.clickEvent().action());
                assertTrue(component.clickEvent().value().startsWith("/flameforge "));
            }
        }

        ConfigService bundledConfig = mock(ConfigService.class);
        when(bundledConfig.getCurrentSnapshot()).thenReturn(ConfigSnapshot.builder().build());
        JavaPlugin bundledPlugin = mock(JavaPlugin.class);
        InputStream resource = FlameForgeCommandTest.class.getResourceAsStream("/messages.yml");
        assertNotNull(resource);
        when(bundledPlugin.getResource("messages.yml")).thenReturn(resource);
        RecordingTextBridge bridge = new RecordingTextBridge();
        MessageService bundledMessages = MessageService.create(bundledPlugin, bundledConfig,
            new TextRenderer(), bridge, new TextPlaceholders(), null);
        FlameForgeCommand bundledCommand = new FlameForgeCommand(bundledPlugin, mock(SchedulerBridge.class),
            bundledMessages, bundledConfig, mock(TierRepository.class), new CommandSuggestionIndex(mock(TierRepository.class)));
        CommandSender mockSender = mock(CommandSender.class);
        when(mockSender.hasPermission(anyString())).thenReturn(true);

        assertTrue(bundledMessages.findMessageString("help.root-header").isPresent());
        assertTrue(bundledCommand.onCommand(mockSender, mock(Command.class), "ff", new String[]{"help"}));
        assertTrue(bridge.components.stream()
            .anyMatch(component -> component.toString().contains("FlameForge") || component.toString().contains("ff")));
    }

    @Test
    void unknownCommandAndUnknownHelpPathReturnConfiguredMessages() {
        sentComponents.clear();
        sentMessageKeys.clear();
        CommandSender sender = adminSender();

        assertTrue(command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"unknownsubcommand"}));
        assertTrue(sentMessageKeys.contains("command.unknown"));

        sentComponents.clear();
        sentMessageKeys.clear();
        assertTrue(command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"help", "nonexistent", "path"}));
        assertTrue(sentMessageKeys.contains("help.unknown-path"));
    }

    @Test
    void tabCompletionAlwaysReturnsNonNullPermissionFilteredImmediateSuggestions() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenReturn(false);
        when(sender.hasPermission("flameforge.command.help")).thenReturn(true);

        List<String> emptyResult = command.onTabComplete(sender, mock(Command.class), "flameforge", new String[]{});
        assertNotNull(emptyResult);

        List<String> helpResult = command.onTabComplete(sender, mock(Command.class), "flameforge", new String[]{"help"});
        assertNotNull(helpResult);

        List<String> stationResult = command.onTabComplete(sender, mock(Command.class), "flameforge", new String[]{"station", "add", ""});
        assertNotNull(stationResult);

        when(sender.hasPermission(anyString())).thenReturn(true);
        List<String> openResult = command.onTabComplete(sender, mock(Command.class), "flameforge", new String[]{"open"});
        assertNotNull(openResult);

        List<String> reloadResult = command.onTabComplete(sender, mock(Command.class), "flameforge", new String[]{"reload"});
        assertNotNull(reloadResult);

        List<String> validateResult = command.onTabComplete(sender, mock(Command.class), "flameforge", new String[]{"validate"});
        assertNotNull(validateResult);

        List<String> tiersResult = command.onTabComplete(sender, mock(Command.class), "flameforge", new String[]{"tiers"});
        assertNotNull(tiersResult);

        List<String> previewResult = command.onTabComplete(sender, mock(Command.class), "flameforge", new String[]{"preview"});
        assertNotNull(previewResult);

        List<String> historyResult = command.onTabComplete(sender, mock(Command.class), "flameforge", new String[]{"history"});
        assertNotNull(historyResult);

        List<String> tpResult = command.onTabComplete(sender, mock(Command.class), "flameforge", new String[]{"tp"});
        assertNotNull(tpResult);
    }

    @Test
    void stationAddGrammarAcceptsGeneratedAutoExplicitAndOptionalProfile() {
        Player sender = playerWithPermission("flameforge.command.station.add");
        ForgeStationService stationService = mock(ForgeStationService.class);
        ReadyServices readyServices = readyServices(stationService, mock(com.arkflame.flameforge.ForgeAccessService.class));
        command.markReady(readyServices);
        runEntityImmediately();

        List<Optional<String>> requestedIds = new ArrayList<>();
        List<String> profiles = new ArrayList<>();
        when(stationService.addTargetedForge(eq(sender), any(Optional.class), anyString()))
            .thenAnswer(invocation -> {
                requestedIds.add(invocation.getArgument(1));
                profiles.add(invocation.getArgument(2));
                return CompletableFuture.completedFuture(ForgeStationService.AddForgeOutcome.noTarget(null));
            });

        command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"station", "add"});
        command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"station", "add", "auto"});
        command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"station", "add", "forge-x", "vip"});

        assertEquals(Arrays.asList(Optional.empty(), Optional.of("auto"), Optional.of("forge-x")), requestedIds);
        assertEquals(Arrays.asList("default", "default", "vip"), profiles);
    }

    @Test
    void stationAddMapsEveryOutcomeAndRefreshesSuggestionsOnlyAfterSuccess() {
        Player sender = playerWithPermission("flameforge.command.station.add");
        when(sender.hasPermission("flameforge.command.station.teleport")).thenReturn(true);
        ForgeStationService stationService = mock(ForgeStationService.class);
        ReadyServices readyServices = readyServices(stationService, mock(com.arkflame.flameforge.ForgeAccessService.class));
        command.markReady(readyServices);
        runEntityImmediately();

        StationRepository.RegisteredForge forge = new StationRepository.RegisteredForge(
            "forge-success", UUID.randomUUID(), "world", 1, 64, 2, "default");
        when(stationService.listStations()).thenReturn(Collections.singletonList(
            new StationRepository.StationData("forge-success", "world", 1, 64, 2, "default")));

        Map<ForgeStationService.Result, ForgeStationService.AddForgeOutcome> outcomes =
            new LinkedHashMap<>();
        outcomes.put(ForgeStationService.Result.INVALID_ID, ForgeStationService.AddForgeOutcome.invalidId("bad"));
        outcomes.put(ForgeStationService.Result.UNKNOWN_PROFILE, ForgeStationService.AddForgeOutcome.unknownProfile("bad"));
        outcomes.put(ForgeStationService.Result.TARGET_UNAVAILABLE, ForgeStationService.AddForgeOutcome.targetUnavailable(null));
        outcomes.put(ForgeStationService.Result.DUPLICATE_ID, ForgeStationService.AddForgeOutcome.duplicateId("duplicate"));
        outcomes.put(ForgeStationService.Result.DUPLICATE_LOCATION, ForgeStationService.AddForgeOutcome.duplicateLocation("duplicate"));
        outcomes.put(ForgeStationService.Result.PERSISTENCE_FAILED, ForgeStationService.AddForgeOutcome.persistenceFailed("failed"));
        outcomes.put(ForgeStationService.Result.ID_GENERATION_EXHAUSTED, ForgeStationService.AddForgeOutcome.idGenerationExhausted(null));
        outcomes.put(ForgeStationService.Result.NO_TARGET, ForgeStationService.AddForgeOutcome.noTarget(null));
        outcomes.put(ForgeStationService.Result.PLAYER_RETIRED, ForgeStationService.AddForgeOutcome.playerRetired());
        outcomes.put(ForgeStationService.Result.ADDED, ForgeStationService.AddForgeOutcome.added("forge-success", forge));

        Map<ForgeStationService.Result, String> expectedFailureKeys = new LinkedHashMap<>();
        expectedFailureKeys.put(ForgeStationService.Result.INVALID_ID, "station-add.invalid-id");
        expectedFailureKeys.put(ForgeStationService.Result.UNKNOWN_PROFILE, "station-add.unknown-profile");
        expectedFailureKeys.put(ForgeStationService.Result.TARGET_UNAVAILABLE, "station-add.target-unavailable");
        expectedFailureKeys.put(ForgeStationService.Result.DUPLICATE_ID, "station-add.duplicate-id");
        expectedFailureKeys.put(ForgeStationService.Result.DUPLICATE_LOCATION, "station-add.duplicate-location");
        expectedFailureKeys.put(ForgeStationService.Result.PERSISTENCE_FAILED, "station-add.persistence-failed");
        expectedFailureKeys.put(ForgeStationService.Result.ID_GENERATION_EXHAUSTED, "station-add.id-generation-exhausted");

        for (Map.Entry<ForgeStationService.Result, ForgeStationService.AddForgeOutcome> entry : outcomes.entrySet()) {
            sentMessageKeys.clear();
            clearInvocations(stationService);
            when(stationService.addTargetedForge(eq(sender), any(Optional.class), anyString()))
                .thenReturn(CompletableFuture.completedFuture(entry.getValue()));

            command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"station", "add", "auto"});

            if (entry.getKey() == ForgeStationService.Result.ADDED) {
                assertEquals(Arrays.asList("station-add.success", "station-list.entry"), sentMessageKeys);
                verify(stationService).listStations();
                assertTrue(command.onTabComplete(sender, mock(Command.class), "flameforge", new String[]{"tp", ""})
                    .contains("forge-success"));
            } else if (entry.getKey() == ForgeStationService.Result.NO_TARGET) {
                assertEquals(Collections.singletonList("station-add.no-block"), sentMessageKeys);
                verify(stationService, never()).listStations();
            } else if (entry.getKey() == ForgeStationService.Result.PLAYER_RETIRED) {
                assertEquals(Collections.singletonList("station-add.player-only"), sentMessageKeys);
                verify(stationService, never()).listStations();
            } else {
                String expectedKey = expectedFailureKeys.get(entry.getKey());
                assertEquals(Collections.singletonList(expectedKey), sentMessageKeys);
                verify(stationService, never()).listStations();
            }
            if (entry.getKey() != ForgeStationService.Result.ADDED) {
                assertTrue(command.onTabComplete(sender, mock(Command.class), "flameforge", new String[]{"tp", ""})
                    .isEmpty());
            }
        }
    }

    @Test
    void openWithoutRegisteredForgeNeverReportsFakeSuccess() {
        Player sender = playerWithPermission("flameforge.command.open");
        ForgeStationService stationService = mock(ForgeStationService.class);
        com.arkflame.flameforge.ForgeAccessService accessService = mock(com.arkflame.flameforge.ForgeAccessService.class);
        command.markReady(readyServices(stationService, accessService));
        runEntityImmediately();
        when(stationService.resolveRegisteredForgeFromTarget(sender))
            .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        assertTrue(command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"open"}));

        assertTrue(sentMessageKeys.contains("open.no-forge-target"));
        assertFalse(sentMessageKeys.contains("open.menu-opened"));
        verifyNoInteractions(accessService);
    }

    @Test
    void consoleAndPlayerSenderRestrictionsDoNotCastOrReturnFalse() {
        CommandSender consoleSender = mock(CommandSender.class);
        when(consoleSender.hasPermission("flameforge.command.station.list")).thenReturn(true);

        String[] args = {"station", "list"};
        boolean result = command.onCommand(consoleSender, mock(Command.class), "flameforge", args);

        assertTrue(result);

        List<String> suggestions = command.onTabComplete(consoleSender, mock(Command.class), "flameforge", new String[]{"station", "list", ""});
        assertNotNull(suggestions);

        assertTrue(command.onCommand(consoleSender, mock(Command.class), "flameforge", new String[]{"tp", "some-id"}));
    }

    @Test
    void operationalRootsDispatchReloadValidateTiersHistoryPreviewAndTeleport() {
        CommandSender sender = adminSender();

        assertTrue(command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"reload"}));
        assertTrue(command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"validate"}));
        assertTrue(command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"tiers"}));
        assertTrue(command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"preview"}));
        assertTrue(command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"history"}));
        assertTrue(command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"tp", "some-id"}));
    }

    @Test
    void notReadyOperationalCommandUsesLoadingFailedOrUnavailableMessage() {
        CommandSender sender = adminSender();

        command.markLoading();
        sentMessageKeys.clear();
        command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"reload"});
        assertTrue(sentMessageKeys.stream().anyMatch(k -> k.equals("reload.started")));

        command.markUnavailable();
        sentMessageKeys.clear();
        command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"reload"});
        assertTrue(sentMessageKeys.stream().anyMatch(k -> k.equals("reload.started")));

        ReadyServices services = mock(ReadyServices.class);
        command.markReady(services);
        sentMessageKeys.clear();
        command.onCommand(sender, mock(Command.class), "flameforge", new String[]{"reload"});
        assertFalse(sentMessageKeys.stream().anyMatch(k -> k.equals("command.loading") || k.equals("command.unavailable")));
    }

    private CommandSender adminSender() {
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission(anyString())).thenReturn(true);
        return sender;
    }

    private Player playerWithPermission(String permission) {
        Player player = mock(Player.class);
        when(player.hasPermission(permission)).thenReturn(true);
        return player;
    }

    private ReadyServices readyServices(ForgeStationService stationService,
                                        com.arkflame.flameforge.ForgeAccessService accessService) {
        ReadyServices services = mock(ReadyServices.class);
        when(services.getStationService()).thenReturn(stationService);
        when(services.getAccessService()).thenReturn(accessService);
        return services;
    }

    private void runEntityImmediately() {
        when(schedulerBridge.runEntity(any(Entity.class), any(Runnable.class), any(Runnable.class)))
            .thenAnswer(invocation -> {
                ((Runnable) invocation.getArgument(1)).run();
                return TaskHandleStub.INSTANCE;
            });
    }

    private static final class RecordingTextBridge extends FakeTextBridge {
        private final List<Component> components = new ArrayList<>();

        private RecordingTextBridge() {
        }

        @Override
        public void send(CommandSender sender, Component component) {
            components.add(component);
        }
    }
}
