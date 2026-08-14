package com.arkflame.flameforge.text;

import com.arkflame.flameforge.config.MessageTemplateLoader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCatalogCoverageTest {

    private static final String BUNDLED_MESSAGES_RESOURCE = "messages.yml";
    private static final Pattern UNRESOLVED_TOKEN = Pattern.compile("%[A-Za-z0-9_-]+%");

    private final TextRenderer renderer = new TextRenderer();

    private static final List<String> COMMAND_KEYS_ADDED = Arrays.asList(
            "command.failed", "command.loading", "command.player-only", "command.unavailable",
            "open.no-forge-target", "open.no-target",
            "preview.material", "preview.no-eligible-variant", "preview.no-variants", "preview.variant",
            "setup-tier-clone.failure", "setup-tier-clone.level-invalid", "setup-tier-create.level-invalid",
            "station-add.usage", "station-remove.usage",
            "station-teleport.not-found", "station-teleport.usage",
            "tier-info.chances", "tier-info.level", "tier-info.requirement-item",
            "tier-info.requirement-money", "tier-info.requirement-xp", "tier-info.requirements-combine",
            "tier-info.requirements-items-header", "tier-info.variant-entry", "tier-info.variants-header",
            "tp.not-found", "tp.usage");

    private static final List<String> STATION_TELEPORT_DYNAMIC_KEYS = Arrays.asList(
            "station-teleport.player-offline", "station-teleport.world-not-found",
            "station-teleport.world-not-loaded", "station-teleport.teleport-rejected",
            "station-teleport.teleport-exception", "station-teleport.player-retired",
            "station-teleport.scheduler-rejected");

    private static final List<String> TESTITEM_KEYS = Arrays.asList(
            "help.descriptions.testitem",
            "testitem.no-permission", "testitem.usage", "testitem.player-only",
            "testitem.tier-not-found", "testitem.variant-not-found",
            "testitem.material-unavailable", "testitem.material-category-mismatch",
            "testitem.variant-ineligible", "testitem.mutation-failed", "testitem.success");

    private static final List<String> TIER_INFO_CONTRACT_KEYS = Arrays.asList(
            "tier-info.level", "tier-info.requirements-combine", "tier-info.requirement-xp",
            "tier-info.requirement-money", "tier-info.requirements-items-header",
            "tier-info.requirement-item", "tier-info.chances", "tier-info.variants-header",
            "tier-info.variant-entry");

    private static final Map<String, String> TIER_INFO_EXACT_TEMPLATES = new LinkedHashMap<>();

    static {
        TIER_INFO_EXACT_TEMPLATES.put("tier-info.level",
                "<dark_gray>• <gray>Level: <white>%level%");
        TIER_INFO_EXACT_TEMPLATES.put("tier-info.requirements-combine",
                "<dark_gray>• <gray>Requirement mode: <white>%combine%");
        TIER_INFO_EXACT_TEMPLATES.put("tier-info.requirement-xp",
                "<dark_gray>  • <gray>XP level: <white>%level%");
        TIER_INFO_EXACT_TEMPLATES.put("tier-info.requirement-money",
                "<dark_gray>  • <gray>Money: <green>$%amount%");
        TIER_INFO_EXACT_TEMPLATES.put("tier-info.requirements-items-header",
                "<dark_gray>• <gray>Required items:");
        TIER_INFO_EXACT_TEMPLATES.put("tier-info.requirement-item",
                "<dark_gray>  • <white>%amount%x <gray>%materials% <dark_gray>%name%");
        TIER_INFO_EXACT_TEMPLATES.put("tier-info.chances",
                "<dark_gray>• <gray>Chances: <green>%success%% <dark_gray>/ <red>%break%% <dark_gray>/ <light_purple>%curse%%");
        TIER_INFO_EXACT_TEMPLATES.put("tier-info.variants-header",
                "<dark_gray>• <gray>Variants <white>(%count%)");
        TIER_INFO_EXACT_TEMPLATES.put("tier-info.variant-entry",
                "<dark_gray>  • <white>%variant_id% <dark_gray>— <gray>%name% <dark_gray>(weight <white>%weight%<dark_gray>)");
    }

    @Test
    void maintainedCommandMessageCatalogExistsInBundledMessages() {
        Map<String, Map<String, Object>> messages = loadFlattenedBundledMessages();

        List<String> catalog = new ArrayList<>();
        catalog.addAll(COMMAND_KEYS_ADDED);
        catalog.addAll(STATION_TELEPORT_DYNAMIC_KEYS);
        catalog.addAll(TESTITEM_KEYS);
        catalog.addAll(TIER_INFO_CONTRACT_KEYS);
        catalog.add("tiers.entry");

        for (String key : catalog) {
            assertTrue(messages.containsKey(key),
                    "maintained public command message key missing from messages.yml: " + key);
        }
    }

    @Test
    void stalePriorityKeysAreReplacedByLevelWording() {
        Map<String, Map<String, Object>> messages = loadFlattenedBundledMessages();

        assertTrue(messages.containsKey("setup-tier-create.level-invalid"));
        assertTrue(messages.containsKey("setup-tier-clone.level-invalid"));
        assertFalse(messages.containsKey("setup-tier-create.priority-invalid"));
        assertFalse(messages.containsKey("setup-tier-clone.priority-invalid"));

        String tiersEntry = messageOf(messages, "tiers.entry");
        assertTrue(tiersEntry.contains("%level%"));
        assertFalse(tiersEntry.contains("%priority%"));
    }

    @Test
    void tierInfoTemplatesMatchThePublicContractExactly() {
        Map<String, Map<String, Object>> messages = loadFlattenedBundledMessages();

        for (Map.Entry<String, String> contract : TIER_INFO_EXACT_TEMPLATES.entrySet()) {
            assertEquals(contract.getValue(), messageOf(messages, contract.getKey()),
                    "tier-info template drifted from the public contract: " + contract.getKey());
        }
    }

    @Test
    void representativeCommandMessagesRenderWithoutFailureOrLeakedTokens() {
        Map<String, Map<String, Object>> messages = loadFlattenedBundledMessages();
        Map<String, MessageArguments> representative = representativeMessages();

        for (Map.Entry<String, MessageArguments> entry : representative.entrySet()) {
            String key = entry.getKey();
            assertNotNull(messages.get(key), "missing template for representative key: " + key);
            String template = messageOf(messages, key);

            String rendered = renderer.renderToLegacy(template, entry.getValue().getStringValues());

            assertFalse(rendered.contains("Missing message key"),
                    "missing-key fallback leaked for " + key + ": " + rendered);
            assertFalse(rendered.contains("Message format error"),
                    "MiniMessage parse failure for " + key + ": " + rendered);
            assertFalse(UNRESOLVED_TOKEN.matcher(rendered).find(),
                    "unresolved placeholder token for " + key + ": " + rendered);
        }
    }

    @Test
    void representativeRenderedValuesArePresent() {
        Map<String, Map<String, Object>> messages = loadFlattenedBundledMessages();
        Map<String, MessageArguments> representative = representativeMessages();

        String tierLevel = render(messages, "tier-info.level", representative.get("tier-info.level"));
        assertTrue(tierLevel.contains("7"));

        String chances = render(messages, "tier-info.chances", representative.get("tier-info.chances"));
        assertTrue(chances.contains("90%"));

        String testItemSuccess = render(messages, "testitem.success", representative.get("testitem.success"));
        assertTrue(testItemSuccess.contains("DIAMOND_SWORD"));
        assertTrue(testItemSuccess.contains("my_tier"));
        assertTrue(testItemSuccess.contains("my_variant"));

        String stationTeleportNotFound =
                render(messages, "station-teleport.not-found", representative.get("station-teleport.not-found"));
        assertTrue(stationTeleportNotFound.contains("forge-1"));

        String teleportException =
                render(messages, "tp.teleport-exception", representative.get("tp.teleport-exception"));
        assertTrue(teleportException.contains("boom"));

        String createSuccess =
                render(messages, "setup-tier-create.success", representative.get("setup-tier-create.success"));
        assertTrue(createSuccess.contains("created with level"));

        String stationListEntry =
                render(messages, "station-list.entry", representative.get("station-list.entry"));
        assertTrue(stationListEntry.contains("10,20,30"));
    }

    private static Map<String, MessageArguments> representativeMessages() {
        Map<String, MessageArguments> messages = new LinkedHashMap<>();
        messages.put("tier-info.level", arguments().string("level", "7"));
        messages.put("tier-info.requirements-combine", arguments().string("combine", "ANY"));
        messages.put("tier-info.requirement-xp", arguments().string("level", "30"));
        messages.put("tier-info.requirement-money", arguments().string("amount", "5000"));
        messages.put("tier-info.requirements-items-header", arguments());
        messages.put("tier-info.requirement-item", arguments()
                .string("amount", "3").string("materials", "DIAMOND,IRON_INGOT").string("name", "Test Ingot"));
        messages.put("tier-info.chances", arguments()
                .string("success", "90").string("break", "5").string("curse", "5"));
        messages.put("tier-info.variants-header", arguments().string("count", "2"));
        messages.put("tier-info.variant-entry", arguments()
                .string("variant_id", "my_variant").string("name", "My Variant").string("weight", "10"));
        messages.put("tier-info.header", arguments().string("tier_id", "my_tier"));
        messages.put("tier-info.enabled", arguments().string("enabled", "true"));
        messages.put("tier-info.cooldown", arguments().string("seconds", "60"));
        messages.put("tier-info.not-found", arguments().string("tier_id", "my_tier"));
        messages.put("tiers.header", arguments().string("page", "1").string("total_pages", "3"));
        messages.put("tiers.entry", arguments().string("tier_id", "my_tier").string("level", "7"));
        messages.put("tiers.footer", arguments().string("page", "1").string("total_pages", "3"));
        messages.put("tiers.empty", arguments());
        messages.put("setup.usage", arguments());
        messages.put("setup-tier-create.usage", arguments());
        messages.put("setup-tier-create.level-invalid", arguments());
        messages.put("setup-tier-create.success", arguments().string("tier_id", "my_tier").string("level", "7"));
        messages.put("setup-tier-clone.usage", arguments());
        messages.put("setup-tier-clone.level-invalid", arguments());
        messages.put("setup-tier-clone.failure", arguments());
        messages.put("setup-tier-clone.success", arguments()
                .string("new_id", "new_tier").string("source_id", "my_tier").string("level", "7"));
        messages.put("testitem.usage", arguments());
        messages.put("testitem.player-only", arguments());
        messages.put("testitem.tier-not-found", arguments().string("tier_id", "my_tier"));
        messages.put("testitem.variant-not-found", arguments()
                .string("tier_id", "my_tier").string("variant_id", "my_variant"));
        messages.put("testitem.material-unavailable", arguments().string("material", "DIAMOND"));
        messages.put("testitem.material-category-mismatch", arguments()
                .string("tier_id", "my_tier").string("variant_id", "my_variant")
                .string("material", "DIAMOND").string("category", "weapon").string("required_category", "armor"));
        messages.put("testitem.variant-ineligible", arguments()
                .string("tier_id", "my_tier").string("variant_id", "my_variant").string("material", "DIAMOND"));
        messages.put("testitem.mutation-failed", arguments()
                .string("tier_id", "my_tier").string("variant_id", "my_variant").string("material", "DIAMOND"));
        messages.put("testitem.success", arguments()
                .string("tier_id", "my_tier").string("variant_id", "my_variant").string("material", "DIAMOND_SWORD"));
        messages.put("command.failed", arguments().string("reason", "broken"));
        messages.put("command.player-only", arguments());
        messages.put("command.loading", arguments());
        messages.put("command.unavailable", arguments());
        messages.put("open.no-target", arguments());
        messages.put("open.no-forge-target", arguments());
        messages.put("open.menu-opened", arguments().string("station_id", "forge-1").string("player_name", "Steve"));
        messages.put("station-add.usage", arguments());
        messages.put("station-add.success", arguments()
                .string("station_id", "forge-1").string("world", "world")
                .string("x", "10").string("y", "20").string("z", "30").string("profile", "basic"));
        messages.put("station-remove.usage", arguments());
        messages.put("station-list.entry", arguments()
                .string("station_id", "forge-1").string("world", "world")
                .string("x", "10").string("y", "20").string("z", "30").string("profile", "basic"));
        messages.put("station-info.usage", arguments());
        messages.put("station-info.header", arguments().string("station_id", "forge-1"));
        messages.put("station-info.world", arguments().string("world", "world"));
        messages.put("station-info.location", arguments().string("x", "10").string("y", "20").string("z", "30"));
        messages.put("station-info.profile", arguments().string("profile", "basic"));
        messages.put("station-info.max-tier", arguments().string("max_tier", "5"));
        messages.put("station-info.not-found", arguments().string("station_id", "forge-1"));
        messages.put("station-teleport.usage", arguments());
        messages.put("station-teleport.player-only", arguments());
        messages.put("station-teleport.not-found", arguments().string("station_id", "forge-1"));
        messages.put("station-teleport.player-offline", arguments());
        messages.put("station-teleport.world-not-found", arguments());
        messages.put("station-teleport.world-not-loaded", arguments());
        messages.put("station-teleport.teleport-rejected", arguments());
        messages.put("station-teleport.teleport-exception", arguments().string("reason", "boom"));
        messages.put("station-teleport.player-retired", arguments());
        messages.put("station-teleport.scheduler-rejected", arguments());
        messages.put("tp.usage", arguments());
        messages.put("tp.player-only", arguments());
        messages.put("tp.not-found", arguments().string("station_id", "forge-1"));
        messages.put("tp.teleport-exception", arguments().string("reason", "boom"));
        messages.put("tp.player-offline", arguments());
        messages.put("tp.world-not-found", arguments());
        messages.put("tp.world-not-loaded", arguments());
        messages.put("tp.teleport-rejected", arguments());
        messages.put("tp.player-retired", arguments());
        messages.put("tp.scheduler-rejected", arguments());
        messages.put("history.header", arguments().string("player_name", "Steve"));
        messages.put("history.current-tier", arguments().string("tier", "5"));
        messages.put("history.no-history", arguments().string("player_name", "Steve"));
        messages.put("history.usage", arguments());
        messages.put("preview.usage", arguments());
        messages.put("preview.tier-not-found", arguments().string("tier_id", "my_tier"));
        messages.put("preview.unknown-material", arguments().string("material", "NOT_A_MATERIAL"));
        messages.put("preview.no-item", arguments());
        messages.put("preview.no-variants", arguments());
        messages.put("preview.no-eligible-variant", arguments());
        messages.put("preview.variant", arguments()
                .string("tier_id", "my_tier").string("variant_id", "my_variant").string("variant_name", "My Variant"));
        messages.put("preview.material", arguments().string("material", "DIAMOND_SWORD"));
        return messages;
    }

    private static MessageArguments arguments() {
        return MessageArguments.create();
    }

    private static String render(Map<String, Map<String, Object>> messages, String key,
                                 MessageArguments arguments) {
        return new TextRenderer().renderToLegacy(messageOf(messages, key), arguments.getStringValues());
    }

    private static String messageOf(Map<String, Map<String, Object>> messages, String key) {
        Map<String, Object> settings = messages.get(key);
        assertNotNull(settings, "missing message settings for key: " + key);
        Object message = settings.get("message");
        assertNotNull(message, "missing 'message' value for key: " + key);
        return String.valueOf(message);
    }

    private static Map<String, Map<String, Object>> loadFlattenedBundledMessages() {
        InputStream stream = MessageCatalogCoverageTest.class.getClassLoader()
                .getResourceAsStream(BUNDLED_MESSAGES_RESOURCE);
        assertNotNull(stream, "bundled resource missing on test classpath: " + BUNDLED_MESSAGES_RESOURCE);
        try (InputStream input = stream;
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return MessageTemplateLoader.flatten(YamlConfiguration.loadConfiguration(reader));
        } catch (IOException failure) {
            throw new AssertionError("failed to read bundled messages.yml", failure);
        }
    }
}
