package com.arkflame.flameforge.text;

import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import com.arkflame.flameforge.config.MessageTemplateLoader;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

public final class MessageService {
    private final ConfigService configService;
    private final TextRenderer renderer;
    private final TextBridge textBridge;
    private final TextPlaceholders placeholders;
    private final Logger logger;
    private final Map<String, Map<String, Object>> bundledMessages;
    private final Set<String> loggedMissingKeys;
    private final Set<String> loggedUnknownPlaceholders;

    private MessageService(final ConfigService configService, final TextRenderer renderer,
                           final TextBridge textBridge, final TextPlaceholders placeholders,
                           final Logger logger, final Map<String, Map<String, Object>> bundledMessages) {
        this.configService = configService;
        this.renderer = renderer;
        this.textBridge = textBridge;
        this.placeholders = placeholders;
        this.logger = logger;
        this.bundledMessages = bundledMessages;
        this.loggedMissingKeys = new HashSet<>();
        this.loggedUnknownPlaceholders = new HashSet<>();
    }

    public static MessageService create(final JavaPlugin plugin, final ConfigService configService,
                                        final TextRenderer renderer, final TextBridge textBridge,
                                        final TextPlaceholders placeholders, final Logger logger) {
        return new MessageService(configService, renderer, textBridge, placeholders, logger,
                loadBundledMessages(plugin, logger));
    }

    public static MessageService create(final ConfigService configService, final TextRenderer renderer,
                                        final TextBridge textBridge, final TextPlaceholders placeholders,
                                        final Logger logger) {
        return new MessageService(configService, renderer, textBridge, placeholders, logger,
                Collections.emptyMap());
    }

    public Optional<String> findMessageString(final String dottedKey) {
        Map<String, Object> settings = findMessageSettings(dottedKey);
        if (settings == null || !settings.containsKey("message")) {
            return Optional.empty();
        }
        return Optional.ofNullable(settings.get("message")).map(String::valueOf);
    }

    public List<String> findMessageLines(final String dottedKey) {
        Map<String, Object> settings = findMessageSettings(dottedKey);
        if (settings == null || !(settings.get("lines") instanceof List)) {
            return Collections.emptyList();
        }

        List<String> lines = new ArrayList<>();
        for (Object line : (List<?>) settings.get("lines")) {
            if (line != null) {
                lines.add(String.valueOf(line));
            }
        }
        return Collections.unmodifiableList(lines);
    }

    public Component renderToComponent(final String dottedKey, final Player player,
                                       final Map<String, String> explicitStringValues,
                                       final Map<String, Component> explicitComponentValues) {
        return renderToComponent(dottedKey, (CommandSender) player,
                explicitStringValues, explicitComponentValues);
    }

    public Component renderToComponent(final String dottedKey, final Player player) {
        return renderToComponent(dottedKey, (CommandSender) player, null, null);
    }

    public Component renderToComponent(final String dottedKey, final Player player,
                                       final MessageArguments arguments) {
        return renderToComponent(dottedKey, (CommandSender) player, arguments);
    }

    public <T extends CommandSender> Component renderToComponent(final String dottedKey, final T sender) {
        return renderToComponent(dottedKey, sender, null, null);
    }

    public Component renderToComponent(final String dottedKey, final CommandSender sender,
                                       final MessageArguments arguments) {
        return renderToComponent(dottedKey, sender,
                arguments != null ? arguments.getStringValues() : null,
                arguments != null ? arguments.getComponentValues() : null);
    }

