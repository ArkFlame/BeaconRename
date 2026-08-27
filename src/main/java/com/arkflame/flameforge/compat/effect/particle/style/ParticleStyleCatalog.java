package com.arkflame.flameforge.compat.effect.particle.style;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class ParticleStyleCatalog {
    private static final Map<ParticleStyleId, ParticleStyle> STYLES = createStyles();

    private ParticleStyleCatalog() {
    }

    public static ParticleStyle get(ParticleStyleId id) {
        if (id == null) {
            throw new IllegalArgumentException("Style id is required");
        }
        ParticleStyle style = STYLES.get(id);
        if (style == null) {
            throw new IllegalArgumentException("Unknown particle style " + id);
        }
        return style;
    }

    public static ParticleStyle of(ParticleStyleId id) {
        return get(id);
    }

    public static List<ParticleStyle> all() {
        return Collections.unmodifiableList(Arrays.asList(
            get(ParticleStyleId.BREAK), get(ParticleStyleId.CURSE),
            get(ParticleStyleId.ELECTRIC), get(ParticleStyleId.ELECTRIC_NETWORK),
            get(ParticleStyleId.EXPLOSIVE), get(ParticleStyleId.CONTAGION),
            get(ParticleStyleId.CONTAGION_NETWORK), get(ParticleStyleId.POISON),
            get(ParticleStyleId.WITHER), get(ParticleStyleId.BLEED),
            get(ParticleStyleId.FIRE), get(ParticleStyleId.SWIFT),
            get(ParticleStyleId.HEAL), get(ParticleStyleId.DEFENSIVE),
            get(ParticleStyleId.HASTE), get(ParticleStyleId.SUCCESS),
            get(ParticleStyleId.GENERIC_MAGIC)));
    }

    public static Map<ParticleStyleId, ParticleStyle> styles() {
        return STYLES;
    }

    private static Map<ParticleStyleId, ParticleStyle> createStyles() {
        EnumMap<ParticleStyleId, ParticleStyle> styles =
            new EnumMap<ParticleStyleId, ParticleStyle>(ParticleStyleId.class);
        styles.put(ParticleStyleId.BREAK, style(ParticleStyleId.BREAK, 239, 68, 68,
            "SMOKE", "LARGE_SMOKE", "CRIT"));
        styles.put(ParticleStyleId.CURSE, style(ParticleStyleId.CURSE, 168, 85, 247,
            "PORTAL", "SPELL", "WITCH"));
        styles.put(ParticleStyleId.ELECTRIC, style(ParticleStyleId.ELECTRIC, 250, 204, 21,
            "ELECTRIC_SPARK", "END_ROD", "ENCHANT", "NOTE", "CRIT"));
        styles.put(ParticleStyleId.ELECTRIC_NETWORK, style(ParticleStyleId.ELECTRIC_NETWORK,
            250, 204, 21, "ELECTRIC_SPARK", "END_ROD", "ENCHANT", "NOTE", "CRIT"));
        styles.put(ParticleStyleId.EXPLOSIVE, style(ParticleStyleId.EXPLOSIVE, 249, 115, 22,
            "EXPLOSION", "EXPLOSION_NORMAL", "EXPLOSION_LARGE", "EXPLOSION_HUGE"));
        styles.put(ParticleStyleId.CONTAGION, style(ParticleStyleId.CONTAGION, 132, 204, 22,
            "SPELL", "HAPPY_VILLAGER", "VILLAGER_HAPPY", "CRIT"));
        styles.put(ParticleStyleId.CONTAGION_NETWORK, style(ParticleStyleId.CONTAGION_NETWORK,
            34, 197, 94, "SPELL", "HAPPY_VILLAGER", "VILLAGER_HAPPY", "CRIT"));
        styles.put(ParticleStyleId.POISON, style(ParticleStyleId.POISON, 34, 197, 94,
            "SPELL", "WITCH", "HAPPY_VILLAGER", "VILLAGER_HAPPY", "CRIT"));
        styles.put(ParticleStyleId.WITHER, style(ParticleStyleId.WITHER, 107, 33, 168,
            "WITCH", "LARGE_SMOKE", "SMOKE_LARGE", "SMOKE"));
        styles.put(ParticleStyleId.BLEED, style(ParticleStyleId.BLEED, 220, 38, 38,
            "CRIT", "REDSTONE", "DUST"));
        styles.put(ParticleStyleId.FIRE, style(ParticleStyleId.FIRE, 239, 68, 68,
            "FLAME", "LAVA", "FIREWORK"));
        styles.put(ParticleStyleId.SWIFT, style(ParticleStyleId.SWIFT, 56, 189, 248,
            "CLOUD", "INSTANT_EFFECT", "SPELL"));
        styles.put(ParticleStyleId.HEAL, style(ParticleStyleId.HEAL, 244, 114, 182,
            "HEART", "VILLAGER_HAPPY", "HAPPY_VILLAGER"));
        styles.put(ParticleStyleId.DEFENSIVE, style(ParticleStyleId.DEFENSIVE, 96, 165, 250,
            "ENCHANT", "SPELL", "EFFECT"));
        styles.put(ParticleStyleId.HASTE, style(ParticleStyleId.HASTE, 250, 204, 21,
            "CLOUD", "INSTANT_EFFECT", "ENCHANT"));
        styles.put(ParticleStyleId.SUCCESS, style(ParticleStyleId.SUCCESS, 245, 158, 11,
            "FLAME", "FIREWORK", "ENCHANT"));
        styles.put(ParticleStyleId.GENERIC_MAGIC, style(ParticleStyleId.GENERIC_MAGIC,
            168, 85, 247, "SPELL", "ENCHANT", "WITCH", "PORTAL"));
        return Collections.unmodifiableMap(styles);
    }

    private static ParticleStyle style(ParticleStyleId id, int red, int green, int blue,
                                       String... candidates) {
        return new ParticleStyle(id, red, green, blue, Arrays.asList(candidates));
    }
}
