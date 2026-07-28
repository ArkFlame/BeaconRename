package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.model.CostMode;

import java.math.BigDecimal;
import java.util.Objects;

public final class ChargeReceipt {

    private final boolean success;
    private final CostMode mode;
    private final BigDecimal xpCharged;
    private final BigDecimal moneyCharged;
    private final String failureReason;

    private ChargeReceipt(boolean success, CostMode mode, BigDecimal xpCharged,
                          BigDecimal moneyCharged, String failureReason) {
        this.success = success;
        this.mode = mode;
        this.xpCharged = xpCharged != null ? xpCharged : BigDecimal.ZERO;
        this.moneyCharged = moneyCharged != null ? moneyCharged : BigDecimal.ZERO;
        this.failureReason = failureReason;
    }

    public static ChargeReceipt success(CostMode mode, BigDecimal xpCharged, BigDecimal moneyCharged) {
        return new ChargeReceipt(true, mode, xpCharged, moneyCharged, null);
    }

    public static ChargeReceipt failure(CostMode mode, String reason) {
        return new ChargeReceipt(false, mode, BigDecimal.ZERO, BigDecimal.ZERO, Objects.requireNonNull(reason));
    }

    public static ChargeReceipt zero() {
        return new ChargeReceipt(true, CostMode.XP_ONLY, BigDecimal.ZERO, BigDecimal.ZERO, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public CostMode getMode() {
        return mode;
    }

    public BigDecimal getXpCharged() {
        return xpCharged;
    }

    public BigDecimal getMoneyCharged() {
        return moneyCharged;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public boolean hasXpCharge() {
        return xpCharged.compareTo(BigDecimal.ZERO) > 0;
    }

    public boolean hasMoneyCharge() {
        return moneyCharged.compareTo(BigDecimal.ZERO) > 0;
    }
}
