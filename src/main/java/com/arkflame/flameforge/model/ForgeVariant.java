package com.arkflame.flameforge.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ForgeVariant {
    private final String id;
    private final String name;
    private final List<String> lore;
    private final double weight;
    private final String icon;
    private final List<String> enchantmentCandidates;
    private final Map<String, Integer> attributeModifiers;
    private final List<ForgePowerDefinition> powers;

    public ForgeVariant(String id, String name, List<String> lore, double weight, String icon,
                        List<String> enchantmentCandidates, Map<String, Integer> attributeModifiers,
                        List<ForgePowerDefinition> powers) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = name != null ? name : "";
        this.lore = lore != null ? Collections.unmodifiableList(new java.util.ArrayList<>(lore)) : Collections.emptyList();
        this.weight = weight;
        this.icon = icon;
        this.enchantmentCandidates = enchantmentCandidates != null ? Collections.unmodifiableList(new java.util.ArrayList<>(enchantmentCandidates)) : Collections.emptyList();
        this.attributeModifiers = attributeModifiers != null ? Collections.unmodifiableMap(new java.util.HashMap<>(attributeModifiers)) : Collections.emptyMap();
        this.powers = powers != null ? Collections.unmodifiableList(new java.util.ArrayList<>(powers)) : Collections.emptyList();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<String> getLore() { return lore; }
    public double getWeight() { return weight; }
    public String getIcon() { return icon; }
    public List<String> getEnchantmentCandidates() { return enchantmentCandidates; }
    public Map<String, Integer> getAttributeModifiers() { return attributeModifiers; }
    public List<ForgePowerDefinition> getPowers() { return powers; }
    public List<String> getPowerIds() {
        return powers.stream().map(ForgePowerDefinition::getId).collect(Collectors.toList());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ForgeVariant)) return false;
        ForgeVariant that = (ForgeVariant) o;
        return Double.compare(that.weight, weight) == 0 &&
               Objects.equals(id, that.id) &&
               Objects.equals(name, that.name) &&
               Objects.equals(lore, that.lore) &&
               Objects.equals(icon, that.icon) &&
               Objects.equals(enchantmentCandidates, that.enchantmentCandidates) &&
               Objects.equals(attributeModifiers, that.attributeModifiers) &&
               Objects.equals(powers, that.powers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, lore, weight, icon, enchantmentCandidates, attributeModifiers, powers);
    }

    @Override
    public String toString() {
        return "ForgeVariant{id=" + id + ", name=" + name + ", lore=" + lore +
               ", weight=" + weight + ", icon=" + icon +
               ", enchantmentCandidates=" + enchantmentCandidates +
               ", attributeModifiers=" + attributeModifiers + ", powers=" + powers + "}";
    }
}
