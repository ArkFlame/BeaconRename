package com.arkflame.flameforge.compat.effect.particle;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

public class ReflectiveBukkitParticleProviderTest {
    public enum LegacyParticle { EXPLOSION_NORMAL, FIREWORKS_SPARK, SPELL, REDSTONE, BLOCK_CRACK, BLOCK_DUST, ITEM_CRACK }
    public enum ModernParticle { DUST, BLOCK, ITEM }

    public interface SpawnApi {
        void spawnParticle(LegacyParticle particle, Location location, int count);
        void spawnParticle(LegacyParticle particle, Location location, int count, Object data);
        void spawnParticle(LegacyParticle particle, Location location, int count,
                            float x, float y, float z, float extra);
        void spawnParticle(LegacyParticle particle, Location location, int count,
                            float x, float y, float z, float extra, Object data);
    }

    public interface ModernSpawnApi {
        void spawnParticle(ModernParticle particle, Location location, int count);
        void spawnParticle(ModernParticle particle, Location location, int count, Object data);
        void spawnParticle(ModernParticle particle, Location location, int count,
                            float x, float y, float z, float extra);
        void spawnParticle(ModernParticle particle, Location location, int count,
                            float x, float y, float z, float extra, Object data);
    }

    public static final class FakeDustOptions {
        public final Color color;
        public final float size;
        public FakeDustOptions(Color color, float size) { this.color = color; this.size = size; }
    }

    public static final class FakeBlockData { }

    public static final class BlockDataFactory {
        public static FakeBlockData createBlockData() { return new FakeBlockData(); }
    }

    private static Location location() {
        return new Location(mock(World.class), 1, 2, 3);
    }

    private static Method rgb() throws Exception {
        return Color.class.getMethod("fromRGB", int.class, int.class, int.class);
    }

    private static ReflectiveBukkitParticleProvider.RuntimeBindings legacyBindings() throws Exception {
        Method noData = SpawnApi.class.getMethod("spawnParticle", LegacyParticle.class,
            Location.class, int.class, float.class, float.class, float.class, float.class);
        Method data = SpawnApi.class.getMethod("spawnParticle", LegacyParticle.class,
            Location.class, int.class, float.class, float.class, float.class, float.class, Object.class);
        ReflectiveBukkitParticleProvider.SpawnMethodSet methods = new ReflectiveBukkitParticleProvider.SpawnMethodSet(
            Arrays.asList(ReflectiveBukkitParticleProvider.InvocationMode.resolve(noData, LegacyParticle.class),
                ReflectiveBukkitParticleProvider.InvocationMode.resolve(data, LegacyParticle.class)));
        Map<String, ReflectiveBukkitParticleProvider.RuntimeParticleDescriptor> descriptors =
            new LinkedHashMap<String, ReflectiveBukkitParticleProvider.RuntimeParticleDescriptor>();
        descriptors.put("EXPLOSION_NORMAL", descriptor(LegacyParticle.EXPLOSION_NORMAL, "EXPLOSION_NORMAL",
            null, ParticleDataKind.NONE, null, methods));
        descriptors.put("FIREWORKS_SPARK", descriptor(LegacyParticle.FIREWORKS_SPARK, "FIREWORKS_SPARK",
            null, ParticleDataKind.NONE, null, methods));
        descriptors.put("SPELL", descriptor(LegacyParticle.SPELL, "SPELL", null, ParticleDataKind.NONE, null, methods));
        descriptors.put("REDSTONE", descriptor(LegacyParticle.REDSTONE, "REDSTONE", Object.class,
            ParticleDataKind.CUSTOM, null, methods));
        descriptors.put("BLOCK_CRACK", descriptor(LegacyParticle.BLOCK_CRACK, "BLOCK_CRACK", MaterialData.class,
            ParticleDataKind.MATERIAL_DATA, null, methods));
        descriptors.put("BLOCK_DUST", descriptor(LegacyParticle.BLOCK_DUST, "BLOCK_DUST", MaterialData.class,
            ParticleDataKind.MATERIAL_DATA, null, methods));
        descriptors.put("ITEM_CRACK", descriptor(LegacyParticle.ITEM_CRACK, "ITEM_CRACK", ItemStack.class,
            ParticleDataKind.ITEM_STACK, null, methods));
        return new ReflectiveBukkitParticleProvider.RuntimeBindings(LegacyParticle.class, Color.class, null, null,
            rgb(), null, null, LegacyParticle.values(), methods, descriptors);
    }

