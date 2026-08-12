package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.compat.effect.ParticleBridge;
import com.arkflame.flameforge.compat.effect.PotionEffectResolver;
import com.arkflame.flameforge.compat.equipment.EquipmentBridge;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.ForgePowerDefinition;
import com.arkflame.flameforge.testfakes.FakeSchedulerBridge;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ForgePowerServiceTest {
    @Test
    void cooldownAndChanceGatePowerActivation() {
        SchedulerBridge scheduler = new FakeSchedulerBridge();
        ForgePowerService service = service(scheduler, mock(PotionEffectResolver.class),
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), mock(MultiStrikeService.class));
        Player attacker = mock(Player.class);
        LivingEntity victim = mock(LivingEntity.class);
        UUID forgeId = UUID.randomUUID();
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getId()).thenReturn("fire");
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_FIRE);
        when(power.getCooldownTicks()).thenReturn(100);
        when(power.getChance()).thenReturn(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE);
        when(power.getFireTicks()).thenReturn(20);

        assertFalse(service.triggerOnHitPower(attacker, victim, power, forgeId));
        assertTrue(service.triggerOnHitPower(attacker, victim, power, forgeId));
        assertFalse(service.triggerOnHitPower(attacker, victim, power, forgeId));
        verify(victim).setFireTicks(20);
    }

    @Test
    void onHitPowersApplyOwnedEffectsWithoutRecursiveDamage() {
        PotionEffectResolver resolver = mock(PotionEffectResolver.class);
        when(resolver.resolve(Collections.singletonList("SPEED")))
            .thenReturn(Optional.of(PotionEffectType.SPEED));
        ForgePowerService service = service(new FakeSchedulerBridge(), resolver,
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), mock(MultiStrikeService.class));
        Player attacker = mock(Player.class);
        LivingEntity victim = mock(LivingEntity.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getId()).thenReturn("potion");
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_POTION);
        when(power.getCooldownTicks()).thenReturn(0);
        when(power.getChance()).thenReturn(BigDecimal.ONE);
        when(power.getEffectCandidates()).thenReturn(Collections.singletonList("SPEED"));
        when(power.getDurationTicks()).thenReturn(40);
        when(power.getAmplifier()).thenReturn(1);

        assertTrue(service.triggerOnHitPower(attacker, victim, power, UUID.randomUUID()));
        verify(victim).addPotionEffect(any());
        verify(attacker, never()).damage(anyDouble());
    }

    @Test
    void chainAndAoePowersDelegateThroughSchedulerSafely() {
        MultiStrikeService multiStrike = mock(MultiStrikeService.class);
        ForgePowerService service = service(new FakeSchedulerBridge(), mock(PotionEffectResolver.class),
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), multiStrike);
        Player attacker = mock(Player.class);
        LivingEntity victim = mock(LivingEntity.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        UUID forgeId = UUID.randomUUID();

        ForgePowerDefinition aoe = mock(ForgePowerDefinition.class);
        when(aoe.getId()).thenReturn("aoe");
        when(aoe.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_AOE_FIRE);
        when(aoe.getChance()).thenReturn(BigDecimal.ONE);
        when(aoe.getCooldownTicks()).thenReturn(0);
        when(aoe.getFireTicks()).thenReturn(20);
        assertTrue(service.triggerOnHitPower(attacker, victim, aoe, forgeId));

        ForgePowerDefinition chain = mock(ForgePowerDefinition.class);
        when(chain.getId()).thenReturn("chain");
        when(chain.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_CHAIN_DAMAGE);
        when(chain.getChance()).thenReturn(BigDecimal.ONE);
        when(chain.getCooldownTicks()).thenReturn(0);
        when(chain.getDamageAmount()).thenReturn(BigDecimal.ONE);
        assertTrue(service.triggerOnHitPower(attacker, victim, chain, forgeId));

        verify(multiStrike, times(2)).execute(eq(attacker), eq(victim), any(), anyBoolean(), any());
    }

    @Test
    void passiveAndShiftPowersRespectActivationContext() {
        EquipmentBridge equipment = mock(EquipmentBridge.class);
        ItemIdentityService identity = mock(ItemIdentityService.class);
        SchedulerBridge scheduler = mock(SchedulerBridge.class);
        ForgePowerService service = service(scheduler, mock(PotionEffectResolver.class), equipment, identity,
            mock(MultiStrikeService.class));
        Player player = mock(Player.class);
        UUID forgeId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        ItemStack equipped = mock(ItemStack.class);
        when(equipped.hasItemMeta()).thenReturn(true);
        when(equipment.getItem(player, EquipmentBridge.Slot.MAINHAND)).thenReturn(equipped);
        when(equipment.getItem(player, EquipmentBridge.Slot.OFFHAND)).thenReturn(null);
        when(identity.readForgeIdentity(equipped)).thenReturn(new ItemIdentityService.ForgeIdentityRead(
            ItemIdentityService.ForgeIdentityStatus.VALID,
            ItemIdentityCodec.Identity.empty().withForgeId(forgeId)));

        ForgePowerDefinition passive = mock(ForgePowerDefinition.class);
        when(passive.getId()).thenReturn("passive");
        when(passive.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.PASSIVE_POTION);
        when(passive.getCooldownTicks()).thenReturn(0);
        when(passive.getActivationSlots()).thenReturn(Collections.emptyList());
        when(passive.getEffectCandidates()).thenReturn(Collections.emptyList());
        assertTrue(service.activatePassivePower(player, passive, forgeId));

        ForgePowerDefinition dash = mock(ForgePowerDefinition.class);
        when(dash.getId()).thenReturn("dash");
        when(dash.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_DASH);
        when(dash.getCooldownTicks()).thenReturn(0);
        assertFalse(service.activateHeal(player, dash, forgeId));

        ForgePowerDefinition heal = mock(ForgePowerDefinition.class);
        when(heal.getId()).thenReturn("heal");
        when(heal.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_HEAL);
        when(heal.getCooldownTicks()).thenReturn(0);
        when(heal.getHealAmount()).thenReturn(BigDecimal.ONE);
        when(player.getMaxHealth()).thenReturn(20.0);
        when(player.getHealth()).thenReturn(10.0);
        assertTrue(service.activateHeal(player, heal, forgeId));
        verify(player).setHealth(11.0);
    }

    private static ForgePowerService service(SchedulerBridge scheduler, PotionEffectResolver resolver,
                                              EquipmentBridge equipment, ItemIdentityService identity,
                                              MultiStrikeService multiStrike) {
        return new ForgePowerService(mock(JavaPlugin.class), scheduler, resolver, equipment, identity,
            ParticleBridge.getInstance(), multiStrike);
    }
}
