package com.arkflame.flameforge.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class ForgeVariant {
    private final String id;
    private final String name;
    private final List<String> lore;
    private final double weight;
    private final String icon;
    private final List<String> applicableGroups;
    private final List<EnchantSpec> enchantments;
    private final List<ForgeAttributeDefinition> attributes;
    private final List<ForgePowerDefinition> powers;

    public ForgeVariant(String id, String name, List<String> lore, double weight, String icon,
                        List<String> applicableGroups, List<EnchantSpec> enchantments,
                        List<ForgeAttributeDefinition> attributes, List<ForgePowerDefinition> powers) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.name = name != null ? name : "";
        this.lore = lore != null ? Collections.unmodifiableList(new java.util.ArrayList<>(lore)) : Collections.emptyList();
        this.weight = weight;
        this.icon = icon;
        this.applicableGroups = applicableGroups != null ? Collections.unmodifiableList(new java.util.ArrayList<>(applicableGroups)) : Collections.emptyList();
        this.enchantments = enchantments != null ? Collections.unmodifiableList(new java.util.ArrayList<>(enchantments)) : Collections.emptyList();
        this.attributes = attributes != null ? Collections.unmodifiableList(new java.util.ArrayList<>(attributes)) : Collections.emptyList();
        this.powers = powers != null ? Collections.unmodifiableList(new java.util.ArrayList<>(powers)) : Collections.emptyList();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public List<String> getLore() { return lore; }
    public double getWeight() { return weight; }
    public String getIcon() { return icon; }
    public List<String> getApplicableGroups() { return applicableGroups; }
    public List<EnchantSpec> getEnchantments() { return enchantments; }
    public List<ForgeAttributeDefinition> getAttributes() { return attributes; }
    public List<ForgePowerDefinition> getPowers() { return powers; }

    public List<String> getEnchantmentCandidates() {
        return enchantments.stream().map(EnchantSpec::getEnchantmentId).collect(Collectors.toList());
    }

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
               Objects.equals(applicableGroups, that.applicableGroups) &&
               Objects.equals(enchantments, that.enchantments) &&
               Objects.equals(attributes, that.attributes) &&
               Objects.equals(powers, that.powers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, lore, weight, icon, applicableGroups, enchantments, attributes, powers);
    }

    @Override
    public String toString() {
        return "ForgeVariant{id=" + id + ", name=" + name + ", lore=" + lore +
               ", weight=" + weight + ", icon=" + icon +
               ", applicableGroups=" + applicableGroups +
               ", enchantments=" + enchantments +
               ", attributes=" + attributes + ", powers=" + powers + "}";
    }
}