    private static ReflectiveBukkitParticleProvider.RuntimeBindings modernBindings() throws Exception {
        Method noData = ModernSpawnApi.class.getMethod("spawnParticle", ModernParticle.class,
            Location.class, int.class, float.class, float.class, float.class, float.class);
        Method data = ModernSpawnApi.class.getMethod("spawnParticle", ModernParticle.class,
            Location.class, int.class, float.class, float.class, float.class, float.class, Object.class);
        ReflectiveBukkitParticleProvider.SpawnMethodSet methods = new ReflectiveBukkitParticleProvider.SpawnMethodSet(
            Arrays.asList(ReflectiveBukkitParticleProvider.InvocationMode.resolve(noData, ModernParticle.class),
                ReflectiveBukkitParticleProvider.InvocationMode.resolve(data, ModernParticle.class)));
        Constructor<FakeDustOptions> dust = FakeDustOptions.class.getConstructor(Color.class, float.class);
        Map<String, ReflectiveBukkitParticleProvider.RuntimeParticleDescriptor> descriptors =
            new LinkedHashMap<String, ReflectiveBukkitParticleProvider.RuntimeParticleDescriptor>();
        descriptors.put("DUST", descriptor(ModernParticle.DUST, "DUST", FakeDustOptions.class,
            ParticleDataKind.DUST, dust, methods));
        descriptors.put("BLOCK", descriptor(ModernParticle.BLOCK, "BLOCK", FakeBlockData.class,
            ParticleDataKind.BLOCK_DATA, null, methods));
        descriptors.put("ITEM", descriptor(ModernParticle.ITEM, "ITEM", ItemStack.class,
            ParticleDataKind.ITEM_STACK, null, methods));
        return new ReflectiveBukkitParticleProvider.RuntimeBindings(ModernParticle.class, Color.class, null, null,
            rgb(), null, BlockDataFactory.class.getMethod("createBlockData"), ModernParticle.values(), methods, descriptors);
    }

    private static ReflectiveBukkitParticleProvider.RuntimeParticleDescriptor descriptor(
        Object constant, String name, Class<?> type, ParticleDataKind kind, Constructor<?> constructor,
        ReflectiveBukkitParticleProvider.SpawnMethodSet methods) {
        return new ReflectiveBukkitParticleProvider.RuntimeParticleDescriptor(constant, name, null, type, kind,
            methods, constructor);
    }

