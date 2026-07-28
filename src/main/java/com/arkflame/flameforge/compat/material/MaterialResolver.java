package com.arkflame.flameforge.compat.material;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class MaterialResolver {
    private static final MaterialResolver INSTANCE = new MaterialResolver();
    private static final Map<String, Material> ALIASES = new HashMap<>();
    private static final Map<String, Material> CACHE = new HashMap<>();

    static {
        ALIASES.put("diamond_sword", Material.DIAMOND_SWORD);
        ALIASES.put("iron_sword", Material.IRON_SWORD);
        ALIASES.put("gold_sword", Material.GOLD_SWORD);
        ALIASES.put("stone_sword", Material.STONE_SWORD);
        ALIASES.put("wood_sword", Material.WOOD_SWORD);
        ALIASES.put("wooden_sword", Material.WOOD_SWORD);

        ALIASES.put("diamond_pickaxe", Material.DIAMOND_PICKAXE);
        ALIASES.put("iron_pickaxe", Material.IRON_PICKAXE);
        ALIASES.put("gold_pickaxe", Material.GOLD_PICKAXE);
        ALIASES.put("stone_pickaxe", Material.STONE_PICKAXE);
        ALIASES.put("wood_pickaxe", Material.WOOD_PICKAXE);
        ALIASES.put("wooden_pickaxe", Material.WOOD_PICKAXE);

        ALIASES.put("diamond_helmet", Material.DIAMOND_HELMET);
        ALIASES.put("diamond_chestplate", Material.DIAMOND_CHESTPLATE);
        ALIASES.put("diamond_leggings", Material.DIAMOND_LEGGINGS);
        ALIASES.put("diamond_boots", Material.DIAMOND_BOOTS);
        ALIASES.put("iron_helmet", Material.IRON_HELMET);
        ALIASES.put("iron_chestplate", Material.IRON_CHESTPLATE);
        ALIASES.put("iron_leggings", Material.IRON_LEGGINGS);
        ALIASES.put("iron_boots", Material.IRON_BOOTS);
        ALIASES.put("gold_helmet", Material.GOLD_HELMET);
        ALIASES.put("gold_chestplate", Material.GOLD_CHESTPLATE);
        ALIASES.put("gold_leggings", Material.GOLD_LEGGINGS);
        ALIASES.put("gold_boots", Material.GOLD_BOOTS);
        ALIASES.put("chainmail_helmet", Material.CHAINMAIL_HELMET);
        ALIASES.put("chainmail_chestplate", Material.CHAINMAIL_CHESTPLATE);
        ALIASES.put("chainmail_leggings", Material.CHAINMAIL_LEGGINGS);
        ALIASES.put("chainmail_boots", Material.CHAINMAIL_BOOTS);
        ALIASES.put("leather_helmet", Material.LEATHER_HELMET);
        ALIASES.put("leather_chestplate", Material.LEATHER_CHESTPLATE);
        ALIASES.put("leather_leggings", Material.LEATHER_LEGGINGS);
        ALIASES.put("leather_boots", Material.LEATHER_BOOTS);

        ALIASES.put("bow", Material.BOW);
        ALIASES.put("arrow", Material.ARROW);
        ALIASES.put("fishing_rod", Material.FISHING_ROD);
        ALIASES.put("shears", Material.SHEARS);
        ALIASES.put("flint_and_steel", Material.FLINT_AND_STEEL);

        ALIASES.put("golden_apple", Material.GOLDEN_APPLE);

        ALIASES.put("beacon", Material.BEACON);
        ALIASES.put("anvil", Material.ANVIL);
        ALIASES.put("enchantment_table", Material.ENCHANTMENT_TABLE);
        ALIASES.put("ender_chest", Material.ENDER_CHEST);
    }

    private MaterialResolver() {
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
            return Optional.ofNullable(CACHE.get(normalized));
        }
        Material material = resolveMaterial(normalized);
        CACHE.put(normalized, material);
        return Optional.ofNullable(material);
    }

    private Material resolveMaterial(final String key) {
        if (ALIASES.containsKey(key)) {
            return ALIASES.get(key);
        }
        try {
            return Material.valueOf(key.toUpperCase());
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

    public Map<String, Material> getAliases() {
        return Collections.unmodifiableMap(new HashMap<>(ALIASES));
    }

    public void clearCache() {
        CACHE.clear();
    }
}
