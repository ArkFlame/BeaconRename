package com.arkflame.flameforge.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class CurseDefinition {
    private final String name;
    private final List<String> lore;
    private final List<String> enchantments;

    public CurseDefinition(String name, List<String> lore, List<String> enchantments) {
        this.name = name != null ? name : "";
        this.lore = lore != null ? Collections.unmodifiableList(new java.util.ArrayList<>(lore)) : Collections.emptyList();
        this.enchantments = enchantments != null ? Collections.unmodifiableList(new java.util.ArrayList<>(enchantments)) : Collections.emptyList();
    }

    public String getName() { return name; }
    public List<String> getLore() { return lore; }
    public List<String> getEnchantments() { return enchantments; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CurseDefinition)) return false;
        CurseDefinition that = (CurseDefinition) o;
        return Objects.equals(name, that.name) &&
               Objects.equals(lore, that.lore) &&
               Objects.equals(enchantments, that.enchantments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, lore, enchantments);
    }

    @Override
    public String toString() {
        return "CurseDefinition{name=" + name + ", lore=" + lore + ", enchantments=" + enchantments + "}";
    }
}
