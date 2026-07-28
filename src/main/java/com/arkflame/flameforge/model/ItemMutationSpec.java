package com.arkflame.flameforge.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ItemMutationSpec {
    private final String resultMaterial;
    private final String resultName;
    private final int amount;
    private final List<EnchantSpec> addEnchants;
    private final List<AttributeSpec> addAttributes;

    private ItemMutationSpec(String resultMaterial, String resultName, int amount,
                             List<EnchantSpec> addEnchants, List<AttributeSpec> addAttributes) {
        this.resultMaterial = resultMaterial;
        this.resultName = resultName;
        this.amount = amount;
        this.addEnchants = Collections.unmodifiableList(Objects.requireNonNull(addEnchants));
        this.addAttributes = Collections.unmodifiableList(Objects.requireNonNull(addAttributes));
    }

    public static ItemMutationSpec of(String resultMaterial, String resultName, int amount,
                                      List<EnchantSpec> addEnchants, List<AttributeSpec> addAttributes) {
        return new ItemMutationSpec(resultMaterial, resultName, amount,
                                    addEnchants != null ? addEnchants : Collections.emptyList(),
                                    addAttributes != null ? addAttributes : Collections.emptyList());
    }

    public static ItemMutationSpec simple(String resultMaterial, int amount) {
        return new ItemMutationSpec(resultMaterial, null, amount, Collections.emptyList(), Collections.emptyList());
    }

    public String getResultMaterial() { return resultMaterial; }
    public String getResultName() { return resultName; }
    public int getAmount() { return amount; }
    public List<EnchantSpec> getAddEnchants() { return addEnchants; }
    public List<AttributeSpec> getAddAttributes() { return addAttributes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemMutationSpec)) return false;
        ItemMutationSpec that = (ItemMutationSpec) o;
        return amount == that.amount &&
               Objects.equals(resultMaterial, that.resultMaterial) &&
               Objects.equals(resultName, that.resultName) &&
               Objects.equals(addEnchants, that.addEnchants) &&
               Objects.equals(addAttributes, that.addAttributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resultMaterial, resultName, amount, addEnchants, addAttributes);
    }

    @Override
    public String toString() {
        return "ItemMutationSpec{resultMaterial=" + resultMaterial + ", resultName=" + resultName +
               ", amount=" + amount + ", addEnchants=" + addEnchants + ", addAttributes=" + addAttributes + "}";
    }
}