    @Test
    void profile112UsesNoDataLegacyParticlesAndTypedLegacyPayloads() throws Exception {
        ReflectiveBukkitParticleProvider provider = new ReflectiveBukkitParticleProvider(new ParticleCatalog(),
            legacyBindings());
        Player viewer = mock(Player.class, withSettings().extraInterfaces(SpawnApi.class));
        SpawnApi spawn = (SpawnApi) viewer;
        Location location = location();

        assertTrue(provider.emit(viewer, request(location, "EXPLOSION_NORMAL", new ParticleRequest.None())));
        verify(spawn).spawnParticle(eq(LegacyParticle.EXPLOSION_NORMAL), any(Location.class), eq(1),
            anyFloat(), anyFloat(), anyFloat(), anyFloat());
        clearInvocations(viewer);

        assertTrue(provider.emit(viewer, request(location, "FIREWORKS_SPARK", new ParticleRequest.None())));
        assertTrue(provider.emit(viewer, request(location, "SPELL", new ParticleRequest.None())));
        verify(spawn).spawnParticle(eq(LegacyParticle.FIREWORKS_SPARK), any(Location.class), eq(1),
            anyFloat(), anyFloat(), anyFloat(), anyFloat());
        verify(spawn).spawnParticle(eq(LegacyParticle.SPELL), any(Location.class), eq(1),
            anyFloat(), anyFloat(), anyFloat(), anyFloat());
        clearInvocations(viewer);

        ParticleRequest.Color color = new ParticleRequest.Color(new ParticleColor(255, 80, 20), 1f);
        assertTrue(provider.emit(viewer, request(location, "REDSTONE", color)));
        verify(spawn).spawnParticle(eq(LegacyParticle.REDSTONE), any(Location.class), eq(1),
            eq(1f), eq(80f / 255f), eq(20f / 255f), eq(0f));
        clearInvocations(viewer);

        assertTrue(provider.emit(viewer, request(location, "BLOCK_CRACK",
            new ParticleRequest.Block(Material.STONE, (byte) 3))));
        assertTrue(provider.emit(viewer, request(location, "BLOCK_DUST",
            new ParticleRequest.Block(Material.STONE, (byte) 3))));
        assertTrue(provider.emit(viewer, request(location, "ITEM_CRACK",
            new ParticleRequest.Item(new ItemStack(Material.STONE)))));
        verify(spawn).spawnParticle(eq(LegacyParticle.BLOCK_CRACK), any(Location.class), eq(1),
            anyFloat(), anyFloat(), anyFloat(), anyFloat(), any(Object.class));
        verify(spawn).spawnParticle(eq(LegacyParticle.BLOCK_DUST), any(Location.class), eq(1),
            anyFloat(), anyFloat(), anyFloat(), anyFloat(), any(Object.class));
        verify(spawn).spawnParticle(eq(LegacyParticle.ITEM_CRACK), any(Location.class), eq(1),
            anyFloat(), anyFloat(), anyFloat(), anyFloat(), any(Object.class));
    }

    @Test
    void profileWithDustOptionsBlockDataAndItemUsesTypedData() throws Exception {
        ReflectiveBukkitParticleProvider provider = new ReflectiveBukkitParticleProvider(new ParticleCatalog(),
            modernBindings());
        Player viewer = mock(Player.class, withSettings().extraInterfaces(ModernSpawnApi.class));
        ModernSpawnApi spawn = (ModernSpawnApi) viewer;
        Location location = location();

        assertTrue(provider.emit(viewer, request(location, "DUST",
            new ParticleRequest.Color(new ParticleColor(1, 2, 3), 2f))));
        assertTrue(provider.emit(viewer, request(location, "BLOCK",
            new ParticleRequest.Block(Material.STONE, (byte) 0))));
        assertTrue(provider.emit(viewer, request(location, "ITEM",
            new ParticleRequest.Item(new ItemStack(Material.STONE)))));

        verify(spawn).spawnParticle(eq(ModernParticle.DUST), any(Location.class), eq(1),
            anyFloat(), anyFloat(), anyFloat(), anyFloat(), any(Object.class));
        verify(spawn).spawnParticle(eq(ModernParticle.BLOCK), any(Location.class), eq(1),
            anyFloat(), anyFloat(), anyFloat(), anyFloat(), any(Object.class));
        verify(spawn).spawnParticle(eq(ModernParticle.ITEM), any(Location.class), eq(1),
            anyFloat(), anyFloat(), anyFloat(), anyFloat(), any(Object.class));
    }

    private static ParticleRequest request(Location location, String candidate, ParticleRequest.Payload payload) {
        return new ParticleRequest(new ParticleRequest.ParticlePosition(location.getWorld(), location.getX(),
            location.getY(), location.getZ()), Arrays.asList(candidate), 1, 0, 0, 0, 0, payload);
    }
}
