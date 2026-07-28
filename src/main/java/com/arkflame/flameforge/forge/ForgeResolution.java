package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.chance.ChanceEntry;
import com.arkflame.flameforge.chance.ChanceTable;
import com.arkflame.flameforge.model.ForgeHistory;
import com.arkflame.flameforge.model.OutcomeDefinition;
import com.arkflame.flameforge.model.OutcomeType;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ForgeResolution {
    private final UUID transactionId;
    private final boolean success;
    private final ChanceTable chanceTable;
    private final ChanceEntry selectedEntry;
    private final OutcomeDefinition selectedOutcome;
    private final ItemStack resultItem;
    private final ForgeHistory historyEntry;
    private final String errorMessage;
    private final List<ItemStack> custodyReturned;
    private final ChargeReceipt chargeReceipt;
    private final boolean preRollFailure;
    private final boolean wardConverted;

    private ForgeResolution(Builder builder) {
        this.transactionId = Objects.requireNonNull(builder.transactionId);
        this.success = builder.success;
        this.chanceTable = builder.chanceTable;
        this.selectedEntry = builder.selectedEntry;
        this.selectedOutcome = builder.selectedOutcome;
        this.resultItem = builder.resultItem;
        this.historyEntry = builder.historyEntry;
        this.errorMessage = builder.errorMessage;
        this.custodyReturned = builder.custodyReturned != null ?
            Collections.unmodifiableList(builder.custodyReturned) :
            Collections.emptyList();
        this.chargeReceipt = builder.chargeReceipt;
        this.preRollFailure = builder.preRollFailure;
        this.wardConverted = builder.wardConverted;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ForgeResolution failure(UUID transactionId, String errorMessage, boolean preRollFailure) {
        return builder()
            .transactionId(transactionId)
            .success(false)
            .errorMessage(errorMessage)
            .preRollFailure(preRollFailure)
            .build();
    }

    public static ForgeResolution success(UUID transactionId, ChanceTable chanceTable,
            ChanceEntry selectedEntry, OutcomeDefinition selectedOutcome,
            ItemStack resultItem, ForgeHistory historyEntry, ChargeReceipt chargeReceipt,
            List<ItemStack> custodyReturned, boolean wardConverted) {
        return builder()
            .transactionId(transactionId)
            .success(true)
            .chanceTable(chanceTable)
            .selectedEntry(selectedEntry)
            .selectedOutcome(selectedOutcome)
            .resultItem(resultItem)
            .historyEntry(historyEntry)
            .chargeReceipt(chargeReceipt)
            .custodyReturned(custodyReturned)
            .wardConverted(wardConverted)
            .build();
    }

    public UUID getTransactionId() { return transactionId; }
    public boolean isSuccess() { return success; }
    public ChanceTable getChanceTable() { return chanceTable; }
    public ChanceEntry getSelectedEntry() { return selectedEntry; }
    public OutcomeDefinition getSelectedOutcome() { return selectedOutcome; }
    public ItemStack getResultItem() { return resultItem; }
    public ForgeHistory getHistoryEntry() { return historyEntry; }
    public String getErrorMessage() { return errorMessage; }
    public List<ItemStack> getCustodyReturned() { return custodyReturned; }
    public ChargeReceipt getChargeReceipt() { return chargeReceipt; }
    public boolean isPreRollFailure() { return preRollFailure; }
    public boolean isWardConverted() { return wardConverted; }

    public boolean hasItemOutput() { return resultItem != null; }

    public static final class Builder {
        private UUID transactionId;
        private boolean success;
        private ChanceTable chanceTable;
        private ChanceEntry selectedEntry;
        private OutcomeDefinition selectedOutcome;
        private ItemStack resultItem;
        private ForgeHistory historyEntry;
        private String errorMessage;
        private List<ItemStack> custodyReturned;
        private ChargeReceipt chargeReceipt;
        private boolean preRollFailure;
        private boolean wardConverted;

        public Builder transactionId(UUID transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
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

        public Builder selectedOutcome(OutcomeDefinition selectedOutcome) {
            this.selectedOutcome = selectedOutcome;
            return this;
        }

        public Builder resultItem(ItemStack resultItem) {
            this.resultItem = resultItem;
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

        public Builder custodyReturned(List<ItemStack> custodyReturned) {
            this.custodyReturned = custodyReturned;
            return this;
        }

        public Builder chargeReceipt(ChargeReceipt chargeReceipt) {
            this.chargeReceipt = chargeReceipt;
            return this;
        }

        public Builder preRollFailure(boolean preRollFailure) {
            this.preRollFailure = preRollFailure;
            return this;
        }

        public Builder wardConverted(boolean wardConverted) {
            this.wardConverted = wardConverted;
            return this;
        }

        public ForgeResolution build() {
            return new ForgeResolution(this);
        }
    }
}
