package com.arkflame.flameforge.effect;

import java.util.concurrent.atomic.AtomicBoolean;

public final class AnimationHandle {
    private final String transactionId;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);

    public AnimationHandle(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean isCompleted() {
        return completed.get();
    }

    public boolean cancel() {
        return cancelled.compareAndSet(false, true);
    }

    public boolean complete() {
        return completed.compareAndSet(false, true);
    }

    public boolean isTerminal() {
        return cancelled.get() || completed.get();
    }
}
