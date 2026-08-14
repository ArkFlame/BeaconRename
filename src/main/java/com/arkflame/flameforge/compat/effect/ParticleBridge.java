package com.arkflame.flameforge.compat.effect;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ParticleBridge {
    private static final ParticleBridge INSTANCE = new ParticleBridge();
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final int MAX_UNAVAILABLE_KEYS = 64;
    private static final Map<String, String> PARTICLE_ALIASES = new ConcurrentHashMap<>();
    private static final Map<String, Integer> LEGACY_EFFECT_IDS = new HashMap<>();
    private static final Map<String, String[]> EQUIVALENCE_FAMILIES = new HashMap<>();
    private static final Map<String, Object> PARTICLE_CACHE = new ConcurrentHashMap<>(MAX_CACHE_ENTRIES);
    private static final Object PARTICLE_NOT_FOUND = new Object();

    private final boolean modernAvailable;
    private final Class<?> particleClass;
    private final Method spawnParticleMethod;
    private final Method coloredSpawnParticleMethod;
    private final Class<?> dustOptionsClass;
    private final Method colorFromRgbMethod;
    private final Constructor<?> dustOptionsConstructor;
    private final AtomicBoolean coloredDustDiagnostic = new AtomicBoolean(false);
    private final Logger logger = Logger.getLogger(ParticleBridge.class.getName());
    private final Set<String> unavailableKeys = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

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

    static {
        EQUIVALENCE_FAMILIES.put("EXPLOSION", new String[] {"EXPLOSION", "EXPLOSION_NORMAL", "EXPLOSION_LARGE", "EXPLOSION_EMITTER"});
        EQUIVALENCE_FAMILIES.put("HAPPY_VILLAGER", new String[] {"HAPPY_VILLAGER", "VILLAGER_HAPPY"});
        EQUIVALENCE_FAMILIES.put("ANGRY_VILLAGER", new String[] {"ANGRY_VILLAGER", "VILLAGER_ANGRY"});
        EQUIVALENCE_FAMILIES.put("INSTANT_EFFECT", new String[] {"INSTANT_EFFECT", "SPELL_INSTANT"});
        EQUIVALENCE_FAMILIES.put("EFFECT", new String[] {"EFFECT", "SPELL"});
        EQUIVALENCE_FAMILIES.put("SMOKE", new String[] {"SMOKE", "SMOKE_NORMAL"});
        EQUIVALENCE_FAMILIES.put("LARGE_SMOKE", new String[] {"LARGE_SMOKE", "SMOKE_LARGE"});
        EQUIVALENCE_FAMILIES.put("DUST", new String[] {"DUST", "REDSTONE"});
        EQUIVALENCE_FAMILIES.put("ENCHANT", new String[] {"ENCHANT", "ENCHANTMENT_TABLE"});
        EQUIVALENCE_FAMILIES.put("ELECTRIC_SPARK", new String[] {"ELECTRIC_SPARK", "NOTE", "CRIT"});
    }

    private ParticleBridge() {
        Class<?> clazz = null;
        Method method = null;
        Method coloredMethod = null;
        Class<?> dustClass = null;
        Method colorMethod = null;
        Constructor<?> dustConstructor = null;
        boolean modern = false;
        try {
            clazz = Class.forName("org.bukkit.Particle");
            method = findSpawnParticleMethod(clazz, 7);
            if (method == null) {
                method = findSpawnParticleMethod(clazz, 3);
            }
            coloredMethod = findSpawnParticleMethod(clazz, 8);
            modern = method != null;
            try {
                dustClass = Class.forName("org.bukkit.Particle$DustOptions");
                Class<?> colorClass = Class.forName("org.bukkit.Color");
                colorMethod = colorClass.getMethod("fromRGB", int.class, int.class, int.class);
                dustConstructor = dustClass.getConstructor(colorClass, float.class);
            } catch (ReflectiveOperationException ignored) {
                coloredMethod = null;
            }
        } catch (ReflectiveOperationException e) {
            clazz = null;
            method = null;
            modern = false;
        }
        this.particleClass = clazz;
        this.spawnParticleMethod = method;
        this.coloredSpawnParticleMethod = coloredMethod;
        this.dustOptionsClass = dustClass;
        this.colorFromRgbMethod = colorMethod;
        this.dustOptionsConstructor = dustConstructor;
        this.modernAvailable = modern;
    }

    private static Method findSpawnParticleMethod(Class<?> particleType, int parameterCount) {
        for (Method method : Player.class.getMethods()) {
            if ("spawnParticle".equals(method.getName()) && method.getParameterTypes().length == parameterCount
                && method.getParameterTypes()[0] == particleType) {
                return method;
            }
        }
        return null;
    }

    public static ParticleBridge getInstance() {
        return INSTANCE;
    }

    public boolean isModernAvailable() {
        return modernAvailable;
    }

    public void sendColoredDust(Player player, Location location, int red, int green, int blue,
                                float size, int count) {
        if (player == null || location == null) {
            return;
        }
        if (coloredSpawnParticleMethod != null && dustOptionsClass != null
            && colorFromRgbMethod != null && dustOptionsConstructor != null) {
            try {
                Object particle = resolveParticle("DUST");
                if (particle == null) {
                    particle = resolveParticle("REDSTONE");
                }
                if (particle != null) {
                    Object color = colorFromRgbMethod.invoke(null, clamp(red), clamp(green), clamp(blue));
                    Object dust = dustOptionsConstructor.newInstance(color, Math.max(0.01f, size));
                    coloredSpawnParticleMethod.invoke(player, particle, location, count,
                        0.0, 0.0, 0.0, 0.0, dust);
                    return;
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                logColoredDustFallback(e);
            } catch (LinkageError e) {
                logColoredDustFallback(e);
            }
        } else {
            logColoredDustFallback(null);
        }
        sendFirstAvailable(player, location, Arrays.asList("DUST", "REDSTONE", "dust"),
            0f, 0f, 0f, 0f, count);
    }

    private void logColoredDustFallback(Throwable failure) {
        if (coloredDustDiagnostic.compareAndSet(false, true)) {
            logger.log(Level.FINE, "Colored forge dust unavailable; using cosmetic fallback", failure);
        }
    }

    public boolean sendFirstAvailable(Player player, Location location, List<String> candidates,
                                      float offsetX, float offsetY, float offsetZ,
                                      float speed, int count) {
        if (player == null || location == null || candidates == null || candidates.isEmpty()) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            for (String name : buildEquivalenceFamily(candidate)) {
                Object particle = resolveParticle(name);
                if (particle == null) {
                    continue;
                }
                if (trySpawnParticle(player, location, particle, offsetX, offsetY, offsetZ, speed, count)) {
                    return true;
                }
            }
            if (tryLegacyEffect(player, location, candidate)) {
                return true;
            }
        }
        logUnavailableFamily(candidates.toString());
        return false;
    }

    private List<String> buildEquivalenceFamily(String candidate) {
        List<String> family = new ArrayList<String>();
        String normalized = candidate.toUpperCase();
        family.add(normalized);
        String[] members = EQUIVALENCE_FAMILIES.get(normalized);
        if (members != null) {
            for (String member : members) {
                if (!family.contains(member)) {
                    family.add(member);
                }
            }
        }
        return family;
    }

    private boolean trySpawnParticle(Player player, Location location, Object particle,
                                     float offsetX, float offsetY, float offsetZ,
                                     float speed, int count) {
        if (spawnParticleMethod == null) {
            return false;
        }
        try {
            if (spawnParticleMethod.getParameterTypes().length == 3) {
                spawnParticleMethod.invoke(player, particle, location, count);
            } else {
                spawnParticleMethod.invoke(player, particle, location, count,
                    (double) offsetX, (double) offsetY, (double) offsetZ, (double) speed);
            }
            return true;
        } catch (Exception | LinkageError e) {
            return false;
        }
    }

    private boolean tryLegacyEffect(Player player, Location location, String particleKey) {
        Effect effect = getLegacyEffect(particleKey);
        if (effect == null) {
            return false;
        }
        try {
            player.playEffect(location, effect, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void logUnavailableFamily(String familyKey) {
        if (familyKey == null || familyKey.isEmpty() || unavailableKeys.size() >= MAX_UNAVAILABLE_KEYS) {
            return;
        }
        if (unavailableKeys.add(familyKey)) {
            logger.log(Level.FINE, "No particle available for family " + familyKey);
        }
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
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

        Integer effectId = LEGACY_EFFECT_IDS.get(particleKey.toLowerCase());
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
            if (spawnParticleMethod.getParameterTypes().length == 3) {
                spawnParticleMethod.invoke(player, particle, location, 1);
            } else {
                spawnParticleMethod.invoke(player, particle, location, 1, 0.0, 0.0, 0.0, 0.0);
            }
        } catch (Exception | LinkageError e) {
            logUnavailableFamily(String.valueOf(particle));
        }
    }

    private void sendLegacyEffect(Player player, Location location, int effectId) {
        try {
            Effect effect = Effect.values()[effectId];
            player.playEffect(location, effect, null);
        } catch (Exception | LinkageError e) {
            logUnavailableFamily("legacy-effect-" + effectId);
        }
    }

    private void sendModern(final Player player, final String particleKey, final Location location,
                            final float offsetX, final float offsetY, final float offsetZ,
                            final float speed, final int count) {
        try {
            Object particle = resolveParticle(particleKey);
            if (particle == null) {
                String alias = PARTICLE_ALIASES.get(particleKey.toLowerCase());
                if (alias != null) {
                    particle = resolveParticle(alias);
                }
            }
            if (particle == null) {
                sendLegacy(player, particleKey, location, offsetX, offsetY, offsetZ, speed, count);
                return;
            }
            if (spawnParticleMethod.getParameterTypes().length == 3) {
                spawnParticleMethod.invoke(player, particle, location, count);
            } else {
                spawnParticleMethod.invoke(player, particle, location, count,
                    (double) offsetX, (double) offsetY, (double) offsetZ, (double) speed);
            }
        } catch (Exception | LinkageError e) {
            sendLegacy(player, particleKey, location, offsetX, offsetY, offsetZ, speed, count);
        }
    }

    private void sendLegacy(final Player player, final String particleKey, final Location location,
                            final float offsetX, final float offsetY, final float offsetZ,
                            final float speed, final int count) {
        Effect effect = getLegacyEffect(particleKey);
        if (effect == null) {
            logUnavailableFamily(particleKey);
            return;
        }
        try {
            player.playEffect(location, effect, null);
        } catch (Exception e) {
            logUnavailableFamily(particleKey);
        }
    }

    private Effect getLegacyEffect(final String particleKey) {
        String effectName = PARTICLE_ALIASES.get(particleKey.toLowerCase());
        if (effectName == null) {
            effectName = particleKey.toLowerCase();
        }
        Integer id = LEGACY_EFFECT_IDS.get(effectName);
        if (id != null) {
            Effect[] values = Effect.values();
            if (id >= 1 && id <= values.length) {
                return values[id - 1];
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
