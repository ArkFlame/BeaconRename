package com.arkflame.flameforge.compat.effect.particle;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LegacyEffectParticleProvider implements ParticleProvider {
    private final ParticleCatalog catalog;
    private final Map<String, Effect> effects;
    private final Method spigotAccessor;
    private final Method spigotMethod;
    private final ParticleCapabilities capabilities;
    private final Logger logger;
    private boolean diagnosticLogged;

    public LegacyEffectParticleProvider(ParticleCatalog catalog) {
        this(catalog, Logger.getLogger(LegacyEffectParticleProvider.class.getName()));
    }

    public LegacyEffectParticleProvider(ParticleCatalog catalog, Logger logger) {
        this.catalog = catalog;
        this.logger = logger == null ? Logger.getLogger(LegacyEffectParticleProvider.class.getName()) : logger;
        this.effects = buildEffects();
        this.spigotAccessor = findSpigotAccessor();
        this.spigotMethod = findSpigotMethod();
        Set<String> names = new HashSet<String>(effects.keySet());
        this.capabilities = new ParticleCapabilities("legacy-effect", false, names);
    }

    private Map<String, Effect> buildEffects() {
        Map<String, Effect> result = new LinkedHashMap<String, Effect>();
        for (Effect effect : Effect.values()) result.put(effect.name(), effect);
        alias(result, "EXPLOSION_NORMAL", "EXPLOSION");
        alias(result, "EXPLOSION_LARGE", "EXPLOSION_HUGE");
        alias(result, "EXPLOSION_EMITTER", "EXPLOSION_HUGE");
        alias(result, "FIREWORK", "FIREWORKS_SPARK");
        alias(result, "SMOKE", "PARTICLE_SMOKE");
        alias(result, "LARGE_SMOKE", "SMOKE_LARGE");
        alias(result, "EFFECT", "SPELL");
        alias(result, "INSTANT_EFFECT", "INSTANT_SPELL");
        alias(result, "WITCH", "WITCH_MAGIC");
        alias(result, "ENCHANT", "FLYING_GLYPH");
        alias(result, "DUST", "COLOURED_DUST");
        alias(result, "BLOCK", "STEP_SOUND");
        alias(result, "ITEM", "ITEM_BREAK");
        alias(result, "SNOWBALL", "SNOWBALL_BREAK");
        alias(result, "SLIME", "SLIME");
        alias(result, "VILLAGER_ANGRY", "VILLAGER_THUNDERCLOUD");
        alias(result, "VILLAGER_HAPPY", "HAPPY_VILLAGER");
        alias(result, "DRIP_WATER", "WATERDRIP");
        alias(result, "DRIP_LAVA", "LAVADRIP");
        alias(result, "ELECTRIC_SPARK", "CRIT");
        return Collections.unmodifiableMap(result);
    }

    private static void alias(Map<String, Effect> effects, String alias, String target) {
        Effect effect = effects.get(target);
        if (effect != null) effects.put(alias, effect);
    }

    private Method findSpigotMethod() {
        try {
            Class<?> spigotClass = Class.forName("org.bukkit.entity.Player$Spigot");
            return spigotClass.getMethod("playEffect", Location.class, Effect.class,
                int.class, int.class, float.class, float.class, float.class, float.class, int.class);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private Method findSpigotAccessor() {
        try {
            return Player.class.getMethod("spigot");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    @Override
    public ParticleCapabilities getCapabilities() { return capabilities; }

    @Override
    public boolean emit(Player viewer, ParticleRequest request) {
        Location location = location(request.getPosition());
        if (location == null) return false;
        for (String candidate : request.getCandidates()) {
            for (String key : catalog.resolve(candidate)) {
                Effect effect = effects.get(key.toUpperCase(Locale.ROOT));
                if (effect == null) continue;
                LegacyData data = data(request.getPayload());
                if (trySpigot(viewer, location, effect, request, data)
                    || tryDirect(viewer, location, effect, data.direct)) return true;
            }
        }
        if (request.getPayload() instanceof ParticleRequest.Color) {
            Effect dust = effects.get("COLOURED_DUST");
            if (dust != null && (tryDirect(viewer, location, dust, null)
                || tryDirect(viewer, location, effects.get("CRIT"), null))) return true;
        }
        return false;
    }

    private Location location(ParticleRequest.ParticlePosition position) {
        if (position == null) return null;
        try {
            return new Location(position.getWorld(), position.getX(), position.getY(), position.getZ());
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private LegacyData data(ParticleRequest.Payload payload) {
        if (payload instanceof ParticleRequest.Block) {
            ParticleRequest.Block block = (ParticleRequest.Block) payload;
            return new LegacyData(block.getMaterial().getId(), block.getData(), block.getMaterial().getId());
        }
        if (payload instanceof ParticleRequest.Item) {
            ItemStack item = ((ParticleRequest.Item) payload).getItem();
            return new LegacyData(item.getTypeId(), item.getDurability(), item.getTypeId());
        }
        if (payload instanceof ParticleRequest.Integer) {
            int value = ((ParticleRequest.Integer) payload).getValue();
            return new LegacyData(value, 0, value);
        }
        return new LegacyData(0, 0, null);
    }

    private boolean trySpigot(Player viewer, Location location, Effect effect,
                              ParticleRequest request, LegacyData data) {
        if (spigotMethod == null) return false;
        try {
            if (spigotAccessor == null) return false;
            Object spigot = spigotAccessor.invoke(viewer);
            Object[] args = new Object[] {location, effect, data.id, data.value,
                (float) request.getOffsetX(), (float) request.getOffsetY(),
                (float) request.getOffsetZ(), (float) request.getExtra(), request.getCount()};
            spigotMethod.invoke(spigot, args);
            return true;
        } catch (Exception | LinkageError failure) {
            diagnose(failure);
            return false;
        }
    }

    private boolean tryDirect(Player viewer, Location location, Effect effect, Object data) {
        if (effect == null) return false;
        try {
            viewer.playEffect(location, effect, data);
            return true;
        } catch (RuntimeException | LinkageError failure) {
            diagnose(failure);
            return false;
        }
    }

    private void diagnose(Throwable failure) {
        if (diagnosticLogged) return;
        diagnosticLogged = true;
        logger.log(Level.FINE, "Legacy particle effect invocation failed", failure);
    }

    private static final class LegacyData {
        private final int id;
        private final int value;
        private final Object direct;
        private LegacyData(int id, int value, Object direct) {
            this.id = id;
            this.value = value;
            this.direct = direct;
        }
    }
}
