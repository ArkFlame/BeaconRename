package com.arkflame.flameforge.compat.effect.particle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ParticleBatch {
    private static final int MAX_REQUESTS = 2048;
    private final List<ParticleRequest> requests;

    public ParticleBatch(List<ParticleRequest> requests) {
        if (requests == null || requests.size() > MAX_REQUESTS) {
            throw new IllegalArgumentException("Particle batch exceeds 2048 requests");
        }
        List<ParticleRequest> copy = new ArrayList<ParticleRequest>(requests.size());
        for (ParticleRequest request : requests) {
            if (request == null) throw new IllegalArgumentException("Particle request cannot be null");
            copy.add(request);
        }
        this.requests = Collections.unmodifiableList(copy);
    }

    public static ParticleBatch single(ParticleRequest request) {
        return new ParticleBatch(Collections.singletonList(request));
    }

    public List<ParticleRequest> getRequests() { return requests; }
}
