package com.arkflame.flameforge.compat.effect.particle;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class ParticleCapabilities {
    private final String provider;
    private final boolean reflective;
    private final Set<String> particleNames;

    public ParticleCapabilities(String provider, boolean reflective, Set<String> particleNames) {
        this.provider = provider == null ? "unknown" : provider;
        this.reflective = reflective;
        this.particleNames = Collections.unmodifiableSet(new LinkedHashSet<String>(particleNames));
    }

    public String getProvider() { return provider; }
    public boolean isReflective() { return reflective; }
    public Set<String> getParticleNames() { return particleNames; }
    public boolean supports(String name) { return name != null && particleNames.contains(name.toUpperCase()); }
}
