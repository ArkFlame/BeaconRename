package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.chance.ChanceEntry;
import com.arkflame.flameforge.chance.ChanceTable;
import com.arkflame.flameforge.model.ForgeHistory;
import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.OutcomeDefinition;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ForgeResolution {
    private final UUID transactionId;
    private final boolean success;
    private final ForgeOutcomeCategory category;
    private final ChanceTable chanceTable;
    private final ChanceEntry selectedEntry;
    private final ForgeVariant usedVariant;
    private final OutcomeDefinition outcome;
    private final ItemStack mutatedItem;
    private final List<ItemStack> allMutatedItems;
    private final ForgeHistory historyEntry;
    private final String errorMessage;
    private final ChargeReceipt chargeReceipt;
    private final List<ItemStack> custodyReturned;
    private final boolean preRollFailure;

    private ForgeResolution(Builder builder) {
        this.transactionId = Objects.requireNonNull(builder.transactionId);
        this.success = builder.success;
        this.category = builder.category;
        this.chanceTable = builder.chanceTable;
        this.selectedEntry = builder.selectedEntry;
        this.usedVariant = builder.usedVariant;
        this.outcome = builder.outcome;
        this.mutatedItem = builder.mutatedItem;
        this.allMutatedItems = builder.allMutatedItems != null ?
            Collections.unmodifiableList(new java.util.ArrayList<>(builder.allMutatedItems)) :
            Collections.emptyList();
        this.historyEntry = builder.historyEntry;
        this.errorMessage = builder.errorMessage;
        this.chargeReceipt = builder.chargeReceipt;
        this.custodyReturned = builder.custodyReturned != null ?
            Collections.unmodifiableList(new java.util.ArrayList<>(builder.custodyReturned)) :
            Collections.emptyList();
        this.preRollFailure = builder.preRollFailure;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ForgeResolution failure(UUID transactionId, ForgeOutcomeCategory category,
            String errorMessage, boolean preRollFailure) {
        return builder()
            .transactionId(transactionId)
            .success(false)
            .category(category != null ? category : ForgeOutcomeCategory.BREAK)
            .errorMessage(errorMessage)
            .preRollFailure(preRollFailure)
            .build();
    }

    public static ForgeResolution success(UUID transactionId, ForgeOutcomeCategory category,
            ChanceTable chanceTable, ChanceEntry selectedEntry, ForgeVariant usedVariant,
            OutcomeDefinition outcome, ItemStack mutatedItem, List<ItemStack> allMutatedItems,
            ForgeHistory historyEntry, ChargeReceipt chargeReceipt,
            List<ItemStack> custodyReturned) {
        return builder()
            .transactionId(transactionId)
            .success(true)
            .category(category)
            .chanceTable(chanceTable)
            .selectedEntry(selectedEntry)
            .usedVariant(usedVariant)
            .outcome(outcome)
            .mutatedItem(mutatedItem)
            .allMutatedItems(allMutatedItems)
            .historyEntry(historyEntry)
            .chargeReceipt(chargeReceipt)
            .custodyReturned(custodyReturned)
            .preRollFailure(false)
            .build();
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public boolean isSuccess() {
        return success;
    }

    public ForgeOutcomeCategory getCategory() {
        return category;
    }

    public ChanceTable getChanceTable() {
        return chanceTable;
    }

    public ChanceEntry getSelectedEntry() {
        return selectedEntry;
    }

    public ForgeVariant getUsedVariant() {
        return usedVariant;
    }

    public OutcomeDefinition getOutcome() {
        return outcome;
    }

    public ItemStack getMutatedItem() {
        return mutatedItem;
    }

    public List<ItemStack> getAllMutatedItems() {
        return allMutatedItems;
    }

    public ForgeHistory getHistoryEntry() {
        return historyEntry;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public ChargeReceipt getChargeReceipt() {
        return chargeReceipt;
    }

    public List<ItemStack> getCustodyReturned() {
        return custodyReturned;
    }

    public boolean isPreRollFailure() {
        return preRollFailure;
    }

    public boolean hasItemOutput() {
        return mutatedItem != null;
    }

    public boolean hasMultipleItems() {
        return allMutatedItems.size() > 1;
    }

    public static final class Builder {
        private UUID transactionId;
        private boolean success;
        private ForgeOutcomeCategory category;
        private ChanceTable chanceTable;
        private ChanceEntry selectedEntry;
        private ForgeVariant usedVariant;
        private OutcomeDefinition outcome;
        private ItemStack mutatedItem;
        private List<ItemStack> allMutatedItems;
        private ForgeHistory historyEntry;
        private String errorMessage;
        private ChargeReceipt chargeReceipt;
        private List<ItemStack> custodyReturned;
        private boolean preRollFailure;

        public Builder transactionId(UUID transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder category(ForgeOutcomeCategory category) {
            this.category = category;
            return this;
        }

        public Builder chanceTable(ChanceTable chanceTable) {
            this.chanceTable = chanceTable;
            return this;
        }

        public Builder selectedEntry(ChanceEntry selectedEntry) {
            this.selectedEntry = selectedEntry;
            return this;
        }

        public Builder usedVariant(ForgeVariant usedVariant) {
            this.usedVariant = usedVariant;
            return this;
        }

        public Builder outcome(OutcomeDefinition outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder mutatedItem(ItemStack mutatedItem) {
            this.mutatedItem = mutatedItem;
            return this;
        }

        public Builder allMutatedItems(List<ItemStack> allMutatedItems) {
            this.allMutatedItems = allMutatedItems;
            return this;
        }

        public Builder historyEntry(ForgeHistory historyEntry) {
            this.historyEntry = historyEntry;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder chargeReceipt(ChargeReceipt chargeReceipt) {
            this.chargeReceipt = chargeReceipt;
            return this;
        }

        public Builder custodyReturned(List<ItemStack> custodyReturned) {
            this.custodyReturned = custodyReturned;
            return this;
        }

        public Builder preRollFailure(boolean preRollFailure) {
            this.preRollFailure = preRollFailure;
            return this;
        }

        public ForgeResolution build() {
            return new ForgeResolution(this);
        }
    }
}
