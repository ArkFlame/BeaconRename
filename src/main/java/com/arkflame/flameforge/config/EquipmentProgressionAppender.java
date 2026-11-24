package com.arkflame.flameforge.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Appends a tier id to a category progression inside an operator equipment.yml while
 * preserving the rest of the file, including comments.
 */
public final class EquipmentProgressionAppender {

    private EquipmentProgressionAppender() {
    }

    public static boolean append(File equipmentFile, String categoryId, String tierId) {
        if (equipmentFile == null || categoryId == null || tierId == null
            || categoryId.trim().isEmpty() || tierId.trim().isEmpty() || !equipmentFile.isFile()) {
            return false;
        }

        List<String> lines;
        try {
            lines = new ArrayList<>(Files.readAllLines(equipmentFile.toPath(), StandardCharsets.UTF_8));
        } catch (IOException failure) {
            return false;
        }

        int categoryLine = findCategoryLine(lines, categoryId);
        if (categoryLine < 0) {
            return false;
        }
        int categoryIndent = indentOf(lines.get(categoryLine));
        int categoryEnd = lines.size();
        for (int i = categoryLine + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (isIgnorable(line)) {
                continue;
            }
            if (indentOf(line) <= categoryIndent) {
                categoryEnd = i;
                break;
            }
        }

        int progressionLine = -1;
        int progressionIndent = -1;
        for (int i = categoryLine + 1; i < categoryEnd; i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.equals("progression:") || trimmed.equals("tier-ids:")) {
                progressionLine = i;
                progressionIndent = indentOf(lines.get(i));
                break;
            }
        }
        if (progressionLine < 0) {
            return false;
        }

        int insertAfter = progressionLine;
        int entryIndent = progressionIndent + 2;
        for (int i = progressionLine + 1; i < categoryEnd; i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int indent = indentOf(line);
            if (indent <= progressionIndent) {
                break;
            }
            if (trimmed.startsWith("- ")) {
                entryIndent = indent;
                insertAfter = i;
                String value = trimmed.substring(2).trim();
                if (value.equalsIgnoreCase(tierId)) {
                    return true;
                }
            }
        }

        lines.add(insertAfter + 1, spaces(entryIndent) + "- " + tierId);
        return writeAtomically(equipmentFile, lines);
    }

    private static int findCategoryLine(List<String> lines, String categoryId) {
        String expected = categoryId.trim().toLowerCase(Locale.ROOT) + ":";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (indentOf(line) == 0) {
                continue;
            }
            String trimmed = line.trim().toLowerCase(Locale.ROOT);
            if (trimmed.equals(expected)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean writeAtomically(File target, List<String> lines) {
        Path parent = target.getParentFile() != null ? target.getParentFile().toPath() : null;
        Path temp = null;
        try {
            temp = parent != null
                ? Files.createTempFile(parent, ".equipment-", ".tmp")
                : Files.createTempFile("equipment-", ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException failure) {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // Best-effort cleanup.
                }
            }
            return false;
        }
    }

    private static boolean isIgnorable(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }

    private static int indentOf(String line) {
        int indent = 0;
        while (indent < line.length()) {
            char c = line.charAt(indent);
            if (c != ' ' && c != '\t') {
                break;
            }
            indent++;
        }
        return indent;
    }

    private static String spaces(int count) {
        StringBuilder builder = new StringBuilder(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            builder.append(' ');
        }
        return builder.toString();
    }
}
