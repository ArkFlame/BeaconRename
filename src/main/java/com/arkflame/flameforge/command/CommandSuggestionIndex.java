package com.arkflame.flameforge.command;

import com.arkflame.flameforge.config.TierRepository;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class CommandSuggestionIndex {

    private final ConcurrentMap<String, String> onlinePlayerNames = new ConcurrentHashMap<>();
    private final TierRepository tierRepository;
    private volatile List<String> cachedProfileIds = Collections.singletonList("default");
    private volatile List<String> cachedStationIds = Collections.emptyList();

    public CommandSuggestionIndex(TierRepository tierRepository) {
        this.tierRepository = tierRepository;
    }

    public void updateOnlinePlayers(Set<String> playerNames) {
        onlinePlayerNames.clear();
        if (playerNames != null) {
            for (String name : playerNames) {
                if (name != null) {
                    onlinePlayerNames.put(name.toLowerCase(Locale.ROOT), name);
                }
            }
        }
    }

    public void updateProfileIds(Set<String> profileIds) {
        this.cachedProfileIds = profileIds != null ? new ArrayList<>(profileIds) : Collections.singletonList("default");
    }

    public void updateStationIds(List<String> stationIds) {
        this.cachedStationIds = stationIds != null ? new ArrayList<>(stationIds) : Collections.emptyList();
    }

    public List<String> getOnlinePlayerNames() {
        return filterAndSort(new ArrayList<>(onlinePlayerNames.values()), "");
    }

    public List<String> getOnlinePlayerNamesMatching(String prefix) {
        return filterAndSort(new ArrayList<>(onlinePlayerNames.values()), prefix);
    }

    public List<String> getOnlinePlayerSuggestions(String prefix) {
        return getOnlinePlayerNamesMatching(prefix);
    }

    public List<String> getRootSuggestions(CommandSender sender, String prefix) {
        return CommandNode.permittedRootNames(sender, prefix);
    }

    public List<String> getRootSuggestions(String prefix) {
        return catalogRootSuggestions(prefix);
    }

    public List<String> getHelpSuggestions(CommandSender sender, String[] args, String prefix) {
        if (args.length == 1) {
            List<CommandNode.HelpEntry> children = CommandNode.immediateChildren(sender, new ArrayList<>());
            List<String> tokens = new ArrayList<>();
            for (CommandNode.HelpEntry entry : children) {
                if (!entry.getPath().isEmpty()) {
                    tokens.add(entry.getPath().get(0));
                }
            }
            return filterAndSort(tokens, prefix);
        }

        if (args.length == 2) {
            String root = args[1].toLowerCase(Locale.ROOT);
            List<CommandNode.HelpEntry> children = CommandNode.immediateChildren(sender, Collections.singletonList(root));
            List<String> tokens = new ArrayList<>();
            for (CommandNode.HelpEntry entry : children) {
                if (entry.getPath().size() > 1) {
                    tokens.add(entry.getPath().get(entry.getPath().size() - 1));
                }
            }
            return filterAndSort(tokens, prefix);
        }

        if (args.length == 3) {
            String root = args[1].toLowerCase(Locale.ROOT);
            if (root.equals("setup")) {
                List<CommandNode.HelpEntry> children = CommandNode.immediateChildren(sender, Arrays.asList("setup", args[2].toLowerCase(Locale.ROOT)));
                List<String> tokens = new ArrayList<>();
                for (CommandNode.HelpEntry entry : children) {
                    if (entry.getPath().size() > 2) {
                        tokens.add(entry.getPath().get(entry.getPath().size() - 1));
                    }
                }
                return filterAndSort(tokens, prefix);
            }
        }

        return Collections.emptyList();
    }

    public List<String> getTierSubSuggestions(String prefix) {
        return catalogSuggestions(null, "tier", 1, prefix);
    }

    public List<String> getTierSubSuggestions(CommandSender sender, String prefix) {
        return catalogSuggestions(sender, "tier", 1, prefix);
    }

    public List<String> getStationSubSuggestions(String prefix) {
        return catalogSuggestions(null, "station", 1, prefix);
    }

    public List<String> getStationSubSuggestions(CommandSender sender, String prefix) {
        return catalogSuggestions(sender, "station", 1, prefix);
    }

    public List<String> getStationIdSuggestions(String prefix) {
        return filterAndSort(cachedStationIds, prefix);
    }

    public List<String> getStationAddSuggestions(String[] args, String prefix) {
        if (args.length == 3) {
            return filterAndSort(Collections.singletonList("auto"), prefix);
        }
        if (args.length == 4) {
            return filterAndSort(cachedProfileIds, prefix);
        }
        return Collections.emptyList();
    }

    public List<String> getTierIdSuggestions(String prefix) {
        return filterAndSort(
            tierRepository.allAscending().stream()
                .map(tier -> tier.getId())
                .collect(java.util.stream.Collectors.toList()),
            prefix
        );
    }

    private List<String> catalogRootSuggestions(String prefix) {
        Map<String, String> roots = new LinkedHashMap<>();
        String lowerPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        for (CommandNode node : CommandNode.values()) {
            if (node.getRoot().toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                roots.put(node.getRoot().toLowerCase(Locale.ROOT), node.getRoot());
            }
        }
        return sorted(roots.values());
    }

    private List<String> catalogSuggestions(CommandSender sender, String root, int part, String prefix) {
        Map<String, String> suggestions = new LinkedHashMap<>();
        for (CommandNode node : CommandNode.values()) {
            if ((sender == null || node.isPermitted(sender)) && node.getRoot().equalsIgnoreCase(root)) {
                String[] parts = node.getSuggestion().split(" ");
                if (parts.length > part) {
                    String suggestion = parts[part];
                    suggestions.put(suggestion.toLowerCase(Locale.ROOT), suggestion);
                }
            }
        }
        return filterAndSort(new ArrayList<>(suggestions.values()), prefix);
    }

    private List<String> filterAndSort(List<String> source, String prefix) {
        String lowerPrefix = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        Map<String, String> matches = new LinkedHashMap<>();
        if (source != null) {
            for (String value : source) {
                if (value != null && value.toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                    matches.put(value.toLowerCase(Locale.ROOT), value);
                }
            }
        }
        return sorted(matches.values());
    }

    private List<String> sorted(Iterable<String> source) {
        List<String> result = new ArrayList<>();
        for (String value : source) {
            result.add(value);
        }
        Collections.sort(result, String::compareToIgnoreCase);
        return result;
    }
}
