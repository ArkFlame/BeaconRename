package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.chance.ChanceEntry;
import com.arkflame.flameforge.chance.ChanceTable;
import com.arkflame.flameforge.model.ForgeHistory;
import com.arkflame.flameforge.model.OutcomeDefinition;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ForgeTransaction {
    private final UUID transactionId;
    private final ForgeContext context;
    private final CostQuote quote;
    private final ChanceTable chanceTable;
    private final List<ChanceEntry> chanceEntries;
    private final ChanceEntry selectedEntry;
    private final OutcomeDefinition selectedOutcome;
    private final ForgeHistory historyBefore;
    private final List<ItemStack> custodySnapshot;
    private final ChargeReceipt chargeReceipt;
    private final long timestamp;

    private ForgeTransaction(Builder builder) {
        this.transactionId = Objects.requireNonNull(builder.transactionId);
        this.context = Objects.requireNonNull(builder.context);
        this.quote = builder.quote;
        this.chanceTable = builder.chanceTable;
        this.chanceEntries = builder.chanceEntries != null ?
            Collections.unmodifiableList(builder.chanceEntries) :
            Collections.emptyList();
        this.selectedEntry = builder.selectedEntry;
        this.selectedOutcome = builder.selectedOutcome;
        this.historyBefore = builder.historyBefore;
        this.custodySnapshot = builder.custodySnapshot != null ?
            Collections.unmodifiableList(builder.custodySnapshot) :
            Collections.emptyList();
        this.chargeReceipt = builder.chargeReceipt;
        this.timestamp = System.currentTimeMillis();
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID getTransactionId() { return transactionId; }
    public ForgeContext getContext() { return context; }
    public CostQuote getQuote() { return quote; }
    public ChanceTable getChanceTable() { return chanceTable; }
    public List<ChanceEntry> getChanceEntries() { return chanceEntries; }
    public ChanceEntry getSelectedEntry() { return selectedEntry; }
    public OutcomeDefinition getSelectedOutcome() { return selectedOutcome; }
    public ForgeHistory getHistoryBefore() { return historyBefore; }
    public List<ItemStack> getCustodySnapshot() { return custodySnapshot; }
    public ChargeReceipt getChargeReceipt() { return chargeReceipt; }
    public long getTimestamp() { return timestamp; }

    public boolean hasQuote() { return quote != null; }
    public boolean hasChanceTable() { return chanceTable != null; }
    public boolean hasSelectedOutcome() { return selectedOutcome != null; }
    public boolean hasChargeReceipt() { return chargeReceipt != null; }

    public static final class Builder {
        private UUID transactionId;
        private ForgeContext context;
        private CostQuote quote;
        private ChanceTable chanceTable;
        private List<ChanceEntry> chanceEntries;
        private ChanceEntry selectedEntry;
        private OutcomeDefinition selectedOutcome;
        private ForgeHistory historyBefore;
        private List<ItemStack> custodySnapshot;
        private ChargeReceipt chargeReceipt;

        public Builder transactionId(UUID transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder context(ForgeContext context) {
            this.context = context;
            return this;
        }

        public Builder quote(CostQuote quote) {
            this.quote = quote;
            return this;
        }

        public Builder chanceTable(ChanceTable chanceTable) {
            this.chanceTable = chanceTable;
            return this;
        }

        public Builder chanceEntries(List<ChanceEntry> chanceEntries) {
            this.chanceEntries = chanceEntries;
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

        public Builder historyBefore(ForgeHistory historyBefore) {
            this.historyBefore = historyBefore;
            return this;
        }

        public Builder custodySnapshot(List<ItemStack> custodySnapshot) {
            this.custodySnapshot = custodySnapshot;
            return this;
        }

        public Builder chargeReceipt(ChargeReceipt chargeReceipt) {
            this.chargeReceipt = chargeReceipt;
            return this;
        }

        public ForgeTransaction build() {
            return new ForgeTransaction(this);
        }
    }
}
