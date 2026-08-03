package com.arkflame.flameforge.compat.effect;

import org.bukkit.EntityEffect;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class SoundResolver {
    private static final int MAX_CACHE_ENTRIES = 256;
    private static final SoundResolver INSTANCE = new SoundResolver();
    private static final Map<String, String[]> ALIASES = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> SOUND_GROUPS = new ConcurrentHashMap<>();
    private static final Set<String> LOGGED_MISSING = new HashSet<>();
    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>(MAX_CACHE_ENTRIES);
    private static final Object NOT_FOUND = new Object();
    private static Logger pluginLogger;

    static {
        final String success = "success";
        final String error = "error";
        final String ui = "ui";

        SOUND_GROUPS.put(success, new HashSet<>(Arrays.asList(
                "entity.player.levelup", "ui.button.click",
                "block.note_block.pling", "entity.experience_orb.pickup")));
        SOUND_GROUPS.put(error, new HashSet<>(Arrays.asList(
                "entity.villager.no", "entity.endermen.teleport",
                "block.anvil.destroy", "entity.wither.break")));
        SOUND_GROUPS.put(ui, new HashSet<>(Arrays.asList(
                "ui.button.click", "entity.slime.squish",
                "entity.bat.takeoff", "entity.arrow.hit_player")));

        ALIASES.put("level_up", new String[]{"ENTITY_PLAYER_LEVELUP", "LEVEL_UP"});
        ALIASES.put("anvil_use", new String[]{"BLOCK_ANVIL_USE", "ANVIL_USE"});
        ALIASES.put("anvil_break", new String[]{"BLOCK_ANVIL_BREAK", "ANVIL_BREAK"});
        ALIASES.put("anvil_land", new String[]{"BLOCK_ANVIL_LAND", "ANVIL_LAND"});
        ALIASES.put("villager_no", new String[]{"ENTITY_VILLAGER_NO", "VILLAGER_NO"});
        ALIASES.put("villager_yes", new String[]{"ENTITY_VILLAGER_YES", "VILLAGER_YES"});
        ALIASES.put("item_break", new String[]{"ENTITY_ITEM_BREAK", "ITEM_BREAK"});
        ALIASES.put("item_pickup", new String[]{"ENTITY_ITEM_PICKUP", "ITEM_PICKUP"});
        ALIASES.put("click", new String[]{"UI_BUTTON_CLICK", "CLICK", "WOOD_CLICK"});
        ALIASES.put("bow_hit", new String[]{"ENTITY_ARROW_HIT", "ARROW_HIT"});
        ALIASES.put("explode", new String[]{"ENTITY_GENERIC_EXPLODE", "EXPLODE"});
        ALIASES.put("fire_work", new String[]{"ENTITY_FIREWORK_LAUNCH", "FIREWORK_LAUNCH"});
        ALIASES.put("enderpearl", new String[]{"ENTITY_ENDERMAN_TELEPORT", "ENDERMAN_TELEPORT"});
        ALIASES.put("chest_open", new String[]{"BLOCK_CHEST_OPEN", "CHEST_OPEN"});
        ALIASES.put("chest_close", new String[]{"BLOCK_CHEST_CLOSE", "CHEST_CLOSE"});
        ALIASES.put("pickup", new String[]{"ENTITY_ITEM_PICKUP", "ITEM_PICKUP"});
    }

    private SoundResolver() {
    }

    public static void setLogger(final Logger logger) {
        pluginLogger = logger;
    }

    public static SoundResolver getInstance() {
        return INSTANCE;
    }

    public Optional<Sound> resolve(String... candidates) {
        if (candidates == null || candidates.length == 0) {
            return Optional.empty();
        }
        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty()) {
                continue;
            }
            Object cached = CACHE.get(candidate);
            if (cached == NOT_FOUND) {
                continue;
            }
            if (cached instanceof Sound) {
                return Optional.of((Sound) cached);
            }
            Sound sound = resolveSingle(candidate);
            if (sound != null) {
                putInCache(candidate, sound);
                return Optional.of(sound);
            }
            putInCache(candidate, NOT_FOUND);
        }
        return Optional.empty();
    }

    private void putInCache(String key, Object value) {
        if (CACHE.size() >= MAX_CACHE_ENTRIES && !CACHE.containsKey(key)) {
            evictOneEntry();
        }
        CACHE.put(key, value);
    }

    private void evictOneEntry() {
        for (Map.Entry<String, Object> entry : CACHE.entrySet()) {
            if (entry.getValue() != NOT_FOUND) {
                CACHE.remove(entry.getKey());
                break;
            }
        }
    }

    private Sound resolveSingle(String key) {
        String normalized = key.toLowerCase().replace(" ", "_");
        String[] aliasCandidates = ALIASES.get(normalized);
        if (aliasCandidates != null) {
            for (String candidate : aliasCandidates) {
                Sound sound = tryParseSound(candidate);
                if (sound != null) {
                    return sound;
                }
            }
        }
        return tryParseSound(key);
    }

    private Sound tryParseSound(String name) {
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public Sound resolveOrThrow(final String key) {
        return resolve(key).orElseThrow(() -> new IllegalArgumentException("Unknown sound: " + key));
    }

    public Sound resolveOrDefault(final String key, final Sound fallback) {
        return resolve(key).orElse(fallback);
    }

    public void playToPlayer(final Player player, final String soundKey, final float volume, final float pitch) {
        if (player == null || soundKey == null || soundKey.isEmpty()) {
            return;
        }
        resolve(soundKey).ifPresent(sound -> {
            try {
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (Exception e) {
                logOnceMissing(soundKey);
            }
        });
    }

    public void playToPlayer(final Player player, final String soundKey) {
        playToPlayer(player, soundKey, 1.0f, 1.0f);
    }

    public void playCosmetic(final Player player, final String soundKey) {
        playCosmetic(player, soundKey, 1.0f, 1.0f);
    }

    public void playCosmetic(final Player player, final String soundKey, final float volume, final float pitch) {
        if (player == null || soundKey == null || soundKey.isEmpty()) {
            return;
        }
        resolve(soundKey).ifPresent(sound -> {
            try {
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (Exception e) {
            }
        });
    }

    public void playEntityEffect(final Player player, final EntityEffect effect) {
        if (player != null && effect != null) {
            try {
                player.playEffect(effect);
            } catch (Exception e) {
            }
        }
    }

    private void logOnceMissing(final String soundKey) {
        if (pluginLogger != null && LOGGED_MISSING.add(soundKey)) {
            pluginLogger.warning("[SoundResolver] Sound not found or unavailable: " + soundKey);
        }
    }

    public List<String> getGroup(final String groupName) {
        final Set<String> group = SOUND_GROUPS.get(groupName.toLowerCase());
        if (group == null) {
            return java.util.Collections.emptyList();
        }
        return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(group));
    }

    public boolean hasGroup(final String groupName) {
        return SOUND_GROUPS.containsKey(groupName.toLowerCase());
    }

    public Map<String, Set<String>> getSoundGroups() {
        final Map<String, Set<String>> copy = new HashMap<>();
        for (final Map.Entry<String, Set<String>> entry : SOUND_GROUPS.entrySet()) {
            copy.put(entry.getKey(), java.util.Collections.unmodifiableSet(new HashSet<>(entry.getValue())));
        }
        return java.util.Collections.unmodifiableMap(copy);
    }

    public void clearCache() {
        CACHE.clear();
    }

    private static class Optional<T> {
        private final T value;
        private Optional(T value) { this.value = value; }
        static <T> Optional<T> of(T value) { return new Optional<>(value); }
        static <T> Optional<T> empty() { return new Optional<>(null); }
        static <T> Optional<T> ofNullable(T value) { return new Optional<>(value); }
        T orElse(T defaultValue) { return value != null ? value : defaultValue; }
        T orElseThrow(java.util.function.Supplier<RuntimeException> ex) {
            if (value == null) throw ex.get();
            return value;
        }
        void ifPresent(java.util.function.Consumer<T> action) {
            if (value != null) action.accept(value);
        }
    }
}
