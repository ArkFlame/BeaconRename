package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class OutcomeExecutionResult {
    private final String outcomeId;
    private final Set<String> executedIds;
    private final ItemStack itemOutput;
    private final ForgeOutcomeCategory category;
    private final String error;
    private final boolean wardConverted;

    private OutcomeExecutionResult(String outcomeId, Set<String> executedIds,
                                   ItemStack itemOutput, ForgeOutcomeCategory category,
                                   String error, boolean wardConverted) {
        this.outcomeId = outcomeId;
        this.executedIds = executedIds != null ?
            Collections.unmodifiableSet(new HashSet<>(executedIds)) :
            Collections.emptySet();
        this.itemOutput = itemOutput;
        this.category = category;
        this.error = error;
        this.wardConverted = wardConverted;
    }

    public static OutcomeExecutionResult success(String outcomeId, Set<String> executedIds) {
        return new OutcomeExecutionResult(outcomeId, executedIds, null,
            ForgeOutcomeCategory.SUCCESS, null, false);
    }

    public static OutcomeExecutionResult successWithItem(String outcomeId, Set<String> executedIds,
                                                         ItemStack itemOutput) {
        return new OutcomeExecutionResult(outcomeId, executedIds, itemOutput,
            ForgeOutcomeCategory.SUCCESS, null, false);
    }

    public static OutcomeExecutionResult breakResult(String outcomeId, Set<String> executedIds,
                                                     ItemStack itemOutput) {
        return new OutcomeExecutionResult(outcomeId, executedIds, itemOutput,
            ForgeOutcomeCategory.BREAK, null, false);
    }

    public static OutcomeExecutionResult curseResult(String outcomeId, Set<String> executedIds,
                                                     ItemStack itemOutput) {
        return new OutcomeExecutionResult(outcomeId, executedIds, itemOutput,
            ForgeOutcomeCategory.CURSE, null, false);
    }

    public static OutcomeExecutionResult wardConverted(String originalOutcomeId, Set<String> executedIds) {
        return new OutcomeExecutionResult(originalOutcomeId, executedIds, null,
            null, null, true);
    }

    public static OutcomeExecutionResult error(String outcomeId, String error) {
        return new OutcomeExecutionResult(outcomeId, Collections.emptySet(), null,
            null, error, false);
    }

    public String getOutcomeId() { return outcomeId; }
    public Set<String> getExecutedIds() { return executedIds; }
    public ItemStack getItemOutput() { return itemOutput; }
    public ForgeOutcomeCategory getCategory() { return category; }
    public String getError() { return error; }
    public boolean isWardConverted() { return wardConverted; }

    public boolean isSuccess() { return error == null && category == ForgeOutcomeCategory.SUCCESS; }
    public boolean hasItemOutput() { return itemOutput != null; }
    public boolean isBreak() { return category == ForgeOutcomeCategory.BREAK; }
    public boolean isCurse() { return category == ForgeOutcomeCategory.CURSE; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OutcomeExecutionResult)) return false;
        OutcomeExecutionResult that = (OutcomeExecutionResult) o;
        return wardConverted == that.wardConverted &&
            Objects.equals(outcomeId, that.outcomeId) &&
            Objects.equals(executedIds, that.executedIds) &&
            Objects.equals(itemOutput, that.itemOutput) &&
            category == that.category &&
            Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(outcomeId, executedIds, itemOutput, category, error, wardConverted);
    }

    @Override
    public String toString() {
        return "OutcomeExecutionResult{outcomeId=" + outcomeId +
            ", executedIds=" + executedIds +
            ", itemOutput=" + (itemOutput != null ? itemOutput.getType() : null) +
            ", category=" + category +
            ", error=" + error +
            ", wardConverted=" + wardConverted + "}";
    }
}
