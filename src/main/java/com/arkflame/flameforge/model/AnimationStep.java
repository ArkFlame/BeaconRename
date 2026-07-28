package com.arkflame.flameforge.model;

import java.util.Objects;

public final class AnimationStep {
    private final int delay;
    private final String type;
    private final String data;

    private AnimationStep(int delay, String type, String data) {
        this.delay = delay;
        this.type = Objects.requireNonNull(type);
        this.data = data;
    }

    public static AnimationStep of(int delay, String type, String data) {
        return new AnimationStep(delay, type, data);
    }

    public static AnimationStep of(int delay, String type) {
        return new AnimationStep(delay, type, null);
    }

    public int getDelay() { return delay; }
    public String getType() { return type; }
    public String getData() { return data; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnimationStep)) return false;
        AnimationStep that = (AnimationStep) o;
        return delay == that.delay &&
               Objects.equals(type, that.type) &&
               Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delay, type, data);
    }

    @Override
    public String toString() {
        return "AnimationStep{delay=" + delay + ", type=" + type + ", data=" + data + "}";
    }
}
