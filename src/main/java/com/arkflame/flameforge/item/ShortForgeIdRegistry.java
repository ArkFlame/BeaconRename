package com.arkflame.flameforge.item;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class ShortForgeIdRegistry {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 8;
    private static final int MAX_ATTEMPT = 255;

    private final ConcurrentMap<UUID, String> idsByForge = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> forgesById = new ConcurrentHashMap<>();
    private final CandidateGenerator candidateGenerator;

    public ShortForgeIdRegistry() {
        this(ShortForgeIdRegistry::defaultCandidate);
    }

    ShortForgeIdRegistry(final CandidateGenerator candidateGenerator) {
        if (candidateGenerator == null) {
            throw new IllegalArgumentException("candidateGenerator");
        }
        this.candidateGenerator = candidateGenerator;
    }

    public String claimOrGenerate(final UUID forgeId) {
        if (forgeId == null) {
            throw new IllegalArgumentException("forgeId");
        }
        final String existing = idsByForge.get(forgeId);
        if (existing != null) {
            return existing;
        }
        for (int attempt = 0; attempt <= MAX_ATTEMPT; attempt++) {
            final String candidate = candidateGenerator.generate(forgeId, attempt);
            if (claimExisting(forgeId, candidate)) {
                return idsByForge.get(forgeId);
            }
        }
        throw new IllegalStateException("No available short Forge ID for " + forgeId);
    }

    public boolean claimExisting(final UUID forgeId, final String shortId) {
        if (forgeId == null || !isValid(shortId)) {
            return false;
        }
        final String existingForForge = idsByForge.get(forgeId);
        if (existingForForge != null) {
            return existingForForge.equals(shortId);
        }
        final UUID existingOwner = forgesById.putIfAbsent(shortId, forgeId);
        if (existingOwner != null && !existingOwner.equals(forgeId)) {
            return false;
        }
        final String claimedId = idsByForge.putIfAbsent(forgeId, shortId);
        if (claimedId != null && !claimedId.equals(shortId)) {
            forgesById.remove(shortId, forgeId);
            return false;
        }
        return true;
    }

    private static boolean isValid(final String shortId) {
        if (shortId == null || shortId.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < shortId.length(); i++) {
            if (ALPHABET.indexOf(shortId.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String defaultCandidate(final UUID forgeId, final int attempt) {
        final byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest((forgeId.toString() + ":" + attempt).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        final long firstFortyBits = ByteBuffer.wrap(digest).getLong() >>> 24;
        final StringBuilder result = new StringBuilder(LENGTH);
        for (int i = LENGTH - 1; i >= 0; i--) {
            result.append(ALPHABET.charAt((int) ((firstFortyBits >>> (i * 5)) & 31L)));
        }
        return result.toString();
    }

    interface CandidateGenerator {
        String generate(UUID forgeId, int attempt);
    }
}
