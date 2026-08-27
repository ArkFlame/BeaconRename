package com.arkflame.flameforge.compat.effect.particle;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.withSettings;

public class ModernParticleCompatibilityContractTest {
    public enum ContractParticle {
        EFFECT, DUST, DUST_COLOR_TRANSITION, BLOCK, ITEM, ENTITY_EFFECT, DRAGON_BREATH,
        SHRIEK, TRAIL, VIBRATION, FIREFLY, COPPER_FIRE_FLAME
    }

    public enum FutureParticle {
        FUTURE_SPARK(Void.TYPE, "minecraft:future_spark"),
        FUTURE_TYPED(FakeVibration.class, "minecraft:future_typed");

        private final Class<?> type;
        private final String key;
        FutureParticle(Class<?> type, String key) { this.type = type; this.key = key; }
        public Class<?> getDataType() { return type; }
        public String getKey() { return key; }
    }

    public interface ContractSpawnApi {
        void spawnParticle(ContractParticle particle, Location location, int count);
        void spawnParticle(ContractParticle particle, Location location, int count, Object data);
        void spawnParticle(ContractParticle particle, Location location, int count,
                            float x, float y, float z, float extra);
        void spawnParticle(ContractParticle particle, Location location, int count,
                            float x, float y, float z, float extra, Object data);
    }

    public interface FutureSpawnApi {
        void spawnParticle(FutureParticle particle, Location location, int count);
        void spawnParticle(FutureParticle particle, Location location, int count, Object data);
        void spawnParticle(FutureParticle particle, Location location, int count,
                            float x, float y, float z, float extra);
        void spawnParticle(FutureParticle particle, Location location, int count,
                            float x, float y, float z, float extra, Object data);
    }

    public static final class FakeDustOptions {
        public FakeDustOptions(Color color, float size) { }
    }

    public static final class FakeDustTransition {
        public FakeDustTransition(Color from, Color to, float size) { }
    }

    public static final class FakeBlockData { }

    public static final class FakeSpell {
        public FakeSpell(Color color, float size) { }
    }

    public static final class FakeTrail {
        public FakeTrail(Location target, Color color, int durationTicks) { }
    }

    public static final class FakeVibration { }

    public static final class BlockDataFactory {
        public static FakeBlockData createBlockData() { return new FakeBlockData(); }
    }

    private static Location location() {
        return new Location(mock(World.class), 4, 5, 6);
    }

    private static ReflectiveBukkitParticleProvider.SpawnMethodSet contractMethods() throws Exception {
        Method noData = ContractSpawnApi.class.getMethod("spawnParticle", ContractParticle.class,
            Location.class, int.class, float.class, float.class, float.class, float.class);
        Method data = ContractSpawnApi.class.getMethod("spawnParticle", ContractParticle.class,
            Location.class, int.class, float.class, float.class, float.class, float.class, Object.class);
        return new ReflectiveBukkitParticleProvider.SpawnMethodSet(Arrays.asList(
            ReflectiveBukkitParticleProvider.InvocationMode.resolve(noData, ContractParticle.class),
            ReflectiveBukkitParticleProvider.InvocationMode.resolve(data, ContractParticle.class)));
    }

    private static ReflectiveBukkitParticleProvider.SpawnMethodSet futureMethods() throws Exception {
        Method noData = FutureSpawnApi.class.getMethod("spawnParticle", FutureParticle.class,
            Location.class, int.class, float.class, float.class, float.class, float.class);
        Method data = FutureSpawnApi.class.getMethod("spawnParticle", FutureParticle.class,
            Location.class, int.class, float.class, float.class, float.class, float.class, Object.class);
        return new ReflectiveBukkitParticleProvider.SpawnMethodSet(Arrays.asList(
            ReflectiveBukkitParticleProvider.InvocationMode.resolve(noData, FutureParticle.class),
            ReflectiveBukkitParticleProvider.InvocationMode.resolve(data, FutureParticle.class)));
    }

    private static ReflectiveBukkitParticleProvider.RuntimeParticleDescriptor descriptor(
        Object constant, String name, Class<?> type, ParticleDataKind kind, Constructor<?> constructor,
        ReflectiveBukkitParticleProvider.SpawnMethodSet methods) {
        return new ReflectiveBukkitParticleProvider.RuntimeParticleDescriptor(constant, name, null, type, kind,
            methods, constructor);
    }

    private static ReflectiveBukkitParticleProvider.RuntimeBindings suppliedBindings(
        Map<String, ReflectiveBukkitParticleProvider.RuntimeParticleDescriptor> descriptors) throws Exception {
        return new ReflectiveBukkitParticleProvider.RuntimeBindings(ContractParticle.class, Color.class, null, null,
            Color.class.getMethod("fromRGB", int.class, int.class, int.class), null,
            BlockDataFactory.class.getMethod("createBlockData"), ContractParticle.values(), contractMethods(), descriptors);
    }

    private static ParticleRequest request(Location location, String candidate, ParticleRequest.Payload payload) {
        return new ParticleRequest(new ParticleRequest.ParticlePosition(location.getWorld(), location.getX(),
            location.getY(), location.getZ()), Arrays.asList(candidate), 1, 0, 0, 0, 0, payload);
    }

