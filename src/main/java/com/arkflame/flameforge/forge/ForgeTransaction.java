package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.chance.ChanceEntry;
import com.arkflame.flameforge.chance.ChanceTable;
import com.arkflame.flameforge.model.ForgeOutcomeCategory;
import com.arkflame.flameforge.model.ForgeVariant;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ForgeTransaction {
    private final UUID transactionId;
    private final ForgeContext context;
    private final ForgePlan plan;
    private final CostQuote quote;
    private final ChargeReceipt chargeReceipt;
    private final ChanceTable chanceTable;
    private final ChanceEntry selectedEntry;
    private final ForgeOutcomeCategory outcomeCategory;
    private final String selectedOutcomeId;
    private final List<ItemStack> custodySnapshot;
    private final List<ItemStack> mutatedItems;
    private final ForgeVariant usedVariant;
    private final long timestamp;
    private volatile boolean rolledBack;
    private volatile RollbackReason rollbackReason;

    private ForgeTransaction(Builder builder) {
        this.transactionId = Objects.requireNonNull(builder.transactionId);
        this.context = builder.context;
        this.plan = builder.plan;
        this.quote = builder.quote;
        this.chargeReceipt = builder.chargeReceipt;
        this.chanceTable = builder.chanceTable;
        this.selectedEntry = builder.selectedEntry;
        this.outcomeCategory = builder.outcomeCategory;
        this.selectedOutcomeId = builder.selectedOutcomeId;
        this.custodySnapshot = builder.custodySnapshot != null ?
            Collections.unmodifiableList(new ArrayList<>(builder.custodySnapshot)) :
            Collections.emptyList();
        this.mutatedItems = builder.mutatedItems != null ?
            Collections.unmodifiableList(new ArrayList<>(builder.mutatedItems)) :
            Collections.emptyList();
        this.usedVariant = builder.usedVariant;
        this.timestamp = System.currentTimeMillis();
        this.rolledBack = false;
        this.rollbackReason = null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID getTransactionId() {
        return transactionId;
    }

    public ForgeContext getContext() {
        return context;
    }

    public ForgePlan getPlan() {
        return plan;
    }

    public CostQuote getQuote() {
        return quote;
    }

    public ChargeReceipt getChargeReceipt() {
        return chargeReceipt;
    }

    public ChanceTable getChanceTable() {
        return chanceTable;
    }

    public ChanceEntry getSelectedEntry() {
        return selectedEntry;
    }

    public ForgeOutcomeCategory getOutcomeCategory() {
        return outcomeCategory;
    }

    public String getSelectedOutcomeId() {
        return selectedOutcomeId;
    }

    public List<ItemStack> getCustodySnapshot() {
        return custodySnapshot;
    }

    public List<ItemStack> getMutatedItems() {
        return mutatedItems;
    }

    public ForgeVariant getUsedVariant() {
        return usedVariant;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isRolledBack() {
        return rolledBack;
    }

    public RollbackReason getRollbackReason() {
        return rollbackReason;
    }

    public boolean hasQuote() {
        return quote != null;
    }

    public boolean hasChanceTable() {
        return chanceTable != null;
    }

    public boolean hasSelectedOutcomeId() {
        return selectedOutcomeId != null;
    }

    public boolean hasChargeReceipt() {
        return chargeReceipt != null;
    }

    public boolean hasMutatedItems() {
        return !mutatedItems.isEmpty();
    }

    public synchronized void rollback(RollbackReason reason, Player player,
            java.util.function.Consumer<List<ItemStack>> custodyReturn,
            java.util.function.Consumer<ChargeReceipt> refund) {
        if (rolledBack) {
            return;
        }
        this.rolledBack = true;
        this.rollbackReason = reason;

        if (!custodySnapshot.isEmpty() && custodyReturn != null) {
            custodyReturn.accept(new ArrayList<>(custodySnapshot));
        }
        if (chargeReceipt != null && chargeReceipt.isSuccess() && refund != null) {
            refund.accept(chargeReceipt);
        }
    }

    public synchronized void addMutatedItem(ItemStack item) {
        if (item != null) {
            List<ItemStack> updated = new ArrayList<>(mutatedItems);
            updated.add(item.clone());
        }
    }

    public static enum RollbackReason {
        PRE_TERMINAL_FAILURE,
        PLAYER_QUIT,
        ANIMATION_FAILED,
        ANIMATION_FAILURE,
        OUTCOME_EXECUTION_FAILED,
        DELIVERY_FAILED
    }

    public static final class Builder {
        private UUID transactionId;
        private ForgeContext context;
        private ForgePlan plan;
        private CostQuote quote;
        private ChargeReceipt chargeReceipt;
        private ChanceTable chanceTable;
        private ChanceEntry selectedEntry;
        private ForgeOutcomeCategory outcomeCategory;
        private String selectedOutcomeId;
        private List<ItemStack> custodySnapshot;
        private List<ItemStack> mutatedItems;
        private ForgeVariant usedVariant;

        public Builder transactionId(UUID transactionId) {
            this.transactionId = transactionId;
            return this;
        }

        public Builder context(ForgeContext context) {
            this.context = context;
            return this;
        }

        public Builder plan(ForgePlan plan) {
            this.plan = plan;
            return this;
        }

        public Builder quote(CostQuote quote) {
            this.quote = quote;
            return this;
        }

        public Builder chargeReceipt(ChargeReceipt chargeReceipt) {
            this.chargeReceipt = chargeReceipt;
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

        public Builder outcomeCategory(ForgeOutcomeCategory outcomeCategory) {
            this.outcomeCategory = outcomeCategory;
            return this;
        }

        public Builder selectedOutcomeId(String selectedOutcomeId) {
            this.selectedOutcomeId = selectedOutcomeId;
            return this;
        }

        public Builder custodySnapshot(List<ItemStack> custodySnapshot) {
            this.custodySnapshot = custodySnapshot;
            return this;
        }

        public Builder mutatedItems(List<ItemStack> mutatedItems) {
            this.mutatedItems = mutatedItems;
            return this;
        }

        public Builder usedVariant(ForgeVariant usedVariant) {
            this.usedVariant = usedVariant;
            return this;
        }

        public ForgeTransaction build() {
            return new ForgeTransaction(this);
        }
    }
}
