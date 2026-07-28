package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.compat.material.MaterialResolver;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.model.OutcomeDefinition;
import com.arkflame.flameforge.model.OutcomeType;
import com.arkflame.flameforge.persistence.AuditLogService;
import com.arkflame.flameforge.text.TextBridge;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class OutcomeExecutor {
    private final JavaPlugin plugin;
    private final SchedulerBridge scheduler;
    private final MaterialResolver materialResolver;
    private final TextBridge textBridge;
    private final AuditLogService auditLog;
    private final DeliveryService deliveryService;
    private final Map<String, Object> wardConfig;

    public OutcomeExecutor(JavaPlugin plugin, SchedulerBridge scheduler,
                           MaterialResolver materialResolver, TextBridge textBridge,
                           AuditLogService auditLog, DeliveryService deliveryService,
                           Map<String, Object> wardConfig) {
        this.plugin = Objects.requireNonNull(plugin);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.materialResolver = Objects.requireNonNull(materialResolver);
        this.textBridge = Objects.requireNonNull(textBridge);
        this.auditLog = Objects.requireNonNull(auditLog);
        this.deliveryService = Objects.requireNonNull(deliveryService);
        this.wardConfig = wardConfig;
    }

    public OutcomeExecutionResult execute(OutcomeDefinition outcome, ItemStack inputItem,
                                          Player player, Location location) {
        Objects.requireNonNull(outcome);
        String outcomeId = outcome.getId();
        Set<String> executedIds = new HashSet<>();
        executedIds.add(outcomeId);

        boolean wardProtected = isWardProtected(outcome);
        boolean wardConverted = false;

        if (wardProtected && shouldConvertToUnchanged(outcome)) {
            wardConverted = true;
            auditLog.logAsync("WARD_CONVERT", player != null ? player.getName() : "console",
                outcomeId, "Protected outcome converted to RETURN_UNCHANGED");
            return OutcomeExecutionResult.wardConverted(outcomeId, executedIds);
        }

        OutcomeType type = outcome.getType();
        switch (type) {
            case BREAK:
                return executeBreak(outcomeId, executedIds);
            case RETURN_UNCHANGED:
                return executeReturnUnchanged(outcomeId, executedIds, inputItem);
            case MODIFY_INPUT:
                return executeModifyInput(outcomeId, executedIds, outcome, inputItem, player, location);
            case CREATE_ITEM:
                return executeCreateItem(outcomeId, executedIds, outcome, player, location);
            case COMMANDS:
                return executeCommands(outcomeId, executedIds, outcome, player, location);
            default:
                return OutcomeExecutionResult.error(outcomeId, "Unknown outcome type: " + type);
        }
    }

    private boolean isWardProtected(OutcomeDefinition outcome) {
        if (wardConfig == null) {
            return false;
        }
        Object protectedTag = wardConfig.get("protected_outcomes");
        if (protectedTag instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> protectedList = (List<String>) protectedTag;
            return protectedList.contains(outcome.getId());
        }
        return Boolean.TRUE.equals(wardConfig.get("protect_all"));
    }

    private boolean shouldConvertToUnchanged(OutcomeDefinition outcome) {
        if (wardConfig == null) {
            return false;
        }
        Object convertTypes = wardConfig.get("convert_to_unchanged");
        if (convertTypes instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> typeList = (List<String>) convertTypes;
            return typeList.contains(outcome.getType().name());
        }
        return false;
    }

    private OutcomeExecutionResult executeBreak(String outcomeId, Set<String> executedIds) {
        return OutcomeExecutionResult.success(outcomeId, executedIds);
    }

    private OutcomeExecutionResult executeReturnUnchanged(String outcomeId, Set<String> executedIds,
                                                          ItemStack inputItem) {
        if (inputItem == null) {
            return OutcomeExecutionResult.error(outcomeId, "Cannot return unchanged: null input");
        }
        ItemStack clone = inputItem.clone();
        return OutcomeExecutionResult.successWithItem(outcomeId, executedIds, clone);
    }

    private OutcomeExecutionResult executeModifyInput(String outcomeId, Set<String> executedIds,
                                                      OutcomeDefinition outcome, ItemStack inputItem,
                                                      Player player, Location location) {
        if (inputItem == null) {
            return OutcomeExecutionResult.error(outcomeId, "Cannot modify: null input");
        }
        if (outcome.getMutation() == null) {
            return OutcomeExecutionResult.error(outcomeId, "MODIFY_INPUT requires mutation spec");
        }

        ItemStack modified = mutateItem(inputItem, outcome.getMutation());
        return OutcomeExecutionResult.successWithItem(outcomeId, executedIds, modified);
    }

    private OutcomeExecutionResult executeCreateItem(String outcomeId, Set<String> executedIds,
                                                     OutcomeDefinition outcome, Player player,
                                                     Location location) {
        if (outcome.getMutation() == null) {
            return OutcomeExecutionResult.error(outcomeId, "CREATE_ITEM requires mutation spec");
        }

        ItemStack created = createItemFromSpec(outcome.getMutation());
        if (created == null) {
            return OutcomeExecutionResult.error(outcomeId, "Failed to create item");
        }

        String deliveryId = deliveryService.generateDeliveryId(player, outcomeId);
        boolean delivered = deliveryService.deliverItem(created, player, location, deliveryId);

        if (!delivered) {
            deliveryService.queuePendingDelivery(deliveryId, player != null ? player.getUniqueId() : null,
                created, null);
        }

        return OutcomeExecutionResult.successWithItem(outcomeId, executedIds, created);
    }

    private OutcomeExecutionResult executeCommands(String outcomeId, Set<String> executedIds,
                                                   OutcomeDefinition outcome, Player player,
                                                   Location location) {
        List<String> commands = outcome.getCommands();
        if (commands == null || commands.isEmpty()) {
            return OutcomeExecutionResult.error(outcomeId, "COMMANDS has no commands defined");
        }

        Set<String> dispatchedCommands = new HashSet<>();
        String playerName = player != null ? player.getName() : "console";
        UUID playerUuid = player != null ? player.getUniqueId() : null;

        for (int i = 0; i < commands.size(); i++) {
            String rawCommand = commands.get(i);
            String resolved = resolveCommandPlaceholders(rawCommand, playerName, playerUuid);

            final int commandIndex = i;
            final String commandToDispatch = resolved;

            TaskHandle handle = scheduler.runGlobal(plugin, () -> {
                boolean success = dispatchCommand(commandToDispatch, player);
                if (!success) {
                    auditLog.logAsync("COMMAND_FAIL", playerName, outcomeId,
                        "Command failed at index " + commandIndex + ": " + commandToDispatch);
                }
            });

            if (handle == null) {
                return OutcomeExecutionResult.commandFailed(outcomeId, executedIds,
                    OutcomeExecutionResult.CommandStatus.FAILED,
                    "Failed to schedule command at index " + i);
            }

            dispatchedCommands.add(rawCommand);
            auditLog.logAsync("COMMAND_DISPATCH", playerName, outcomeId,
                "Dispatched: " + rawCommand);
        }

        if (!dispatchedCommands.isEmpty()) {
            String deliveryId = deliveryService.generateDeliveryId(player, outcomeId + "_commands");
            if (player == null || !player.isOnline()) {
                deliveryService.queuePendingDelivery(deliveryId, playerUuid, null,
                    outcome.getCommands());
            }
        }

        return OutcomeExecutionResult.commandSuccess(outcomeId, executedIds,
            OutcomeExecutionResult.CommandStatus.DISPATCHED);
    }

    private String resolveCommandPlaceholders(String command, String playerName, UUID playerUuid) {
        String resolved = command;
        resolved = resolved.replace("%player_name%", playerName);
        resolved = resolved.replace("%player%", playerName);
        resolved = resolved.replace("%player_uuid%", playerUuid != null ? playerUuid.toString() : "");
        return resolved;
    }

    private boolean dispatchCommand(String command, Player player) {
        try {
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            if (player != null && player.isOnline()) {
                boolean result = player.performCommand(command);
                return result;
            } else {
                boolean result = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                return result;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private ItemStack mutateItem(ItemStack input, com.arkflame.flameforge.model.ItemMutationSpec mutation) {
        ItemStack result = input.clone();

        if (mutation.getResultMaterial() != null) {
            materialResolver.resolve(mutation.getResultMaterial()).ifPresent(material -> {
                result.setType(material);
            });
        }

        if (mutation.getAmount() > 0) {
            result.setAmount(mutation.getAmount());
        }

        if (mutation.getResultName() != null && result.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = result.getItemMeta();
            meta.setDisplayName(mutation.getResultName());
            result.setItemMeta(meta);
        }

        return result;
    }

    private ItemStack createItemFromSpec(com.arkflame.flameforge.model.ItemMutationSpec spec) {
        if (spec.getResultMaterial() == null) {
            return null;
        }

        return materialResolver.makeItem(spec.getResultMaterial(), spec.getAmount()).map(item -> {
            if (spec.getResultName() != null && item.hasItemMeta()) {
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(spec.getResultName());
                item.setItemMeta(meta);
            }
            return item;
        }).orElse(null);
    }
}
