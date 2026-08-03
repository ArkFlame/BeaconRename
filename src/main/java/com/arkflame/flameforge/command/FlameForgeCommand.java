package com.arkflame.flameforge.command;

import com.arkflame.flameforge.ForgeAccessService;
import com.arkflame.flameforge.compat.material.MaterialResolver;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TeleportBridge;
import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.config.ValidationIssue;
import com.arkflame.flameforge.config.ValidationReport;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.TierChances;
import com.arkflame.flameforge.model.TierDefinition;
import com.arkflame.flameforge.model.TierRequirements;
import com.arkflame.flameforge.persistence.PlayerStateRepository;
import com.arkflame.flameforge.persistence.StationRepository;
import com.arkflame.flameforge.station.ForgeStationService;
import com.arkflame.flameforge.text.MessageArguments;
import com.arkflame.flameforge.text.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public final class FlameForgeCommand implements CommandExecutor, TabCompleter {

    private static final int LIST_PAGE_SIZE = 8;

    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final MessageService messageService;
    private final ConfigService configService;
    private final TierRepository tierRepository;
    private final MaterialResolver materialResolver;
    private final CommandSuggestionIndex suggestionIndex;
    private final AtomicReference<CommandContext> context = new AtomicReference<>(CommandContext.loading());

    public FlameForgeCommand(JavaPlugin plugin, SchedulerBridge scheduler, MessageService messageService,
                             ConfigService configService, TierRepository tierRepository,
                             CommandSuggestionIndex suggestionIndex) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.messageService = messageService;
        this.configService = configService;
        this.tierRepository = tierRepository;
        this.materialResolver = MaterialResolver.getInstance();
        this.suggestionIndex = suggestionIndex;
    }

    public void markLoading() {
        context.set(CommandContext.loading());
    }

    public void markReady(ReadyServices readyServices) {
        context.set(CommandContext.ready(readyServices));
    }

    public void markFailed(StartupFailure failure) {
        context.set(CommandContext.failed(failure));
    }

    public void markUnavailable() {
        context.set(CommandContext.unavailable());
    }

    public CommandContext snapshot() {
        return context.get();
    }

    public boolean isReady() {
        return snapshot().isReady();
    }

    public boolean isLoading() {
        return snapshot().isLoading();
    }

    public StartupFailure getStartupFailure() {
        return snapshot().getStartupFailure();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            return commandHelp(sender, Collections.emptyList(), label);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("help".equals(sub)) {
            if (args.length > 1 && !"help".equals(args[1])) {
                return commandHelpForGroup(sender, args, 1, label);
            }
            return commandHelp(sender, Collections.emptyList(), label);
        }

        switch (sub) {
            case "open":
                return commandOpen(sender, args);
            case "reload":
                return commandReload(sender);
            case "validate":
                return commandValidate(sender);
            case "tiers":
                return commandTiers(sender, args.length > 1 ? parsePageArg(args[1]) : 1);
            case "tier":
                return commandTierInfo(sender, args);
            case "preview":
                return commandPreview(sender, args);
            case "history":
                return commandHistory(sender, args);
            case "tp":
                return commandTeleport(sender, args);
            case "station":
                return commandStation(sender, args);
            case "setup":
                return commandSetup(sender, args);
            default:
                send(sender, "command.unknown");
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) {
            return Collections.emptyList();
        }

        String prefix = args[args.length - 1];
        if (args.length == 1) {
            return suggestionIndex.getRootSuggestions(sender, prefix);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help":
                return suggestionIndex.getHelpSuggestions(sender, args, prefix);
            case "open":
                if (args.length == 2 && permitted(sender, "flameforge.command.open.others")) {
                    return suggestionIndex.getOnlinePlayerSuggestions(prefix);
                }
                return Collections.emptyList();
            case "reload":
            case "validate":
            case "tiers":
                return Collections.emptyList();
            case "tier":
                if (args.length == 2) {
                    return suggestionIndex.getTierSubSuggestions(sender, prefix);
                }
                if (args.length == 3 && "info".equalsIgnoreCase(args[1])) {
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
                if (args.length == 2 && permitted(sender, "flameforge.command.history.others")) {
                    return suggestionIndex.getOnlinePlayerSuggestions(prefix);
                }
                return Collections.emptyList();
            case "tp":
                return permitted(sender, "flameforge.command.station.teleport")
                    ? filterPrefixStationIds(prefix) : Collections.emptyList();
            case "station":
                return tabCompleteStation(sender, args, prefix);
            case "setup":
                return tabCompleteSetup(sender, args, prefix);
            default:
                return Collections.emptyList();
        }
    }

    private boolean commandUnavailable(CommandSender sender, CommandContext ctx) {
        if (ctx.isFailed()) {
            StartupFailure failure = ctx.getStartupFailure();
            String reason = failure != null ? failure.getReason() : "unknown";
            send(sender, "command.failed", messageArguments("reason", reason));
        } else if (ctx.isUnavailable()) {
            send(sender, "command.unavailable");
        } else {
            send(sender, "command.loading");
        }
        return true;
    }

    private boolean requirePermission(CommandSender sender, String messageKey, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        send(sender, messageKey, messageArguments("permission", permission));
        return false;
    }

    private void sendStartupBlocker(CommandSender sender, CommandContext ctx) {
        if (ctx.isFailed()) {
            StartupFailure failure = ctx.getStartupFailure();
            String reason = failure != null ? failure.getReason() : "unknown";
            send(sender, "startup.failed", messageArguments("reason", reason));
        } else if (ctx.isUnavailable()) {
            send(sender, "startup.unavailable");
        } else {
            send(sender, "startup.loading");
        }
    }

    private boolean commandHelp(CommandSender sender, List<String> parentPath, String label) {
        if (!requirePermission(sender, "command.no-permission", "flameforge.command.help")) {
            return true;
        }

        List<CommandNode.HelpEntry> children = CommandNode.immediateChildren(sender, parentPath);

        String pluginName = (plugin.getDescription() != null && plugin.getDescription().getName() != null)
            ? plugin.getDescription().getName() : "FlameForge";
        String groupName = null;
        if (!parentPath.isEmpty()) {
            String lastToken = parentPath.get(parentPath.size() - 1);
            groupName = lastToken.substring(0, 1).toUpperCase(Locale.ROOT) + lastToken.substring(1);
            for (int i = parentPath.size() - 2; i >= 0; i--) {
                groupName = parentPath.get(i).substring(0, 1).toUpperCase(Locale.ROOT)
                    + parentPath.get(i).substring(1) + " " + groupName;
            }
        }

        boolean parentExists = false;
        if (!parentPath.isEmpty()) {
            String parentToken = parentPath.get(0);
            for (CommandNode node : CommandNode.values()) {
                String[] parts = node.getSuggestion().split(" ");
                if (parts.length > 0 && parts[0].equalsIgnoreCase(parentToken)) {
                    parentExists = true;
                    break;
                }
            }
        }

        send(sender, "help.border");
        String headerKey = parentPath.isEmpty() ? "help.root-header" : "help.group-header";
        if (parentPath.isEmpty()) {
            send(sender, headerKey, messageArguments("plugin_name", pluginName));
        } else {
            send(sender, headerKey, messageArguments("plugin_name", pluginName, "group_name", groupName));
        }

        for (CommandNode.HelpEntry entry : children) {
            String description = entry.getDescriptionKey() != null
                ? messageService.findMessageString(entry.getDescriptionKey()).orElse("")
                : "";
            MessageArguments arguments = messageArguments(
                "label", label,
                "usage", entry.getUsage(),
                "description", description,
                "suggestion", entry.getSuggestion()
            );
            Component line = messageService.renderToComponent("help.entry", sender, arguments)
                .hoverEvent(HoverEvent.showText(messageService.renderToComponent("help.hover", sender,
                    messageArguments("label", label, "suggestion", entry.getSuggestion()))))
                .clickEvent(ClickEvent.suggestCommand("/" + label + " " + entry.getSuggestion()));
            messageService.sendComponent(sender, line);
        }

        if (children.isEmpty()) {
            if (!parentPath.isEmpty() && !parentExists) {
                StringBuilder pathBuilder = new StringBuilder();
                for (int i = 0; i < parentPath.size(); i++) {
                    if (i > 0) pathBuilder.append(" ");
                    pathBuilder.append(parentPath.get(i));
                }
                send(sender, "help.unknown-path", messageArguments("path", pathBuilder.toString()));
            } else {
                send(sender, "help.empty");
            }
        }

        send(sender, "help.border");
        return true;
    }

    private boolean commandHelpForGroup(CommandSender sender, String[] args, int groupStart, String label) {
        List<String> parentPath = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i].toLowerCase(Locale.ROOT);
            if ("help".equals(arg)) {
                continue;
            }
            parentPath.add(arg);
        }
        return commandHelp(sender, parentPath, label);
    }

    private boolean commandOpen(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "command.no-permission", "flameforge.command.open")) {
            return true;
        }
        if (!(sender instanceof Player)) {
            send(sender, "command.player-only");
            return true;
        }

        Player target = (Player) sender;
        if (args.length > 1) {
            if (!requirePermission(sender, "open.no-permission", "flameforge.command.open.others")) {
                return true;
            }
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                send(sender, "open.no-target");
                return true;
            }
        }

        CommandContext ctx = snapshot();
        if (!ctx.isReady()) {
            sendStartupBlocker(sender, ctx);
            return true;
        }

        openForgeForTarget(sender, target);
        return true;
    }

    private void openForgeForTarget(CommandSender sender, Player target) {
        ForgeStationService stationService = snapshot().getReadyServices().getStationService();
        stationService.resolveRegisteredForgeFromTarget(target).thenAccept(station ->
            runOnSenderScheduler(sender, () -> {
                if (!station.isPresent()) {
                    send(sender, "open.no-forge-target");
                    return;
                }

                ForgeAccessService accessService = snapshot().getReadyServices().getAccessService();
                accessService.openForgeFromId(target, station.get().id).thenAccept(result -> {
                    runOnSenderScheduler(sender, () -> {
                        switch (result.getStatus()) {
                            case OPENED:
                                send(sender, "open.menu-opened", messageArguments("station_id", station.get().id,
                                    "player_name", target.getName()));
                                break;
                            case PLAYER_OFFLINE:
                                send(sender, "open.player-offline");
                                break;
                            case FORGE_NOT_FOUND:
                                send(sender, "open.forge-not-found", messageArguments("station_id", result.getStationId()));
                                break;
                            case PROFILE_NOT_FOUND:
                                send(sender, "open.profile-not-found", messageArguments("station_id", result.getStationId()));
                                break;
                            case PERMISSION_REQUIRED: {
                                List<String> perms = result.getRequiredPermissions();
                                String permStr = perms.isEmpty() ? "" : perms.get(0);
                                send(sender, "open.station-permission-required", messageArguments("permission", permStr));
                                break;
                            }
                            case NO_ALLOWED_TIER:
                                send(sender, "open.no-allowed-tier", messageArguments("station_id", result.getStationId()));
                                break;
                            case SCHEDULER_REJECTED:
                                send(sender, "open.scheduler-rejected", messageArguments("reference", result.getReference()));
                                break;
                            case MENU_OPEN_FAILED:
                                send(sender, "open.menu-open-failed", messageArguments("reference", result.getReference()));
                                break;
                            case PLAYER_RETIRED:
                                send(sender, "open.player-retired");
                                break;
                        }
                    });
                });
            }));
    }

    private boolean commandReload(CommandSender sender) {
        if (!requirePermission(sender, "reload.no-permission", "flameforge.command.reload")) {
            return true;
        }
        send(sender, "reload.started");
        configService.reloadAsync().whenComplete((result, ex) -> runOnSenderScheduler(sender, () -> {
            if (ex != null) {
                send(sender, "reload.load-failed", messageArguments("reason", ex.getMessage()));
                return;
            }
            switch (result.getStatus()) {
                case APPLIED:
                    send(sender, "reload.success");
                    break;
                case VALIDATION_REJECTED:
                    send(sender, "reload.validation-rejected");
                    break;
                case ALREADY_RUNNING:
                    send(sender, "reload.already-running");
                    break;
                case SCHEDULER_REJECTED:
                    send(sender, "reload.scheduler-rejected", messageArguments("reference", result.getReference()));
                    break;
                case LOAD_FAILED:
                    send(sender, "reload.load-failed", messageArguments("reason", result.getReason(),
                        "reference", result.getReference()));
                    break;
            }
        }));
        return true;
    }

    private boolean commandValidate(CommandSender sender) {
        if (!requirePermission(sender, "validate.no-permission", "flameforge.command.validate")) {
            return true;
        }
        send(sender, "validate.started");
        scheduler.runAsync(plugin, () -> configService.asyncReloadWithCallback(() ->
            runOnSenderScheduler(sender, () -> sendValidationResult(sender))));
        return true;
    }

    private void sendValidationResult(CommandSender sender) {
        ValidationReport report = configService.getValidationReport();
        if (!report.hasErrors() && !report.hasWarnings()) {
            send(sender, "validate.passed");
            return;
        }
        for (ValidationIssue issue : report.getErrors()) {
            send(sender, "validate.error", messageArguments("path", issue.getPath(), "message", issue.getMessage()));
        }
        for (ValidationIssue issue : report.getWarnings()) {
            send(sender, "validate.warning", messageArguments("path", issue.getPath(), "message", issue.getMessage()));
        }
    }

    private boolean commandTiers(CommandSender sender, int page) {
        if (!requirePermission(sender, "tiers.no-permission", "flameforge.command.tiers")) {
            return true;
        }
        List<TierDefinition> tiers = tierRepository.allAscending();
        if (tiers.isEmpty()) {
            send(sender, "tiers.empty");
            return true;
        }
        int totalPages = (tiers.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE;
        page = Math.max(1, Math.min(page, totalPages));
        send(sender, "tiers.header", messageArguments("page", String.valueOf(page),
            "total_pages", String.valueOf(totalPages)));
        int start = (page - 1) * LIST_PAGE_SIZE;
        int end = Math.min(start + LIST_PAGE_SIZE, tiers.size());
        for (int i = start; i < end; i++) {
            TierDefinition tier = tiers.get(i);
            send(sender, "tiers.entry", messageArguments("tier_id", tier.getId(),
                "level", String.valueOf(tier.getLevel())));
        }
        if (totalPages > 1) {
            send(sender, "tiers.footer", messageArguments("page", String.valueOf(page),
                "total_pages", String.valueOf(totalPages)));
        }
        return true;
    }

    private boolean commandTierInfo(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "tier-info.no-permission", "flameforge.command.tier.info")) {
            return true;
        }
        if (args.length < 3 || !"info".equalsIgnoreCase(args[1])) {
            send(sender, "tier-info.usage");
            return true;
        }
        String tierId = args[2];
        Optional<TierDefinition> optTier = tierRepository.findById(tierId);
        if (!optTier.isPresent()) {
            send(sender, "tier-info.not-found", messageArguments("tier_id", tierId));
            return true;
        }

        TierDefinition tier = optTier.get();
        send(sender, "tier-info.header", messageArguments("tier_id", tier.getId()));
        send(sender, "tier-info.level", messageArguments("level", String.valueOf(tier.getLevel())));
        send(sender, "tier-info.enabled", messageArguments("enabled", String.valueOf(tier.isEnabled())));
        send(sender, "tier-info.cooldown", messageArguments("seconds", String.valueOf(tier.getCooldownSeconds())));

        TierRequirements reqs = tier.getRequirements();
        if (reqs != null) {
            if (reqs.getCombine() != null) {
                send(sender, "tier-info.requirements-combine", messageArguments("combine", reqs.getCombine().name()));
            }
            TierRequirements.XpRequirement xp = reqs.getXp();
            if (xp != null && xp.isEnabled()) {
                send(sender, "tier-info.requirement-xp", messageArguments("level", String.valueOf(xp.getLevel())));
            }
            TierRequirements.MoneyRequirement money = reqs.getMoney();
            if (money != null && money.isEnabled()) {
                send(sender, "tier-info.requirement-money", messageArguments("amount", money.getAmount().toString()));
            }
            TierRequirements.ItemsRequirement items = reqs.getItems();
            if (items != null && items.isEnabled() && !items.getItems().isEmpty()) {
                send(sender, "tier-info.requirements-items-header");
                for (TierRequirements.ItemRequirement item : items.getItems()) {
                    StringBuilder matBuilder = new StringBuilder();
                    List<String> mats = item.getMaterialCandidates();
                    for (int i = 0; i < mats.size(); i++) {
                        if (i > 0) matBuilder.append(",");
                        matBuilder.append(mats.get(i));
                    }
                    send(sender, "tier-info.requirement-item", messageArguments(
                        "materials", matBuilder.toString(),
                        "amount", String.valueOf(item.getAmount()),
                        "name", item.getDisplayName() != null ? item.getDisplayName() : ""));
                }
            }
        }

        TierChances chances = tier.getChances();
        if (chances != null) {
            send(sender, "tier-info.chances", messageArguments(
                "success", String.valueOf(chances.getSuccessPercent()),
                "break", String.valueOf(chances.getBreakPercent()),
                "curse", String.valueOf(chances.getCursePercent())));
        }

        List<ForgeVariant> variants = tier.getVariants();
        if (variants != null && !variants.isEmpty()) {
            send(sender, "tier-info.variants-header", messageArguments("count", String.valueOf(variants.size())));
            for (ForgeVariant variant : variants) {
                send(sender, "tier-info.variant-entry", messageArguments(
                    "variant_id", variant.getId(),
                    "name", variant.getName(),
                    "weight", String.valueOf(variant.getWeight())));
            }
        }
        return true;
    }

    private boolean commandPreview(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "preview.no-permission", "flameforge.command.preview")) {
            return true;
        }
        if (!(sender instanceof Player)) {
            send(sender, "command.player-only");
            return true;
        }
        if (args.length < 2) {
            send(sender, "preview.usage");
            return true;
        }
        String tierId = args[1];
        Optional<TierDefinition> optTier = tierRepository.findById(tierId);
        if (!optTier.isPresent()) {
            send(sender, "preview.tier-not-found", messageArguments("tier_id", tierId));
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
                send(sender, "preview.unknown-material", messageArguments("material", materialKey));
                return true;
            }
        }
        if (heldItem == null || heldItem.getType() == Material.AIR) {
            send(sender, "preview.no-item");
            return true;
        }

        List<ForgeVariant> variants = tier.getVariants();
        if (variants == null || variants.isEmpty()) {
            send(sender, "preview.no-variants");
            return true;
        }

        ForgeVariant selectedVariant = selectEligibleVariant(variants, heldItem);
        if (selectedVariant == null) {
            send(sender, "preview.no-eligible-variant");
            return true;
        }

        send(sender, "preview.variant", messageArguments("tier_id", tierId,
            "variant_id", selectedVariant.getId(),
            "variant_name", selectedVariant.getName()));
        send(sender, "preview.material", messageArguments("material", heldItem.getType().name()));
        return true;
    }

    private ForgeVariant selectEligibleVariant(List<ForgeVariant> variants, ItemStack item) {
        for (ForgeVariant variant : variants) {
            if (isVariantEligible(variant, item)) {
                return variant;
            }
        }
        return null;
    }

    private boolean isVariantEligible(ForgeVariant variant, ItemStack item) {
        List<String> candidates = variant.getEnchantmentCandidates();
        if (candidates == null || candidates.isEmpty()) {
            return true;
        }
        String itemMaterial = item.getType().name();
        for (String candidate : candidates) {
            if (candidate.equalsIgnoreCase(itemMaterial) || candidate.equalsIgnoreCase("ANY")) {
                return true;
            }
        }
        return false;
    }

    private boolean commandHistory(CommandSender sender, String[] args) {
        UUID targetUuid;
        String targetName;
        if (args.length > 1) {
            if (!requirePermission(sender, "history.no-permission", "flameforge.command.history.others")) {
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                send(sender, "open.no-target");
                return true;
            }
            targetUuid = target.getUniqueId();
            targetName = target.getName();
        } else {
            if (!requirePermission(sender, "history.no-permission", "flameforge.command.history")) {
                return true;
            }
            if (!(sender instanceof Player)) {
                send(sender, "history.usage");
                return true;
            }
            targetUuid = ((Player) sender).getUniqueId();
            targetName = sender.getName();
        }

        send(sender, "history.header", messageArguments("player_name", targetName));
        CommandContext ctx = snapshot();
        if (!ctx.isReady()) {
            sendStartupBlocker(sender, ctx);
            return true;
        }
        PlayerStateRepository.PlayerState state = ctx.getReadyServices().getPlayerStateRepository()
            .getSnapshot(targetUuid);
        if (state == null) {
            send(sender, "history.no-history", messageArguments("player_name", targetName));
            return true;
        }
        send(sender, "history.current-tier", messageArguments("tier", String.valueOf(state.tier)));
        send(sender, "history.not-implemented");
        return true;
    }

    private boolean commandTeleport(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "tp.no-permission", "flameforge.command.station.teleport")) {
            return true;
        }
        if (!(sender instanceof Player)) {
            send(sender, "tp.player-only");
            return true;
        }
        if (args.length < 2) {
            send(sender, "tp.usage");
            return true;
        }
        String id = args[1];
        CommandContext ctx = snapshot();
        if (!ctx.isReady()) {
            sendStartupBlocker(sender, ctx);
            return true;
        }
        ForgeStationService stationService = ctx.getReadyServices().getStationService();
        StationRepository.StationData station = stationService.getStationById(id).orElse(null);
        if (station == null) {
            send(sender, "tp.not-found", messageArguments("station_id", id));
            return true;
        }
        stationService.teleportToStation((Player) sender, station)
            .thenAccept(result -> sendTeleportResult(sender, result, "tp", id));
        return true;
    }

    private boolean commandStation(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "station.usage");
            return true;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "add":
                return commandStationAdd(sender, args);
            case "remove":
                return commandStationRemove(sender, args);
            case "list":
                return commandStationList(sender, args.length > 2 ? parsePageArg(args[2]) : 1);
            case "info":
                return commandStationInfo(sender, args);
            case "teleport":
                return commandStationTeleport(sender, args);
            default:
                send(sender, "station.unknown");
                return true;
        }
    }

    private boolean commandStationAdd(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "station-add.no-permission", "flameforge.command.station.add")) {
            return true;
        }
        if (!(sender instanceof Player)) {
            send(sender, "station-add.player-only");
            return true;
        }
        if (args.length > 4) {
            send(sender, "station-add.usage");
            return true;
        }

        Optional<String> requestedId = args.length > 2 ? Optional.of(args[2]) : Optional.empty();
        String profile = args.length > 3 ? args[3] : ForgeStationService.DEFAULT_PROFILE_ID;
        CommandContext ctx = snapshot();
        if (!ctx.isReady()) {
            sendStartupBlocker(sender, ctx);
            return true;
        }

        ForgeStationService stationService = ctx.getReadyServices().getStationService();
        stationService.addTargetedForge((Player) sender, requestedId, profile).thenAccept(outcome ->
            runOnSenderScheduler(sender, () -> sendAddOutcome(sender, outcome)));
        return true;
    }

    private void sendAddOutcome(CommandSender sender, ForgeStationService.AddForgeOutcome outcome) {
        switch (outcome.result()) {
            case ADDED:
                StationRepository.RegisteredForge forge = outcome.forge();
                send(sender, "station-add.success", messageArguments("station_id", outcome.finalId(),
                    "world", forge.getWorldName(), "x", String.valueOf(forge.getX()),
                    "y", String.valueOf(forge.getY()), "z", String.valueOf(forge.getZ()),
                    "profile", forge.getProfileId()));
                send(sender, "station-list.entry", messageArguments("station_id", outcome.finalId(),
                    "world", forge.getWorldName(), "x", String.valueOf(forge.getX()),
                    "y", String.valueOf(forge.getY()), "z", String.valueOf(forge.getZ()),
                    "profile", forge.getProfileId()));
                suggestionIndex.updateStationIds(currentStationIds());
                return;
            case INVALID_ID:
                send(sender, "station-add.invalid-id", messageArguments("station_id", outcome.finalId()));
                return;
            case UNKNOWN_PROFILE:
                send(sender, "station-add.unknown-profile", messageArguments("station_id", outcome.finalId()));
                return;
            case TARGET_UNAVAILABLE:
                send(sender, "station-add.target-unavailable", messageArguments("station_id", outcome.finalId()));
                return;
            case DUPLICATE_ID:
                send(sender, "station-add.duplicate-id", messageArguments("station_id", outcome.finalId()));
                return;
            case DUPLICATE_LOCATION:
                send(sender, "station-add.duplicate-location", messageArguments("station_id", outcome.finalId()));
                return;
            case PERSISTENCE_FAILED:
                send(sender, "station-add.persistence-failed", messageArguments("station_id", outcome.finalId()));
                return;
            case ID_GENERATION_EXHAUSTED:
                send(sender, "station-add.id-generation-exhausted", messageArguments("station_id", outcome.finalId()));
                return;
            case NO_TARGET:
                send(sender, "station-add.no-block");
                return;
            case PLAYER_RETIRED:
                send(sender, "station-add.player-only");
                return;
        }
    }

    private List<String> currentStationIds() {
        CommandContext ctx = snapshot();
        if (!ctx.isReady()) {
            return Collections.emptyList();
        }
        return ctx.getReadyServices().getStationService().listStations().stream()
            .map(station -> station.id).collect(Collectors.toList());
    }

    private boolean commandStationRemove(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "station-remove.no-permission", "flameforge.command.station.remove")) {
            return true;
        }
        if (args.length < 3) {
            send(sender, "station-remove.usage");
            return true;
        }
        String id = args[2];
        CommandContext ctx = snapshot();
        if (!ctx.isReady()) {
            sendStartupBlocker(sender, ctx);
            return true;
        }
        ctx.getReadyServices().getStationService().removeStation(id).thenAccept(outcome ->
            runOnSenderScheduler(sender, () -> {
                switch (outcome.getResult()) {
                    case REMOVED:
                        send(sender, "station-remove.success", messageArguments("station_id", id));
                        suggestionIndex.updateStationIds(currentStationIds());
                        break;
                    case NOT_FOUND:
                        send(sender, "station-remove.not-found", messageArguments("station_id", id));
                        break;
                    case PERSISTENCE_FAILED:
                        send(sender, "station-remove.persistence-failed",
                            messageArguments("station_id", id, "reference", outcome.getReference()));
                        break;
                }
            }));
        return true;
    }

    private boolean commandStationList(CommandSender sender, int page) {
        if (!requirePermission(sender, "station-list.no-permission", "flameforge.command.station.list")) {
            return true;
        }
        CommandContext ctx = snapshot();
        if (!ctx.isReady()) {
            sendStartupBlocker(sender, ctx);
            return true;
        }
        List<StationRepository.StationData> stations = ctx.getReadyServices().getStationService().listStations();
        if (stations.isEmpty()) {
            send(sender, "station-list.empty");
            return true;
        }
        int totalPages = (stations.size() + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE;
        page = Math.max(1, Math.min(page, totalPages));
        send(sender, "station-list.header", messageArguments("page", String.valueOf(page),
            "total_pages", String.valueOf(totalPages)));
        int start = (page - 1) * LIST_PAGE_SIZE;
        int end = Math.min(start + LIST_PAGE_SIZE, stations.size());
        for (int i = start; i < end; i++) {
            StationRepository.StationData station = stations.get(i);
            send(sender, "station-list.entry", messageArguments("station_id", station.id,
                "world", station.world, "x", String.valueOf(station.x), "y", String.valueOf(station.y),
                "z", String.valueOf(station.z), "profile", station.profile));
        }
        if (totalPages > 1) {
            send(sender, "station-list.footer", messageArguments("page", String.valueOf(page),
                "total_pages", String.valueOf(totalPages)));
        }
        return true;
    }

    private boolean commandStationInfo(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "station-info.no-permission", "flameforge.command.station.info")) {
            return true;
        }
        if (args.length < 3) {
            send(sender, "station-info.usage");
            return true;
        }
        String id = args[2];
        CommandContext ctx = snapshot();
        if (!ctx.isReady()) {
            sendStartupBlocker(sender, ctx);
            return true;
        }
        ForgeStationService.StationInfo info = ctx.getReadyServices().getStationService().getStationInfo(id).orElse(null);
        if (info == null) {
            send(sender, "station-info.not-found", messageArguments("station_id", id));
            return true;
        }
        send(sender, "station-info.header", messageArguments("station_id", info.getId()));
        send(sender, "station-info.world", messageArguments("world", info.getWorld()));
        send(sender, "station-info.location", messageArguments("x", String.valueOf(info.getX()),
            "y", String.valueOf(info.getY()), "z", String.valueOf(info.getZ())));
        send(sender, "station-info.profile", messageArguments("profile", info.getProfileId()));
        if (info.getProfile() != null) {
            send(sender, "station-info.max-tier", messageArguments("max_tier",
                String.valueOf(info.getProfile().getMaxTierUnlocked())));
        }
        return true;
    }

    private boolean commandStationTeleport(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "station-teleport.no-permission", "flameforge.command.station.teleport")) {
            return true;
        }
        if (!(sender instanceof Player)) {
            send(sender, "station-teleport.player-only");
            return true;
        }
        if (args.length < 3) {
            send(sender, "station-teleport.usage");
            return true;
        }
        String id = args[2];
        CommandContext ctx = snapshot();
        if (!ctx.isReady()) {
            sendStartupBlocker(sender, ctx);
            return true;
        }
        ForgeStationService stationService = ctx.getReadyServices().getStationService();
        StationRepository.StationData station = stationService.getStationById(id).orElse(null);
        if (station == null) {
            send(sender, "station-teleport.not-found", messageArguments("station_id", id));
            return true;
        }
        stationService.teleportToStation((Player) sender, station)
            .thenAccept(result -> sendTeleportResult(sender, result, "station-teleport", id));
        return true;
    }

    private void sendTeleportResult(CommandSender sender, TeleportBridge.TeleportOutcome result,
                                    String keyPrefix, String id) {
        switch (result.getStatus()) {
            case TELEPORTED:
                send(sender, keyPrefix + ".success", messageArguments("station_id", id));
                break;
            case PLAYER_OFFLINE:
                send(sender, keyPrefix + ".player-offline");
                break;
            case WORLD_NOT_FOUND:
                send(sender, keyPrefix + ".world-not-found");
                break;
            case WORLD_NOT_LOADED:
                send(sender, keyPrefix + ".world-not-loaded");
                break;
            case TELEPORT_REJECTED:
                send(sender, keyPrefix + ".teleport-rejected");
                break;
            case TELEPORT_EXCEPTION:
                send(sender, keyPrefix + ".teleport-exception", messageArguments("reason", result.getReason()));
                break;
            case PLAYER_RETIRED:
                send(sender, keyPrefix + ".player-retired");
                break;
            case SCHEDULER_REJECTED:
                send(sender, keyPrefix + ".scheduler-rejected");
                break;
        }
    }

    private boolean commandSetup(CommandSender sender, String[] args) {
        if (!requirePermission(sender, "setup.no-permission", "flameforge.command.setup.tier")) {
            return true;
        }
        if (args.length < 3) {
            send(sender, "setup.usage");
            return true;
        }
        if (!"tier".equalsIgnoreCase(args[1])) {
            send(sender, "setup.unknown");
            return true;
        }
        switch (args[2].toLowerCase(Locale.ROOT)) {
            case "create":
                return commandSetupTierCreate(sender, args);
            case "clone":
                return commandSetupTierClone(sender, args);
            default:
                send(sender, "setup.unknown");
                return true;
        }
    }

    private boolean commandSetupTierCreate(CommandSender sender, String[] args) {
        if (args.length < 5) {
            send(sender, "setup-tier-create.usage");
            return true;
        }
        String id = args[3];
        int level;
        try {
            level = Integer.parseInt(args[4]);
        } catch (NumberFormatException e) {
            send(sender, "setup-tier-create.level-invalid");
            return true;
        }
        if (tierRepository.findById(id).isPresent()) {
            send(sender, "setup-tier-create.already-exists", messageArguments("tier_id", id));
            return true;
        }
        tierRepository.create(id, level);
        send(sender, "setup-tier-create.success", messageArguments("tier_id", id,
            "level", String.valueOf(level)));
        send(sender, "setup-tier-create.note");
        return true;
    }

    private boolean commandSetupTierClone(CommandSender sender, String[] args) {
        if (args.length < 6) {
            send(sender, "setup-tier-clone.usage");
            return true;
        }
        String sourceId = args[3];
        String newId = args[4];
        int level;
        try {
            level = Integer.parseInt(args[5]);
        } catch (NumberFormatException e) {
            send(sender, "setup-tier-clone.level-invalid");
            return true;
        }
        if (!tierRepository.findById(sourceId).isPresent()) {
            send(sender, "setup-tier-clone.source-not-found", messageArguments("source_id", sourceId));
            return true;
        }
        if (tierRepository.findById(newId).isPresent()) {
            send(sender, "setup-tier-clone.already-exists", messageArguments("new_id", newId));
            return true;
        }
        TierDefinition cloned = tierRepository.clone(sourceId, newId, level);
        if (cloned == null) {
            send(sender, "setup-tier-clone.failure");
            return true;
        }
        send(sender, "setup-tier-clone.success", messageArguments("new_id", newId,
            "source_id", sourceId, "level", String.valueOf(level)));
        send(sender, "setup-tier-clone.note");
        return true;
    }

    private int parsePageArg(String value) {
        try {
            return Math.max(1, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private List<String> filterPrefixTierIds(String prefix) {
        return filterPrefix(tierRepository.allAscending().stream().map(TierDefinition::getId)
            .collect(Collectors.toList()), prefix);
    }

    private List<String> filterPrefixStationIds(String prefix) {
        return suggestionIndex.getStationIdSuggestions(prefix);
    }

    private List<String> filterPrefixMaterial(String prefix) {
        return filterPrefix(new ArrayList<>(materialResolver.getAliases().keySet()), prefix);
    }

    private List<String> filterPrefix(List<String> values, String prefix) {
        String lowerPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        Map<String, String> matches = new LinkedHashMap<>();
        for (String value : values) {
            if (value != null && value.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                matches.put(value.toLowerCase(Locale.ROOT), value);
            }
        }
        List<String> result = new ArrayList<>(matches.values());
        Collections.sort(result, String::compareToIgnoreCase);
        return result;
    }

    private List<String> tabCompleteStation(CommandSender sender, String[] args, String prefix) {
        if (!hasAnyStationPermission(sender)) {
            return Collections.emptyList();
        }
        if (args.length == 2) {
            return suggestionIndex.getStationSubSuggestions(sender, prefix);
        }
        String stationCommand = args[1].toLowerCase(Locale.ROOT);
        if ("add".equals(stationCommand) && permitted(sender, "flameforge.command.station.add")) {
            return suggestionIndex.getStationAddSuggestions(args, prefix);
        }
        if (("remove".equals(stationCommand) && permitted(sender, "flameforge.command.station.remove"))
            || ("info".equals(stationCommand) && permitted(sender, "flameforge.command.station.info"))
            || ("teleport".equals(stationCommand) && permitted(sender, "flameforge.command.station.teleport"))) {
            return args.length == 3 ? filterPrefixStationIds(prefix) : Collections.emptyList();
        }
        return Collections.emptyList();
    }

    private List<String> tabCompleteSetup(CommandSender sender, String[] args, String prefix) {
        if (!permitted(sender, "flameforge.command.setup.tier")) {
            return Collections.emptyList();
        }
        if (args.length == 2) {
            return catalogSuggestions(sender, "setup", 1, prefix);
        }
        if (!"tier".equalsIgnoreCase(args[1])) {
            return Collections.emptyList();
        }
        if (args.length == 3) {
            return catalogSuggestions(sender, "setup", 2, prefix);
        }
        String tierCommand = args[2].toLowerCase(Locale.ROOT);
        if ("create".equals(tierCommand)) {
            if (args.length == 4) {
                return filterPrefix(Collections.singletonList("<id>"), prefix);
            }
            if (args.length == 5) {
                return filterPrefix(Collections.singletonList("<priority>"), prefix);
            }
        }
        if ("clone".equals(tierCommand)) {
            if (args.length == 4) {
                return filterPrefixTierIds(prefix);
            }
            if (args.length == 5) {
                return filterPrefix(Collections.singletonList("<id>"), prefix);
            }
            if (args.length == 6) {
                return filterPrefix(Collections.singletonList("<priority>"), prefix);
            }
        }
        return Collections.emptyList();
    }

    private List<String> catalogSuggestions(CommandSender sender, String root, int part, String prefix) {
        Map<String, String> suggestions = new LinkedHashMap<>();
        for (CommandNode node : CommandNode.values()) {
            if (!node.isPermitted(sender) || !node.getRoot().equalsIgnoreCase(root)) {
                continue;
            }
            String[] parts = node.getSuggestion().split(" ");
            if (parts.length > part) {
                String suggestion = parts[part];
                suggestions.put(suggestion.toLowerCase(Locale.ROOT), suggestion);
            }
        }
        return filterPrefix(new ArrayList<>(suggestions.values()), prefix);
    }

    private boolean hasAnyStationPermission(CommandSender sender) {
        return permitted(sender, "flameforge.command.station.add")
            || permitted(sender, "flameforge.command.station.remove")
            || permitted(sender, "flameforge.command.station.list")
            || permitted(sender, "flameforge.command.station.info")
            || permitted(sender, "flameforge.command.station.teleport");
    }

    private boolean permitted(CommandSender sender, String permission) {
        return sender.hasPermission(CommandNode.ADMIN_PERMISSION) || sender.hasPermission(permission);
    }

    private void send(CommandSender sender, String key) {
        messageService.send(sender, key);
    }

    private void send(CommandSender sender, String key, MessageArguments arguments) {
        messageService.send(sender, key, arguments);
    }

    private MessageArguments messageArguments(String... values) {
        MessageArguments arguments = MessageArguments.create();
        for (int i = 0; i + 1 < values.length; i += 2) {
            arguments.string(values[i], values[i + 1]);
        }
        return arguments;
    }

    private void runOnSenderScheduler(CommandSender sender, Runnable callback) {
        if (sender instanceof Player) {
            scheduler.runEntity((Player) sender, callback, () -> {});
        } else {
            scheduler.runGlobal(plugin, callback);
        }
    }

}
