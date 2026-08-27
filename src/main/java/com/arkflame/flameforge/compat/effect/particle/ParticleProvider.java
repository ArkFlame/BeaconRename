package com.arkflame.flameforge.compat.effect.particle;

import org.bukkit.entity.Player;

public interface ParticleProvider {
    ParticleCapabilities getCapabilities();
    boolean emit(Player viewer, ParticleRequest request);

    default boolean emit(Player viewer, ParticleBatch batch) {
        boolean emitted = false;
        for (ParticleRequest request : batch.getRequests()) {
            emitted = emit(viewer, request) || emitted;
        }
        return emitted;
    }
}