    public Component renderToComponent(final String dottedKey, final CommandSender sender,
                                       final Map<String, String> explicitStringValues,
                                       final Map<String, Component> explicitComponentValues) {
        Optional<String> messageOpt = findMessageString(dottedKey);
        if (!messageOpt.isPresent()) {
            return missingMessageComponent(dottedKey);
        }

        Player player = sender instanceof Player ? (Player) sender : null;
        Map<String, String> stringValues = new HashMap<>(placeholders.resolveStringValues(player));
        Map<String, Component> componentValues = new HashMap<>(placeholders.resolveComponentValues(player));

        if (explicitStringValues != null) {
            stringValues.putAll(explicitStringValues);
        }
        if (explicitComponentValues != null) {
            componentValues.putAll(explicitComponentValues);
        }

        Set<String> messageUnknowns = new HashSet<>();
        for (String placeholder : renderer.extractPlaceholders(messageOpt.get()).keySet()) {
            String percentKey = "%" + placeholder + "%";
            if (!stringValues.containsKey(placeholder) && !stringValues.containsKey(percentKey)
                    && !componentValues.containsKey(placeholder) && !componentValues.containsKey(percentKey)) {
                messageUnknowns.add(placeholder);
            }
        }

        for (String unknown : messageUnknowns) {
            logUnknownPlaceholder(dottedKey, unknown);
            stringValues.put(unknown, "");
        }

        return renderer.render(messageOpt.get(), stringValues, componentValues, dottedKey, logger);
    }

    public List<Component> renderLinesToComponents(final String dottedKey, final Player player,
                                                   final Map<String, String> explicitStringValues,
                                                   final Map<String, Component> explicitComponentValues) {
        return renderLinesToComponents(dottedKey, (CommandSender) player,
                explicitStringValues, explicitComponentValues);
    }

    public List<Component> renderLinesToComponents(final String dottedKey, final Player player) {
        return renderLinesToComponents(dottedKey, (CommandSender) player, null, null);
    }

    public List<Component> renderLinesToComponents(final String dottedKey, final Player player,
                                                   final MessageArguments arguments) {
        return renderLinesToComponents(dottedKey, (CommandSender) player, arguments);
    }

    public <T extends CommandSender> List<Component> renderLinesToComponents(final String dottedKey,
                                                                               final T sender) {
        return renderLinesToComponents(dottedKey, sender, null, null);
    }

    public List<Component> renderLinesToComponents(final String dottedKey, final CommandSender sender,
                                                   final MessageArguments arguments) {
        return renderLinesToComponents(dottedKey, sender,
                arguments != null ? arguments.getStringValues() : null,
                arguments != null ? arguments.getComponentValues() : null);
    }

    public List<Component> renderLinesToComponents(final String dottedKey, final CommandSender sender,
                                                   final Map<String, String> explicitStringValues,
                                                   final Map<String, Component> explicitComponentValues) {
        List<String> lines = findMessageLines(dottedKey);
        if (lines.isEmpty()) {
            return Collections.singletonList(missingMessageComponent(dottedKey));
        }

        Player player = sender instanceof Player ? (Player) sender : null;
        Map<String, String> stringValues = new HashMap<>(placeholders.resolveStringValues(player));
        Map<String, Component> componentValues = new HashMap<>(placeholders.resolveComponentValues(player));
        if (explicitStringValues != null) {
            stringValues.putAll(explicitStringValues);
        }
        if (explicitComponentValues != null) {
            componentValues.putAll(explicitComponentValues);
        }

        List<Component> components = new ArrayList<>(lines.size());
        for (String line : lines) {
            components.add(renderer.render(line, stringValues, componentValues, dottedKey, logger));
        }
        return components;
    }

    public void send(final Player player, final String dottedKey) {
        send((CommandSender) player, dottedKey);
    }

    public void send(final CommandSender sender, final String dottedKey) {
        if (sender != null) {
            textBridge.send(sender, renderToComponent(dottedKey, sender));
        }
    }

    public void send(final Player player, final String dottedKey,
                     final Map<String, String> explicitStringValues,
                     final Map<String, Component> explicitComponentValues) {
        if (player != null) {
            textBridge.send(player, renderToComponent(dottedKey, player,
                    explicitStringValues, explicitComponentValues));
        }
    }

    public void send(final CommandSender sender, final String dottedKey, final MessageArguments arguments) {
        if (sender != null) {
            textBridge.send(sender, renderToComponent(dottedKey, sender, arguments));
        }
    }

    public void sendLines(final Player player, final String dottedKey) {
        sendLines((CommandSender) player, dottedKey, null);
    }

