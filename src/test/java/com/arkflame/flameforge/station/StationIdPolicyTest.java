package com.arkflame.flameforge.station;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StationIdPolicyTest {

    @Test
    void normalizeAndExplicitValidationCoverNullWhitespaceCaseAndInvalidCharacters() {
        assertEquals("", StationIdPolicy.normalize(null));
        assertEquals("", StationIdPolicy.normalize("   "));
        assertEquals("abc", StationIdPolicy.normalize("  abc  "));
        assertEquals("abc", StationIdPolicy.normalize("\tabc\n"));

        assertFalse(StationIdPolicy.isValidExplicit(null));
        assertFalse(StationIdPolicy.isValidExplicit(""));
        assertFalse(StationIdPolicy.isValidExplicit("   "));
        assertFalse(StationIdPolicy.isValidExplicit("ABC"));
        assertFalse(StationIdPolicy.isValidExplicit("abc def"));
        assertFalse(StationIdPolicy.isValidExplicit("abc@def"));

        assertTrue(StationIdPolicy.isValidExplicit("a"));
        assertTrue(StationIdPolicy.isValidExplicit("abc"));
        assertTrue(StationIdPolicy.isValidExplicit("abc123"));
        assertTrue(StationIdPolicy.isValidExplicit("abc-123"));
        assertTrue(StationIdPolicy.isValidExplicit("abc_123"));
    }

    @Test
    void autoTokenAndGeneratedCandidateFollowStableGrammar() {
        assertTrue(StationIdPolicy.isAutoToken("auto"));
        assertFalse(StationIdPolicy.isAutoToken("Auto"));
        assertFalse(StationIdPolicy.isAutoToken("auto "));
        assertFalse(StationIdPolicy.isAutoToken(""));

        String candidate = StationIdPolicy.generateCandidate();
        assertNotNull(candidate);
        assertTrue(candidate.startsWith("forge-"), "Candidate should start with 'forge-': " + candidate);
        assertEquals(18, candidate.length(), "forge- + 12 hex chars = 18 chars");
        String suffix = candidate.substring("forge-".length());
        assertTrue(suffix.matches("[a-f0-9]{12}"), "Suffix should be 12 hex chars: " + suffix);

        String second = StationIdPolicy.generateCandidate();
        assertNotNull(second);
        assertTrue(second.startsWith("forge-"));
        assertEquals(18, second.length());
        assertNotEquals(candidate, second, "Generated candidates should be unique");
    }
}
