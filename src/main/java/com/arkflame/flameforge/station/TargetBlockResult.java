package com.arkflame.flameforge.station;

import java.util.Objects;

public final class TargetBlockResult {
    public enum Status {
        FOUND,
        NO_TARGET,
        UNAVAILABLE,
        PLAYER_RETIRED
    }

    private final Status status;
    private final TargetBlockSnapshot snapshot;

    public TargetBlockResult(Status status, TargetBlockSnapshot snapshot) {
        this.status = Objects.requireNonNull(status);
        this.snapshot = snapshot;
    }

    public static TargetBlockResult found(TargetBlockSnapshot snapshot) {
        return new TargetBlockResult(Status.FOUND, Objects.requireNonNull(snapshot));
    }

    public static TargetBlockResult noTarget() {
        return new TargetBlockResult(Status.NO_TARGET, null);
    }

    public static TargetBlockResult unavailable() {
        return new TargetBlockResult(Status.UNAVAILABLE, null);
    }

    public static TargetBlockResult retired() {
        return new TargetBlockResult(Status.PLAYER_RETIRED, null);
    }

    public Status status() {
        return status;
    }

    public TargetBlockSnapshot snapshot() {
        return snapshot;
    }

    public boolean isFound() {
        return status == Status.FOUND;
    }
}