    public void sendLines(final CommandSender sender, final String dottedKey) {
        sendLines(sender, dottedKey, null);
    }

    public void sendLines(final Player player, final String dottedKey, final MessageArguments arguments) {
        sendLines((CommandSender) player, dottedKey, arguments);
    }

    public void sendLines(final CommandSender sender, final String dottedKey, final MessageArguments arguments) {
        if (sender == null) {
            return;
        }
        for (Component component : renderLinesToComponents(dottedKey, sender, arguments)) {
            textBridge.send(sender, component);
        }
    }

    public void sendComponent(final Player player, final Component component) {
        if (player != null && component != null) {
            textBridge.send(player, component);
        }
    }

    public void sendComponent(final CommandSender sender, final Component component) {
        if (sender != null && component != null) {
            textBridge.send(sender, component);
        }
    }

    public void sendTitle(final Player player, final String dottedKey) {
        if (player != null) {
            textBridge.sendTitle(player, renderToComponent(dottedKey, player), Component.empty(), 10, 70, 20);
        }
    }

    public void sendTitle(final Player player, final String dottedKey, final String subtitleKey) {
        if (player != null) {
            Component subtitle = subtitleKey != null ? renderToComponent(subtitleKey, player) : Component.empty();
            textBridge.sendTitle(player, renderToComponent(dottedKey, player), subtitle, 10, 70, 20);
        }
    }

    public void sendActionBar(final Player player, final String dottedKey) {
        if (player != null) {
            textBridge.sendActionBar(player, renderToComponent(dottedKey, player));
        }
    }

    public void sendBroadcast(final String dottedKey) {
        textBridge.broadcast(renderToComponent(dottedKey, (CommandSender) null));
    }

    private Map<String, Object> findMessageSettings(final String dottedKey) {
        ConfigSnapshot snapshot = configService.getCurrentSnapshot();
        if (snapshot != null) {
            Map<String, Object> settings = snapshot.getMessageSettings(dottedKey);
            if (settings != null) {
                return settings;
            }
        }
        return bundledMessages.get(dottedKey);
    }

    private static Map<String, Map<String, Object>> loadBundledMessages(final JavaPlugin plugin,
                                                                          final Logger logger) {
        if (plugin == null) {
            return Collections.emptyMap();
        }
        InputStream resource = plugin.getResource("messages.yml");
        if (resource == null) {
            return Collections.emptyMap();
        }
        try (InputStream stream = resource;
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return MessageTemplateLoader.flatten(YamlConfiguration.loadConfiguration(reader));
        } catch (Exception failure) {
            if (logger != null) {
                logger.warning("Unable to load bundled messages.yml: " + failure.getMessage());
            }
            return Collections.emptyMap();
        }
    }

    private synchronized void logMissingKey(final String key) {
        if (!loggedMissingKeys.contains(key)) {
            loggedMissingKeys.add(key);
            if (logger != null) {
                logger.warning("Missing message key: " + key);
            }
        }
    }

    private synchronized void logUnknownPlaceholder(final String messageKey, final String placeholder) {
        String logKey = messageKey + ":" + placeholder;
        if (!loggedUnknownPlaceholders.contains(logKey)) {
            loggedUnknownPlaceholders.add(logKey);
            if (logger != null) {
                logger.warning("Unknown placeholder '" + placeholder + "' in message '" + messageKey + "'");
            }
        }
    }

    private Component missingMessageComponent(String dottedKey) {
        if (!loggedMissingKeys.contains(dottedKey)) {
            loggedMissingKeys.add(dottedKey);
            if (logger != null) {
                logger.warning("Missing message key: " + dottedKey + ". Check messages.yml.");
            }
        }
        return Component.text()
                .content("Missing message key '")
                .color(NamedTextColor.RED)
                .append(Component.text(dottedKey, NamedTextColor.WHITE))
                .append(Component.text("'. Check messages.yml.", NamedTextColor.RED))
                .build();
    }

    public TextRenderer getRenderer() {
        return renderer;
    }

    public TextBridge getTextBridge() {
        return textBridge;
    }
}
