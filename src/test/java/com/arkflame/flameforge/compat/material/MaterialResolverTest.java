package com.arkflame.flameforge.compat.material;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaterialResolverTest {

    @Test
    void materialResolutionAndItemCreationUseCompatibilityFallbacks() {
        MaterialResolver resolver = MaterialResolver.getInstance();
        resolver.clearCache();

        Material known = null;
        for (Material candidate : Material.values()) {
            if (resolver.isItem(new ItemStack(candidate))) {
                known = candidate;
                break;
            }
        }
        assertNotNull(known);

        assertEquals(known, resolver.resolveOrThrow(known.name()));
        assertEquals(known, resolver.resolveOrDefault("unknown-material", known));

        Optional<MaterialResolver.ResolvedMaterial> resolved =
            resolver.get("unknown-material", known.name());
        assertTrue(resolved.isPresent());
        assertEquals(known, resolved.get().getMaterial());

        Optional<ItemStack> item = resolver.item(2, "unknown-material", known.name());
        assertTrue(item.isPresent());
        assertEquals(known, item.get().getType());
        assertEquals(2, item.get().getAmount());
        assertFalse(resolver.resolve("unknown-material").isPresent());
    }
}
