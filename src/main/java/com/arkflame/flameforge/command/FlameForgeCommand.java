package com.arkflame.flameforge.command;

import com.arkflame.flameforge.compat.material.MaterialResolver;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.TierParser;
import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.config.ValidationIssue;
import com.arkflame.flameforge.config.ValidationReport;
import com.arkflame.flameforge.item.ItemFactory;
import com.arkflame.flameforge.model.OutcomeDefinition;
import com.arkflame.flameforge.model.TierCost;
import com.arkflame.flameforge.model.TierDefinition;
import com.arkflame.flameforge.persistence.PlayerStateRepository;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.station.ForgeStationService;
import com.arkflame.flameforge.text.TextBridge;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class FlameForgeCommand implements CommandExecutor, TabCompleter {

    private static final int PAGE_SIZE = 8;

    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final TextBridge text;
    private final ConfigService configService;
    private final ForgeStationService stationService;
    private final StationRepository stationRepository;
    private final PlayerStateRepository playerStateRepository;
    private final TierRepository tierRepository;
    private final MaterialResolver materialResolver;

    public FlameForgeCommand(JavaPlugin plugin, SchedulerBridge scheduler, TextBridge text,
                             ConfigService configService, ForgeStationService stationService,
                             StationRepository stationRepository, PlayerStateRepository playerStateRepository,
                             TierRepository tierRepository) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.text = text;
        this.configService = configService;
        this.stationService = stationService;
        this.stationRepository = stationRepository;
        this.playerStateRepository = playerStateRepository;
        this.tierRepository = tierRepository;
        this.materialResolver = MaterialResolver.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return commandHelp(sender, 1);
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help":
                return commandHelp(sender, args.length > 1 ? parsePage(args[1]) : 1);
            case "open":
                return commandOpen(sender, args);
            case "reload":
                return commandReload(sender);
            case "validate":
                return commandValidate(sender);
            case "tiers":
                return commandTiers(sender, args.length > 1 ? parsePage(args[1]) : 1);
            case "tier":
                return commandTierInfo(sender, args);
            case "preview":
                return commandPreview(sender, args);
            case "history":
                return commandHistory(sender, args);
            case "station":
                return commandStation(sender, args);
            case "setup":
                return commandSetup(sender, args);
            default:
                send(sender, Component.text("Unknown command. Use /" + label + " help for available commands.", NamedTextColor.RED));
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }

        String prefix = args[args.length - 1].toLowerCase();

        if (args.length == 1) {
            List<String> cmds = Arrays.asList("help", "open", "reload", "validate", "tiers", "tier", "preview", "history", "station", "setup");
            return filterPrefix(cmds, prefix);
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "help":
                return Collections.emptyList();
            case "open":
                if (args.length == 2) {
                    if (sender.hasPermission("flameforge.command.open.others")) {
                        return filterPrefix(getOnlinePlayerNames(), prefix);
                    }
                }
                return Collections.emptyList();
            case "reload":
            case "validate":
                return Collections.emptyList();
            case "tiers":
                return Collections.emptyList();
            case "tier":
                if (args.length == 2) {
                    return filterPrefix(Arrays.asList("info"), prefix);
                }
                if (args.length == 3 && "info".equals(args[1].toLowerCase())) {
                    return filterPrefixTierIds(prefix);
                }
                return Collections.emptyList();
            case "preview":
                if (args.length == 2) {
                    return filterPrefixTierIds(prefix);
                }
                if (args.length == 3) {
                    return filterPrefixMaterial(prefix);
                }
                return Collections.emptyList();
            case "history":
                if (args.length == 2) {
                    if (sender.hasPermission("flameforge.command.history.others")) {
                        return filterPrefix(getOnlinePlayerNames(), prefix);
                    }
                }
                return Collections.emptyList();
            case "station":
                return tabCompleteStation(sender, args, prefix);
            case "setup":
                return tabCompleteSetup(sender, args, prefix);
            default:
                return Collections.emptyList();
        }
    }

    private List<String> tabCompleteStation(CommandSender sender, String[] args, String prefix) {
        if (!sender.hasPermission("flameforge.command.station.add") &&
            !sender.hasPermission("flameforge.command.station.remove") &&
            !sender.hasPermission("flameforge.command.station.list") &&
            !sender.hasPermission("flameforge.command.station.info") &&
            !sender.hasPermission("flameforge.command.station.teleport")) {
            return Collections.emptyList();
        }

        if (args.length == 2) {
            List<String> sub = new ArrayList<>();
            if (sender.hasPermission("flameforge.command.station.add")) sub.add("add");
            if (sender.hasPermission("flameforge.command.station.remove")) sub.add("remove");
            if (sender.hasPermission("flameforge.command.station.list")) sub.add("list");
            if (sender.hasPermission("flameforge.command.station.info")) sub.add("info");
            if (sender.hasPermission("flameforge.command.station.teleport")) sub.add("teleport");
            return filterPrefix(sub, prefix);
        }

        String stationCmd = args[1].toLowerCase();

        if ("add".equals(stationCmd) && sender.hasPermission("flameforge.command.station.add")) {
            if (args.length == 3) {
                return filterPrefix(Arrays.asList("<id>"), prefix);
            }
            if (args.length == 4) {
                List<String> profiles = new ArrayList<>();
                Map<String, Object> snapshot = configService.getCurrentSnapshot().getStationProfile("default") != null
                    ? configService.getCurrentSnapshot().getStationProfile("default") : Collections.emptyMap();
                profiles.add("default");
                return filterPrefix(profiles, prefix);
            }
        }

        if ("remove".equals(stationCmd) && sender.hasPermission("flameforge.command.station.remove")) {
            if (args.length == 3) {
                return filterPrefixStationIds(prefix);
            }
        }

        if ("list".equals(stationCmd) && sender.hasPermission("flameforge.command.station.list")) {
            return Collections.emptyList();
        }

        if ("info".equals(stationCmd) && sender.hasPermission("flameforge.command.station.info")) {
            if (args.length == 3) {
                return filterPrefixStationIds(prefix);
            }
        }

        if ("teleport".equals(stationCmd) && sender.hasPermission("flameforge.command.station.teleport")) {
            if (args.length == 3) {
                return filterPrefixStationIds(prefix);
            }
        }

        return Collections.emptyList();
    }

    private List<String> tabCompleteSetup(CommandSender sender, String[] args, String prefix) {
        if (!sender.hasPermission("flameforge.command.setup.tier")) {
            return Collections.emptyList();
        }

        if (args.length == 2) {
            return filterPrefix(Arrays.asList("tier"), prefix);
        }

        if ("tier".equals(args[1].toLowerCase()) && args.length >= 3) {
            if (args.length == 3) {
                return filterPrefix(Arrays.asList("create", "clone"), prefix);
            }

            String tierCmd = args[2].toLowerCase();

            if ("create".equals(tierCmd)) {
                if (args.length == 4) {
                    return filterPrefix(Arrays.asList("<id>"), prefix);
                }
                if (args.length == 5) {
                    return filterPrefix(Arrays.asList("<priority>"), prefix);
                }
            }

            if ("clone".equals(tierCmd)) {
                if (args.length == 4) {
                    return filterPrefixTierIds(prefix);
                }
                if (args.length == 5) {
                    return filterPrefix(Arrays.asList("<id>"), prefix);
                }
                if (args.length == 6) {
                    return filterPrefix(Arrays.asList("<priority>"), prefix);
                }
            }
        }

        return Collections.emptyList();
    }

    private boolean commandHelp(CommandSender sender, int page) {
        if (!sender.hasPermission("flameforge.command.help")) {
            send(sender, Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        List<CommandEntry> commands = buildCommandList(sender);
        int totalPages = (commands.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Math.max(1, Math.min(page, totalPages));

        send(sender, Component.text("=== FlameForge Help (" + page + "/" + totalPages + ") ===", NamedTextColor.GOLD));

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, commands.size());

        for (int i = start; i < end; i++) {
            CommandEntry entry = commands.get(i);
            Component line = Component.text()
                .color(NamedTextColor.GRAY)
                .append(Component.text(entry.usage, NamedTextColor.YELLOW))
                .append(Component.text(" - ", NamedTextColor.GRAY))
                .append(Component.text(entry.description, NamedTextColor.WHITE))
                .build();
            send(sender, line);
        }

        if (totalPages > 1) {
            send(sender, Component.text("Use /flameforge help <page> for more pages.", NamedTextColor.GRAY));
        }

        return true;
    }

    private List<CommandEntry> buildCommandList(CommandSender sender) {
        List<CommandEntry> commands = new ArrayList<>();
        commands.add(new CommandEntry("help [page]", "Show this help menu"));
        if (sender.hasPermission("flameforge.command.open")) {
            commands.add(new CommandEntry("open [player]", "Open the beacon menu"));
        }
        if (sender.hasPermission("flameforge.command.reload")) {
            commands.add(new CommandEntry("reload", "Reload configuration"));
        }
        if (sender.hasPermission("flameforge.command.validate")) {
            commands.add(new CommandEntry("validate", "Validate configurations"));
        }
        if (sender.hasPermission("flameforge.command.tiers")) {
            commands.add(new CommandEntry("tiers [page]", "List all tiers"));
        }
        if (sender.hasPermission("flameforge.command.tier.info")) {
            commands.add(new CommandEntry("tier info <tier>", "Show tier information"));
        }
        if (sender.hasPermission("flameforge.command.preview")) {
            commands.add(new CommandEntry("preview <tier> [material]", "Preview rename outcome"));
        }
        if (sender.hasPermission("flameforge.command.history")) {
            commands.add(new CommandEntry("history [player]", "View rename history"));
        }
        if (sender.hasPermission("flameforge.command.station.add")) {
            commands.add(new CommandEntry("station add <id> [profile]", "Add a rename station"));
        }
        if (sender.hasPermission("flameforge.command.station.remove")) {
            commands.add(new CommandEntry("station remove <id>", "Remove a rename station"));
        }
        if (sender.hasPermission("flameforge.command.station.list")) {
            commands.add(new CommandEntry("station list [page]", "List rename stations"));
        }
        if (sender.hasPermission("flameforge.command.station.info")) {
            commands.add(new CommandEntry("station info <id>", "Show station information"));
        }
        if (sender.hasPermission("flameforge.command.station.teleport")) {
            commands.add(new CommandEntry("station teleport <id>", "Teleport to a station"));
        }
        if (sender.hasPermission("flameforge.command.setup.tier")) {
            commands.add(new CommandEntry("setup tier create <id> <priority>", "Create a new tier"));
            commands.add(new CommandEntry("setup tier clone <source> <id> <priority>", "Clone a tier"));
        }
        return commands;
    }

    private boolean commandOpen(CommandSender sender, String[] args) {
        if (!sender.hasPermission("flameforge.command.open")) {
            send(sender, Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player)) {
            send(sender, Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        Player player = (Player) sender;
        Player target = player;

        if (args.length > 1) {
            if (!sender.hasPermission("flameforge.command.open.others")) {
                send(sender, Component.text("You don't have permission to open menus for other players.", NamedTextColor.RED));
                return true;
            }
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                send(sender, Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
        }

        send(player, Component.text("Opening beacon menu for " + target.getName() + "...", NamedTextColor.YELLOW));
        openBeaconMenu(target);
        return true;
    }

    private void openBeaconMenu(Player player) {
        Optional<ForgeStationService.StationInfo> optInfo = stationService.resolveStationFromClick(player)
            .flatMap(data -> stationService.getStationInfo(data.id));

        if (!optInfo.isPresent()) {
            Component msg = Component.text("You must be looking at a beacon to open the menu.", NamedTextColor.RED);
            text.send(player, msg);
            return;
        }

        ForgeStationService.StationInfo info = optInfo.get();
        player.sendMessage(Component.text("Beacon menu opened at station: " + info.getId()).color(NamedTextColor.GREEN).toString());
    }

    private boolean commandReload(CommandSender sender) {
        if (!sender.hasPermission("flameforge.command.reload")) {
            send(sender, Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        send(sender, Component.text("Reloading configuration...", NamedTextColor.YELLOW));

        configService.asyncReloadWithCallback(() -> {
            Component msg = Component.text("Configuration reloaded successfully.", NamedTextColor.GREEN);
            scheduler.runGlobalLater(plugin, () -> send(sender, msg), 1L);
        });

        return true;
    }

    private boolean commandValidate(CommandSender sender) {
        if (!sender.hasPermission("flameforge.command.validate")) {
            send(sender, Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        send(sender, Component.text("Validating configuration...", NamedTextColor.YELLOW));

        scheduler.runAsync(plugin, () -> {
            configService.asyncReloadWithCallback(() -> {
                Component result = buildValidationResult();
                scheduler.runGlobalLater(plugin, () -> send(sender, result), 1L);
            });
        });

        return true;
    }

    private Component buildValidationResult() {
        ValidationReport report = configService.getValidationReport();
        if (!report.hasErrors() && !report.hasWarnings()) {
            return Component.text("Validation passed: No errors or warnings found.", NamedTextColor.GREEN);
        }

        Component result = Component.text("Validation Report:", NamedTextColor.YELLOW);

        if (report.hasErrors()) {
            for (ValidationIssue issue : report.getErrors()) {
                result = result.append(Component.text()
                    .append(Component.text("\n  [ERROR] ", NamedTextColor.RED))
                    .append(Component.text(issue.getPath() + ": " + issue.getMessage(), NamedTextColor.WHITE))
                    .build());
            }
        }

        if (report.hasWarnings()) {
            for (ValidationIssue issue : report.getWarnings()) {
                result = result.append(Component.text()
                    .append(Component.text("\n  [WARN] ", NamedTextColor.YELLOW))
                    .append(Component.text(issue.getPath() + ": " + issue.getMessage(), NamedTextColor.WHITE))
                    .build());
            }
        }

        return result;
    }

    private boolean commandTiers(CommandSender sender, int page) {
        if (!sender.hasPermission("flameforge.command.tiers")) {
            send(sender, Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        List<TierDefinition> tiers = configService.getAllTiers();
        if (tiers.isEmpty()) {
            send(sender, Component.text("No tiers configured.", NamedTextColor.YELLOW));
            return true;
        }

        int totalPages = (tiers.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Math.max(1, Math.min(page, totalPages));

        send(sender, Component.text("=== Tiers (" + page + "/" + totalPages + ") ===", NamedTextColor.GOLD));

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, tiers.size());

        for (int i = start; i < end; i++) {
            TierDefinition tier = tiers.get(i);
            Component line = Component.text()
                .color(NamedTextColor.GRAY)
                .append(Component.text(tier.getId(), NamedTextColor.YELLOW))
                .append(Component.text(" (Priority: " + tier.getTierLevel() + ")", NamedTextColor.WHITE))
                .build();
            send(sender, line);
        }

        if (totalPages > 1) {
            send(sender, Component.text("Use /flameforge tiers <page> for more pages.", NamedTextColor.GRAY));
        }

        return true;
    }

    private boolean commandTierInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("flameforge.command.tier.info")) {
            send(sender, Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 3 || !"info".equals(args[1].toLowerCase())) {
            send(sender, Component.text("Usage: /flameforge tier info <tier>", NamedTextColor.RED));
            return true;
        }

        String tierId = args[2];
        Optional<TierDefinition> optTier = configService.findTier(tierId);

        if (!optTier.isPresent()) {
            send(sender, Component.text("Tier not found: " + tierId, NamedTextColor.RED));
            return true;
        }

        TierDefinition tier = optTier.get();
        TierParser.TierExtra extra = tierRepository.findExtra(tierId).orElse(null);

        send(sender, Component.text("=== Tier: " + tier.getId() + " ===", NamedTextColor.GOLD));
        send(sender, Component.text("Priority: " + tier.getTierLevel(), NamedTextColor.WHITE));
        send(sender, Component.text("Success Animation: " + tier.getSuccessAnimationDuration() + " ticks", NamedTextColor.WHITE));
        send(sender, Component.text("Fail Animation: " + tier.getFailAnimationDuration() + " ticks", NamedTextColor.WHITE));

        TierCost cost = tier.getCost();
        if (cost != null) {
            send(sender, Component.text("Cost Mode: " + cost.getMode().name(), NamedTextColor.WHITE));
        }

        if (extra != null) {
            send(sender, Component.text("Enabled: " + extra.isEnabled(), NamedTextColor.WHITE));
            send(sender, Component.text("Cooldown: " + extra.getCooldownSeconds() + " seconds", NamedTextColor.WHITE));
        }

        List<OutcomeDefinition> outcomes = tier.getOutcomes();
        if (!outcomes.isEmpty()) {
            send(sender, Component.text("Outcomes (" + outcomes.size() + "):", NamedTextColor.WHITE));
            for (OutcomeDefinition outcome : outcomes) {
                Component outcomeLine = Component.text()
                    .color(NamedTextColor.GRAY)
                    .append(Component.text("  - " + outcome.getId() + " (", NamedTextColor.YELLOW))
                    .append(Component.text(outcome.getType().name(), NamedTextColor.WHITE))
                    .append(Component.text(", weight: " + outcome.getWeight() + ")", NamedTextColor.GRAY))
                    .build();
                send(sender, outcomeLine);
            }
        }

        return true;
    }

    private boolean commandPreview(CommandSender sender, String[] args) {
        if (!sender.hasPermission("flameforge.command.preview")) {
            send(sender, Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player)) {
            send(sender, Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2) {
            send(sender, Component.text("Usage: /flameforge preview <tier> [material]", NamedTextColor.RED));
            return true;
        }

        String tierId = args[1];
        Optional<TierDefinition> optTier = configService.findTier(tierId);

        if (!optTier.isPresent()) {
            send(sender, Component.text("Tier not found: " + tierId, NamedTextColor.RED));
            return true;
        }

        TierDefinition tier = optTier.get();

        Player player = (Player) sender;
        ItemStack heldItem = player.getItemInHand();

        if (args.length >= 3) {
            String materialKey = args[2];
            if (materialResolver.resolve(materialKey).isPresent()) {
                heldItem = new ItemStack(materialResolver.resolveOrThrow(materialKey), 1);
            } else {
                send(sender, Component.text("Unknown material: " + materialKey, NamedTextColor.RED));
                return true;
            }
        }

        if (heldItem == null || heldItem.getType() == Material.AIR) {
            send(sender, Component.text("You must be holding an item to preview.", NamedTextColor.RED));
            return true;
        }

        List<OutcomeDefinition> outcomes = tier.getOutcomes();
        if (outcomes.isEmpty()) {
            send(sender, Component.text("This tier has no outcomes configured.", NamedTextColor.YELLOW));
            return true;
        }

        OutcomeDefinition firstOutcome = outcomes.get(0);
        if (firstOutcome.getMutation() != null) {
            Optional<ItemStack> preview = ItemFactory.getInstance().createPreview(firstOutcome.getMutation(), heldItem);
            if (preview.isPresent()) {
                send(sender, Component.text("Preview for tier '" + tierId + "':", NamedTextColor.GREEN));
                send(sender, Component.text("Result material: " + preview.get().getType().name(), NamedTextColor.WHITE));
            }
        } else {
            send(sender, Component.text("This tier does not modify items.", NamedTextColor.YELLOW));
        }

        return true;
    }

    private boolean commandHistory(CommandSender sender, String[] args) {
        if (!sender.hasPermission("flameforge.command.history")) {
            send(sender, Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        UUID targetUuid;
        String targetName;

        if (args.length > 1) {
            if (!sender.hasPermission("flameforge.command.history.others")) {
                send(sender, Component.text("You don't have permission to view other players' history.", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                send(sender, Component.text("Player not found.", NamedTextColor.RED));
                return true;
            }
            targetUuid = target.getUniqueId();
            targetName = target.getName();
        } else {
            if (!(sender instanceof Player)) {
                send(sender, Component.text("Usage: /flameforge history <player>", NamedTextColor.RED));
                return true;
            }
            targetUuid = ((Player) sender).getUniqueId();
            targetName = sender.getName();
        }

        send(sender, Component.text("History for " + targetName + ":", NamedTextColor.GOLD));

        PlayerStateRepository.PlayerState state = playerStateRepository.getSnapshot(targetUuid);
        if (state == null) {
            send(sender, Component.text("No history found for " + targetName, NamedTextColor.YELLOW));
            return true;
        }

        send(sender, Component.text("Current Tier: " + state.tier, NamedTextColor.WHITE));
        send(sender, Component.text("Reforges: " + state.tier, NamedTextColor.WHITE));

        if (state.pityCooldown > 0) {
            send(sender, Component.text("Pity Cooldown: " + state.pityCooldown + "ms remaining", NamedTextColor.WHITE));
        }

        send(sender, Component.text("(Full history log not yet implemented)", NamedTextColor.GRAY));

        return true;
    }

    private boolean commandStation(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, Component.text("Usage: /flameforge station <add|remove|list|info|teleport> [args]", NamedTextColor.RED));
            return true;
        }

        String stationCmd = args[1].toLowerCase();

        switch (stationCmd) {
            case "add":
                return commandStationAdd(sender, args);
            case "remove":
                return commandStationRemove(sender, args);
            case "list":
                return commandStationList(sender, args.length > 2 ? parsePage(args[2]) : 1);
            case "info":
                return commandStationInfo(sender, args);
            case "teleport":
                return commandStationTeleport(sender, args);
            default:
                send(sender, Component.text("Unknown station command. Use /flameforge station list to see available commands.", NamedTextColor.RED));
                return true;
        }
    }

    private boolean commandStationAdd(CommandSender sender, String[] args) {
        if (!sender.hasPermission("flameforge.command.station.add")) {
            send(sender, Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player)) {
            send(sender, Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 3) {
            send(sender, Component.text("Usage: /flameforge station add <id> [profile]", NamedTextColor.RED));
            return true;
        }

        String id = args[2];
        String profile = args.length > 3 ? args[3] : "default";

        Player player = (Player) sender;

        Optional<StationRepository.StationData> resolved = stationService.resolveStationFromClick(player);
        if (!resolved.isPresent()) {
            send(sender, Component.text("You must be looking at a beacon block.", NamedTextColor.RED));
            return true;
        }

        StationRepository.StationData beacon = resolved.get();
        boolean added = stationService.addStation(id, beacon.toLocation(Bukkit.getWorld(beacon.world)).getBlock(), profile);

        if (added) {
            send(sender, Component.text("Station '" + id + "' added successfully.", NamedTextColor.GREEN));
        } else {
            send(sender, Component.text("Failed to add station. ID may already exist or coordinates are invalid.", NamedTextColor.RED));
        }

        return true;
    }

    private boolean commandStationRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("flameforge.command.station.remove")) {
            send(sender, Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 3) {
            send(sender, Component.text("Usage: /flameforge station remove <id>", NamedTextColor.RED));
            return true;
        }

        String id = args[2];
        boolean removed = stationService.removeStation(id);

        if (removed) {
            send(sender, Component.text("Station '" + id + "' removed successfully.", NamedTextColor.GREEN));
        } else {
            send(sender, Component.text("Station not found: " + id, NamedTextColor.RED));
        }

        return true;
    }

    private boolean commandStationList(CommandSender sender, int page) {
        if (!sender.hasPermission("flameforge.command.station.list")) {
            send(sender, Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        List<StationRepository.StationData> stations = stationService.listStations();

        if (stations.isEmpty()) {
            send(sender, Component.text("No stations configured.", NamedTextColor.YELLOW));
            return true;
        }

        int totalPages = (stations.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        page = Math.max(1, Math.min(page, totalPages));

        send(sender, Component.text("=== Stations (" + page + "/" + totalPages + ") ===", NamedTextColor.GOLD));

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, stations.size());

        for (int i = start; i < end; i++) {
            StationRepository.StationData station = stations.get(i);
            Component line = Component.text()
                .color(NamedTextColor.GRAY)
                .append(Component.text(station.id, NamedTextColor.YELLOW))
                .append(Component.text(" [" + station.world + " " + station.x + "," + station.y + "," + station.z + "]", NamedTextColor.WHITE))
                .append(Component.text(" (Profile: " + station.profile + ")", NamedTextColor.GRAY))
                .build();
            send(sender, line);
        }

        if (totalPages > 1) {
            send(sender, Component.text("Use /flameforge station list <page> for more pages.", NamedTextColor.GRAY));
        }

        return true;
    }

    private boolean commandStationInfo(CommandSender sender, String[] args) {
        if (!sender.hasPermission("flameforge.command.station.info")) {
            send(sender, Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 3) {
            send(sender, Component.text("Usage: /flameforge station info <id>", NamedTextColor.RED));
            return true;
        }

        String id = args[2];
        Optional<ForgeStationService.StationInfo> optInfo = stationService.getStationInfo(id);

        if (!optInfo.isPresent()) {
            send(sender, Component.text("Station not found: " + id, NamedTextColor.RED));
            return true;
        }

        ForgeStationService.StationInfo info = optInfo.get();

        send(sender, Component.text("=== Station: " + info.getId() + " ===", NamedTextColor.GOLD));
        send(sender, Component.text("World: " + info.getWorld(), NamedTextColor.WHITE));
        send(sender, Component.text("Location: " + info.getX() + ", " + info.getY() + ", " + info.getZ(), NamedTextColor.WHITE));
        send(sender, Component.text("Profile: " + info.getProfileId(), NamedTextColor.WHITE));

        if (info.getProfile() != null) {
            send(sender, Component.text("Max Tier: " + info.getProfile().getMaxTierUnlocked(), NamedTextColor.WHITE));
        }

        return true;
    }

    private boolean commandStationTeleport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("flameforge.command.station.teleport")) {
            send(sender, Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (!(sender instanceof Player)) {
            send(sender, Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 3) {
            send(sender, Component.text("Usage: /flameforge station teleport <id>", NamedTextColor.RED));
            return true;
        }

        String id = args[2];
        Player player = (Player) sender;

        Optional<StationRepository.StationData> optStation = stationService.getStationById(id);
        if (!optStation.isPresent()) {
            send(sender, Component.text("Station not found: " + id, NamedTextColor.RED));
            return true;
        }

        StationRepository.StationData station = optStation.get();

        ForgeStationService.TeleportResult result = stationService.teleportToStation(player, station);

        switch (result) {
            case SUCCESS_SYNC:
            case SUCCESS_ASYNC:
                send(sender, Component.text("Teleporting to station '" + id + "'...", NamedTextColor.GREEN));
                break;
            case WORLD_NOT_FOUND:
                send(sender, Component.text("World not found for station.", NamedTextColor.RED));
                break;
            case WORLD_NOT_LOADED:
                send(sender, Component.text("World is not loaded. Teleport failed.", NamedTextColor.RED));
                break;
            case FAILURE:
            default:
                send(sender, Component.text("Teleport failed.", NamedTextColor.RED));
                break;
        }

        return true;
    }

    private boolean commandSetup(CommandSender sender, String[] args) {
        if (!sender.hasPermission("flameforge.command.setup.tier")) {
            send(sender, Component.text("You don't have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 3) {
            send(sender, Component.text("Usage: /flameforge setup tier <create|clone> [args]", NamedTextColor.RED));
            return true;
        }

        if (!"tier".equals(args[1].toLowerCase())) {
            send(sender, Component.text("Unknown setup command. Use /flameforge setup tier create|clone", NamedTextColor.RED));
            return true;
        }

        String tierCmd = args[2].toLowerCase();

        switch (tierCmd) {
            case "create":
                return commandSetupTierCreate(sender, args);
            case "clone":
                return commandSetupTierClone(sender, args);
            default:
                send(sender, Component.text("Unknown tier setup command. Use create or clone.", NamedTextColor.RED));
                return true;
        }
    }

    private boolean commandSetupTierCreate(CommandSender sender, String[] args) {
        if (args.length < 5) {
            send(sender, Component.text("Usage: /flameforge setup tier create <id> <priority>", NamedTextColor.RED));
            return true;
        }

        String id = args[3];
        int priority;

        try {
            priority = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            send(sender, Component.text("Priority must be a number.", NamedTextColor.RED));
            return true;
        }

        if (tierRepository.find(id).isPresent()) {
            send(sender, Component.text("Tier already exists: " + id, NamedTextColor.RED));
            return true;
        }

        TierDefinition tier = tierRepository.create(id, priority);

        send(sender, Component.text("Tier '" + id + "' created with priority " + priority + ".", NamedTextColor.GREEN));
        send(sender, Component.text("Note: New tiers are not populated with default outcomes. Edit the tier file manually.", NamedTextColor.YELLOW));

        return true;
    }

    private boolean commandSetupTierClone(CommandSender sender, String[] args) {
        if (args.length < 6) {
            send(sender, Component.text("Usage: /flameforge setup tier clone <source> <id> <priority>", NamedTextColor.RED));
            return true;
        }

        String sourceId = args[3];
        String newId = args[4];
        int priority;

        try {
            priority = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            send(sender, Component.text("Priority must be a number.", NamedTextColor.RED));
            return true;
        }

        if (tierRepository.find(sourceId).isEmpty()) {
            send(sender, Component.text("Source tier not found: " + sourceId, NamedTextColor.RED));
            return true;
        }

        if (tierRepository.find(newId).isPresent()) {
            send(sender, Component.text("Tier already exists: " + newId, NamedTextColor.RED));
            return true;
        }

        TierDefinition cloned = tierRepository.clone(sourceId, newId);

        if (cloned == null) {
            send(sender, Component.text("Failed to clone tier.", NamedTextColor.RED));
            return true;
        }

        send(sender, Component.text("Tier '" + newId + "' cloned from '" + sourceId + "' with priority " + priority + ".", NamedTextColor.GREEN));
        send(sender, Component.text("Note: Cloned tiers inherit all settings. Edit the tier file manually to adjust.", NamedTextColor.YELLOW));

        return true;
    }

    private int parsePage(String arg) {
        try {
            return Math.max(1, Integer.parseInt(arg));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private List<String> filterPrefix(List<String> list, String prefix) {
        if (prefix.isEmpty()) {
            return list;
        }
        return list.stream()
            .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
            .sorted(String::compareToIgnoreCase)
            .collect(Collectors.toList());
    }

    private List<String> filterPrefixTierIds(String prefix) {
        return configService.getAllTiers().stream()
            .map(TierDefinition::getId)
            .filter(id -> id.toLowerCase().startsWith(prefix.toLowerCase()))
            .sorted(String::compareToIgnoreCase)
            .collect(Collectors.toList());
    }

    private List<String> filterPrefixStationIds(String prefix) {
        return stationService.listStations().stream()
            .map(s -> s.id)
            .filter(id -> id.toLowerCase().startsWith(prefix.toLowerCase()))
            .sorted(String::compareToIgnoreCase)
            .collect(Collectors.toList());
    }

    private List<String> filterPrefixMaterial(String prefix) {
        return materialResolver.getAliases().keySet().stream()
            .filter(name -> name.toLowerCase().startsWith(prefix.toLowerCase()))
            .sorted(String::compareToIgnoreCase)
            .collect(Collectors.toList());
    }

    private List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .sorted(String::compareToIgnoreCase)
            .collect(Collectors.toList());
    }

    private void send(CommandSender sender, Component component) {
        text.send(sender, component);
    }

    private static final class CommandEntry {
        final String usage;
        final String description;

        CommandEntry(String usage, String description) {
            this.usage = usage;
            this.description = description;
        }
    }
}
