package com.arkflame.flameforge.command;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public enum CommandNode {

    HELP("help", "help [page]", "help", "flameforge.command.help",
        "help.descriptions.help", Category.GENERAL, false, false, AccessClass.USER),
    OPEN_SELF("open", "open", "open", "flameforge.command.open",
        "help.descriptions.open", Category.FORGING, true, false, AccessClass.USER),
    OPEN_OTHER("open", "open <player>", "open", "flameforge.command.open.others",
        "help.descriptions.open", Category.FORGING, true, false, AccessClass.ADMIN),
    TIERS("tiers", "tiers [page]", "tiers", "flameforge.command.tiers",
        "help.descriptions.tiers", Category.FORGE_MANAGEMENT, true, false, AccessClass.ADMIN),
    TIER_INFO("tier", "tier info <tier>", "tier info", "flameforge.command.tier.info",
        "help.descriptions.tier-info", Category.FORGE_MANAGEMENT, true, false, AccessClass.ADMIN),
    PREVIEW("preview", "preview <tier> [material]", "preview", "flameforge.command.preview",
        "help.descriptions.preview", Category.FORGING, true, false, AccessClass.ADMIN),
    HISTORY_SELF("history", "history", "history", "flameforge.command.history",
        "help.descriptions.history", Category.FORGING, true, false, AccessClass.USER),
    HISTORY_OTHER("history", "history <player>", "history", "flameforge.command.history.others",
        "help.descriptions.history", Category.FORGING, true, false, AccessClass.ADMIN),
    TP("tp", "tp <id>", "tp", "flameforge.command.station.teleport",
        "help.descriptions.station-teleport", Category.FORGE_MANAGEMENT, true, false, AccessClass.ADMIN),
    STATION_ADD("station", "station add [id|auto] [profile]", "station add",
        "flameforge.command.station.add", "help.descriptions.station-add",
        Category.FORGE_MANAGEMENT, true, false, AccessClass.ADMIN),
    STATION_REMOVE("station", "station remove <id>", "station remove",
        "flameforge.command.station.remove", "help.descriptions.station-remove",
        Category.FORGE_MANAGEMENT, true, false, AccessClass.ADMIN),
    STATION_LIST("station", "station list [page]", "station list",
        "flameforge.command.station.list", "help.descriptions.station-list",
        Category.FORGE_MANAGEMENT, true, false, AccessClass.ADMIN),
    STATION_INFO("station", "station info <id>", "station info",
        "flameforge.command.station.info", "help.descriptions.station-info",
        Category.FORGE_MANAGEMENT, true, false, AccessClass.ADMIN),
    STATION_TELEPORT_ALIAS("station", "station teleport <id>", "station teleport",
        "flameforge.command.station.teleport", "help.descriptions.station-teleport",
        Category.FORGE_MANAGEMENT, true, true, AccessClass.ADMIN),
    RELOAD("reload", "reload", "reload", "flameforge.command.reload",
        "help.descriptions.reload", Category.ADMINISTRATION, true, false, AccessClass.ADMIN),
    VALIDATE("validate", "validate", "validate", "flameforge.command.validate",
        "help.descriptions.validate", Category.ADMINISTRATION, true, false, AccessClass.ADMIN),
    TEST_ITEM("testitem", "testitem <tier> <variant> [material]", "testitem",
        "flameforge.command.testitem", "help.descriptions.testitem",
        Category.ADMINISTRATION, true, false, AccessClass.ADMIN),
    SETUP_TIER_CREATE("setup", "setup tier create <id> <level>", "setup tier create",
        "flameforge.command.setup.tier", "help.descriptions.setup-tier-create",
        Category.ADMINISTRATION, true, false, AccessClass.ADMIN),
    SETUP_TIER_CLONE("setup", "setup tier clone <source> <id> <level>", "setup tier clone",
        "flameforge.command.setup.tier", "help.descriptions.setup-tier-clone",
        Category.ADMINISTRATION, true, false, AccessClass.ADMIN);

    public enum Category {
        GENERAL,
        FORGING,
        FORGE_MANAGEMENT,
        ADMINISTRATION
    }

    public enum AccessClass {
        USER,
        ADMIN
    }

    public static final String ADMIN_PERMISSION = "flameforge.admin";

    private final String root;
    private final String usage;
    private final String suggestion;
    private final String permission;
    private final String descriptionKey;
    private final Category category;
    private final boolean readyOnly;
    private final boolean alias;
    private final AccessClass accessClass;

    CommandNode(String root, String usage, String suggestion, String permission,
                String descriptionKey, Category category, boolean readyOnly, boolean alias,
                AccessClass accessClass) {
        this.root = root;
        this.usage = usage;
        this.suggestion = suggestion;
        this.permission = permission;
        this.descriptionKey = descriptionKey;
        this.category = category;
        this.readyOnly = readyOnly;
        this.alias = alias;
        this.accessClass = accessClass;
    }

    public String getRoot() {
        return root;
    }

    public String getUsage() {
        return usage;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public Optional<String> getPermission() {
        return Optional.of(permission);
    }

    public Optional<String> getDescriptionKey() {
        return Optional.of(descriptionKey);
    }

    public Category getCategory() {
        return category;
    }

    public boolean isReadyOnly() {
        return readyOnly;
    }

    public boolean isAlias() {
        return alias;
    }

    public boolean getAlias() {
        return alias;
    }

    public AccessClass getAccessClass() {
        return accessClass;
    }

    public boolean isPermitted(CommandSender sender) {
        return sender != null && (sender.hasPermission(ADMIN_PERMISSION) || sender.hasPermission(permission));
    }

    public boolean visibleTo(CommandSender sender) {
        return !alias && isPermitted(sender);
    }

    public static List<String> permittedRootNames(CommandSender sender, String prefix) {
        Map<String, String> roots = new LinkedHashMap<>();
        String lowerPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        for (CommandNode node : values()) {
            if (node.isPermitted(sender) && node.root.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                String lowerRoot = node.root.toLowerCase(Locale.ROOT);
                roots.put(lowerRoot, node.root);
            }
        }
        List<String> result = new ArrayList<>(roots.values());
        Collections.sort(result, String::compareToIgnoreCase);
        return result;
    }

    public static List<HelpEntry> immediateChildren(CommandSender sender, List<String> parentPath) {
        if (parentPath == null) {
            parentPath = Collections.emptyList();
        }

        List<HelpEntry> results = new ArrayList<>();
        Map<String, HelpEntry> dedup = new LinkedHashMap<>();

        for (CommandNode node : values()) {
            if (!node.visibleTo(sender)) {
                continue;
            }

            String[] parts = node.getSuggestion().split(" ");
            String token = tokenFor(parts, parentPath);

            if (token != null && !dedup.containsKey(token)) {
                dedup.put(token, new HelpEntry(
                    appendPath(parentPath, token),
                    node.getUsage(),
                    node.getSuggestion(),
                    node.getPermission().orElse(null),
                    node.getDescriptionKey().orElse(null),
                    node.getAccessClass(),
                    isGroup(node)
                ));
            }
        }

        results.addAll(dedup.values());

        List<HelpEntry> userEntries = new ArrayList<>();
        List<HelpEntry> adminEntries = new ArrayList<>();
        for (HelpEntry entry : results) {
            if (entry.getAccessClass() == AccessClass.USER) {
                userEntries.add(entry);
            } else {
                adminEntries.add(entry);
            }
        }

        List<HelpEntry> sorted = new ArrayList<>();
        sorted.addAll(userEntries);
        sorted.addAll(adminEntries);
        return sorted;
    }

    private static String tokenFor(String[] parts, List<String> parentPath) {
        int index = parentPath.size();
        if (index < parts.length) {
            if (index > 0 && !matchesPrefix(parts, parentPath)) {
                return null;
            }
            return parts[index];
        }
        return null;
    }

    private static boolean matchesPrefix(String[] parts, List<String> parentPath) {
        for (int i = 0; i < parentPath.size(); i++) {
            if (!parts[i].equalsIgnoreCase(parentPath.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> appendPath(List<String> parentPath, String token) {
        List<String> result = new ArrayList<>(parentPath);
        result.add(token);
        return result;
    }

    private static boolean isGroup(CommandNode node) {
        String[] parts = node.getSuggestion().split(" ");
        if (parts.length <= 1) {
            return false;
        }
        String root = node.getRoot().toLowerCase(Locale.ROOT);
        String firstToken = parts[0].toLowerCase(Locale.ROOT);

        if (root.equals(firstToken)) {
            return parts.length > 2 || !parts[parts.length - 1].equalsIgnoreCase(root);
        }
        return true;
    }

    public String getUsageTemplate() {
        return usage;
    }

    public static class HelpEntry {
        private final List<String> path;
        private final String usage;
        private final String suggestion;
        private final String permission;
        private final String descriptionKey;
        private final AccessClass accessClass;
        private final boolean group;

        public HelpEntry(List<String> path, String usage, String suggestion, String permission,
                        String descriptionKey, AccessClass accessClass, boolean group) {
            this.path = path;
            this.usage = usage;
            this.suggestion = suggestion;
            this.permission = permission;
            this.descriptionKey = descriptionKey;
            this.accessClass = accessClass;
            this.group = group;
        }

        public List<String> getPath() {
            return path;
        }

        public String getUsage() {
            return usage;
        }

        public String getSuggestion() {
            return suggestion;
        }

        public String getPermission() {
            return permission;
        }

        public String getDescriptionKey() {
            return descriptionKey;
        }

        public AccessClass getAccessClass() {
            return accessClass;
        }

        public boolean isGroup() {
            return group;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HelpEntry that = (HelpEntry) o;
            return group == that.group &&
                   accessClass == that.accessClass &&
                   Objects.equals(path, that.path) &&
                   Objects.equals(usage, that.usage) &&
                   Objects.equals(suggestion, that.suggestion) &&
                   Objects.equals(permission, that.permission) &&
                   Objects.equals(descriptionKey, that.descriptionKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, usage, suggestion, permission, descriptionKey, accessClass, group);
        }
    }
}
