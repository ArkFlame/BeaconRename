package com.arkflame.flameforge.compat.effect;

import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ParticleBridge {
    private static final ParticleBridge INSTANCE = new ParticleBridge();
    private static final Map<String, ParticleEntry> MODERN_PARTICLES = new ConcurrentHashMap<>();
    private static final Map<Integer, String> LEGACY_ID_MAP = new HashMap<>();

    private final boolean modernAvailable;
    private Class<?> particleClass;
    private Method particleMethod;
    private Method particlesMethod;
    private Object defaultSampler;

    static {
        MODERN_PARTICLES.put("explode", new ParticleEntry("explosion_normal", 0));
        MODERN_PARTICLES.put("explosion", new ParticleEntry("explosion_normal", 0));
        MODERN_PARTICLES.put("large_explosion", new ParticleEntry("explosion_large", 0));
        MODERN_PARTICLES.put("huge_explosion", new ParticleEntry("explosion_large", 0));
        MODERN_PARTICLES.put("firework", new ParticleEntry("firework", 0));
        MODERN_PARTICLES.put("bubble", new ParticleEntry("bubble", 0));
        MODERN_PARTICLES.put("bubble_pop", new ParticleEntry("bubble", 0));
        MODERN_PARTICLES.put("underwater", new ParticleEntry("bubble", 0));
        MODERN_PARTICLES.put("crit", new ParticleEntry("crit", 0));
        MODERN_PARTICLES.put("magic_crit", new ParticleEntry("crit", 0));
        MODERN_PARTICLES.put("smoke", new ParticleEntry("smoke", 0));
        MODERN_PARTICLES.put("smoke_normal", new ParticleEntry("smoke", 0));
        MODERN_PARTICLES.put("largesmoke", new ParticleEntry("smoke_large", 0));
        MODERN_PARTICLES.put("large_smoke", new ParticleEntry("smoke_large", 0));
        MODERN_PARTICLES.put("spell", new ParticleEntry("spell", 0));
        MODERN_PARTICLES.put("spell_instant", new ParticleEntry("spell_instant", 0));
        MODERN_PARTICLES.put("effect", new ParticleEntry("spell", 0));
        MODERN_PARTICLES.put("witch", new ParticleEntry("spell_witch", 0));
        MODERN_PARTICLES.put("note", new ParticleEntry("note", 0));
        MODERN_PARTICLES.put("note_block", new ParticleEntry("note", 0));
        MODERN_PARTICLES.put("portal", new ParticleEntry("portal", 0));
        MODERN_PARTICLES.put("ender_portal", new ParticleEntry("portal", 0));
        MODERN_PARTICLES.put("flame", new ParticleEntry("flame", 0));
        MODERN_PARTICLES.put("lava", new ParticleEntry("lava", 0));
        MODERN_PARTICLES.put("footstep", new ParticleEntry("footstep", 0));
        MODERN_PARTICLES.put("midnight", new ParticleEntry("midnight", 0));
        MODERN_PARTICLES.put("splash", new ParticleEntry("splash", 0));
        MODERN_PARTICLES.put("splash_pool", new ParticleEntry("splash", 0));
        MODERN_PARTICLES.put("particle_splash", new ParticleEntry("splash", 0));
        MODERN_PARTICLES.put("eye_of_ender", new ParticleEntry("endRod", 0));
        MODERN_PARTICLES.put("mobspawner", new ParticleEntry("smoke_large", 0));
        MODERN_PARTICLES.put("item_crack", new ParticleEntry("item_slime", 0));
        MODERN_PARTICLES.put("item_break", new ParticleEntry("item_slime", 0));
        MODERN_PARTICLES.put("block_crack", new ParticleEntry("block_dust", 0));
        MODERN_PARTICLES.put("block_dust", new ParticleEntry("block_dust", 0));
        MODERN_PARTICLES.put("snowball", new ParticleEntry("snowball", 0));
        MODERN_PARTICLES.put("snow_shovel", new ParticleEntry("snow_shovel", 0));
        MODERN_PARTICLES.put("slime", new ParticleEntry("item_slime", 0));
        MODERN_PARTICLES.put("heart", new ParticleEntry("heart", 0));
        MODERN_PARTICLES.put("angry_villager", new ParticleEntry("villager_angry", 0));
        MODERN_PARTICLES.put("angry Villager", new ParticleEntry("villager_angry", 0));
        MODERN_PARTICLES.put("happy_villager", new ParticleEntry("villager_happy", 0));
        MODERN_PARTICLES.put("happy Villager", new ParticleEntry("villager_happy", 0));
        MODERN_PARTICLES.put("drip_water", new ParticleEntry("drip_water", 0));
        MODERN_PARTICLES.put("drip_lava", new ParticleEntry("drip_lava", 0));
        MODERN_PARTICLES.put("drip", new ParticleEntry("drip_water", 0));
        MODERN_PARTICLES.put("spit", new ParticleEntry("spit", 0));
        MODERN_PARTICLES.put("squid_ink", new ParticleEntry("squid_ink", 0));
        MODERN_PARTICLES.put("bubble_column_up", new ParticleEntry("bubble_column_up", 0));
        MODERN_PARTICLES.put("current_down", new ParticleEntry("current_down", 0));
        MODERN_PARTICLES.put("dragon_breath", new ParticleEntry("dragon_breath", 0));
        MODERN_PARTICLES.put("ash", new ParticleEntry("ash", 0));
        MODERN_PARTICLES.put("crimson_spore", new ParticleEntry("crimson_spore", 0));
        MODERN_PARTICLES.put("warped_spore", new ParticleEntry("warped_spore", 0));
        MODERN_PARTICLES.put("soul", new ParticleEntry("soul", 0));
        MODERN_PARTICLES.put("dust", new ParticleEntry("dust", 0));
        MODERN_PARTICLES.put("item_crack_", new ParticleEntry("item", 0));

        LEGACY_ID_MAP.put(1, "explosion_normal");
        LEGACY_ID_MAP.put(2, "explosion_large");
        LEGACY_ID_MAP.put(3, "explosion_fire");
        LEGACY_ID_MAP.put(4, "bubble");
        LEGACY_ID_MAP.put(5, "bubble_pop");
        LEGACY_ID_MAP.put(6, "bubble_column_up");
        LEGACY_ID_MAP.put(7, "current_down");
        LEGACY_ID_MAP.put(8, "crit");
        LEGACY_ID_MAP.put(9, "magic_crit");
        LEGACY_ID_MAP.put(10, "smoke");
        LEGACY_ID_MAP.put(11, "smoke_large");
        LEGACY_ID_MAP.put(12, "spell");
        LEGACY_ID_MAP.put(13, "spell_instant");
        LEGACY_ID_MAP.put(14, "spell_witch");
        LEGACY_ID_MAP.put(15, "note");
        LEGACY_ID_MAP.put(16, "portal");
        LEGACY_ID_MAP.put(17, "flame");
        LEGACY_ID_MAP.put(18, "lava");
        LEGACY_ID_MAP.put(19, "footstep");
        LEGACY_ID_MAP.put(20, "midnight");
        LEGACY_ID_MAP.put(21, "splash");
        LEGACY_ID_MAP.put(22, "splash_pool");
        LEGACY_ID_MAP.put(23, "particle_splash");
        LEGACY_ID_MAP.put(24, "endRod");
        LEGACY_ID_MAP.put(25, "mobspawner");
        LEGACY_ID_MAP.put(26, "item_slime");
        LEGACY_ID_MAP.put(27, "snowball");
        LEGACY_ID_MAP.put(28, "snow_shovel");
        LEGACY_ID_MAP.put(29, "heart");
        LEGACY_ID_MAP.put(30, "villager_angry");
        LEGACY_ID_MAP.put(31, "villager_happy");
        LEGACY_ID_MAP.put(32, "drip_water");
        LEGACY_ID_MAP.put(33, "drip_lava");
        LEGACY_ID_MAP.put(34, "dust");
        LEGACY_ID_MAP.put(35, "dragon_breath");
        LEGACY_ID_MAP.put(36, "ash");
        LEGACY_ID_MAP.put(37, "crimson_spore");
        LEGACY_ID_MAP.put(38, "warped_spore");
        LEGACY_ID_MAP.put(39, "soul");
        LEGACY_ID_MAP.put(40, "spit");
        LEGACY_ID_MAP.put(41, "squid_ink");
    }

    private ParticleBridge() {
        this.modernAvailable = initializeModern();
    }

    private boolean initializeModern() {
        try {
            final String nmsVersion = detectNMSVersion();
            if (nmsVersion == null) {
                return false;
            }
            final String packagePath = "net.minecraft.server." + nmsVersion;
            particleClass = Class.forName(packagePath + ".Particle");
            final String registryClassName = packagePath + ".ParticleRegistry";
            Class<?> registryClass = Class.forName(registryClassName);
            Field registryField = null;
            for (Field field : registryClass.getDeclaredFields()) {
                if (field.getType().getSimpleName().equals("ParticleRegistry")) {
                    registryField = field;
                    break;
                }
            }
            if (registryField == null) {
                return false;
            }
            registryField.setAccessible(true);
            Object registry = registryField.get(null);
            if (registry == null) {
                return false;
            }
            particleMethod = registryClass.getMethod("getById", int.class);
            particlesMethod = Class.forName(packagePath + ".PacketPlayOutWorldParticles")
                    .getMethod("a", particleClass, boolean.class, float.class, float.class, float.class, float.class, float.class, float.class, float.class, int.class);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String detectNMSVersion() {
        String[] possibleVersions = {
                "v1_8_R3", "v1_9_R1", "v1_9_R2", "v1_10_R1", "v1_11_R1",
                "v1_12_R1", "v1_13_R1", "v1_13_R2", "v1_14_R1", "v1_15_R1",
                "v1_16_R1", "v1_16_R2", "v1_16_R3", "v1_17_R1", "v1_18_R1",
                "v1_18_R2", "v1_19_R1", "v1_19_R2", "v1_19_R3", "v1_20_R1",
                "v1_20_R2", "v1_20_R3", "v1_21_R1"
        };
        for (String version : possibleVersions) {
            try {
                Class.forName("net.minecraft.server." + version + ".Particle");
                return version;
            } catch (ClassNotFoundException e) {
                // continue
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

    private void sendModern(final Player player, final String particleKey, final Location location,
                            final float offsetX, final float offsetY, final float offsetZ,
                            final float speed, final int count) {
        try {
            ParticleEntry entry = MODERN_PARTICLES.get(particleKey.toLowerCase());
            String modernName = (entry != null) ? entry.modernName : particleKey.toLowerCase();
            Object particle = particleMethod.invoke(null, getModernParticleId(modernName));
            if (particle == null) {
                return;
            }
            Object packet = particlesMethod.invoke(null, particle, true,
                    (float) location.getX(), (float) location.getY(), (float) location.getZ(),
                    offsetX, offsetY, offsetZ, speed, count);
            if (packet != null) {
                sendPacket(player, packet);
            }
        } catch (Exception e) {
            sendLegacy(player, particleKey, location, offsetX, offsetY, offsetZ, speed, count);
        }
    }

    private int getModernParticleId(final String modernName) {
        for (Map.Entry<String, ParticleEntry> entry : MODERN_PARTICLES.entrySet()) {
            if (entry.getValue().modernName.equals(modernName)) {
                return entry.getValue().id;
            }
        }
        return 0;
    }

    private void sendLegacy(final Player player, final String particleKey, final Location location,
                            final float offsetX, final float offsetY, final float offsetZ,
                            final float speed, final int count) {
        try {
            Effect effect = getLegacyEffect(particleKey);
            if (effect != null) {
                Object packet = createLegacyPacket(location, offsetX, offsetY, offsetZ, speed, count, effect);
                if (packet != null) {
                    sendPacket(player, packet);
                }
            }
        } catch (Exception e) {
            // fallback to basic effect
            try {
                player.playEffect(location, Effect.valueOf("FOOTSTEP"), null);
            } catch (Exception ignored) {
            }
        }
    }

    private Effect getLegacyEffect(final String particleKey) {
        final String lower = particleKey.toLowerCase();
        if (MODERN_PARTICLES.containsKey(lower)) {
            return Effect.valueOf(MODERN_PARTICLES.get(lower).modernName.toUpperCase());
        }
        try {
            return Effect.valueOf(particleKey.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Object createLegacyPacket(final Location location, final float offsetX, final float offsetY,
                                     final float offsetZ, final float speed, final int count, final Effect effect)
            throws Exception {
        Class<?> packetClass = Class.forName("net.minecraft.server." + detectNMSVersion() + ".PacketPlayOutWorldParticles");
        Object packet = packetClass.newInstance();
        Field aField = packetClass.getDeclaredField("a");
        aField.setAccessible(true);
        aField.set(packet, effect.getId());
        Field bField = packetClass.getDeclaredField("b");
        bField.setAccessible(true);
        bField.set(packet, (float) location.getX());
        Field cField = packetClass.getDeclaredField("c");
        cField.setAccessible(true);
        cField.set(packet, (float) location.getY());
        Field dField = packetClass.getDeclaredField("d");
        dField.setAccessible(true);
        dField.set(packet, (float) location.getZ());
        Field eField = packetClass.getDeclaredField("e");
        eField.setAccessible(true);
        eField.set(packet, offsetX);
        Field fField = packetClass.getDeclaredField("f");
        fField.setAccessible(true);
        fField.set(packet, offsetY);
        Field gField = packetClass.getDeclaredField("g");
        gField.setAccessible(true);
        gField.set(packet, offsetZ);
        Field hField = packetClass.getDeclaredField("h");
        hField.setAccessible(true);
        hField.set(packet, speed);
        Field iField = packetClass.getDeclaredField("i");
        iField.setAccessible(true);
        iField.set(packet, count);
        return packet;
    }

    private void sendPacket(final Player player, final Object packet) throws Exception {
        Method getHandle = player.getClass().getMethod("getHandle");
        Object entityPlayer = getHandle.invoke(player);
        Field playerConnectionField = entityPlayer.getClass().getField("playerConnection");
        Object playerConnection = playerConnectionField.get(entityPlayer);
        Method sendPacket = playerConnection.getClass().getMethod("sendPacket",
                Class.forName("net.minecraft.server." + detectNMSVersion() + ".Packet"));
        sendPacket.invoke(playerConnection, packet);
    }

    public Map<String, String> getAvailableParticles() {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, ParticleEntry> entry : MODERN_PARTICLES.entrySet()) {
            result.put(entry.getKey(), entry.getValue().modernName);
        }
        return Collections.unmodifiableMap(result);
    }

    private static class ParticleEntry {
        final String modernName;
        final int id;

        ParticleEntry(String modernName, int id) {
            this.modernName = modernName;
            this.id = id;
        }
    }
}
