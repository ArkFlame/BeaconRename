package com.arkflame.flameforge.compat.material;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class MaterialResolver {
    private static final int MAX_CACHE_ENTRIES = 256;

    interface MaterialLookup {
        Material find(String normalizedName);
    }

    private static final MaterialResolver INSTANCE = new MaterialResolver((name) -> {
        try {
            return Material.getMaterial(name);
        } catch (Exception e) {
            return null;
        }
    });

    private static final Map<String, String[]> ALIASES = new HashMap<>();
    private static final Map<String, ResolvedMaterial> CACHE = new HashMap<>(MAX_CACHE_ENTRIES);
    private static final Set<String> LOGGED_MISSING = new HashSet<>();

    static {
        ALIASES.put("diamond_sword", new String[]{"DIAMOND_SWORD"});
        ALIASES.put("iron_sword", new String[]{"IRON_SWORD"});
        ALIASES.put("gold_sword", new String[]{"GOLD_SWORD"});
        ALIASES.put("stone_sword", new String[]{"STONE_SWORD"});
        ALIASES.put("wood_sword", new String[]{"WOOD_SWORD"});
        ALIASES.put("wooden_sword", new String[]{"WOOD_SWORD"});

        ALIASES.put("diamond_pickaxe", new String[]{"DIAMOND_PICKAXE"});
        ALIASES.put("iron_pickaxe", new String[]{"IRON_PICKAXE"});
        ALIASES.put("gold_pickaxe", new String[]{"GOLD_PICKAXE"});
        ALIASES.put("stone_pickaxe", new String[]{"STONE_PICKAXE"});
        ALIASES.put("wood_pickaxe", new String[]{"WOOD_PICKAXE"});
        ALIASES.put("wooden_pickaxe", new String[]{"WOOD_PICKAXE"});

        ALIASES.put("diamond_helmet", new String[]{"DIAMOND_HELMET"});
        ALIASES.put("diamond_chestplate", new String[]{"DIAMOND_CHESTPLATE"});
        ALIASES.put("diamond_leggings", new String[]{"DIAMOND_LEGGINGS"});
        ALIASES.put("diamond_boots", new String[]{"DIAMOND_BOOTS"});
        ALIASES.put("iron_helmet", new String[]{"IRON_HELMET"});
        ALIASES.put("iron_chestplate", new String[]{"IRON_CHESTPLATE"});
        ALIASES.put("iron_leggings", new String[]{"IRON_LEGGINGS"});
        ALIASES.put("iron_boots", new String[]{"IRON_BOOTS"});
        ALIASES.put("gold_helmet", new String[]{"GOLD_HELMET"});
        ALIASES.put("gold_chestplate", new String[]{"GOLD_CHESTPLATE"});
        ALIASES.put("gold_leggings", new String[]{"GOLD_LEGGINGS"});
        ALIASES.put("gold_boots", new String[]{"GOLD_BOOTS"});
        ALIASES.put("chainmail_helmet", new String[]{"CHAINMAIL_HELMET"});
        ALIASES.put("chainmail_chestplate", new String[]{"CHAINMAIL_CHESTPLATE"});
        ALIASES.put("chainmail_leggings", new String[]{"CHAINMAIL_LEGGINGS"});
        ALIASES.put("chainmail_boots", new String[]{"CHAINMAIL_BOOTS"});
        ALIASES.put("leather_helmet", new String[]{"LEATHER_HELMET"});
        ALIASES.put("leather_chestplate", new String[]{"LEATHER_CHESTPLATE"});
        ALIASES.put("leather_leggings", new String[]{"LEATHER_LEGGINGS"});
        ALIASES.put("leather_boots", new String[]{"LEATHER_BOOTS"});

        ALIASES.put("bow", new String[]{"BOW"});
        ALIASES.put("arrow", new String[]{"ARROW"});
        ALIASES.put("fishing_rod", new String[]{"FISHING_ROD"});
        ALIASES.put("shears", new String[]{"SHEARS"});
        ALIASES.put("flint_and_steel", new String[]{"FLINT_AND_STEEL"});

        ALIASES.put("golden_apple", new String[]{"GOLDEN_APPLE"});

        ALIASES.put("beacon", new String[]{"BEACON"});
        ALIASES.put("anvil", new String[]{"ANVIL"});
        ALIASES.put("enchantment_table", new String[]{"ENCHANTING_TABLE", "ENCHANTMENT_TABLE"});
        ALIASES.put("ender_chest", new String[]{"ENDER_CHEST"});

        ALIASES.put("golden_sword", new String[]{"GOLDEN_SWORD", "GOLD_SWORD"});
        ALIASES.put("black_stained_glass_pane", new String[]{"BLACK_STAINED_GLASS_PANE", "STAINED_GLASS_PANE:15"});
        ALIASES.put("cyan_stained_glass_pane", new String[]{"CYAN_STAINED_GLASS_PANE", "STAINED_GLASS_PANE:9"});
        ALIASES.put("magenta_stained_glass_pane", new String[]{"MAGENTA_STAINED_GLASS_PANE", "STAINED_GLASS_PANE:2"});
        ALIASES.put("gray_stained_glass_pane", new String[]{"GRAY_STAINED_GLASS_PANE", "STAINED_GLASS_PANE:7"});
    }

    private final MaterialLookup materialLookup;

    MaterialResolver(MaterialLookup materialLookup) {
        this.materialLookup = materialLookup;
    }

    private MaterialResolver() {
        this.materialLookup = (name) -> {
            try {
                return Material.valueOf(name);
            } catch (IllegalArgumentException e) {
                return null;
            }
        };
    }

    public static MaterialResolver getInstance() {
        return INSTANCE;
    }

    public Optional<Material> resolve(final String key) {
        if (key == null || key.isEmpty()) {
            return Optional.empty();
        }
        final String normalized = key.toLowerCase().replace(" ", "_");
        if (CACHE.containsKey(normalized)) {
            ResolvedMaterial rm = CACHE.get(normalized);
            return Optional.ofNullable(rm != null ? rm.getMaterial() : null);
        }
        Material material = resolveMaterial(normalized);
        if (material != null) {
            putInCache(normalized, new ResolvedMaterial(material, (short) 0, false));
        } else {
            putInCache(normalized, null);
        }
        return Optional.ofNullable(material);
    }

    private void putInCache(String key, ResolvedMaterial value) {
        if (CACHE.size() >= MAX_CACHE_ENTRIES && !CACHE.containsKey(key)) {
            evictOneEntry();
        }
        CACHE.put(key, value);
    }

    private void evictOneEntry() {
        for (Map.Entry<String, ResolvedMaterial> entry : CACHE.entrySet()) {
            if (entry.getValue() != null) {
                CACHE.remove(entry.getKey());
                break;
            }
        }
    }

    private Material resolveMaterial(final String key) {
        String[] candidates = ALIASES.get(key);
        if (candidates != null) {
            for (String candidate : candidates) {
                Material mat = tryParseMaterial(candidate);
                if (mat != null) {
                    return mat;
                }
            }
            return null;
        }
        return tryParseMaterial(key);
    }

    private Material tryParseMaterial(String name) {
        if (name.contains(":")) {
            String[] parts = name.split(":", 2);
            try {
                Material mat = Material.valueOf(parts[0]);
                return mat;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public Material resolveOrThrow(final String key) {
        return resolve(key).orElseThrow(() -> new IllegalArgumentException("Unknown material: " + key));
    }

    public Material resolveOrDefault(final String key, final Material fallback) {
        return resolve(key).orElse(fallback);
    }

    public Material resolveOrThrow(final String key, final String errorMessage) {
        return resolve(key).orElseThrow(() -> new IllegalArgumentException(errorMessage));
    }

    public boolean isValid(final String key) {
        return resolve(key).isPresent();
    }

    public boolean isItem(final ItemStack item) {
        return item != null && item.getType() != Material.AIR;
    }

    public Optional<ItemStack> makeItem(final String materialKey, final int amount) {
        return resolve(materialKey).map(material -> new ItemStack(material, amount));
    }

    public ItemStack makeItemOrThrow(final String materialKey, final int amount) {
        return makeItem(materialKey, amount)
                .orElseThrow(() -> new IllegalArgumentException("Unknown material: " + materialKey));
    }

    public Optional<ResolvedMaterial> get(String... candidates) {
        if (candidates == null || candidates.length == 0) {
            return Optional.empty();
        }
        String cacheKey = buildCacheKey(candidates);
        if (CACHE.containsKey(cacheKey)) {
            return Optional.ofNullable(CACHE.get(cacheKey));
        }

        for (String candidate : candidates) {
            if (candidate == null || candidate.isEmpty() || candidate.trim().isEmpty()) {
                continue;
            }
            Optional<ResolvedMaterial> result = parseAndResolve(candidate);
            if (result.isPresent()) {
                putInCache(cacheKey, result.get());
                return result;
            }
        }
        putInCache(cacheKey, null);
        return Optional.empty();
    }

    public ResolvedMaterial getOrThrow(String... candidates) {
        return get(candidates).orElseThrow(() -> new IllegalArgumentException("No valid material found for candidates: " + Arrays.toString(candidates)));
    }

    public Optional<ItemStack> item(int amount, String... candidates) {
        return get(candidates).map(rm -> rm.toItemStack(amount));
    }

    public ItemStack itemOrThrow(int amount, String... candidates) {
        return get(candidates).map(rm -> rm.toItemStack(amount))
                .orElseThrow(() -> new IllegalArgumentException("No valid material found for candidates: " + Arrays.toString(candidates)));
    }

    private Optional<ResolvedMaterial> parseAndResolve(String candidate) {
        String normalized = normalize(candidate);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        short legacyData = 0;
        String materialName = normalized;

        int colonIdx = normalized.lastIndexOf(':');
        if (colonIdx > 0) {
            String dataStr = normalized.substring(colonIdx + 1);
            materialName = normalized.substring(0, colonIdx);
            try {
                legacyData = Short.parseShort(dataStr);
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }

        Material material = materialLookup.find(materialName);
        if (material == null) {
            return Optional.empty();
        }

        boolean applyLegacy = (colonIdx > 0) && (legacyData != 0);
        return Optional.of(new ResolvedMaterial(material, legacyData, applyLegacy));
    }

    private String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
    }

    private String buildCacheKey(String... candidates) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(candidates[i] != null ? candidates[i] : "");
        }
        return sb.toString();
    }

    public Map<String, String[]> getAliases() {
        return new HashMap<>(ALIASES);
    }

    public void clearCache() {
        CACHE.clear();
    }

    public static class ResolvedMaterial {
        private final Material material;
        private final short legacyData;
        private final boolean applyLegacy;

        public ResolvedMaterial(Material material, short legacyData, boolean applyLegacy) {
            this.material = material;
            this.legacyData = legacyData;
            this.applyLegacy = applyLegacy;
        }

        public Material getMaterial() {
            return material;
        }

        public short getLegacyData() {
            return legacyData;
        }

        public boolean isApplyLegacy() {
            return applyLegacy;
        }

        public ItemStack toItemStack(int amount) {
            if (material == null) {
                throw new IllegalStateException("Material is null");
            }
            ItemStack stack = new ItemStack(material, amount);
            if (applyLegacy && legacyData != 0) {
                try {
                    stack.setDurability(legacyData);
                } catch (Exception e) {
                }
            }
            return stack;
        }
    }
}
