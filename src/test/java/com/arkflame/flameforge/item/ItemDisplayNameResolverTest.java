package com.arkflame.flameforge.item;

import com.arkflame.flameforge.config.ConfigService;
import com.arkflame.flameforge.config.ConfigSnapshot;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItemDisplayNameResolverTest {

    @Test
    void configuredDisplayNameWinsOverIdentityBaseName() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .putRoot("item-display-names.DIAMOND_SWORD", "Excalibur")
                .putRoot("forge.prefer-configured-display-name", true)
                .build();
        ConfigService configService = mock(ConfigService.class);
        when(configService.getCurrentSnapshot()).thenReturn(snapshot);
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        ItemDisplayNameResolver resolver = new ItemDisplayNameResolver(identityService, configService);

        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty()
                .withBaseDisplayName("Diamond Sword");

        assertEquals("Excalibur", resolver.resolve(new ItemStack(Material.DIAMOND_SWORD), identity));
    }

    @Test
    void identityBaseNameWinsWhenPreferConfiguredDisabled() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder()
                .putRoot("item-display-names.DIAMOND_SWORD", "Excalibur")
                .putRoot("forge.prefer-configured-display-name", false)
                .build();
        ConfigService configService = mock(ConfigService.class);
        when(configService.getCurrentSnapshot()).thenReturn(snapshot);
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        ItemDisplayNameResolver resolver = new ItemDisplayNameResolver(identityService, configService);

        ItemIdentityCodec.Identity identity = ItemIdentityCodec.Identity.empty()
                .withBaseDisplayName("Diamond Sword");

        assertEquals("Diamond Sword", resolver.resolve(new ItemStack(Material.DIAMOND_SWORD), identity));
    }

    @Test
    void fallsBackToDefaultBaseName() {
        ConfigSnapshot snapshot = ConfigSnapshot.builder().build();
        ConfigService configService = mock(ConfigService.class);
        when(configService.getCurrentSnapshot()).thenReturn(snapshot);
        ItemIdentityService identityService = mock(ItemIdentityService.class);
        when(identityService.defaultBaseDisplayName(Material.DIAMOND_SWORD)).thenReturn("Fallback");
        ItemDisplayNameResolver resolver = new ItemDisplayNameResolver(identityService, configService);

        assertEquals("Fallback", resolver.resolve(new ItemStack(Material.DIAMOND_SWORD), null));
    }
}
