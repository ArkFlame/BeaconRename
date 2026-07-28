package com.arkflame.flameforge.compat.effect;

import org.bukkit.EntityEffect;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public final class SoundResolver {
    private static final SoundResolver INSTANCE = new SoundResolver();
    private static final Map<String, Sound> ALIASES = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> SOUND_GROUPS = new ConcurrentHashMap<>();
    private static final Set<String> LOGGED_MISSING = Collections.synchronizedSet(new HashSet<>());
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

        ALIASES.put("level_up", Sound.LEVEL_UP);
        ALIASES.put("anvil_use", Sound.ANVIL_USE);
        ALIASES.put("anvil_break", Sound.ANVIL_BREAK);
        ALIASES.put("anvil_land", Sound.ANVIL_LAND);
        ALIASES.put("villager_no", Sound.VILLAGER_NO);
        ALIASES.put("villager_yes", Sound.VILLAGER_YES);
        ALIASES.put("item_break", Sound.ITEM_BREAK);
        ALIASES.put("item_pickup", Sound.ITEM_PICKUP);
        ALIASES.put("click", Sound.CLICK);
        ALIASES.put("bow_hit", Sound.ARROW_HIT);
        ALIASES.put("explode", Sound.EXPLODE);
        ALIASES.put("fire_work", Sound.FIREWORK_LAUNCH);
        ALIASES.put("enderpearl", Sound.ENDERMAN_TELEPORT);
        ALIASES.put("chest_open", Sound.CHEST_OPEN);
        ALIASES.put("chest_close", Sound.CHEST_CLOSE);
        ALIASES.put("click_1", Sound.valueOf("WOOD_CLICK"));
        ALIASES.put("click_2", Sound.valueOf("CLICK"));
        ALIASES.put("stone_click", Sound.valueOf("STEP_STONE"));
    }

    private SoundResolver() {
    }

    public static void setLogger(final Logger logger) {
        pluginLogger = logger;
    }

    public static SoundResolver getInstance() {
        return INSTANCE;
    }

    public Optional<Sound> resolve(final String key) {
        if (key == null || key.isEmpty()) {
            return Optional.empty();
        }
        final String normalized = key.toLowerCase().replace(" ", "_");
        if (ALIASES.containsKey(normalized)) {
            return Optional.of(ALIASES.get(normalized));
        }
        try {
            return Optional.of(Sound.valueOf(normalized.toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
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
                // cosmetic sound, no-op
            }
        });
    }

    public void playEntityEffect(final Player player, final EntityEffect effect) {
        if (player != null && effect != null) {
            try {
                player.playEffect(effect);
            } catch (Exception e) {
                // no-op for cosmetic entity effects
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
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new java.util.ArrayList<>(group));
    }

    public boolean hasGroup(final String groupName) {
        return SOUND_GROUPS.containsKey(groupName.toLowerCase());
    }

    public Map<String, Set<String>> getSoundGroups() {
        final Map<String, Set<String>> copy = new HashMap<>();
        for (final Map.Entry<String, Set<String>> entry : SOUND_GROUPS.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(new HashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static class Optional<T> {
        private final T value;
        private Optional(T value) { this.value = value; }
        static <T> Optional<T> of(T value) { return new Optional<>(value); }
        static <T> Optional<T> empty() { return new Optional<>(null); }
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
