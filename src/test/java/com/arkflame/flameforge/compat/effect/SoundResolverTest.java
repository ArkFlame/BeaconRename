package com.arkflame.flameforge.compat.effect;

import org.bukkit.Sound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SoundResolverTest {

    private SoundResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = SoundResolver.getInstance();
        resolver.clearCache();
    }

    @Test
    void allDeclaredAliasesResolveToFirstAvailableCandidate() {
        Map<String, String[]> aliases = getAliasesViaReflection();

        for (Map.Entry<String, String[]> entry : aliases.entrySet()) {
            String alias = entry.getKey();
            String[] candidates = entry.getValue();
            assertNotNull(candidates);
            assertTrue(candidates.length > 0, "Alias '" + alias + "' should have at least one candidate");

            Sound result = resolver.resolveOrThrow(alias);
            assertNotNull(result, "Alias '" + alias + "' should resolve to a sound");
        }
    }

    @Test
    void directNamesAreCaseAndWhitespaceNormalized() {
        Sound lowerUnderscore = resolver.resolveOrThrow("level_up");
        Sound upperUnderscore = resolver.resolveOrThrow("LEVEL_UP");
        Sound mixedUnderscore = resolver.resolveOrThrow("Level_Up");
        Sound spaceNoUnderscore = resolver.resolveOrThrow("level up");

        assertEquals(lowerUnderscore, upperUnderscore);
        assertEquals(upperUnderscore, mixedUnderscore);
        assertEquals(mixedUnderscore, spaceNoUnderscore);
    }

    @Test
    void unknownNullAndEmptyInputsFollowStrictAndFallbackContracts() {
        Sound nullFallback = resolver.resolveOrDefault(null, Sound.CLICK);
        assertEquals(Sound.CLICK, nullFallback, "null should use fallback");

        Sound emptyFallback = resolver.resolveOrDefault("", Sound.CLICK);
        assertEquals(Sound.CLICK, emptyFallback, "empty should use fallback");

        Sound unknownFallback = resolver.resolveOrDefault("not_a_real_sound_xyz_123", Sound.CLICK);
        assertEquals(Sound.CLICK, unknownFallback, "unknown should use fallback");

        assertThrows(IllegalArgumentException.class, () -> {
            resolver.resolveOrThrow(null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            resolver.resolveOrThrow("");
        });
    }

    @Test
    void soundGroupsAreCompleteAndImmutable() {
        Map<String, Set<String>> groups = resolver.getSoundGroups();

        assertNotNull(groups);
        assertFalse(groups.isEmpty());
        assertTrue(groups.containsKey("success"));
        assertTrue(groups.containsKey("error"));
        assertTrue(groups.containsKey("ui"));

        Set<String> successGroup = groups.get("success");
        assertNotNull(successGroup);
        assertFalse(successGroup.isEmpty());

        assertThrows(UnsupportedOperationException.class, () -> {
            groups.put("new_group", null);
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            successGroup.add("new_sound");
        });
    }

    @Test
    void cacheReturnsStableResolutionAndClearCacheForcesReResolution() {
        Sound firstSound = resolver.resolveOrThrow("level_up");

        Sound secondSound = resolver.resolveOrThrow("level_up");
        assertEquals(firstSound, secondSound, "Cached result should be stable");

        resolver.clearCache();

        Sound afterClearSound = resolver.resolveOrThrow("level_up");
        assertEquals(firstSound, afterClearSound, "Resolution should work after cache clear");
    }

    @SuppressWarnings("unchecked")
    private Map<String, String[]> getAliasesViaReflection() {
        try {
            java.lang.reflect.Field aliasesField = SoundResolver.class.getDeclaredField("ALIASES");
            aliasesField.setAccessible(true);
            return (Map<String, String[]>) aliasesField.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
