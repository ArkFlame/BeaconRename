package com.arkflame.flameforge.compat.scheduler;

public interface TaskHandle {
    void cancel();
    boolean isCancelled();
}