    @Test
    void effectPayloadContractChangesByRuntimeDataTypeNotVersionBranch() throws Exception {
        Location location = location();
        ParticleRequest.Color color = new ParticleRequest.Color(new ParticleColor(20, 40, 60), 1f);
        Map<String, ReflectiveBukkitParticleProvider.RuntimeParticleDescriptor> none =
            new LinkedHashMap<String, ReflectiveBukkitParticleProvider.RuntimeParticleDescriptor>();
        none.put("EFFECT", descriptor(ContractParticle.EFFECT, "EFFECT", Void.TYPE, ParticleDataKind.NONE,
            null, contractMethods()));
        ReflectiveBukkitParticleProvider profileC = new ReflectiveBukkitParticleProvider(new ParticleCatalog(),
            suppliedBindings(none));
        Player viewerC = mock(Player.class, withSettings().extraInterfaces(ContractSpawnApi.class));
        ContractSpawnApi spawnC = (ContractSpawnApi) viewerC;

        assertTrue(profileC.emit(viewerC, request(location, "EFFECT", color)));
        verify(spawnC).spawnParticle(eq(ContractParticle.EFFECT), any(Location.class), eq(1),
            anyFloat(), anyFloat(), anyFloat(), anyFloat());

        Map<String, ReflectiveBukkitParticleProvider.RuntimeParticleDescriptor> spell =
            new LinkedHashMap<String, ReflectiveBukkitParticleProvider.RuntimeParticleDescriptor>();
        spell.put("EFFECT", descriptor(ContractParticle.EFFECT, "EFFECT", FakeSpell.class,
            ParticleDataKind.SPELL, FakeSpell.class.getConstructor(Color.class, float.class), contractMethods()));
        ReflectiveBukkitParticleProvider profileD = new ReflectiveBukkitParticleProvider(new ParticleCatalog(),
            suppliedBindings(spell));
        Player viewerD = mock(Player.class, withSettings().extraInterfaces(ContractSpawnApi.class));
        ContractSpawnApi spawnD = (ContractSpawnApi) viewerD;

        assertTrue(profileD.emit(viewerD, request(location, "EFFECT", color)));
        ArgumentCaptor<Object> spellData = ArgumentCaptor.forClass(Object.class);
        verify(spawnD).spawnParticle(eq(ContractParticle.EFFECT), any(Location.class), eq(1),
            anyFloat(), anyFloat(), anyFloat(), anyFloat(), spellData.capture());
        assertTrue(spellData.getValue() instanceof FakeSpell);
    }

