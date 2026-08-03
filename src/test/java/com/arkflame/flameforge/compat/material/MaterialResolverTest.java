package com.arkflame.flameforge.compat.material;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MaterialResolverTest {

    private MaterialResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = MaterialResolver.getInstance();
        resolver.clearCache();
    }

    @Test
    void allDeclaredAliasesResolveToFirstAvailableCandidate() {
        Map<String, String[]> aliases = resolver.getAliases();

        for (Map.Entry<String, String[]> entry : aliases.entrySet()) {
            String alias = entry.getKey();
            String[] candidates = entry.getValue();
            assertNotNull(candidates);
            assertTrue(candidates.length > 0, "Alias '" + alias + "' should have at least one candidate");

            Optional<Material> result = resolver.resolve(alias);
            assertTrue(result.isPresent(), "Alias '" + alias + "' should resolve to a material");
        }

        String[] candidates = {"FAKE_MATERIAL_XYZ", "DIAMOND_SWORD"};
        Optional<MaterialResolver.ResolvedMaterial> resolved = resolver.get(candidates);
        assertTrue(resolved.isPresent(), "First unavailable candidate should fall through to DIAMOND_SWORD");
        assertEquals(Material.DIAMOND_SWORD, resolved.get().getMaterial());

        String[] reversed = {"DIAMOND_SWORD", "FAKE_MATERIAL_XYZ"};
        Optional<MaterialResolver.ResolvedMaterial> resolvedReverse = resolver.get(reversed);
        assertTrue(resolvedReverse.isPresent());
        assertEquals(Material.DIAMOND_SWORD, resolvedReverse.get().getMaterial());
    }

    @Test
    void directLegacyDataCaseAndWhitespaceInputsFollowCompatibilityContract() {
        Optional<Material> lowerUnderscore = resolver.resolve("diamond_sword");
        Optional<Material> upperUnderscore = resolver.resolve("DIAMOND_SWORD");
        Optional<Material> mixedUnderscore = resolver.resolve("Diamond_Sword");
        Optional<Material> spaceNoUnderscore = resolver.resolve("diamond sword");

        assertTrue(lowerUnderscore.isPresent());
        assertTrue(upperUnderscore.isPresent());
        assertTrue(mixedUnderscore.isPresent());
        assertTrue(spaceNoUnderscore.isPresent());

        assertEquals(lowerUnderscore.get(), upperUnderscore.get());
        assertEquals(upperUnderscore.get(), mixedUnderscore.get());
        assertEquals(mixedUnderscore.get(), spaceNoUnderscore.get());

        Optional<MaterialResolver.ResolvedMaterial> legacyWithData = resolver.get("STAINED_GLASS_PANE:15");
        assertTrue(legacyWithData.isPresent(), "Legacy data candidate with :data should resolve");
        assertEquals(Material.STAINED_GLASS_PANE, legacyWithData.get().getMaterial());
        assertTrue(legacyWithData.get().isApplyLegacy());

        Optional<MaterialResolver.ResolvedMaterial> modernAndLegacy = resolver.get("GOLDEN_APPLE", "GOLD_APPLE");
        assertTrue(modernAndLegacy.isPresent(), "First available candidate should win when both available");
        assertEquals(Material.GOLDEN_APPLE, modernAndLegacy.get().getMaterial());
        assertFalse(modernAndLegacy.get().isApplyLegacy());

        Optional<MaterialResolver.ResolvedMaterial> onlyLegacy = resolver.get("STAINED_GLASS_PANE:9");
        assertTrue(onlyLegacy.isPresent(), "Legacy-only candidate should still resolve");
        assertEquals(Material.STAINED_GLASS_PANE, onlyLegacy.get().getMaterial());
        assertTrue(onlyLegacy.get().isApplyLegacy());
    }

    @Test
    void unknownNullAndEmptyInputsFollowOptionalStrictAndFallbackContracts() {
        Optional<Material> nullResult = resolver.resolve(null);
        assertFalse(nullResult.isPresent(), "null input should return empty");

        Optional<Material> emptyResult = resolver.resolve("");
        assertFalse(emptyResult.isPresent(), "empty input should return empty");

        Optional<Material> unknownResult = resolver.resolve("not_a_real_material_xyz_123");
        assertFalse(unknownResult.isPresent(), "unknown input should return empty");

        Material fallback = Material.STONE;
        Material nullFallback = resolver.resolveOrDefault(null, fallback);
        assertEquals(fallback, nullFallback, "null should use fallback");

        Material emptyFallback = resolver.resolveOrDefault("", fallback);
        assertEquals(fallback, emptyFallback, "empty should use fallback");

        assertThrows(IllegalArgumentException.class, () -> {
            resolver.resolveOrThrow(null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            resolver.resolveOrThrow("");
        });

        Optional<MaterialResolver.ResolvedMaterial> nullCandidatesGet = resolver.get((String[]) null);
        assertFalse(nullCandidatesGet.isPresent(), "null candidates should return empty");

        Optional<MaterialResolver.ResolvedMaterial> emptyCandidatesGet = resolver.get();
        assertFalse(emptyCandidatesGet.isPresent(), "empty candidates should return empty");

        Optional<MaterialResolver.ResolvedMaterial> unknownCandidatesGet = resolver.get("FAKE_MAT_999", "ANOTHER_FAKE_XXX");
        assertFalse(unknownCandidatesGet.isPresent(), "all-unknown candidates should return empty");

        Optional<MaterialResolver.ResolvedMaterial> nullMiddleCandidate = resolver.get("DIAMOND_SWORD", null, "IRON_SWORD");
        assertTrue(nullMiddleCandidate.isPresent(), "null in middle should be skipped");
        assertEquals(Material.DIAMOND_SWORD, nullMiddleCandidate.get().getMaterial());

        Optional<MaterialResolver.ResolvedMaterial> emptyMiddleCandidate = resolver.get("DIAMOND_SWORD", "", "IRON_SWORD");
        assertTrue(emptyMiddleCandidate.isPresent(), "empty string should be skipped");
        assertEquals(Material.DIAMOND_SWORD, emptyMiddleCandidate.get().getMaterial());
    }

    @Test
    void itemCreationAndItemValidationUseResolvedMaterial() {
        Optional<ItemStack> itemOpt = resolver.makeItem("diamond_sword", 1);
        assertTrue(itemOpt.isPresent(), "Should create item for valid material");
        ItemStack item = itemOpt.get();
        assertEquals(Material.DIAMOND_SWORD, item.getType());
        assertEquals(1, item.getAmount());

        Optional<ItemStack> multiItem = resolver.makeItem("diamond_sword", 5);
        assertTrue(multiItem.isPresent());
        assertEquals(5, multiItem.get().getAmount());

        Optional<ItemStack> invalidItem = resolver.makeItem("not_real_material_xyz", 1);
        assertFalse(invalidItem.isPresent(), "Should not create item for invalid material");

        ItemStack validItem = new ItemStack(Material.DIAMOND_SWORD);
        assertTrue(resolver.isItem(validItem), "Valid item should pass isItem check");

        ItemStack airItem = new ItemStack(Material.AIR);
        assertFalse(resolver.isItem(airItem), "AIR should fail isItem check");

        assertFalse(resolver.isItem(null), "null should fail isItem check");

        Optional<ItemStack> candidateItem = resolver.item(3, "DIAMOND_SWORD", "FAKE_MAT");
        assertTrue(candidateItem.isPresent(), "item() with valid candidate should create item");
        assertEquals(3, candidateItem.get().getAmount());
        assertEquals(Material.DIAMOND_SWORD, candidateItem.get().getType());

        Optional<ItemStack> candidateItemWithLegacy = resolver.item(2, "STAINED_GLASS_PANE:15", "GLASS_PANE");
        assertTrue(candidateItemWithLegacy.isPresent(), "item() with legacy data should create item");
        ItemStack legacyItem = candidateItemWithLegacy.get();
        assertEquals(Material.STAINED_GLASS_PANE, legacyItem.getType());
        assertEquals((short) 15, legacyItem.getDurability());

        Optional<ItemStack> unknownCandidatesItem = resolver.item(1, "FAKE_ONE", "FAKE_TWO");
        assertFalse(unknownCandidatesItem.isPresent(), "item() with no valid candidates should return empty");
    }

    @Test
    void aliasesAreImmutableAndCacheReturnsStableResolution() {
        Map<String, String[]> aliases = resolver.getAliases();
        Map<String, String[]> secondCall = resolver.getAliases();
        assertEquals(aliases, secondCall);
        assertNotSame(aliases, secondCall, "Each call should return a new map instance");

        Optional<Material> firstResolve = resolver.resolve("diamond_sword");
        assertTrue(firstResolve.isPresent());
        Material firstMaterial = firstResolve.get();

        Optional<Material> secondResolve = resolver.resolve("diamond_sword");
        assertTrue(secondResolve.isPresent());
        assertEquals(firstMaterial, secondResolve.get(), "Cached result should be stable");

        resolver.clearCache();

        Optional<Material> afterClearResolve = resolver.resolve("diamond_sword");
        assertTrue(afterClearResolve.isPresent());
        assertEquals(firstMaterial, afterClearResolve.get(), "Resolution should work after cache clear");

        Optional<MaterialResolver.ResolvedMaterial> firstGet = resolver.get("IRON_SWORD");
        assertTrue(firstGet.isPresent());
        Material firstGetMaterial = firstGet.get().getMaterial();

        Optional<MaterialResolver.ResolvedMaterial> secondGet = resolver.get("IRON_SWORD");
        assertTrue(secondGet.isPresent());
        assertEquals(firstGetMaterial, secondGet.get().getMaterial(), "get() cache should be stable");

        resolver.clearCache();

        Optional<MaterialResolver.ResolvedMaterial> afterClearGet = resolver.get("IRON_SWORD");
        assertTrue(afterClearGet.isPresent());
        assertEquals(firstGetMaterial, afterClearGet.get().getMaterial(), "get() should work after cache clear");

        String cacheKey = resolver.get("DIAMOND_PICKAXE", "GOLD_PICKAXE").toString();
        String secondCacheKey = resolver.get("DIAMOND_PICKAXE", "GOLD_PICKAXE").toString();
        assertEquals(cacheKey, secondCacheKey, "Same candidates should produce same cache key");

        String reversedCacheKey = resolver.get("GOLD_PICKAXE", "DIAMOND_PICKAXE").toString();
        assertNotEquals(cacheKey, reversedCacheKey, "Different candidate order should produce different cache key");
    }
}
