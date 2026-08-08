package com.arkflame.flameforge.item;

import com.arkflame.flameforge.model.AttributeSpec;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class AttributeBridge {
    private static final AttributeBridge INSTANCE = new AttributeBridge();
    private static final UUID FALLBACK_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final String MODIFIER_PREFIX = "flameforge:";

    private volatile Boolean modernAttributesAvailable;
    private volatile Boolean modernUnbreakableAvailable;
    private volatile Boolean modernCustomModelDataAvailable;

    private Method addAttributeModifierMethod;
    private Method getAttributeModifiersMethod;
    private Method removeAttributeModifierMethod;
    private Method setUnbreakableMethod;
    private Method isUnbreakableMethod;
    private Method setCustomModelDataMethod;
    private Method hasCustomModelDataMethod;
    private Method getCustomModelDataMethod;
    private Method attributeModifierGetNameMethod;
    private Method attributeModifierGetUniqueIdMethod;
    private Method attributeModifierGetAmountMethod;
    private Method attributeModifierGetOperationMethod;
    private Class<?> attributeClass;
    private Class<?> attributeModifierClass;
    private Class<?> attributeOperationClass;
    private Object addNumberOperation;

    private AttributeBridge() {
        initReflection();
    }

    private void initReflection() {
        try {
            attributeClass = Class.forName("org.bukkit.attribute.Attribute");
            attributeModifierClass = Class.forName("org.bukkit.attribute.AttributeModifier");
            attributeOperationClass = Class.forName("org.bukkit.attribute.AttributeModifier$Operation");
            addAttributeModifierMethod = ItemMeta.class.getMethod("addAttributeModifier", attributeClass, attributeModifierClass);
            getAttributeModifiersMethod = ItemMeta.class.getMethod("getAttributeModifiers", attributeClass);
            removeAttributeModifierMethod = ItemMeta.class.getMethod("removeAttributeModifier", attributeClass, attributeModifierClass);
            setUnbreakableMethod = ItemMeta.class.getMethod("setUnbreakable", boolean.class);
            isUnbreakableMethod = ItemMeta.class.getMethod("isUnbreakable");
            setCustomModelDataMethod = ItemMeta.class.getMethod("setCustomModelData", Integer.class);
            hasCustomModelDataMethod = ItemMeta.class.getMethod("hasCustomModelData");
            getCustomModelDataMethod = ItemMeta.class.getMethod("getCustomModelData");
            attributeModifierGetNameMethod = attributeModifierClass.getMethod("getName");
            attributeModifierGetUniqueIdMethod = attributeModifierClass.getMethod("getUniqueId");
            attributeModifierGetAmountMethod = attributeModifierClass.getMethod("getAmount");
            attributeModifierGetOperationMethod = attributeModifierClass.getMethod("getOperation");
            addNumberOperation = attributeOperationClass.getField("ADD_NUMBER").get(null);
            modernAttributesAvailable = true;
            modernUnbreakableAvailable = true;
            modernCustomModelDataAvailable = true;
        } catch (Exception e) {
            modernAttributesAvailable = false;
            modernUnbreakableAvailable = false;
            modernCustomModelDataAvailable = false;
        }
    }

    public static AttributeBridge getInstance() {
        return INSTANCE;
    }

    public static AttributeBridge.Result apply(final ItemStack item, final List<AttributeSpec> specs) {
        return getInstance().applyAttributes(item, specs);
    }

    public Result applyAttributes(final ItemStack item, final List<AttributeSpec> specs) {
        if (item == null || specs == null || specs.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        if (!Boolean.TRUE.equals(modernAttributesAvailable)) {
            return Result.partial(Collections.emptyList(), specs);
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Result.fail("item has no meta");
        }
        final List<AttributeSpec> applied = new ArrayList<>();
        final List<AttributeSpec> excluded = new ArrayList<>();
        for (final AttributeSpec spec : specs) {
            final Optional<?> attrOpt = resolveAttribute(spec.getAttribute());
            if (!attrOpt.isPresent()) {
                excluded.add(spec);
                continue;
            }
            final Object bukkitAttr = attrOpt.get();
            final Optional<?> modifierOpt = createModifier(spec, bukkitAttr);
            if (!modifierOpt.isPresent()) {
                excluded.add(spec);
                continue;
            }
            try {
                addAttributeModifierMethod.invoke(meta, bukkitAttr, modifierOpt.get());
                applied.add(spec);
            } catch (Exception e) {
                excluded.add(spec);
            }
        }
        item.setItemMeta(meta);
        if (!excluded.isEmpty()) {
            return Result.partial(applied, excluded);
        }
        return Result.success(applied);
    }

    public Optional<?> resolveAttribute(final String key) {
        if (key == null || key.isEmpty()) {
            return Optional.empty();
        }
        if (attributeClass == null) {
            return Optional.empty();
        }
        final String normalized = key.toLowerCase().replace(" ", "_").replace("-", "_");
        try {
            final Method valueOfMethod = attributeClass.getMethod("valueOf", String.class);
            final Object attr = valueOfMethod.invoke(null, normalized.toUpperCase());
            return Optional.of(attr);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<?> createModifier(final AttributeSpec spec, final Object attribute) {
        if (spec == null || attribute == null) {
            return Optional.empty();
        }
        if (!Boolean.TRUE.equals(modernAttributesAvailable)) {
            return Optional.empty();
        }
        final double value = spec.getMinValue();
        final UUID uuid = generateUuidForAttribute(spec.getAttribute());
        final String name = MODIFIER_PREFIX + spec.getAttribute();
        try {
            return Optional.of(attributeModifierClass.getConstructor(UUID.class, String.class, double.class, attributeOperationClass)
                    .newInstance(uuid, name, value, addNumberOperation));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private UUID generateUuidForAttribute(final String attributeKey) {
        if (attributeKey == null) {
            return FALLBACK_UUID;
        }
        try {
            return UUID.nameUUIDFromBytes(("flameforge." + attributeKey).getBytes());
        } catch (Exception e) {
            return FALLBACK_UUID;
        }
    }

    public List<AttributeSpec> extractFromMeta(final ItemMeta meta) {
        if (meta == null || !Boolean.TRUE.equals(modernAttributesAvailable)) {
            return Collections.emptyList();
        }
        final List<AttributeSpec> specs = new ArrayList<>();
        try {
            final Object[] attributeEnums = (Object[]) attributeClass.getMethod("values").invoke(null);
            for (final Object attribute : attributeEnums) {
                final Method nameMethod = attributeClass.getMethod("name");
                final String attrName = (String) nameMethod.invoke(attribute);
                final Object modifiers = getAttributeModifiersMethod.invoke(meta, attribute);
                if (modifiers != null) {
                    final Iterable<?> modifierList = (Iterable<?>) modifiers;
                    for (final Object mod : modifierList) {
                        final String modName = (String) attributeModifierGetNameMethod.invoke(mod);
                        if (!modName.startsWith(MODIFIER_PREFIX)) {
                            continue;
                        }
                        final double amount = (double) attributeModifierGetAmountMethod.invoke(mod);
                        final Object op = attributeModifierGetOperationMethod.invoke(mod);
                        specs.add(AttributeSpec.of(
                                attrName,
                                amount,
                                amount,
                                op.toString()
                        ));
                    }
                }
            }
        } catch (Exception e) {
        }
        return specs;
    }

    public boolean isModernAttributesAvailable() {
        return Boolean.TRUE.equals(modernAttributesAvailable);
    }

    public void removeFlameForgeAttributes(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return;
        }
        if (!Boolean.TRUE.equals(modernAttributesAvailable)) {
            return;
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        try {
            final Object[] attributeEnums = (Object[]) attributeClass.getMethod("values").invoke(null);
            for (final Object attribute : attributeEnums) {
                final Method nameMethod = attributeClass.getMethod("name");
                final String attrName = (String) nameMethod.invoke(attribute);
                final Object modifiers = getAttributeModifiersMethod.invoke(meta, attribute);
                if (modifiers != null) {
                    final List<Object> toRemove = new ArrayList<>();
                    final Iterable<?> modifierList = (Iterable<?>) modifiers;
                    for (final Object mod : modifierList) {
                        final String modName = (String) attributeModifierGetNameMethod.invoke(mod);
                        if (modName.startsWith(MODIFIER_PREFIX)) {
                            toRemove.add(mod);
                        }
                    }
                    for (final Object mod : toRemove) {
                        removeAttributeModifierMethod.invoke(meta, attribute, mod);
                    }
                }
            }
            item.setItemMeta(meta);
        } catch (Exception e) {
        }
    }

    public Optional<ItemStack> setUnbreakable(final ItemStack item, final boolean unbreakable) {
        if (item == null) {
            return Optional.empty();
        }
        if (!Boolean.TRUE.equals(modernUnbreakableAvailable)) {
            return Optional.empty();
        }
        final ItemStack clone = item.clone();
        final ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        try {
            setUnbreakableMethod.invoke(meta, unbreakable);
            clone.setItemMeta(meta);
            return Optional.of(clone);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<Boolean> isUnbreakable(final ItemStack item) {
        if (item == null || !item.hasItemMeta() || !Boolean.TRUE.equals(modernUnbreakableAvailable)) {
            return Optional.empty();
        }
        try {
            return Optional.of((Boolean) isUnbreakableMethod.invoke(item.getItemMeta()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<ItemStack> setCustomModelData(final ItemStack item, final Integer data) {
        if (item == null) {
            return Optional.empty();
        }
        if (!Boolean.TRUE.equals(modernCustomModelDataAvailable)) {
            return Optional.empty();
        }
        final ItemStack clone = item.clone();
        final ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        try {
            setCustomModelDataMethod.invoke(meta, data);
            clone.setItemMeta(meta);
            return Optional.of(clone);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<Integer> getCustomModelData(final ItemStack item) {
        if (item == null || !item.hasItemMeta() || !Boolean.TRUE.equals(modernCustomModelDataAvailable)) {
            return Optional.empty();
        }
        try {
            final ItemMeta meta = item.getItemMeta();
            final boolean has = (Boolean) hasCustomModelDataMethod.invoke(meta);
            if (has) {
                return Optional.of((Integer) getCustomModelDataMethod.invoke(meta));
            }
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Boolean isModernCustomModelDataAvailable() {
        return modernCustomModelDataAvailable;
    }

    public static final class Result {
        private final List<AttributeSpec> applied;
        private final List<AttributeSpec> excluded;
        private final boolean success;

        private Result(final List<AttributeSpec> applied, final List<AttributeSpec> excluded, final boolean success) {
            this.applied = Collections.unmodifiableList(applied);
            this.excluded = Collections.unmodifiableList(excluded);
            this.success = success;
        }

        public static Result success(final List<AttributeSpec> applied) {
            return new Result(applied, Collections.emptyList(), true);
        }

        public static Result partial(final List<AttributeSpec> applied, final List<AttributeSpec> excluded) {
            return new Result(applied, excluded, false);
        }

        public static Result fail(final String reason) {
            return new Result(Collections.emptyList(), Collections.emptyList(), false);
        }

        public List<AttributeSpec> getApplied() { return applied; }
        public List<AttributeSpec> getExcluded() { return excluded; }
        public boolean isSuccess() { return success; }
        public boolean hasExclusions() { return !excluded.isEmpty(); }
    }
}
