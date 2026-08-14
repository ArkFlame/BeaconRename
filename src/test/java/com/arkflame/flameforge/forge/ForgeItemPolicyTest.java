package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.config.ValidationReport;
import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.PlayerForgeState;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ForgeItemPolicyTest {

    @TempDir
    Path tempDir;

    @Test
    void eligibleVanillaItemPassesAndDisallowedCustomizationFails() {
        ForgeItemInspection inspection = mock(ForgeItemInspection.class);
        ForgeItemPolicy policy = new ForgeItemPolicy(inspection);
        Player player = mock(Player.class);
        PlayerForgeState state = PlayerForgeState.of("player");
        ItemStack item = mock(ItemStack.class);
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty();

        when(inspection.inspect(player, state, item)).thenReturn(
            new ForgeItemInspection.InspectionResult(ForgeItemInspection.Status.READY, identity));
        assertTrue(policy.checkItem(player, state, item).isAllowed());

        when(inspection.inspect(player, state, item)).thenReturn(
            new ForgeItemInspection.InspectionResult(ForgeItemInspection.Status.CUSTOM_NAME, identity));
        ForgeItemPolicy.PolicyResult denied = policy.checkItem(player, state, item);
        assertFalse(denied.isAllowed());
        assertEquals("menu.item-denied.customized", denied.getMessageKey());
    }

    @Test
    void tierMaterialAndPlayerRequirementsGateReadiness() {
        ForgeItemInspection inspection = mock(ForgeItemInspection.class);
        ForgeItemPolicy policy = new ForgeItemPolicy(inspection);
        Player player = mock(Player.class);
        PlayerForgeState state = PlayerForgeState.of("player");
        ItemStack item = mock(ItemStack.class);
        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty();

        when(inspection.inspect(player, state, item)).thenReturn(
            new ForgeItemInspection.InspectionResult(ForgeItemInspection.Status.DENIED_MATERIAL, identity));
        assertFalse(policy.isReady(player, state, item));

        when(inspection.inspect(player, state, item)).thenReturn(
            new ForgeItemInspection.InspectionResult(ForgeItemInspection.Status.TIER_PERMISSION_REQUIRED, identity));
        ForgeItemPolicy.PolicyResult denied = policy.checkItem(player, state, item);
        assertFalse(denied.isAllowed());
        assertEquals("menu.item-denied.permission", denied.getMessageKey());
    }

    @Test
    void equipmentCategoryGroupAcceptsSwordButNotWool() throws Exception {
        ForgeVariantEligibility eligibility = loadedEligibility();

        ForgeVariant weaponVariant = variant("weapon-v", "WEAPON");
        ItemStack sword = itemStack(Material.DIAMOND_SWORD);
        ItemStack wool = itemStack(Material.WOOL);

        assertTrue(eligibility.isEligible(sword, weaponVariant));
        assertFalse(eligibility.isEligible(wool, weaponVariant));
    }

    @Test
    void amuletFallbackGroupAcceptsWoolButNotSword() throws Exception {
        ForgeVariantEligibility eligibility = loadedEligibility();

        ForgeVariant amuletVariant = variant("amulet-v", "AMULET");
        ItemStack sword = itemStack(Material.DIAMOND_SWORD);
        ItemStack wool = itemStack(Material.WOOL);

        assertTrue(eligibility.isEligible(wool, amuletVariant));
        assertFalse(eligibility.isEligible(sword, amuletVariant));
    }

    @Test
    void legacyMaterialGroupStillWorksForCustomSwordGroup() throws Exception {
        ForgeVariantEligibility eligibility = loadedEligibility();

        ForgeVariant swordVariant = variant("sword-v", "SWORD");
        ItemStack sword = itemStack(Material.DIAMOND_SWORD);
        ItemStack wool = itemStack(Material.WOOL);

        assertTrue(eligibility.isEligible(sword, swordVariant));
        assertFalse(eligibility.isEligible(wool, swordVariant));
    }

    @Test
    void emptyAndAnyGroupsRemainGloballyEligible() throws Exception {
        ForgeVariantEligibility eligibility = loadedEligibility();

        ForgeVariant emptyVariant = new ForgeVariant("empty-v", "", Collections.emptyList(), 1.0, null,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        ForgeVariant anyVariant = variant("any-v", "ANY");

        ItemStack wool = itemStack(Material.WOOL);

        assertTrue(eligibility.isEligible(wool, emptyVariant));
        assertTrue(eligibility.isEligible(wool, anyVariant));
    }

    private ForgeVariantEligibility loadedEligibility() throws IOException {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        stubAllBundledResources(plugin);
        Files.createDirectories(tempDir.resolve("tiers"));

        TierRepository repository = new TierRepository(plugin);
        ValidationReport report = repository.load();
        assertFalse(report.hasErrors());

        ItemIdentityService identityService = mock(ItemIdentityService.class);
        when(identityService.matchesMaterialGroup(Material.DIAMOND_SWORD, "SWORD")).thenReturn(true);
        return new ForgeVariantEligibility(identityService, repository);
    }

    private ForgeVariant variant(String id, String group) {
        return new ForgeVariant(id, "", Collections.emptyList(), 1.0, null,
            Collections.singletonList(group), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    private ItemStack itemStack(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        return item;
    }

    private void stubAllBundledResources(JavaPlugin plugin) {
        stubBundledResource(plugin, "equipment.yml");
        for (int level = 1; level <= 7; level++) {
            stubBundledResource(plugin, "tiers/tier" + level + ".yml");
            for (String category : new String[]{"weapon", "armor", "shield", "amulet"}) {
                stubBundledResource(plugin, "tiers/" + category + "_tier" + level + ".yml");
            }
        }
    }

    private void stubBundledResource(JavaPlugin plugin, String path) {
        InputStream source = getClass().getClassLoader().getResourceAsStream(path);
        assertNotNull(source);
        try {
            byte[] bytes = readBytes(source);
            when(plugin.getResource(path))
                .thenAnswer(invocation -> new ByteArrayInputStream(bytes.clone()));
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private byte[] readBytes(InputStream source) throws IOException {
        try (InputStream input = source; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[256];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }
}
