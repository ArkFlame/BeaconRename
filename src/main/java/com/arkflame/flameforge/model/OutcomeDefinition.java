package com.arkflame.flameforge.model;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class OutcomeDefinition {
    private final String id;
    private final OutcomeType type;
    private final BigDecimal weight;
    private final ItemMutationSpec mutation;
    private final List<String> commands;
    private final int displayOrder;

    private OutcomeDefinition(String id, OutcomeType type, BigDecimal weight,
                             ItemMutationSpec mutation, List<String> commands, int displayOrder) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.weight = validateWeight(weight);
        this.mutation = mutation;
        this.commands = commands != null ? Collections.unmodifiableList(commands) : Collections.emptyList();
        this.displayOrder = displayOrder;
    }

    private static BigDecimal validateWeight(BigDecimal weight) {
        if (weight == null) {
            throw new IllegalArgumentException("weight cannot be null");
        }
        if (weight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("weight must be positive");
        }
        if (weight.scale() > 6) {
            throw new IllegalArgumentException("weight scale cannot exceed 6");
        }
        return weight;
    }

    public static OutcomeDefinition of(String id, OutcomeType type, BigDecimal weight,
                                       ItemMutationSpec mutation, List<String> commands, int displayOrder) {
        return new OutcomeDefinition(id, type, weight, mutation, commands, displayOrder);
    }

    public static OutcomeDefinition breakOutcome(String id, BigDecimal weight, int displayOrder) {
        return new OutcomeDefinition(id, OutcomeType.BREAK, weight, null, Collections.emptyList(), displayOrder);
    }

    public static OutcomeDefinition returnUnchanged(String id, BigDecimal weight, int displayOrder) {
        return new OutcomeDefinition(id, OutcomeType.RETURN_UNCHANGED, weight, null, Collections.emptyList(), displayOrder);
    }

    public String getId() { return id; }
    public OutcomeType getType() { return type; }
    public BigDecimal getWeight() { return weight; }
    public ItemMutationSpec getMutation() { return mutation; }
    public List<String> getCommands() { return commands; }
    public int getDisplayOrder() { return displayOrder; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OutcomeDefinition)) return false;
        OutcomeDefinition that = (OutcomeDefinition) o;
        return displayOrder == that.displayOrder &&
               Objects.equals(id, that.id) &&
               type == that.type &&
               weight.compareTo(that.weight) == 0 &&
               Objects.equals(mutation, that.mutation) &&
               Objects.equals(commands, that.commands);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, type, weight, mutation, commands, displayOrder);
    }

    @Override
    public String toString() {
        return "OutcomeDefinition{id=" + id + ", type=" + type + ", weight=" + weight +
               ", mutation=" + mutation + ", commands=" + commands + ", displayOrder=" + displayOrder + "}";
    }
}
