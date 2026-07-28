package com.arkflame.flameforge.chance;

import java.util.concurrent.ThreadLocalRandom;

public final class ThreadLocalRandomSource implements RandomSource {
    private static final ThreadLocalRandomSource INSTANCE = new ThreadLocalRandomSource();

    private ThreadLocalRandomSource() {}

    public static ThreadLocalRandomSource getInstance() {
        return INSTANCE;
    }

    @Override
    public long nextLong(long bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        return ThreadLocalRandom.current().nextLong(bound);
    }
}
