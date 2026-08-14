package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.compat.effect.ParticleBridge;
import com.arkflame.flameforge.compat.effect.PotionEffectResolver;
import com.arkflame.flameforge.compat.equipment.EquipmentBridge;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.config.TierRepository;
import com.arkflame.flameforge.item.ItemIdentityCodec;
import com.arkflame.flameforge.item.ItemIdentityService;
import com.arkflame.flameforge.model.ForgePowerDefinition;
import com.arkflame.flameforge.model.ForgeVariant;
import com.arkflame.flameforge.model.TierDefinition;
import com.arkflame.flameforge.testfakes.FakeSchedulerBridge;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

class ForgePowerServiceTest {
    @Test
    void cooldownAndChanceGatePowerActivation() {
        SchedulerBridge scheduler = new FakeSchedulerBridge();
        ForgePowerService service = service(scheduler, mock(PotionEffectResolver.class),
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), mock(MultiStrikeService.class),
            mock(ParticleBridge.class), mock(TierRepository.class));
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
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), mock(MultiStrikeService.class),
            mock(ParticleBridge.class), mock(TierRepository.class));
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
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), multiStrike,
            mock(ParticleBridge.class), mock(TierRepository.class));
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

        verify(multiStrike).executeRadial(eq(attacker), eq(victim), any(), eq(false), any());
        verify(multiStrike).executeChain(eq(attacker), eq(victim), any(), eq(false), any());
    }

    @Test
    void passiveAndShiftPowersRespectActivationContext() {
        EquipmentBridge equipment = mock(EquipmentBridge.class);
        ItemIdentityService identity = mock(ItemIdentityService.class);
        SchedulerBridge scheduler = mock(SchedulerBridge.class);
        ForgePowerService service = service(scheduler, mock(PotionEffectResolver.class), equipment, identity,
            mock(MultiStrikeService.class), mock(ParticleBridge.class), mock(TierRepository.class));
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

    @Test
    void inventoryCacheReturnsDefensiveCopy() {
        EquipmentBridge equipment = mock(EquipmentBridge.class);
        ItemIdentityService identity = mock(ItemIdentityService.class);
        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        UUID forgeId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        ItemStack item = mock(ItemStack.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(identity.readForgeIdentity(item)).thenReturn(new ItemIdentityService.ForgeIdentityRead(
            ItemIdentityService.ForgeIdentityStatus.VALID,
            ItemIdentityCodec.Identity.empty().withForgeId(forgeId)));
        when(equipment.getInventoryContents(player)).thenReturn(new ItemStack[] {item});
        when(equipment.getArmorContents(player)).thenReturn(new ItemStack[0]);
        ForgePowerService service = service(new FakeSchedulerBridge(), mock(PotionEffectResolver.class),
            equipment, identity, mock(MultiStrikeService.class), mock(ParticleBridge.class),
            mock(TierRepository.class));

        service.refreshInventoryCache(player);
        Set<UUID> cached = service.getCachedInventoryForgeIds(player);
        cached.clear();

        assertTrue(service.hasCachedInventoryForgeId(player, forgeId));
    }

    @Test
    void onHitPoisonAppliesToNonPlayerVictim() {
        PotionEffectResolver resolver = mock(PotionEffectResolver.class);
        when(resolver.resolve(Collections.singletonList("POISON")))
            .thenReturn(Optional.of(PotionEffectType.POISON));
        ForgePowerService service = service(new FakeSchedulerBridge(), resolver,
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), mock(MultiStrikeService.class),
            mock(ParticleBridge.class), mock(TierRepository.class));
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        LivingEntity cow = mock(LivingEntity.class);
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getId()).thenReturn("poison");
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_POTION);
        when(power.getChance()).thenReturn(BigDecimal.ONE);
        when(power.getCooldownTicks()).thenReturn(0);
        when(power.getEffectCandidates()).thenReturn(Collections.singletonList("POISON"));
        when(power.getDurationTicks()).thenReturn(100);
        when(power.getAmplifier()).thenReturn(0);

        assertTrue(service.triggerOnHitPower(attacker, cow, power, UUID.randomUUID()));

        ArgumentCaptor<PotionEffect> captor = ArgumentCaptor.forClass(PotionEffect.class);
        verify(cow).addPotionEffect(captor.capture());
        assertEquals(PotionEffectType.POISON, captor.getValue().getType());
    }

    @Test
    void scorchingAoeFireSetsNearbyMobsOnFire() {
        MultiStrikeService multiStrike = mock(MultiStrikeService.class);
        ForgePowerService service = service(new FakeSchedulerBridge(), mock(PotionEffectResolver.class),
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), multiStrike,
            mock(ParticleBridge.class), mock(TierRepository.class));
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getLocation()).thenReturn(new Location(mock(World.class), 0, 0, 0));
        LivingEntity zombie = mock(LivingEntity.class);
        when(zombie.getLocation()).thenReturn(new Location(mock(World.class), 0, 0, 0));
        doAnswer(invocation -> {
            MultiStrikeService.StrikeAction action = invocation.getArgument(4);
            action.apply(zombie);
            return null;
        }).when(multiStrike).executeRadial(any(), any(), any(), anyBoolean(), any());

        ForgePowerDefinition aoe = mock(ForgePowerDefinition.class);
        when(aoe.getId()).thenReturn("aoe");
        when(aoe.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_AOE_FIRE);
        when(aoe.getChance()).thenReturn(BigDecimal.ONE);
        when(aoe.getCooldownTicks()).thenReturn(0);
        when(aoe.getFireTicks()).thenReturn(60);

        assertTrue(service.triggerOnHitPower(attacker, victim, aoe, UUID.randomUUID()));

        verify(multiStrike).executeRadial(eq(attacker), eq(victim), eq(aoe), eq(false), any());
        verify(zombie).setFireTicks(60);
    }

    @Test
    void explosiveEmitsParticlesAndDamagesSecondariesWithoutBukkitExplosion() {
        World world = mock(World.class);
        MultiStrikeService multiStrike = mock(MultiStrikeService.class);
        ParticleBridge particles = mock(ParticleBridge.class);
        ForgePowerService service = service(new FakeSchedulerBridge(), mock(PotionEffectResolver.class),
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), multiStrike,
            particles, mock(TierRepository.class));
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getLocation()).thenReturn(new Location(world, 0, 0, 0));
        LivingEntity zombie = mock(LivingEntity.class);
        when(zombie.getLocation()).thenReturn(new Location(world, 5, 0, 5));
        doAnswer(invocation -> {
            MultiStrikeService.StrikeAction action = invocation.getArgument(4);
            action.apply(victim);
            action.apply(zombie);
            return null;
        }).when(multiStrike).executeRadial(any(), any(), any(), anyBoolean(), any());

        ForgePowerDefinition explosive = mock(ForgePowerDefinition.class);
        when(explosive.getId()).thenReturn("explosive");
        when(explosive.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_EXPLOSIVE);
        when(explosive.getChance()).thenReturn(BigDecimal.ONE);
        when(explosive.getCooldownTicks()).thenReturn(0);
        when(explosive.getDamageAmount()).thenReturn(new BigDecimal("5"));
        when(explosive.getSecondaryDamageMultiplier()).thenReturn(new BigDecimal("0.5"));
        when(explosive.getPrimaryKnockbackMultiplier()).thenReturn(BigDecimal.ZERO);

        assertTrue(service.triggerOnHitPower(attacker, victim, explosive, UUID.randomUUID()));

        verify(multiStrike).executeRadial(eq(attacker), eq(victim), any(), eq(false), any());
        verify(victim).damage(5.0);
        verify(zombie).damage(2.5);
        verify(particles, times(2)).sendToPlayer(eq(attacker), eq("EXPLOSION"), any(Location.class),
            eq(0F), eq(0F), eq(0F), eq(0F), eq(1));
        verify(world, never()).createExplosion(any(Location.class), anyFloat());
    }

    @Test
    void contagionChainRoutesPoisonThroughStrikeServiceForMobs() {
        PotionEffectResolver resolver = mock(PotionEffectResolver.class);
        when(resolver.resolve(Collections.singletonList("POISON")))
            .thenReturn(Optional.of(PotionEffectType.POISON));
        MultiStrikeService multiStrike = mock(MultiStrikeService.class);
        ForgePowerService service = service(new FakeSchedulerBridge(), resolver,
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), multiStrike,
            mock(ParticleBridge.class), mock(TierRepository.class));
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getLocation()).thenReturn(new Location(mock(World.class), 0, 0, 0));
        LivingEntity cow = mock(LivingEntity.class);
        when(cow.getLocation()).thenReturn(new Location(mock(World.class), 3, 0, 0));
        doAnswer(invocation -> {
            MultiStrikeService.StrikeAction action = invocation.getArgument(4);
            action.apply(cow);
            return null;
        }).when(multiStrike).executeChain(any(), any(), any(), anyBoolean(), any());

        ForgePowerDefinition chain = mock(ForgePowerDefinition.class);
        when(chain.getId()).thenReturn("contagion");
        when(chain.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_CHAIN_POTION);
        when(chain.getChance()).thenReturn(BigDecimal.ONE);
        when(chain.getCooldownTicks()).thenReturn(0);
        when(chain.getEffectCandidates()).thenReturn(Collections.singletonList("POISON"));
        when(chain.getDurationTicks()).thenReturn(100);
        when(chain.getAmplifier()).thenReturn(0);

        assertTrue(service.triggerOnHitPower(attacker, victim, chain, UUID.randomUUID()));

        verify(multiStrike).executeChain(eq(attacker), eq(victim), eq(chain), eq(false), any());
        ArgumentCaptor<PotionEffect> captor = ArgumentCaptor.forClass(PotionEffect.class);
        verify(cow).addPotionEffect(captor.capture());
        assertEquals(PotionEffectType.POISON, captor.getValue().getType());
    }

    @Test
    void electricEveryNHitEmitsParticlesAndStrikesLightning() {
        World world = mock(World.class);
        ParticleBridge particles = mock(ParticleBridge.class);
        ForgePowerService service = service(new FakeSchedulerBridge(), mock(PotionEffectResolver.class),
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), mock(MultiStrikeService.class),
            particles, mock(TierRepository.class));
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getLocation()).thenReturn(new Location(world, 10, 20, 30));

        ForgePowerDefinition lightning = mock(ForgePowerDefinition.class);
        when(lightning.getId()).thenReturn("electric");
        when(lightning.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.EVERY_N_HIT_LIGHTNING);
        when(lightning.getHitInterval()).thenReturn(1);
        when(lightning.getChance()).thenReturn(BigDecimal.ONE);
        when(lightning.getCooldownTicks()).thenReturn(0);

        assertTrue(service.triggerOnHitPower(attacker, victim, lightning, UUID.randomUUID()));

        verify(world).strikeLightning(any(Location.class));
        verify(particles).sendToPlayer(eq(attacker), eq("ELECTRIC_SPARK"), any(Location.class),
            eq(0F), eq(0F), eq(0F), eq(0F), eq(1));
    }

    @Test
    void healEmitsHeartParticlesAroundHealedPlayer() {
        ParticleBridge particles = mock(ParticleBridge.class);
        ForgePowerService service = service(mock(SchedulerBridge.class), mock(PotionEffectResolver.class),
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), mock(MultiStrikeService.class),
            particles, mock(TierRepository.class));
        Player player = mock(Player.class);
        UUID forgeId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getMaxHealth()).thenReturn(20.0);
        when(player.getHealth()).thenReturn(10.0);
        when(player.getLocation()).thenReturn(new Location(mock(World.class), 0, 0, 0));

        ForgePowerDefinition heal = mock(ForgePowerDefinition.class);
        when(heal.getId()).thenReturn("heal");
        when(heal.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_HEAL);
        when(heal.getCooldownTicks()).thenReturn(0);
        when(heal.getHealAmount()).thenReturn(BigDecimal.ONE);

        assertTrue(service.activateHeal(player, heal, forgeId));

        verify(player).setHealth(11.0);
        verify(particles).sendToPlayer(eq(player), eq("HEART"), any(Location.class),
            eq(0F), eq(0F), eq(0F), eq(0F), eq(1));
        verify(particles).sendColoredDust(eq(player), any(Location.class),
            eq(244), eq(114), eq(182), anyFloat(), anyInt());
    }

    @Test
    void dashEmitsSemanticParticlesWhenVelocityApplied() {
        ParticleBridge particles = mock(ParticleBridge.class);
        ForgePowerService service = service(mock(SchedulerBridge.class), mock(PotionEffectResolver.class),
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), mock(MultiStrikeService.class),
            particles, mock(TierRepository.class));
        Player player = mock(Player.class);
        UUID forgeId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getLocation()).thenReturn(new Location(mock(World.class), 0, 0, 0));

        ForgePowerDefinition dash = mock(ForgePowerDefinition.class);
        when(dash.getId()).thenReturn("dash");
        when(dash.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_DASH);
        when(dash.getCooldownTicks()).thenReturn(0);
        when(dash.getHorizontalStrength()).thenReturn(BigDecimal.ONE);
        when(dash.getVerticalStrength()).thenReturn(BigDecimal.ZERO);

        assertTrue(service.activateDash(player, dash, forgeId));

        verify(player).setVelocity(any(Vector.class));
        verify(particles).sendToPlayer(eq(player), eq("INSTANT_EFFECT"), any(Location.class),
            eq(0F), eq(0F), eq(0F), eq(0F), eq(1));
        verify(particles).sendColoredDust(eq(player), any(Location.class),
            eq(56), eq(189), eq(248), anyFloat(), anyInt());
    }

    @Test
    void passiveActivationParticleEmitsOnceNotPerRefreshTick() {
        SchedulerBridge scheduler = mock(SchedulerBridge.class);
        ParticleBridge particles = mock(ParticleBridge.class);
        PotionEffectResolver resolver = mock(PotionEffectResolver.class);
        when(resolver.resolve(Collections.singletonList("REGENERATION")))
            .thenReturn(Optional.of(PotionEffectType.REGENERATION));
        EquipmentBridge equipment = mock(EquipmentBridge.class);
        ItemIdentityService identity = mock(ItemIdentityService.class);
        TierRepository tiers = mock(TierRepository.class);
        ForgePowerService service = service(scheduler, resolver, equipment, identity,
            mock(MultiStrikeService.class), particles, tiers);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        UUID forgeId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(mock(World.class), 0, 0, 0));

        ItemStack item = mock(ItemStack.class);
        when(item.hasItemMeta()).thenReturn(true);
        when(identity.readForgeIdentity(item)).thenReturn(new ItemIdentityService.ForgeIdentityRead(
            ItemIdentityService.ForgeIdentityStatus.VALID,
            ItemIdentityCodec.Identity.empty()
                .withForgeId(forgeId)
                .withLastTierId("tier1")
                .withLastVariantId("variant1")
                .withActivePowerIds(Collections.singletonList("passive"))));
        when(equipment.getInventoryContents(player)).thenReturn(new ItemStack[] {item});
        when(equipment.getArmorContents(player)).thenReturn(new ItemStack[0]);
        when(equipment.getItem(player, EquipmentBridge.Slot.MAINHAND)).thenReturn(null);
        when(equipment.getItem(player, EquipmentBridge.Slot.OFFHAND)).thenReturn(null);

        TierDefinition tier = mock(TierDefinition.class);
        when(tiers.findById("tier1")).thenReturn(Optional.of(tier));
        ForgeVariant variant = mock(ForgeVariant.class);
        when(variant.getId()).thenReturn("variant1");
        when(tier.getVariants()).thenReturn(Collections.singletonList(variant));
        ForgePowerDefinition passive = mock(ForgePowerDefinition.class);
        when(passive.getId()).thenReturn("passive");
        when(passive.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.PASSIVE_POTION);
        when(passive.getCooldownTicks()).thenReturn(0);
        when(passive.getEffectCandidates()).thenReturn(Collections.singletonList("REGENERATION"));
        when(passive.getActivationSlots())
            .thenReturn(Collections.singletonList(ForgePowerDefinition.ActivationSlot.INVENTORY));
        when(passive.getDurationTicks()).thenReturn(40);
        when(passive.getAmplifier()).thenReturn(0);
        when(variant.getPowers()).thenReturn(Collections.singletonList(passive));

        AtomicReference<Runnable> refreshTask = new AtomicReference<>();
        when(scheduler.runEntityLater(any(Entity.class), any(Runnable.class), any(Runnable.class), anyLong()))
            .thenAnswer(invocation -> {
                refreshTask.set(invocation.getArgument(1));
                return mock(TaskHandle.class);
            });

        service.refreshPassivePowers(player);

        verify(particles, times(1)).sendToPlayer(eq(player), eq("HEART"), any(Location.class),
            eq(0F), eq(0F), eq(0F), eq(0F), eq(1));

        Runnable refresh = refreshTask.get();
        assertNotNull(refresh);
        refresh.run();
        refresh.run();

        verify(particles, times(1)).sendToPlayer(eq(player), eq("HEART"), any(Location.class),
            eq(0F), eq(0F), eq(0F), eq(0F), eq(1));
    }

    @Test
    void armorReductionParticleEmitsAtPlayer() {
        ParticleBridge particles = mock(ParticleBridge.class);
        ForgePowerService service = service(mock(SchedulerBridge.class), mock(PotionEffectResolver.class),
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), mock(MultiStrikeService.class),
            particles, mock(TierRepository.class));
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(mock(World.class), 0, 0, 0));

        service.emitArmorReductionParticle(player);

        verify(particles).sendToPlayer(eq(player), eq("ENCHANT"), any(Location.class),
            eq(0F), eq(0F), eq(0F), eq(0F), eq(1));
        verify(particles).sendColoredDust(eq(player), any(Location.class),
            eq(96), eq(165), eq(250), anyFloat(), anyInt());
    }

    private static ForgePowerService service(SchedulerBridge scheduler, PotionEffectResolver resolver,
                                              EquipmentBridge equipment, ItemIdentityService identity,
                                              MultiStrikeService multiStrike, ParticleBridge particles,
                                              TierRepository tiers) {
        return new ForgePowerService(mock(JavaPlugin.class), scheduler, particles, resolver, equipment,
            identity, multiStrike, tiers);
    }
}
