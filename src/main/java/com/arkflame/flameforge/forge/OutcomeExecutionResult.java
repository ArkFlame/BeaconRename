package com.arkflame.flameforge.forge;

import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class OutcomeExecutionResult {
    private final String outcomeId;
    private final Set<String> executedIds;
    private final ItemStack itemOutput;
    private final CommandStatus commandStatus;
    private final String error;
    private final boolean wardConverted;

    private OutcomeExecutionResult(String outcomeId, Set<String> executedIds,
                                   ItemStack itemOutput, CommandStatus commandStatus,
                                   String error, boolean wardConverted) {
        this.outcomeId = outcomeId;
        this.executedIds = executedIds != null ?
            Collections.unmodifiableSet(new HashSet<>(executedIds)) :
            Collections.emptySet();
        this.itemOutput = itemOutput;
        this.commandStatus = commandStatus;
        this.error = error;
        this.wardConverted = wardConverted;
    }

    public static OutcomeExecutionResult success(String outcomeId, Set<String> executedIds) {
        return new OutcomeExecutionResult(outcomeId, executedIds, null,
            CommandStatus.NOT_EXECUTED, null, false);
    }

    public static OutcomeExecutionResult successWithItem(String outcomeId, Set<String> executedIds,
                                                         ItemStack itemOutput) {
        return new OutcomeExecutionResult(outcomeId, executedIds, itemOutput,
            CommandStatus.NOT_EXECUTED, null, false);
    }

    public static OutcomeExecutionResult commandSuccess(String outcomeId, Set<String> executedIds,
                                                        CommandStatus status) {
        return new OutcomeExecutionResult(outcomeId, executedIds, null, status, null, false);
    }

    public static OutcomeExecutionResult commandFailed(String outcomeId, Set<String> executedIds,
                                                       CommandStatus status, String error) {
        return new OutcomeExecutionResult(outcomeId, executedIds, null, status, error, false);
    }

    public static OutcomeExecutionResult wardConverted(String originalOutcomeId, Set<String> executedIds) {
        return new OutcomeExecutionResult(originalOutcomeId, executedIds, null,
            CommandStatus.NOT_EXECUTED, null, true);
    }

    public static OutcomeExecutionResult error(String outcomeId, String error) {
        return new OutcomeExecutionResult(outcomeId, Collections.emptySet(), null,
            CommandStatus.NOT_EXECUTED, error, false);
    }

    public String getOutcomeId() { return outcomeId; }
    public Set<String> getExecutedIds() { return executedIds; }
    public ItemStack getItemOutput() { return itemOutput; }
    public CommandStatus getCommandStatus() { return commandStatus; }
    public String getError() { return error; }
    public boolean isWardConverted() { return wardConverted; }

    public boolean isSuccess() { return error == null && commandStatus != CommandStatus.FAILED; }
    public boolean hasItemOutput() { return itemOutput != null; }
    public boolean hadCommandExecution() { return commandStatus != CommandStatus.NOT_EXECUTED; }

    public enum CommandStatus {
        NOT_EXECUTED,
        DISPATCHED,
        COMPLETED,
        FAILED
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OutcomeExecutionResult)) return false;
        OutcomeExecutionResult that = (OutcomeExecutionResult) o;
        return wardConverted == that.wardConverted &&
            Objects.equals(outcomeId, that.outcomeId) &&
            Objects.equals(executedIds, that.executedIds) &&
            Objects.equals(itemOutput, that.itemOutput) &&
            commandStatus == that.commandStatus &&
            Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(outcomeId, executedIds, itemOutput, commandStatus, error, wardConverted);
    }

    @Override
    public String toString() {
        return "OutcomeExecutionResult{outcomeId=" + outcomeId +
            ", executedIds=" + executedIds +
            ", itemOutput=" + (itemOutput != null ? itemOutput.getType() : null) +
            ", commandStatus=" + commandStatus +
            ", error=" + error +
            ", wardConverted=" + wardConverted + "}";
    }
}
