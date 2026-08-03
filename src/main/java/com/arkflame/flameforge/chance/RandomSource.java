package com.arkflame.flameforge.chance;

import java.util.Random;

public interface RandomSource {
    long nextLong(long bound);
    double nextDouble();
    double nextDouble(double bound);
}
