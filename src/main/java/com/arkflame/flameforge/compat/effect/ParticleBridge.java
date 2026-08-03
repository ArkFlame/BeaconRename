package com.arkflame.flameforge.compat.effect;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ParticleBridge {
    private static final ParticleBridge INSTANCE = new ParticleBridge();
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final Map<String, String> PARTICLE_ALIASES = new ConcurrentHashMap<>();
    private static final Map<String, Integer> LEGACY_EFFECT_IDS = new HashMap<>();
    private static final Map<String, Object> PARTICLE_CACHE = new ConcurrentHashMap<>(MAX_CACHE_ENTRIES);
    private static final Object PARTICLE_NOT_FOUND = new Object();

    private final boolean modernAvailable;
    private final Class<?> particleClass;
    private final Method spawnParticleMethod;

    static {
        PARTICLE_ALIASES.put("explode", "explosion_normal");
        PARTICLE_ALIASES.put("explosion", "explosion_normal");
        PARTICLE_ALIASES.put("large_explosion", "explosion_large");
        PARTICLE_ALIASES.put("huge_explosion", "explosion_large");
        PARTICLE_ALIASES.put("firework", "firework");
        PARTICLE_ALIASES.put("bubble", "bubble");
        PARTICLE_ALIASES.put("bubble_pop", "bubble");
        PARTICLE_ALIASES.put("underwater", "bubble");
        PARTICLE_ALIASES.put("crit", "crit");
        PARTICLE_ALIASES.put("magic_crit", "crit");
        PARTICLE_ALIASES.put("smoke", "smoke");
        PARTICLE_ALIASES.put("smoke_normal", "smoke");
        PARTICLE_ALIASES.put("largesmoke", "smoke_large");
        PARTICLE_ALIASES.put("large_smoke", "smoke_large");
        PARTICLE_ALIASES.put("spell", "spell");
        PARTICLE_ALIASES.put("spell_instant", "spell_instant");
        PARTICLE_ALIASES.put("effect", "spell");
        PARTICLE_ALIASES.put("witch", "witch");
        PARTICLE_ALIASES.put("note", "note");
        PARTICLE_ALIASES.put("note_block", "note");
        PARTICLE_ALIASES.put("portal", "portal");
        PARTICLE_ALIASES.put("ender_portal", "portal");
        PARTICLE_ALIASES.put("flame", "flame");
        PARTICLE_ALIASES.put("lava", "lava");
        PARTICLE_ALIASES.put("footstep", "footstep");
        PARTICLE_ALIASES.put("midnight", "midnight");
        PARTICLE_ALIASES.put("splash", "splash");
        PARTICLE_ALIASES.put("splash_pool", "splash");
        PARTICLE_ALIASES.put("particle_splash", "splash");
        PARTICLE_ALIASES.put("eye_of_ender", "end_rod");
        PARTICLE_ALIASES.put("mobspawner", "smoke_large");
        PARTICLE_ALIASES.put("item_crack", "item_slime");
        PARTICLE_ALIASES.put("item_break", "item_slime");
        PARTICLE_ALIASES.put("block_crack", "block_dust");
        PARTICLE_ALIASES.put("block_dust", "block_dust");
        PARTICLE_ALIASES.put("snowball", "snowball");
        PARTICLE_ALIASES.put("snow_shovel", "snowball");
        PARTICLE_ALIASES.put("slime", "item_slime");
        PARTICLE_ALIASES.put("heart", "heart");
        PARTICLE_ALIASES.put("angry_villager", "villager_angry");
        PARTICLE_ALIASES.put("happy_villager", "villager_happy");
        PARTICLE_ALIASES.put("drip_water", "drip_water");
        PARTICLE_ALIASES.put("drip_lava", "drip_lava");
        PARTICLE_ALIASES.put("drip", "drip_water");
        PARTICLE_ALIASES.put("spit", "spit");
        PARTICLE_ALIASES.put("squid_ink", "squid_ink");
        PARTICLE_ALIASES.put("bubble_column_up", "bubble_column_up");
        PARTICLE_ALIASES.put("current_down", "current_down");
        PARTICLE_ALIASES.put("dragon_breath", "dragon_breath");
        PARTICLE_ALIASES.put("ash", "ash");
        PARTICLE_ALIASES.put("crimson_spore", "crimson_spore");
        PARTICLE_ALIASES.put("warped_spore", "warped_spore");
        PARTICLE_ALIASES.put("soul", "soul");
        PARTICLE_ALIASES.put("dust", "dust");
        PARTICLE_ALIASES.put("item_crack_", "item");

        LEGACY_EFFECT_IDS.put("explosion_normal", 1);
        LEGACY_EFFECT_IDS.put("explosion_large", 2);
        LEGACY_EFFECT_IDS.put("explosion_fire", 3);
        LEGACY_EFFECT_IDS.put("bubble", 4);
        LEGACY_EFFECT_IDS.put("bubble_pop", 5);
        LEGACY_EFFECT_IDS.put("bubble_column_up", 6);
        LEGACY_EFFECT_IDS.put("current_down", 7);
        LEGACY_EFFECT_IDS.put("crit", 8);
        LEGACY_EFFECT_IDS.put("magic_crit", 9);
        LEGACY_EFFECT_IDS.put("smoke", 10);
        LEGACY_EFFECT_IDS.put("smoke_large", 11);
        LEGACY_EFFECT_IDS.put("spell", 12);
        LEGACY_EFFECT_IDS.put("spell_instant", 13);
        LEGACY_EFFECT_IDS.put("witch", 14);
        LEGACY_EFFECT_IDS.put("note", 15);
        LEGACY_EFFECT_IDS.put("portal", 16);
        LEGACY_EFFECT_IDS.put("flame", 17);
        LEGACY_EFFECT_IDS.put("lava", 18);
        LEGACY_EFFECT_IDS.put("footstep", 19);
        LEGACY_EFFECT_IDS.put("midnight", 20);
        LEGACY_EFFECT_IDS.put("splash", 21);
        LEGACY_EFFECT_IDS.put("end_rod", 24);
        LEGACY_EFFECT_IDS.put("smoke_large", 25);
        LEGACY_EFFECT_IDS.put("item_slime", 26);
        LEGACY_EFFECT_IDS.put("snowball", 27);
        LEGACY_EFFECT_IDS.put("heart", 29);
        LEGACY_EFFECT_IDS.put("villager_angry", 30);
        LEGACY_EFFECT_IDS.put("villager_happy", 31);
        LEGACY_EFFECT_IDS.put("drip_water", 32);
        LEGACY_EFFECT_IDS.put("drip_lava", 33);
        LEGACY_EFFECT_IDS.put("dust", 34);
        LEGACY_EFFECT_IDS.put("dragon_breath", 35);
        LEGACY_EFFECT_IDS.put("ash", 36);
        LEGACY_EFFECT_IDS.put("crimson_spore", 37);
        LEGACY_EFFECT_IDS.put("warped_spore", 38);
        LEGACY_EFFECT_IDS.put("soul", 39);
        LEGACY_EFFECT_IDS.put("spit", 40);
        LEGACY_EFFECT_IDS.put("squid_ink", 41);
    }

    private ParticleBridge() {
        Class<?> clazz = null;
        Method method = null;
        boolean modern = false;
        try {
            clazz = Class.forName("org.bukkit.Particle");
            method = Player.class.getMethod("spawnParticle", clazz, Location.class, int.class);
            modern = true;
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            clazz = null;
            method = null;
            modern = false;
        }
        this.particleClass = clazz;
        this.spawnParticleMethod = method;
        this.modernAvailable = modern;
    }

    public static ParticleBridge getInstance() {
        return INSTANCE;
    }

    public boolean isModernAvailable() {
        return modernAvailable;
    }

    public void sendToPlayer(final Player player, final String particleKey, final Location location,
                             final float offsetX, final float offsetY, final float offsetZ,
                             final float speed, final int count) {
        if (player == null || particleKey == null || location == null) {
            return;
        }
        if (modernAvailable) {
            sendModern(player, particleKey, location, offsetX, offsetY, offsetZ, speed, count);
        } else {
            sendLegacy(player, particleKey, location, offsetX, offsetY, offsetZ, speed, count);
        }
    }

    public void sendToPlayer(Player player, String particleKey, String... candidates) {
        if (player == null || particleKey == null) {
            return;
        }
        Object cached = PARTICLE_CACHE.get(particleKey);
        if (cached == PARTICLE_NOT_FOUND) {
            return;
        }
        if (cached != null) {
            sendModernParticle(player, locationFromPlayer(player), cached);
            return;
        }

        Object particle = resolveParticle(particleKey);
        if (particle != null) {
            putInCache(particleKey, particle);
            sendModernParticle(player, locationFromPlayer(player), particle);
            return;
        }

        if (candidates != null) {
            for (String candidate : candidates) {
                Object p = resolveParticle(candidate);
                if (p != null) {
                    putInCache(particleKey, p);
                    sendModernParticle(player, locationFromPlayer(player), p);
                    return;
                }
            }
        }

        Integer effectId = LEGACY_EFFECT_IDS.get(particleKey.toUpperCase());
        if (effectId != null) {
            sendLegacyEffect(player, locationFromPlayer(player), effectId);
            putInCache(particleKey, particleKey);
            return;
        }

        putInCache(particleKey, PARTICLE_NOT_FOUND);
    }

    private void putInCache(String key, Object value) {
        if (PARTICLE_CACHE.size() >= MAX_CACHE_ENTRIES && !PARTICLE_CACHE.containsKey(key)) {
            evictOneEntry();
        }
        PARTICLE_CACHE.put(key, value);
    }

    private void evictOneEntry() {
        for (Map.Entry<String, Object> entry : PARTICLE_CACHE.entrySet()) {
            if (entry.getValue() != PARTICLE_NOT_FOUND) {
                PARTICLE_CACHE.remove(entry.getKey());
                break;
            }
        }
    }

    private Location locationFromPlayer(Player player) {
        return player.getLocation();
    }

    private Object resolveParticle(String key) {
        if (particleClass == null) {
            return null;
        }
        try {
            return Enum.valueOf((Class<Enum>) particleClass, key.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void sendModernParticle(Player player, Location location, Object particle) {
        if (spawnParticleMethod == null) {
            return;
        }
        try {
            spawnParticleMethod.invoke(player, particle, location, 1, 0, 0, 0, 0);
        } catch (Exception e) {
        }
    }

    private void sendLegacyEffect(Player player, Location location, int effectId) {
        try {
            Effect effect = Effect.values()[effectId];
            player.playEffect(location, effect, null);
        } catch (Exception e) {
        }
    }

    private void sendModern(final Player player, final String particleKey, final Location location,
                            final float offsetX, final float offsetY, final float offsetZ,
                            final float speed, final int count) {
        try {
            String particleName = PARTICLE_ALIASES.getOrDefault(particleKey.toLowerCase(), particleKey.toLowerCase());
            Object particle = resolveParticle(particleName);
            if (particle == null) {
                sendLegacy(player, particleKey, location, offsetX, offsetY, offsetZ, speed, count);
                return;
            }
            spawnParticleMethod.invoke(player, particle, location, count, offsetX, offsetY, offsetZ, speed);
        } catch (Exception e) {
            sendLegacy(player, particleKey, location, offsetX, offsetY, offsetZ, speed, count);
        }
    }

    private void sendLegacy(final Player player, final String particleKey, final Location location,
                            final float offsetX, final float offsetY, final float offsetZ,
                            final float speed, final int count) {
        try {
            Effect effect = getLegacyEffect(particleKey);
            if (effect != null) {
                player.playEffect(location, effect, null);
            }
        } catch (Exception e) {
            try {
                player.playEffect(location, Effect.FOOTSTEP, null);
            } catch (Exception ignored) {
            }
        }
    }

    private Effect getLegacyEffect(final String particleKey) {
        String effectName = PARTICLE_ALIASES.get(particleKey.toLowerCase());
        if (effectName == null) {
            effectName = particleKey.toLowerCase();
        }
        Integer id = LEGACY_EFFECT_IDS.get(effectName);
        if (id != null) {
            for (Effect effect : Effect.values()) {
                if (effect.getId() == id) {
                    return effect;
                }
            }
        }
        try {
            return Effect.valueOf(effectName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public Map<String, String> getAvailableParticles() {
        return Collections.unmodifiableMap(new HashMap<>(PARTICLE_ALIASES));
    }
}
