package com.arkflame.flameforge.forge;

import java.util.Collections;
import java.util.Map;

public final class ForgePlanResult {
    public enum Status {
        READY,
        NO_INPUT,
        INVALID_ITEM,
        CURSED,
        NEXT_TIER_MISSING,
        NEXT_TIER_DISABLED,
        STATION_TIER_BLOCKED,
        PERMISSION_REQUIRED,
        NO_ELIGIBLE_VARIANTS,
        MISSING_REQUIREMENTS,
        ECONOMY_UNAVAILABLE,
        SESSION_RETIRED,
        INTERNAL_FAILURE
    }

    public final Status status;
    public final ForgePlan plan;
    public final String reasonKey;
    public final Map<String, String> args;

    private ForgePlanResult(Status status, ForgePlan plan, String reasonKey, Map<String, String> args) {
        this.status = status;
        this.plan = plan;
        this.reasonKey = reasonKey;
        this.args = args != null ? args : Collections.emptyMap();
    }

    public static ForgePlanResult ready(ForgePlan plan) {
        return new ForgePlanResult(Status.READY, plan, null, Collections.emptyMap());
    }

    public static ForgePlanResult noInput() {
        return new ForgePlanResult(Status.NO_INPUT, null, "menu.item-denied.empty", Collections.emptyMap());
    }

    public static ForgePlanResult invalidItem() {
        return new ForgePlanResult(Status.INVALID_ITEM, null, "menu.item-denied.invalid-identity", Collections.emptyMap());
    }

    public static ForgePlanResult nextTierMissing() {
        return new ForgePlanResult(Status.NEXT_TIER_MISSING, null, "menu.item-denied.no-tier", Collections.emptyMap());
    }

    public static ForgePlanResult nextTierDisabled() {
        return new ForgePlanResult(Status.NEXT_TIER_DISABLED, null, "menu.item-denied.no-tier", Collections.emptyMap());
    }

    public static ForgePlanResult stationTierBlocked() {
        return new ForgePlanResult(Status.STATION_TIER_BLOCKED, null, "menu.item-denied.station", Collections.emptyMap());
    }

    public static ForgePlanResult permissionRequired(String permission) {
        return new ForgePlanResult(Status.PERMISSION_REQUIRED, null, "menu.item-denied.permission",
            Collections.singletonMap("permission", permission));
    }

    public static ForgePlanResult noEligibleVariants() {
        return new ForgePlanResult(Status.NO_ELIGIBLE_VARIANTS, null, "menu.item-denied.no-tier", Collections.emptyMap());
    }

    public static ForgePlanResult internalFailure(String details) {
        return new ForgePlanResult(Status.INTERNAL_FAILURE, null, "menu.forge-start-failed",
            Collections.emptyMap());
    }

    public static ForgePlanResult missingRequirements(String details) {
        return new ForgePlanResult(Status.MISSING_REQUIREMENTS, null, "menu.requirements-not-met",
            Collections.emptyMap());
    }

    public static ForgePlanResult economyUnavailable() {
        return new ForgePlanResult(Status.ECONOMY_UNAVAILABLE, null, "menu.requirements-not-met", Collections.emptyMap());
    }

    public static ForgePlanResult sessionRetired() {
        return new ForgePlanResult(Status.SESSION_RETIRED, null, "menu.forge-start-failed", Collections.emptyMap());
    }

    public boolean isReady() {
        return status == Status.READY;
    }
}
