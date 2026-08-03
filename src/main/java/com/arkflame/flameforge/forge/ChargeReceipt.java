package com.arkflame.flameforge.forge;

import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChargeReceipt {

    private final boolean success;
    private final int xpCharged;
    private final BigDecimal moneyCharged;
    private final List<RemovedItemStack> removedItems;
    private final String failureReasonKey;
    private final AtomicBoolean refunded;

    private ChargeReceipt(boolean success, int xpCharged, BigDecimal moneyCharged,
                          List<RemovedItemStack> removedItems, String failureReasonKey) {
        this.success = success;
        this.xpCharged = xpCharged;
        this.moneyCharged = moneyCharged != null ? moneyCharged : BigDecimal.ZERO;
        this.removedItems = removedItems != null ? Collections.unmodifiableList(removedItems) : Collections.emptyList();
        this.failureReasonKey = failureReasonKey;
        this.refunded = new AtomicBoolean(false);
    }

    public static ChargeReceipt success(int xpCharged, BigDecimal moneyCharged, List<RemovedItemStack> removedItems) {
        return new ChargeReceipt(true, xpCharged, moneyCharged, removedItems, null);
    }

    public static ChargeReceipt failure(String reasonKey) {
        return new ChargeReceipt(false, 0, BigDecimal.ZERO, Collections.emptyList(), Objects.requireNonNull(reasonKey));
    }

    public static ChargeReceipt zero() {
        return new ChargeReceipt(true, 0, BigDecimal.ZERO, Collections.emptyList(), null);
    }

    public boolean isSuccess() { return success; }
    public int getXpCharged() { return xpCharged; }
    public BigDecimal getMoneyCharged() { return moneyCharged; }
    public List<RemovedItemStack> getRemovedItems() { return removedItems; }
    public String getFailureReasonKey() { return failureReasonKey; }
    public String getFailureReason() { return failureReasonKey; }
    public boolean isRefunded() { return refunded.get(); }

    public void markRefunded() { refunded.set(true); }

    public static final class RemovedItemStack {
        private final int sourceSlot;
        private final ItemStack clonedStack;

        public RemovedItemStack(int sourceSlot, ItemStack clonedStack) {
            this.sourceSlot = sourceSlot;
            this.clonedStack = clonedStack != null ? clonedStack.clone() : null;
        }

        public int getSourceSlot() { return sourceSlot; }
        public ItemStack getClonedStack() { return clonedStack != null ? clonedStack.clone() : null; }
    }
}
