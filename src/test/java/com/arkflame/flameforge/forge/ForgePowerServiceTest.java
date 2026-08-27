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
import org.bukkit.Material;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

class ForgePowerServiceTest {
    @Test
    void drainingOnHitReactivatesAtExactCooldownExpiryAcrossTargets() {
        AtomicLong now = new AtomicLong(0L);
        ForgePowerService service = timedService(now, () -> false, mock(JavaPlugin.class));
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(attacker.getMaxHealth()).thenReturn(20.0);
        when(attacker.getHealth()).thenReturn(10.0);
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getId()).thenReturn("draining");
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_HEAL);
        when(power.getCooldownTicks()).thenReturn(60);
        when(power.getChance()).thenReturn(BigDecimal.ONE);
        when(power.getHealAmount()).thenReturn(new BigDecimal("0.5"));
        UUID forgeId = UUID.randomUUID();

        assertTrue(service.triggerOnHitPower(attacker, mock(LivingEntity.class), power, forgeId));
        now.set(2999L);
        assertFalse(service.triggerOnHitPower(attacker, mock(LivingEntity.class), power, forgeId));
        now.set(3000L);
        assertTrue(service.triggerOnHitPower(attacker, mock(LivingEntity.class), power, forgeId));
        now.set(6000L);
        assertTrue(service.triggerOnHitPower(attacker, mock(LivingEntity.class), power, forgeId));

        verify(attacker, times(3)).setHealth(10.5);
    }

    @Test
    void chanceMissDoesNotConsumeCooldown() {
        AtomicLong now = new AtomicLong(0L);
        ForgePowerService service = timedService(now, () -> false, mock(JavaPlugin.class));
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(attacker.getMaxHealth()).thenReturn(20.0);
        when(attacker.getHealth()).thenReturn(10.0);
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getId()).thenReturn("chance");
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_HEAL);
        when(power.getCooldownTicks()).thenReturn(60);
        when(power.getChance()).thenReturn(BigDecimal.ZERO, BigDecimal.ONE);
        when(power.getHealAmount()).thenReturn(BigDecimal.ONE);
        UUID forgeId = UUID.randomUUID();

        assertFalse(service.triggerOnHitPower(attacker, mock(LivingEntity.class), power, forgeId));
        assertTrue(service.triggerOnHitPower(attacker, mock(LivingEntity.class), power, forgeId));
        verify(attacker).setHealth(11.0);
    }

    @Test
    void regressedTimeSourceExpiresStaleCooldownInsteadOfPermanentBlock() {
        AtomicLong now = new AtomicLong(10000L);
        ForgePowerService service = timedService(now, () -> false, mock(JavaPlugin.class));
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(attacker.getMaxHealth()).thenReturn(20.0);
        when(attacker.getHealth()).thenReturn(10.0);
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getId()).thenReturn("regressed");
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_HEAL);
        when(power.getCooldownTicks()).thenReturn(60);
        when(power.getChance()).thenReturn(BigDecimal.ONE);
        when(power.getHealAmount()).thenReturn(BigDecimal.ONE);
        UUID forgeId = UUID.randomUUID();

        assertTrue(service.triggerOnHitPower(attacker, mock(LivingEntity.class), power, forgeId));
        now.set(9000L);
        assertTrue(service.triggerOnHitPower(attacker, mock(LivingEntity.class), power, forgeId));
        now.set(11999L);
        assertFalse(service.triggerOnHitPower(attacker, mock(LivingEntity.class), power, forgeId));
        now.set(12000L);
        assertTrue(service.triggerOnHitPower(attacker, mock(LivingEntity.class), power, forgeId));
    }

    @Test
    void cooldownKeyIsolatedByForgeAndPower() {
        AtomicLong now = new AtomicLong(0L);
        ForgePowerService service = timedService(now, () -> false, mock(JavaPlugin.class));
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(attacker.getMaxHealth()).thenReturn(20.0);
        when(attacker.getHealth()).thenReturn(10.0);
        ForgePowerDefinition powerA = hitHealPower("power-a");
        ForgePowerDefinition powerB = hitHealPower("power-b");
        UUID forgeA = UUID.randomUUID();
        UUID forgeB = UUID.randomUUID();

        assertTrue(service.triggerOnHitPower(attacker, mock(LivingEntity.class), powerA, forgeA));
        assertTrue(service.triggerOnHitPower(attacker, mock(LivingEntity.class), powerA, forgeB));
        assertTrue(service.triggerOnHitPower(attacker, mock(LivingEntity.class), powerB, forgeA));
        assertFalse(service.triggerOnHitPower(attacker, mock(LivingEntity.class), powerA, forgeA));
    }

    @Test
    void sameKeyAtomicAcquireAllowsExactlyOneWinner() throws Exception {
        AtomicLong now = new AtomicLong(0L);
        ForgePowerService service = timedService(now, () -> false, mock(JavaPlugin.class));
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(attacker.getMaxHealth()).thenReturn(20.0);
        when(attacker.getHealth()).thenReturn(10.0);
        ForgePowerDefinition power = hitHealPower("atomic");
        UUID forgeId = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Future<Boolean> first = executor.submit(() -> {
                barrier.await(5L, TimeUnit.SECONDS);
                return service.triggerOnHitPower(attacker, mock(LivingEntity.class), power, forgeId);
            });
            Future<Boolean> second = executor.submit(() -> {
                barrier.await(5L, TimeUnit.SECONDS);
                return service.triggerOnHitPower(attacker, mock(LivingEntity.class), power, forgeId);
            });

            assertNotEquals(first.get(5L, TimeUnit.SECONDS), second.get(5L, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void zeroCooldownAlwaysReactivatesAndDoesNotLeaveStaleBlock() {
        AtomicLong now = new AtomicLong(0L);
        ForgePowerService service = timedService(now, () -> false, mock(JavaPlugin.class));
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        when(attacker.getMaxHealth()).thenReturn(20.0);
        when(attacker.getHealth()).thenReturn(10.0);
        ForgePowerDefinition power = hitHealPower("zero");
        UUID forgeId = UUID.randomUUID();

        assertTrue(service.triggerOnHitPower(attacker, mock(LivingEntity.class), power, forgeId));
        when(power.getCooldownTicks()).thenReturn(0);
        assertTrue(service.triggerOnHitPower(attacker, mock(LivingEntity.class), power, forgeId));
        when(power.getCooldownTicks()).thenReturn(60);
        assertTrue(service.triggerOnHitPower(attacker, mock(LivingEntity.class), power, forgeId));
    }

    @Test
    void powerTraceDisabledAndEnabledDecisionsStayServiceOwned() {
        JavaPlugin disabledPlugin = mock(JavaPlugin.class);
        Logger disabledLogger = mock(Logger.class);
        when(disabledPlugin.getLogger()).thenReturn(disabledLogger);
        ForgePowerService disabled = timedService(new AtomicLong(0L), () -> false, disabledPlugin);
        Player disabledPlayer = mock(Player.class);
        when(disabledPlayer.getUniqueId()).thenReturn(UUID.randomUUID());
        ForgePowerDefinition miss = hitHealPower("trace-miss");
        when(miss.getChance()).thenReturn(BigDecimal.ZERO);

        assertFalse(disabled.triggerOnHitPower(disabledPlayer, mock(LivingEntity.class), miss, UUID.randomUUID()));
        verify(disabledLogger, never()).info(anyString());

        JavaPlugin enabledPlugin = mock(JavaPlugin.class);
        Logger enabledLogger = mock(Logger.class);
        when(enabledPlugin.getLogger()).thenReturn(enabledLogger);
        AtomicLong now = new AtomicLong(0L);
        ForgePowerService enabled = timedService(now, () -> true, enabledPlugin);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getMaxHealth()).thenReturn(20.0);
        when(player.getHealth()).thenReturn(10.0);
        ForgePowerDefinition power = hitHealPower("trace");
        UUID forgeId = UUID.randomUUID();
        ForgePowerDefinition enabledMiss = hitHealPower("trace-miss-enabled");
        when(enabledMiss.getChance()).thenReturn(BigDecimal.ZERO);

        assertFalse(enabled.triggerOnHitPower(player, mock(LivingEntity.class), enabledMiss, UUID.randomUUID()));
        assertTrue(enabled.triggerOnHitPower(player, mock(LivingEntity.class), power, forgeId));
        assertFalse(enabled.triggerOnHitPower(player, mock(LivingEntity.class), power, forgeId));
        now.set(3000L);
        assertTrue(enabled.triggerOnHitPower(player, mock(LivingEntity.class), power, forgeId));

        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(enabledLogger, times(10)).info(messages.capture());
        List<String> values = messages.getAllValues();
        assertTrue(values.get(0).contains("stage=POWER_ENTRY"));
        assertTrue(values.get(1).contains("stage=CHANCE_MISS")
            && values.get(1).contains("detail=chance=0"));
        assertFalse(values.get(0).contains("COOLDOWN_ACQUIRED"));
        assertFalse(values.get(1).contains("COOLDOWN_ACQUIRED"));
        assertTrue(values.stream().anyMatch(value -> value.contains("[PowerTrace] player=")
            && value.contains("stage=POWER_ENTRY")));
        assertTrue(values.stream().anyMatch(value -> value.contains("stage=COOLDOWN_BLOCK")
            && value.contains("remainingMs=3000")));
        assertTrue(values.stream().anyMatch(value -> value.contains("stage=POWER_APPLIED")));
    }

    private static ForgePowerDefinition hitHealPower(String id) {
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getId()).thenReturn(id);
        when(power.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_HEAL);
        when(power.getCooldownTicks()).thenReturn(60);
        when(power.getChance()).thenReturn(BigDecimal.ONE);
        when(power.getHealAmount()).thenReturn(BigDecimal.ONE);
        return power;
    }

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
    void zeroCooldownRemovesStaleEntryAndExactExpiryCanReuse() {
        AtomicLong now = new AtomicLong(0L);
        ForgePowerService service = new ForgePowerService(mock(JavaPlugin.class),
            new FakeSchedulerBridge(), mock(ParticleBridge.class), mock(PotionEffectResolver.class),
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), mock(MultiStrikeService.class),
            mock(TierRepository.class), now::get, 16, () -> false);
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        UUID forgeId = UUID.randomUUID();
        ForgePowerDefinition power = mock(ForgePowerDefinition.class);
        when(power.getId()).thenReturn("cooldown");
        when(power.getCooldownTicks()).thenReturn(10);

        assertTrue(service.usePower(player, power, forgeId));
        now.set(499L);
        assertFalse(service.canUsePower(player, power, forgeId));
        now.set(500L);
        assertTrue(service.canUsePower(player, power, forgeId));

        when(power.getCooldownTicks()).thenReturn(0);
        assertTrue(service.canUsePower(player, power, forgeId));
        when(power.getCooldownTicks()).thenReturn(10);
        assertTrue(service.canUsePower(player, power, forgeId));
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
    void lethalFireAppliesPrimaryBeforeChanceWithoutAoeOnMiss() {
        MultiStrikeService multiStrike = mock(MultiStrikeService.class);
        ForgePowerService service = service(new FakeSchedulerBridge(), mock(PotionEffectResolver.class),
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), multiStrike,
            mock(ParticleBridge.class), mock(TierRepository.class));
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getFireTicks()).thenReturn(5);
        ForgePowerDefinition aoe = mock(ForgePowerDefinition.class);
        when(aoe.getId()).thenReturn("lethal-aoe");
        when(aoe.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_AOE_FIRE);
        when(aoe.getChance()).thenReturn(BigDecimal.ZERO);
        when(aoe.getCooldownTicks()).thenReturn(20);
        when(aoe.getFireTicks()).thenReturn(60);

        assertFalse(service.triggerOnHitPower(attacker, victim, aoe, UUID.randomUUID(), true));

        verify(victim).setFireTicks(60);
        verify(multiStrike, never()).executeRadial(any(), any(), any(), anyBoolean(), any());
    }

    @Test
    void nonlethalFireMissDoesNotSetPrimaryOnFire() {
        ForgePowerService service = service(new FakeSchedulerBridge(), mock(PotionEffectResolver.class),
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), mock(MultiStrikeService.class),
            mock(ParticleBridge.class), mock(TierRepository.class));
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        LivingEntity victim = mock(LivingEntity.class);
        ForgePowerDefinition fire = mock(ForgePowerDefinition.class);
        when(fire.getId()).thenReturn("fire");
        when(fire.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_FIRE);
        when(fire.getChance()).thenReturn(BigDecimal.ZERO);
        when(fire.getCooldownTicks()).thenReturn(20);
        when(fire.getFireTicks()).thenReturn(60);

        assertFalse(service.triggerOnHitPower(attacker, victim, fire, UUID.randomUUID(), false));

        verify(victim, never()).setFireTicks(anyInt());
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
        PotionEffectResolver resolver = mock(PotionEffectResolver.class);
        when(resolver.resolve(Collections.singletonList("SPEED")))
            .thenReturn(Optional.of(PotionEffectType.SPEED));
        TierRepository tiers = mock(TierRepository.class);
        ForgePowerService service = service(scheduler, resolver, equipment, identity,
            mock(MultiStrikeService.class), mock(ParticleBridge.class), tiers);
        Player player = mock(Player.class);
        UUID forgeId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(mock(World.class), 0, 0, 0));
        when(equipment.getStorageContents(player)).thenReturn(new ItemStack[0]);
        ItemStack equipped = mock(ItemStack.class);
        when(equipped.hasItemMeta()).thenReturn(true);
        when(equipment.getItem(player, EquipmentBridge.Slot.MAINHAND)).thenReturn(equipped);
        when(equipment.getItem(player, EquipmentBridge.Slot.OFFHAND)).thenReturn(null);
        when(identity.readForgeIdentity(equipped)).thenReturn(new ItemIdentityService.ForgeIdentityRead(
            ItemIdentityService.ForgeIdentityStatus.VALID,
            ItemIdentityCodec.Identity.empty()
                .withForgeId(forgeId)
                .withLastTierId("tier1")
                .withLastVariantId("variant1")
                .withActivePowerIds(Collections.singletonList("passive"))));
        TierDefinition tier = mock(TierDefinition.class);
        when(tiers.findById("tier1")).thenReturn(Optional.of(tier));
        ForgeVariant variant = mock(ForgeVariant.class);
        when(variant.getId()).thenReturn("variant1");
        when(tier.getVariants()).thenReturn(Collections.singletonList(variant));
        ForgePowerDefinition passive = mock(ForgePowerDefinition.class);
        when(passive.getId()).thenReturn("passive");
        when(passive.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.PASSIVE_POTION);
        when(passive.getCooldownTicks()).thenReturn(0);
        when(passive.getActivationSlots()).thenReturn(Collections.emptyList());
        when(passive.getEffectCandidates()).thenReturn(Collections.singletonList("SPEED"));
        when(passive.getDurationTicks()).thenReturn(40);
        when(passive.getAmplifier()).thenReturn(0);
        when(variant.getPowers()).thenReturn(Collections.singletonList(passive));
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
        when(equipment.getStorageContents(player)).thenReturn(new ItemStack[] {item});
        ForgePowerService service = service(new FakeSchedulerBridge(), mock(PotionEffectResolver.class),
            equipment, identity, mock(MultiStrikeService.class), mock(ParticleBridge.class),
            mock(TierRepository.class));

        service.refreshInventoryCache(player);
        Set<UUID> cached = service.getCachedInventoryForgeIds(player);
        cached.clear();

        assertTrue(service.hasCachedInventoryForgeId(player, forgeId));
    }

    @Test
    void cursedValidIdentityIsExcludedFromPassiveSnapshot() {
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
            ItemIdentityCodec.Identity.empty().withForgeId(forgeId).withCursed(true)));
        when(equipment.getStorageContents(player)).thenReturn(new ItemStack[] {item});
        ForgePowerService service = service(new FakeSchedulerBridge(), mock(PotionEffectResolver.class),
            equipment, identity, mock(MultiStrikeService.class), mock(ParticleBridge.class),
            mock(TierRepository.class));

        service.refreshInventoryCache(player);

        assertFalse(service.hasCachedInventoryForgeId(player, forgeId));
    }

    @Test
    void onHitPoisonAppliesToNonPlayerVictim() {
        PotionEffectResolver resolver = mock(PotionEffectResolver.class);
        ParticleBridge particles = mock(ParticleBridge.class);
        when(resolver.resolve(Collections.singletonList("POISON")))
            .thenReturn(Optional.of(PotionEffectType.POISON));
        ForgePowerService service = service(new FakeSchedulerBridge(), resolver,
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), mock(MultiStrikeService.class),
            particles, mock(TierRepository.class));
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        LivingEntity cow = mock(LivingEntity.class);
        when(cow.getLocation()).thenReturn(new Location(mock(World.class), 0, 0, 0));
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
        verify(particles).sendFirstAvailable(eq(attacker), any(Location.class),
            eq(Arrays.asList("SPELL", "WITCH", "HAPPY_VILLAGER", "VILLAGER_HAPPY", "CRIT")),
            eq(0F), eq(0F), eq(0F), eq(0F), eq(1));
        verify(particles).sendColoredDust(eq(attacker), any(Location.class),
            eq(34), eq(197), eq(94), anyFloat(), eq(1));
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
        verify(particles, times(2)).sendFirstAvailable(eq(attacker), any(Location.class), anyList(),
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
        verify(particles).sendFirstAvailable(eq(attacker), any(Location.class),
            eq(Arrays.asList("ELECTRIC_SPARK", "END_ROD", "ENCHANT", "NOTE", "CRIT")),
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
        verify(particles).sendFirstAvailable(eq(player), any(Location.class), anyList(),
            eq(0F), eq(0F), eq(0F), eq(0F), eq(1));
        verify(particles).sendColoredDust(eq(player), any(Location.class),
            eq(244), eq(114), eq(182), anyFloat(), anyInt());
    }

    @Test
    void dashEmitsSemanticParticlesWhenVelocityApplied() {
        ParticleBridge particles = mock(ParticleBridge.class);
        ForgePowerService service = service(new FakeSchedulerBridge(), mock(PotionEffectResolver.class),
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), mock(MultiStrikeService.class),
            particles, mock(TierRepository.class));
        Player player = mock(Player.class);
        UUID forgeId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        Location location = new Location(mock(World.class), 0, 0, 0);
        location.setDirection(new Vector(1, 0, 0));
        when(player.getLocation()).thenReturn(location);

        ForgePowerDefinition dash = mock(ForgePowerDefinition.class);
        when(dash.getId()).thenReturn("dash");
        when(dash.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_DASH);
        when(dash.getCooldownTicks()).thenReturn(0);
        when(dash.getHorizontalStrength()).thenReturn(BigDecimal.ONE);
        when(dash.getVerticalStrength()).thenReturn(BigDecimal.ZERO);

        assertTrue(service.activateDash(player, dash, forgeId));

        verify(player).setVelocity(any(Vector.class));
        verify(particles).sendFirstAvailable(eq(player), any(Location.class), anyList(),
            eq(0F), eq(0F), eq(0F), eq(0F), eq(1));
        verify(particles).sendColoredDust(eq(player), any(Location.class),
            eq(56), eq(189), eq(248), anyFloat(), anyInt());
    }

    @Test
    void leapingVelocityUsesFullConfiguredHorizontalAndVerticalStrength() {
        ForgePowerService service = service(new FakeSchedulerBridge(), mock(PotionEffectResolver.class),
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), mock(MultiStrikeService.class),
            mock(ParticleBridge.class), mock(TierRepository.class));
        Player player = mock(Player.class);
        UUID forgeId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        Location location = new Location(mock(World.class), 0, 0, 0);
        location.setDirection(new Vector(1, 0, 0));
        when(player.getLocation()).thenReturn(location);

        ForgePowerDefinition dash = mock(ForgePowerDefinition.class);
        when(dash.getId()).thenReturn("dash");
        when(dash.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_DASH);
        when(dash.getCooldownTicks()).thenReturn(0);
        when(dash.getHorizontalStrength()).thenReturn(new BigDecimal("2.0"));
        when(dash.getVerticalStrength()).thenReturn(new BigDecimal("1.0"));

        assertTrue(service.activateDash(player, dash, forgeId));

        ArgumentCaptor<Vector> captor = ArgumentCaptor.forClass(Vector.class);
        verify(player).setVelocity(captor.capture());
        Vector velocity = captor.getValue();
        assertEquals(2.0, velocity.getX(), 0.0001);
        assertEquals(1.0, velocity.getY(), 0.0001);
        assertEquals(0.0, velocity.getZ(), 0.0001);
    }

    @Test
    void leapingHorizontalMagnitudeDoesNotCollapseWhenLookingSteeply() {
        ForgePowerService service = service(new FakeSchedulerBridge(), mock(PotionEffectResolver.class),
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), mock(MultiStrikeService.class),
            mock(ParticleBridge.class), mock(TierRepository.class));
        Player player = mock(Player.class);
        UUID forgeId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        Location location = new Location(mock(World.class), 0, 0, 0);
        location.setDirection(new Vector(0, 1, 0));
        when(player.getLocation()).thenReturn(location);

        ForgePowerDefinition dash = mock(ForgePowerDefinition.class);
        when(dash.getId()).thenReturn("dash");
        when(dash.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.SHIFT_RIGHT_CLICK_DASH);
        when(dash.getCooldownTicks()).thenReturn(0);
        when(dash.getHorizontalStrength()).thenReturn(new BigDecimal("2.0"));
        when(dash.getVerticalStrength()).thenReturn(BigDecimal.ZERO);

        assertTrue(service.activateDash(player, dash, forgeId));

        ArgumentCaptor<Vector> captor = ArgumentCaptor.forClass(Vector.class);
        verify(player).setVelocity(captor.capture());
        Vector velocity = captor.getValue();
        assertEquals(0.0, velocity.getX(), 0.0001);
        assertEquals(2.0, velocity.getY(), 0.0001);
        assertEquals(2.0, velocity.getZ(), 0.0001);
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
        when(equipment.getStorageContents(player)).thenReturn(new ItemStack[] {item});
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

        verify(particles, times(1)).sendFirstAvailable(eq(player), any(Location.class), anyList(),
            eq(0F), eq(0F), eq(0F), eq(0F), eq(1));

        Runnable refresh = refreshTask.get();
        assertNotNull(refresh);
        refresh.run();
        refresh.run();

        verify(particles, times(1)).sendFirstAvailable(eq(player), any(Location.class), anyList(),
            eq(0F), eq(0F), eq(0F), eq(0F), eq(1));
    }

    @Test
    void passiveArmorSpeedStartsAndKeepsOneBindingAcrossUnrelatedRefresh() {
        SchedulerBridge scheduler = mock(SchedulerBridge.class);
        PotionEffectResolver resolver = mock(PotionEffectResolver.class);
        when(resolver.resolve(Collections.singletonList("SPEED")))
            .thenReturn(Optional.of(PotionEffectType.SPEED));
        ParticleBridge particles = mock(ParticleBridge.class);
        EquipmentBridge equipment = mock(EquipmentBridge.class);
        ItemIdentityService identity = mock(ItemIdentityService.class);
        TierRepository tiers = mock(TierRepository.class);
        ForgePowerService service = service(scheduler, resolver, equipment, identity,
            mock(MultiStrikeService.class), particles, tiers);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        UUID armorForgeId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(mock(World.class), 0, 0, 0));
        when(equipment.getStorageContents(player)).thenReturn(new ItemStack[0]);
        when(equipment.getItem(player, EquipmentBridge.Slot.MAINHAND)).thenReturn(null);
        when(equipment.getItem(player, EquipmentBridge.Slot.OFFHAND)).thenReturn(null);

        ItemStack armorItem = mock(ItemStack.class);
        when(armorItem.hasItemMeta()).thenReturn(true);
        when(identity.readForgeIdentity(armorItem)).thenReturn(new ItemIdentityService.ForgeIdentityRead(
            ItemIdentityService.ForgeIdentityStatus.VALID,
            ItemIdentityCodec.Identity.empty()
                .withForgeId(armorForgeId)
                .withLastTierId("armor-tier")
                .withLastVariantId("armor-variant")
                .withActivePowerIds(Collections.singletonList("speed-passive"))));
        when(equipment.getItem(player, EquipmentBridge.Slot.HEAD)).thenReturn(armorItem);

        TierDefinition tier = mock(TierDefinition.class);
        when(tiers.findById("armor-tier")).thenReturn(Optional.of(tier));
        ForgeVariant variant = mock(ForgeVariant.class);
        when(variant.getId()).thenReturn("armor-variant");
        when(tier.getVariants()).thenReturn(Collections.singletonList(variant));
        ForgePowerDefinition passive = mock(ForgePowerDefinition.class);
        when(passive.getId()).thenReturn("speed-passive");
        when(passive.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.PASSIVE_POTION);
        when(passive.getCooldownTicks()).thenReturn(100);
        when(passive.getActivationSlots())
            .thenReturn(Collections.singletonList(ForgePowerDefinition.ActivationSlot.HEAD));
        when(passive.getEffectCandidates()).thenReturn(Collections.singletonList("SPEED"));
        when(passive.getDurationTicks()).thenReturn(200);
        when(passive.getAmplifier()).thenReturn(1);
        when(variant.getPowers()).thenReturn(Collections.singletonList(passive));

        AtomicReference<TaskHandle> bindingHandle = new AtomicReference<>();
        when(scheduler.runEntityLater(any(Entity.class), any(Runnable.class), any(Runnable.class), anyLong()))
            .thenAnswer(invocation -> {
                TaskHandle handle = mock(TaskHandle.class);
                bindingHandle.set(handle);
                return handle;
            });

        service.refreshPassivePowers(player);

        ItemStack unrelated = mock(ItemStack.class);
        when(unrelated.hasItemMeta()).thenReturn(true);
        when(identity.readForgeIdentity(unrelated)).thenReturn(new ItemIdentityService.ForgeIdentityRead(
            ItemIdentityService.ForgeIdentityStatus.VALID,
            ItemIdentityCodec.Identity.empty()
                .withForgeId(UUID.randomUUID())
                .withLastTierId("other-tier")
                .withLastVariantId("other-variant")
                .withActivePowerIds(Collections.emptyList())));
        when(equipment.getItem(player, EquipmentBridge.Slot.MAINHAND)).thenReturn(unrelated);

        service.refreshPassivePowers(player);

        verify(scheduler, times(1)).runEntityLater(any(Entity.class), any(Runnable.class),
            any(Runnable.class), anyLong());
        verify(bindingHandle.get(), never()).cancel();
        verify(particles, times(1)).sendFirstAvailable(eq(player), any(Location.class), anyList(),
            eq(0F), eq(0F), eq(0F), eq(0F), eq(1));
        verify(player, times(1)).addPotionEffect(any(PotionEffect.class));
    }

    @Test
    void passiveRemovalCancelsOwnedRefreshAndDoesNotRemovePotionDirectly() {
        SchedulerBridge scheduler = mock(SchedulerBridge.class);
        PotionEffectResolver resolver = mock(PotionEffectResolver.class);
        when(resolver.resolve(Collections.singletonList("SPEED")))
            .thenReturn(Optional.of(PotionEffectType.SPEED));
        EquipmentBridge equipment = mock(EquipmentBridge.class);
        ItemIdentityService identity = mock(ItemIdentityService.class);
        TierRepository tiers = mock(TierRepository.class);
        ForgePowerService service = service(scheduler, resolver, equipment, identity,
            mock(MultiStrikeService.class), mock(ParticleBridge.class), tiers);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        UUID armorForgeId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(mock(World.class), 0, 0, 0));
        when(equipment.getStorageContents(player)).thenReturn(new ItemStack[0]);
        when(equipment.getItem(player, EquipmentBridge.Slot.MAINHAND)).thenReturn(null);
        when(equipment.getItem(player, EquipmentBridge.Slot.OFFHAND)).thenReturn(null);

        ItemStack armorItem = mock(ItemStack.class);
        when(armorItem.hasItemMeta()).thenReturn(true);
        when(identity.readForgeIdentity(armorItem)).thenReturn(new ItemIdentityService.ForgeIdentityRead(
            ItemIdentityService.ForgeIdentityStatus.VALID,
            ItemIdentityCodec.Identity.empty()
                .withForgeId(armorForgeId)
                .withLastTierId("armor-tier")
                .withLastVariantId("armor-variant")
                .withActivePowerIds(Collections.singletonList("speed-passive"))));
        when(equipment.getItem(player, EquipmentBridge.Slot.HEAD)).thenReturn(armorItem);

        TierDefinition tier = mock(TierDefinition.class);
        when(tiers.findById("armor-tier")).thenReturn(Optional.of(tier));
        ForgeVariant variant = mock(ForgeVariant.class);
        when(variant.getId()).thenReturn("armor-variant");
        when(tier.getVariants()).thenReturn(Collections.singletonList(variant));
        ForgePowerDefinition passive = mock(ForgePowerDefinition.class);
        when(passive.getId()).thenReturn("speed-passive");
        when(passive.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.PASSIVE_POTION);
        when(passive.getCooldownTicks()).thenReturn(0);
        when(passive.getActivationSlots())
            .thenReturn(Collections.singletonList(ForgePowerDefinition.ActivationSlot.HEAD));
        when(passive.getEffectCandidates()).thenReturn(Collections.singletonList("SPEED"));
        when(passive.getDurationTicks()).thenReturn(40);
        when(passive.getAmplifier()).thenReturn(1);
        when(variant.getPowers()).thenReturn(Collections.singletonList(passive));

        AtomicReference<TaskHandle> bindingHandle = new AtomicReference<>();
        when(scheduler.runEntityLater(any(Entity.class), any(Runnable.class), any(Runnable.class), anyLong()))
            .thenAnswer(invocation -> {
                TaskHandle handle = mock(TaskHandle.class);
                bindingHandle.set(handle);
                return handle;
            });

        service.refreshPassivePowers(player);

        when(equipment.getItem(player, EquipmentBridge.Slot.HEAD)).thenReturn(null);
        service.refreshPassivePowers(player);

        verify(bindingHandle.get()).cancel();
        verify(player, never()).removePotionEffect(any(PotionEffectType.class));
    }

    @Test
    void inventoryPassiveCountsStorageAndSelectedMainHandWithoutDuplicateBinding() {
        SchedulerBridge scheduler = mock(SchedulerBridge.class);
        PotionEffectResolver resolver = mock(PotionEffectResolver.class);
        when(resolver.resolve(Collections.singletonList("REGENERATION")))
            .thenReturn(Optional.of(PotionEffectType.REGENERATION));
        EquipmentBridge equipment = mock(EquipmentBridge.class);
        ItemIdentityService identity = mock(ItemIdentityService.class);
        TierRepository tiers = mock(TierRepository.class);
        ForgePowerService service = service(scheduler, resolver, equipment, identity,
            mock(MultiStrikeService.class), mock(ParticleBridge.class), tiers);

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
        when(equipment.getStorageContents(player)).thenReturn(new ItemStack[] {item});
        when(equipment.getItem(player, EquipmentBridge.Slot.MAINHAND)).thenReturn(item);
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
        when(passive.getActivationSlots())
            .thenReturn(Collections.singletonList(ForgePowerDefinition.ActivationSlot.INVENTORY));
        when(passive.getEffectCandidates()).thenReturn(Collections.singletonList("REGENERATION"));
        when(passive.getDurationTicks()).thenReturn(40);
        when(passive.getAmplifier()).thenReturn(0);
        when(variant.getPowers()).thenReturn(Collections.singletonList(passive));

        when(scheduler.runEntityLater(any(Entity.class), any(Runnable.class), any(Runnable.class), anyLong()))
            .thenAnswer(invocation -> mock(TaskHandle.class));

        service.refreshPassivePowers(player);

        verify(scheduler, times(1)).runEntityLater(any(Entity.class), any(Runnable.class),
            any(Runnable.class), anyLong());
        assertTrue(service.hasCachedInventoryForgeId(player, forgeId));
    }

    @Test
    void offhandPassiveUsesExplicitOffhandSlot() {
        SchedulerBridge scheduler = mock(SchedulerBridge.class);
        PotionEffectResolver resolver = mock(PotionEffectResolver.class);
        when(resolver.resolve(Collections.singletonList("SPEED")))
            .thenReturn(Optional.of(PotionEffectType.SPEED));
        EquipmentBridge equipment = mock(EquipmentBridge.class);
        ItemIdentityService identity = mock(ItemIdentityService.class);
        TierRepository tiers = mock(TierRepository.class);
        ForgePowerService service = service(scheduler, resolver, equipment, identity,
            mock(MultiStrikeService.class), mock(ParticleBridge.class), tiers);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        UUID forgeId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(mock(World.class), 0, 0, 0));
        when(equipment.getStorageContents(player)).thenReturn(new ItemStack[0]);
        when(equipment.getItem(player, EquipmentBridge.Slot.MAINHAND)).thenReturn(null);

        ItemStack offhandItem = mock(ItemStack.class);
        when(offhandItem.hasItemMeta()).thenReturn(true);
        when(identity.readForgeIdentity(offhandItem)).thenReturn(new ItemIdentityService.ForgeIdentityRead(
            ItemIdentityService.ForgeIdentityStatus.VALID,
            ItemIdentityCodec.Identity.empty()
                .withForgeId(forgeId)
                .withLastTierId("tier1")
                .withLastVariantId("variant1")
                .withActivePowerIds(Arrays.asList("off-passive", "default-passive", "main-passive"))));
        when(equipment.getItem(player, EquipmentBridge.Slot.OFFHAND)).thenReturn(offhandItem);

        TierDefinition tier = mock(TierDefinition.class);
        when(tiers.findById("tier1")).thenReturn(Optional.of(tier));
        ForgeVariant variant = mock(ForgeVariant.class);
        when(variant.getId()).thenReturn("variant1");
        when(tier.getVariants()).thenReturn(Collections.singletonList(variant));
        ForgePowerDefinition offPassive = mock(ForgePowerDefinition.class);
        when(offPassive.getId()).thenReturn("off-passive");
        when(offPassive.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.PASSIVE_POTION);
        when(offPassive.getCooldownTicks()).thenReturn(0);
        when(offPassive.getActivationSlots())
            .thenReturn(Collections.singletonList(ForgePowerDefinition.ActivationSlot.OFFHAND));
        when(offPassive.getEffectCandidates()).thenReturn(Collections.singletonList("SPEED"));
        when(offPassive.getDurationTicks()).thenReturn(40);
        when(offPassive.getAmplifier()).thenReturn(0);
        ForgePowerDefinition defaultPassive = mock(ForgePowerDefinition.class);
        when(defaultPassive.getId()).thenReturn("default-passive");
        when(defaultPassive.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.PASSIVE_POTION);
        when(defaultPassive.getCooldownTicks()).thenReturn(0);
        when(defaultPassive.getActivationSlots()).thenReturn(Collections.emptyList());
        when(defaultPassive.getEffectCandidates()).thenReturn(Collections.singletonList("SPEED"));
        when(defaultPassive.getDurationTicks()).thenReturn(40);
        when(defaultPassive.getAmplifier()).thenReturn(0);
        ForgePowerDefinition mainPassive = mock(ForgePowerDefinition.class);
        when(mainPassive.getId()).thenReturn("main-passive");
        when(mainPassive.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.PASSIVE_POTION);
        when(mainPassive.getCooldownTicks()).thenReturn(0);
        when(mainPassive.getActivationSlots())
            .thenReturn(Collections.singletonList(ForgePowerDefinition.ActivationSlot.MAINHAND));
        when(mainPassive.getEffectCandidates()).thenReturn(Collections.singletonList("SPEED"));
        when(mainPassive.getDurationTicks()).thenReturn(40);
        when(mainPassive.getAmplifier()).thenReturn(0);
        when(variant.getPowers())
            .thenReturn(Arrays.asList(offPassive, defaultPassive, mainPassive));

        when(scheduler.runEntityLater(any(Entity.class), any(Runnable.class), any(Runnable.class), anyLong()))
            .thenAnswer(invocation -> mock(TaskHandle.class));

        service.refreshPassivePowers(player);

        verify(scheduler, times(2)).runEntityLater(any(Entity.class), any(Runnable.class),
            any(Runnable.class), anyLong());
    }

    @Test
    void passiveActivationDoesNotConsumeCooldown() {
        SchedulerBridge scheduler = mock(SchedulerBridge.class);
        PotionEffectResolver resolver = mock(PotionEffectResolver.class);
        when(resolver.resolve(Collections.singletonList("SPEED")))
            .thenReturn(Optional.of(PotionEffectType.SPEED));
        EquipmentBridge equipment = mock(EquipmentBridge.class);
        ItemIdentityService identity = mock(ItemIdentityService.class);
        TierRepository tiers = mock(TierRepository.class);
        ForgePowerService service = service(scheduler, resolver, equipment, identity,
            mock(MultiStrikeService.class), mock(ParticleBridge.class), tiers);

        Player player = mock(Player.class);
        UUID playerId = UUID.randomUUID();
        UUID forgeId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(mock(World.class), 0, 0, 0));
        when(equipment.getStorageContents(player)).thenReturn(new ItemStack[0]);
        when(equipment.getItem(player, EquipmentBridge.Slot.MAINHAND)).thenReturn(null);
        when(equipment.getItem(player, EquipmentBridge.Slot.OFFHAND)).thenReturn(null);

        ItemStack armorItem = mock(ItemStack.class);
        when(armorItem.hasItemMeta()).thenReturn(true);
        when(identity.readForgeIdentity(armorItem)).thenReturn(new ItemIdentityService.ForgeIdentityRead(
            ItemIdentityService.ForgeIdentityStatus.VALID,
            ItemIdentityCodec.Identity.empty()
                .withForgeId(forgeId)
                .withLastTierId("tier1")
                .withLastVariantId("variant1")
                .withActivePowerIds(Collections.singletonList("passive"))));
        when(equipment.getItem(player, EquipmentBridge.Slot.HEAD)).thenReturn(armorItem);

        TierDefinition tier = mock(TierDefinition.class);
        when(tiers.findById("tier1")).thenReturn(Optional.of(tier));
        ForgeVariant variant = mock(ForgeVariant.class);
        when(variant.getId()).thenReturn("variant1");
        when(tier.getVariants()).thenReturn(Collections.singletonList(variant));
        ForgePowerDefinition passive = mock(ForgePowerDefinition.class);
        when(passive.getId()).thenReturn("passive");
        when(passive.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.PASSIVE_POTION);
        when(passive.getCooldownTicks()).thenReturn(100);
        when(passive.getActivationSlots())
            .thenReturn(Collections.singletonList(ForgePowerDefinition.ActivationSlot.HEAD));
        when(passive.getEffectCandidates()).thenReturn(Collections.singletonList("SPEED"));
        when(passive.getDurationTicks()).thenReturn(40);
        when(passive.getAmplifier()).thenReturn(0);
        when(variant.getPowers()).thenReturn(Collections.singletonList(passive));

        when(scheduler.runEntityLater(any(Entity.class), any(Runnable.class), any(Runnable.class), anyLong()))
            .thenAnswer(invocation -> mock(TaskHandle.class));

        service.refreshPassivePowers(player);

        assertTrue(service.isCooldownExpired(player, passive, forgeId));
        assertTrue(service.canUsePower(player, passive, forgeId));
    }

    @Test
    void bleedEmitsBlockBreakVisualsAndDustOnActivationAndPulseWithoutHeart() {
        ParticleBridge particles = mock(ParticleBridge.class);
        ForgePowerService service = service(new FakeSchedulerBridge(), mock(PotionEffectResolver.class),
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), mock(MultiStrikeService.class),
            particles, mock(TierRepository.class));
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getLocation()).thenReturn(new Location(mock(World.class), 0, 64, 0));

        ForgePowerDefinition bleed = mock(ForgePowerDefinition.class);
        when(bleed.getId()).thenReturn("bleed");
        when(bleed.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_BLEED);
        when(bleed.getChance()).thenReturn(BigDecimal.ONE);
        when(bleed.getCooldownTicks()).thenReturn(0);
        when(bleed.getDamageAmount()).thenReturn(new BigDecimal("5"));
        when(bleed.getPulseCount()).thenReturn(2);
        when(bleed.getPulseIntervalTicks()).thenReturn(1);

        assertTrue(service.triggerOnHitPower(attacker, victim, bleed, UUID.randomUUID()));

        ArgumentCaptor<Location> bleedLocations = ArgumentCaptor.forClass(Location.class);
        verify(particles, times(9)).sendBlockBreak(eq(attacker), bleedLocations.capture(),
            eq(Material.REDSTONE_BLOCK), eq(4));
        assertEquals(0.0, bleedLocations.getAllValues().get(0).getX(), 0.0001);
        assertEquals(64.90, bleedLocations.getAllValues().get(0).getY(), 0.0001);
        assertEquals(0.0, bleedLocations.getAllValues().get(0).getZ(), 0.0001);
        assertEquals(0.18, bleedLocations.getAllValues().get(1).getX(), 0.0001);
        assertEquals(65.12, bleedLocations.getAllValues().get(1).getY(), 0.0001);
        assertEquals(-0.12, bleedLocations.getAllValues().get(1).getZ(), 0.0001);
        assertEquals(-0.16, bleedLocations.getAllValues().get(2).getX(), 0.0001);
        assertEquals(65.28, bleedLocations.getAllValues().get(2).getY(), 0.0001);
        assertEquals(0.14, bleedLocations.getAllValues().get(2).getZ(), 0.0001);
        verify(particles, times(3)).sendColoredDust(eq(attacker), any(Location.class),
            eq(220), eq(38), eq(38), anyFloat(), eq(2));
        verify(particles, times(3)).sendFirstAvailable(eq(attacker), any(Location.class),
            eq(Collections.singletonList("CRIT")), eq(0F), eq(0F), eq(0F), eq(0F), eq(2));
        verify(victim, times(2)).damage(5.0);
        verify(particles, never()).sendFirstAvailable(eq(attacker), any(Location.class),
            eq(Collections.singletonList("HEART")), eq(0F), eq(0F), eq(0F), eq(0F), anyInt());
    }

    @Test
    void electricChainEmitsSparkFamilyAtCountFourOnStruckEntities() {
        MultiStrikeService multiStrike = mock(MultiStrikeService.class);
        ParticleBridge particles = mock(ParticleBridge.class);
        ForgePowerService service = service(new FakeSchedulerBridge(), mock(PotionEffectResolver.class),
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), multiStrike,
            particles, mock(TierRepository.class));
        Player attacker = mock(Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID());
        LivingEntity victim = mock(LivingEntity.class);
        when(victim.getLocation()).thenReturn(new Location(mock(World.class), 0, 0, 0));
        LivingEntity zombie = mock(LivingEntity.class);
        when(zombie.getLocation()).thenReturn(new Location(mock(World.class), 3, 0, 0));
        doAnswer(invocation -> {
            MultiStrikeService.StrikeAction action = invocation.getArgument(4);
            action.apply(victim);
            action.apply(zombie);
            return null;
        }).when(multiStrike).executeChain(any(), any(), any(), anyBoolean(), any());

        ForgePowerDefinition chain = mock(ForgePowerDefinition.class);
        when(chain.getId()).thenReturn("node");
        when(chain.getPowerType()).thenReturn(ForgePowerDefinition.PowerType.ON_HIT_CHAIN_DAMAGE);
        when(chain.getChance()).thenReturn(BigDecimal.ONE);
        when(chain.getCooldownTicks()).thenReturn(0);
        when(chain.getDamageAmount()).thenReturn(BigDecimal.ONE);

        assertTrue(service.triggerOnHitPower(attacker, victim, chain, UUID.randomUUID()));

        verify(particles, times(2)).sendFirstAvailable(eq(attacker), any(Location.class),
            eq(Arrays.asList("ELECTRIC_SPARK", "END_ROD", "ENCHANT", "NOTE", "CRIT")),
            eq(0F), eq(0F), eq(0F), eq(0F), eq(4));
        verify(particles, times(2)).sendColoredDust(eq(attacker), any(Location.class),
            eq(250), eq(204), eq(21), anyFloat(), eq(4));
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

        verify(particles).sendFirstAvailable(eq(player), any(Location.class), anyList(),
            eq(0F), eq(0F), eq(0F), eq(0F), eq(1));
        verify(particles).sendColoredDust(eq(player), any(Location.class),
            eq(96), eq(165), eq(250), anyFloat(), anyInt());
    }

    private static ForgePowerService service(SchedulerBridge scheduler, PotionEffectResolver resolver,
                                              EquipmentBridge equipment, ItemIdentityService identity,
                                              MultiStrikeService multiStrike, ParticleBridge particles,
                                              TierRepository tiers) {
        return service(mock(JavaPlugin.class), scheduler, resolver, equipment, identity, multiStrike,
            particles, tiers, new ForgePowerService.SystemTimeSource(), () -> false);
    }

    private static ForgePowerService timedService(AtomicLong now, BooleanSupplier traceEnabled,
                                                   JavaPlugin plugin) {
        return service(plugin, new FakeSchedulerBridge(), mock(PotionEffectResolver.class),
            mock(EquipmentBridge.class), mock(ItemIdentityService.class), mock(MultiStrikeService.class),
            mock(ParticleBridge.class), mock(TierRepository.class), now::get, traceEnabled);
    }

    private static ForgePowerService service(JavaPlugin plugin, SchedulerBridge scheduler,
                                              PotionEffectResolver resolver, EquipmentBridge equipment,
                                              ItemIdentityService identity, MultiStrikeService multiStrike,
                                              ParticleBridge particles, TierRepository tiers,
                                              ForgePowerService.TimeSource timeSource,
                                              BooleanSupplier traceEnabled) {
        return new ForgePowerService(plugin, scheduler, particles, resolver, equipment,
            identity, multiStrike, tiers, timeSource, 16, traceEnabled);
    }
}
