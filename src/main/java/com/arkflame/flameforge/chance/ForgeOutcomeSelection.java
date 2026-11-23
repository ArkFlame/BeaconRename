package com.arkflame.flameforge.chance;

import com.arkflame.flameforge.model.ForgeOutcomeCategory;

/** A rolled outcome plus the failure variant that produced it (null on success). */
public final class ForgeOutcomeSelection {
    private final ForgeOutcomeCategory category;
    private final String failureVariantId;

    public ForgeOutcomeSelection(ForgeOutcomeCategory category, String failureVariantId) {
        this.category = category;
        this.failureVariantId = failureVariantId;
    }

    public static ForgeOutcomeSelection of(ForgeOutcomeCategory category, String failureVariantId) {
        return new ForgeOutcomeSelection(category, failureVariantId);
    }

    public ForgeOutcomeCategory getCategory() { return category; }
    public String getFailureVariantId() { return failureVariantId; }
    public boolean isFailure() { return category != ForgeOutcomeCategory.SUCCESS; }
}
