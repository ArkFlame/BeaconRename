package com.arkflame.flameforge.testfakes;

import com.arkflame.flameforge.compat.scheduler.TaskHandle;

public final class TaskHandleStub implements TaskHandle {
    private volatile boolean cancelled = false;

    public static final TaskHandle INSTANCE = new TaskHandleStub();

    public void reset() {
        cancelled = false;
    }

    @Override
    public void cancel() {
        cancelled = true;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }
}