    @Test
    void currentTypedParticlesAndNoDataParticlesUseDeclaredRuntimeContracts() throws Exception {
        ReflectiveBukkitParticleProvider.SpawnMethodSet methods = contractMethods();
        Map<String, ReflectiveBukkitParticleProvider.RuntimeParticleDescriptor> descriptors =
            new LinkedHashMap<String, ReflectiveBukkitParticleProvider.RuntimeParticleDescriptor>();
        descriptors.put("minecraft:dust", descriptor(ContractParticle.DUST, "DUST", FakeDustOptions.class,
            ParticleDataKind.DUST, FakeDustOptions.class.getConstructor(Color.class, float.class), methods));
        descriptors.put("DUST_COLOR_TRANSITION", descriptor(ContractParticle.DUST_COLOR_TRANSITION,
            "DUST_COLOR_TRANSITION", FakeDustTransition.class, ParticleDataKind.DUST_TRANSITION,
            FakeDustTransition.class.getConstructor(Color.class, Color.class, float.class), methods));
        descriptors.put("BLOCK", descriptor(ContractParticle.BLOCK, "BLOCK", FakeBlockData.class,
            ParticleDataKind.BLOCK_DATA, null, methods));
        descriptors.put("ITEM", descriptor(ContractParticle.ITEM, "ITEM", ItemStack.class,
            ParticleDataKind.ITEM_STACK, null, methods));
        descriptors.put("ENTITY_EFFECT", descriptor(ContractParticle.ENTITY_EFFECT, "ENTITY_EFFECT", Void.TYPE,
            ParticleDataKind.NONE, null, methods));
        descriptors.put("DRAGON_BREATH", descriptor(ContractParticle.DRAGON_BREATH, "DRAGON_BREATH", Void.TYPE,
            ParticleDataKind.NONE, null, methods));
        descriptors.put("SHRIEK", descriptor(ContractParticle.SHRIEK, "SHRIEK", Void.TYPE,
            ParticleDataKind.NONE, null, methods));
        descriptors.put("TRAIL", descriptor(ContractParticle.TRAIL, "TRAIL", FakeTrail.class,
            ParticleDataKind.TRAIL, FakeTrail.class.getConstructor(Location.class, Color.class, int.class), methods));
        descriptors.put("VIBRATION", descriptor(ContractParticle.VIBRATION, "VIBRATION", FakeVibration.class,
            ParticleDataKind.CUSTOM, null, methods));
        descriptors.put("FIREFLY", descriptor(ContractParticle.FIREFLY, "FIREFLY", Void.TYPE,
            ParticleDataKind.NONE, null, methods));
        descriptors.put("COPPER_FIRE_FLAME", descriptor(ContractParticle.COPPER_FIRE_FLAME,
            "COPPER_FIRE_FLAME", Void.TYPE, ParticleDataKind.NONE, null, methods));

        ReflectiveBukkitParticleProvider provider = new ReflectiveBukkitParticleProvider(new ParticleCatalog(),
            suppliedBindings(descriptors));
        Player viewer = mock(Player.class, withSettings().extraInterfaces(ContractSpawnApi.class));
        ContractSpawnApi spawn = (ContractSpawnApi) viewer;
        Location location = location();
        ParticleRequest.Color color = new ParticleRequest.Color(new ParticleColor(1, 2, 3), 1f);

        assertTrue(provider.emit(viewer, request(location, "minecraft:dust", color)));
        assertTrue(provider.emit(viewer, request(location, "DUST_COLOR_TRANSITION",
            new ParticleRequest.DustTransition(new ParticleColor(1, 2, 3), new ParticleColor(4, 5, 6), 1f))));
        assertTrue(provider.emit(viewer, request(location, "BLOCK",
            new ParticleRequest.Block(Material.STONE, (byte) 0))));
        assertTrue(provider.emit(viewer, request(location, "ITEM",
            new ParticleRequest.Item(new ItemStack(Material.STONE)))));
        assertTrue(provider.emit(viewer, request(location, "ENTITY_EFFECT", new ParticleRequest.None())));
        assertTrue(provider.emit(viewer, request(location, "DRAGON_BREATH", new ParticleRequest.None())));
        assertTrue(provider.emit(viewer, request(location, "SHRIEK", new ParticleRequest.None())));
        assertTrue(provider.emit(viewer, request(location, "TRAIL", new ParticleRequest.Trail(
            new ParticleRequest.ParticlePosition(location.getWorld(), 7, 8, 9), new ParticleColor(4, 5, 6), 2))));
        assertTrue(provider.emit(viewer, request(location, "VIBRATION", new ParticleRequest.Custom(new FakeVibration()))));
        assertTrue(provider.emit(viewer, request(location, "FIREFLY", new ParticleRequest.None())));
        assertTrue(provider.emit(viewer, request(location, "COPPER_FIRE_FLAME", new ParticleRequest.None())));

        verify(spawn, times(6)).spawnParticle(any(ContractParticle.class), any(Location.class), eq(1),
            anyFloat(), anyFloat(), anyFloat(), anyFloat(), any(Object.class));
        verify(spawn, times(5)).spawnParticle(any(ContractParticle.class), any(Location.class), eq(1),
            anyFloat(), anyFloat(), anyFloat(), anyFloat());
    }

    @Test
    void futureEnumConstantsAutoIndexAndRejectIncompatibleTypedPayloads() throws Exception {
        ReflectiveBukkitParticleProvider.SpawnMethodSet methods = futureMethods();
        ReflectiveBukkitParticleProvider.RuntimeBindings bindings = new ReflectiveBukkitParticleProvider.RuntimeBindings(
            FutureParticle.class, Color.class, FutureParticle.class.getMethod("getDataType"),
            FutureParticle.class.getMethod("getKey"), Color.class.getMethod("fromRGB", int.class, int.class, int.class),
            null, null, FutureParticle.values(), methods,
            java.util.Collections.<String, ReflectiveBukkitParticleProvider.RuntimeParticleDescriptor>emptyMap());
        ReflectiveBukkitParticleProvider provider = new ReflectiveBukkitParticleProvider(new ParticleCatalog(), bindings);
        Player viewer = mock(Player.class, withSettings().extraInterfaces(FutureSpawnApi.class));
        FutureSpawnApi spawn = (FutureSpawnApi) viewer;
        Location location = location();

        assertTrue(provider.emit(viewer, request(location, "minecraft:future_spark", new ParticleRequest.None())));
        assertTrue(provider.emit(viewer, request(location, "FUTURE_TYPED",
            new ParticleRequest.Custom(new FakeVibration()))));
        assertFalse(provider.emit(viewer, request(location, "FUTURE_TYPED", new ParticleRequest.None())));
        assertFalse(provider.emit(viewer, request(location, "FUTURE_TYPED",
            new ParticleRequest.Custom(new Object()))));
        assertTrue(provider.emit(viewer, new ParticleRequest(new ParticleRequest.ParticlePosition(location.getWorld(),
            location.getX(), location.getY(), location.getZ()), Arrays.asList("FUTURE_TYPED", "FUTURE_SPARK"),
            1, 0, 0, 0, 0, new ParticleRequest.None())));

        verify(spawn, times(2)).spawnParticle(eq(FutureParticle.FUTURE_SPARK), any(Location.class), eq(1),
            anyFloat(), anyFloat(), anyFloat(), anyFloat());
        verify(spawn).spawnParticle(eq(FutureParticle.FUTURE_TYPED), any(Location.class), eq(1),
            anyFloat(), anyFloat(), anyFloat(), anyFloat(), any(Object.class));
    }
}
