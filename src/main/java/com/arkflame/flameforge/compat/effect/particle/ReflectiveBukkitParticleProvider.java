package com.arkflame.flameforge.compat.effect.particle;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.material.MaterialData;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ReflectiveBukkitParticleProvider implements ParticleProvider {
    private static final int MAX_DIAGNOSTICS = 64;

    private final ParticleCatalog catalog;
    private final Class<?> bukkitColorClass;
    private final Map<String, RuntimeParticleDescriptor> descriptors;
    private final Method colorFromRgb;
    private final Method colorFromArgb;
    private final Method createBlockData;
    private final ParticleCapabilities capabilities;
    private final Logger logger;
    private final Set<String> diagnostics = new LinkedHashSet<String>();

    public ReflectiveBukkitParticleProvider(ParticleCatalog catalog) {
        this(catalog, Logger.getLogger(ReflectiveBukkitParticleProvider.class.getName()));
    }

    public ReflectiveBukkitParticleProvider(ParticleCatalog catalog, Logger logger) {
        this(catalog, logger, RuntimeBindings.production());
    }

    ReflectiveBukkitParticleProvider(ParticleCatalog catalog, RuntimeBindings bindings) {
        this(catalog, Logger.getLogger(ReflectiveBukkitParticleProvider.class.getName()), bindings);
    }

    ReflectiveBukkitParticleProvider(ParticleCatalog catalog, Logger logger, RuntimeBindings bindings) {
        if (catalog == null || bindings == null) throw new IllegalArgumentException("Particle dependencies cannot be null");
        this.catalog = catalog;
        this.logger = logger == null ? Logger.getLogger(ReflectiveBukkitParticleProvider.class.getName()) : logger;
        this.bukkitColorClass = bindings.colorClass;
        this.colorFromRgb = bindings.colorFromRgb;
        this.colorFromArgb = bindings.colorFromArgb;
        this.createBlockData = bindings.createBlockData;
        this.descriptors = Collections.unmodifiableMap(indexParticles(bindings));
        this.capabilities = new ParticleCapabilities("reflective-bukkit", true,
            new LinkedHashSet<String>(this.descriptors.keySet()));
    }

    private Map<String, RuntimeParticleDescriptor> indexParticles(RuntimeBindings bindings) {
        if (!bindings.suppliedDescriptors.isEmpty()) {
            Map<String, RuntimeParticleDescriptor> supplied = new LinkedHashMap<String, RuntimeParticleDescriptor>();
            for (Map.Entry<String, RuntimeParticleDescriptor> entry : bindings.suppliedDescriptors.entrySet()) {
                RuntimeParticleDescriptor descriptor = entry.getValue();
                if (descriptor == null) continue;
                supplied.put(entry.getKey().toUpperCase(Locale.ROOT), descriptor);
                supplied.put(descriptor.name.toUpperCase(Locale.ROOT), descriptor);
                if (descriptor.key != null) supplied.put(descriptor.key.toUpperCase(Locale.ROOT), descriptor);
            }
            return supplied;
        }
        Map<String, RuntimeParticleDescriptor> result = new LinkedHashMap<String, RuntimeParticleDescriptor>();
        Object[] constants = bindings.constants;
        if (constants == null) throw new IllegalStateException("Particle class is not an enum");
        SpawnMethodSet spawnMethods = bindings.spawnMethods == null
            ? resolveSpawnMethods(bindings.particleClass) : bindings.spawnMethods;
        for (Object constant : constants) {
            if (constant == null) continue;
            String name = constant instanceof Enum<?> ? ((Enum<?>) constant).name() : constant.toString();
            Class<?> requiredType = runtimeDataType(bindings.dataType, constant);
            String key = runtimeKey(bindings.keyMethod, constant);
            ParticleDataKind kind = classify(requiredType);
            Constructor<?> constructor = resolveConstructor(requiredType, kind, bindings.colorClass);
            RuntimeParticleDescriptor descriptor = new RuntimeParticleDescriptor(constant, name, key,
                requiredType, kind, spawnMethods, constructor);
            result.put(name.toUpperCase(Locale.ROOT), descriptor);
            if (key != null) result.put(key.toUpperCase(Locale.ROOT), descriptor);
        }
        return result;
    }

    private static Class<?> runtimeDataType(Method method, Object constant) {
        if (method == null) return null;
        try {
            Object returned = method.invoke(constant);
            return returned instanceof Class<?> ? (Class<?>) returned : null;
        } catch (Exception ignored) {
            return null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    private static String runtimeKey(Method method, Object constant) {
        if (method == null) return null;
        try {
            Object returned = method.invoke(constant);
            return returned == null ? null : returned.toString();
        } catch (Exception ignored) {
            return null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    private static Constructor<?> resolveConstructor(Class<?> requiredType, ParticleDataKind kind, Class<?> color) {
        if (requiredType == null) return null;
        try {
            if (kind == ParticleDataKind.DUST || kind == ParticleDataKind.SPELL) {
                return color == null ? null : requiredType.getConstructor(color, float.class);
            }
            if (kind == ParticleDataKind.DUST_TRANSITION) {
                return color == null ? null : requiredType.getConstructor(color, color, float.class);
            }
            if (kind == ParticleDataKind.TRAIL) {
                return color == null ? null : requiredType.getConstructor(Location.class, color, int.class);
            }
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (LinkageError ignored) {
            return null;
        }
        return null;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        if (type == null) return null;
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static ParticleDataKind classify(Class<?> type) {
        if (type == null || type == Void.class || type == Void.TYPE) return ParticleDataKind.NONE;
        String name = type.getName().toUpperCase(Locale.ROOT);
        if (name.endsWith("DUSTOPTIONS")) return ParticleDataKind.DUST;
        if (name.endsWith("DUSTTRANSITION")) return ParticleDataKind.DUST_TRANSITION;
        if (name.endsWith("SPELL")) return ParticleDataKind.SPELL;
        if (name.endsWith("TRAIL")) return ParticleDataKind.TRAIL;
        if (name.equals("ORG.BUKKIT.COLOR")) return ParticleDataKind.COLOR;
        if (name.endsWith("BLOCKDATA")) return ParticleDataKind.BLOCK_DATA;
        if (name.endsWith("MATERIALDATA")) return ParticleDataKind.MATERIAL_DATA;
        if (name.endsWith("ITEMSTACK")) return ParticleDataKind.ITEM_STACK;
        if (type == java.lang.Float.class || type == Float.TYPE) return ParticleDataKind.FLOAT;
        if (type == java.lang.Integer.class || type == Integer.TYPE) return ParticleDataKind.INTEGER;
        return ParticleDataKind.CUSTOM;
    }

    private static SpawnMethodSet resolveSpawnMethods(Class<?> particleType) {
        List<InvocationMode> modes = new ArrayList<InvocationMode>();
        if (particleType == null) return new SpawnMethodSet(modes);
        for (Method method : Player.class.getMethods()) {
            InvocationMode mode = InvocationMode.resolve(method, particleType);
            if (mode != null) modes.add(mode);
        }
        return new SpawnMethodSet(modes);
    }

    @Override
    public ParticleCapabilities getCapabilities() { return capabilities; }

    @Override
    public boolean emit(Player viewer, ParticleRequest request) {
        try {
            if (viewer == null || request == null) return false;
            Location location = location(request.getPosition());
            if (location == null) return false;
            for (String candidate : request.getCandidates()) {
                for (String key : catalog.resolve(candidate)) {
                    RuntimeParticleDescriptor descriptor = descriptors.get(key.toUpperCase(Locale.ROOT));
                    if (descriptor == null) continue;
                    Object data = adapt(descriptor, request.getPayload());
                    if (data == Unsupported.INSTANCE) {
                        if (isRedstone(candidate, descriptor) && request.getPayload() instanceof ParticleRequest.Color
                            && invokeRedstone(viewer, location, descriptor, request)) return true;
                        continue;
                    }
                    if (invoke(viewer, location, descriptor, request, data)) return true;
                }
            }
        } catch (RuntimeException failure) {
            diagnose("provider-boundary", failure);
        } catch (LinkageError failure) {
            diagnose("provider-boundary", failure);
        }
        return false;
    }

    private static boolean isRedstone(String candidate, RuntimeParticleDescriptor descriptor) {
        return "REDSTONE".equalsIgnoreCase(candidate) || "REDSTONE".equalsIgnoreCase(descriptor.name);
    }

    private Location location(ParticleRequest.ParticlePosition position) {
        if (position == null) return null;
        try {
            return new Location(position.getWorld(), position.getX(), position.getY(), position.getZ());
        } catch (RuntimeException failure) {
            diagnose("location", failure);
            return null;
        } catch (LinkageError failure) {
            diagnose("location", failure);
            return null;
        }
    }

    private Object adapt(RuntimeParticleDescriptor descriptor, ParticleRequest.Payload payload) {
        try {
            switch (descriptor.kind) {
                case NONE:
                    return null;
                case DUST:
                    if (!(payload instanceof ParticleRequest.Color) || descriptor.constructor == null) return Unsupported.INSTANCE;
                    ParticleRequest.Color dust = (ParticleRequest.Color) payload;
                    return descriptor.constructor.newInstance(color(dust.getColor()), dust.getSize());
                case DUST_TRANSITION:
                    if (!(payload instanceof ParticleRequest.DustTransition) || descriptor.constructor == null) return Unsupported.INSTANCE;
                    ParticleRequest.DustTransition transition = (ParticleRequest.DustTransition) payload;
                    return descriptor.constructor.newInstance(color(transition.getFrom()), color(transition.getTo()), transition.getSize());
                case SPELL:
                    if (!(payload instanceof ParticleRequest.Color) || descriptor.constructor == null) return Unsupported.INSTANCE;
                    ParticleRequest.Color spell = (ParticleRequest.Color) payload;
                    return descriptor.constructor.newInstance(color(spell.getColor()), spell.getSize());
                case TRAIL:
                    if (!(payload instanceof ParticleRequest.TrailPayload) || descriptor.constructor == null) return Unsupported.INSTANCE;
                    ParticleRequest.TrailPayload trail = (ParticleRequest.TrailPayload) payload;
                    Location target = location(trail.getTarget());
                    if (target == null) return Unsupported.INSTANCE;
                    return descriptor.constructor.newInstance(target, color(trail.getColor()), trail.getDurationTicks());
                case COLOR:
                    if (!(payload instanceof ParticleRequest.Color)) return Unsupported.INSTANCE;
                    return color(((ParticleRequest.Color) payload).getColor());
                case BLOCK_DATA:
                    if (!(payload instanceof ParticleRequest.Block) || createBlockData == null) return Unsupported.INSTANCE;
                    return createBlockData.invoke(((ParticleRequest.Block) payload).getMaterial());
                case MATERIAL_DATA:
                    if (!(payload instanceof ParticleRequest.Block)) return Unsupported.INSTANCE;
                    ParticleRequest.Block block = (ParticleRequest.Block) payload;
                    return new MaterialData(block.getMaterial(), block.getData());
                case ITEM_STACK:
                    if (!(payload instanceof ParticleRequest.Item)) return Unsupported.INSTANCE;
                    return ((ParticleRequest.Item) payload).getItem();
                case FLOAT:
                    if (!(payload instanceof ParticleRequest.Float)) return Unsupported.INSTANCE;
                    return ((ParticleRequest.Float) payload).getValue();
                case INTEGER:
                    if (!(payload instanceof ParticleRequest.Integer)) return Unsupported.INSTANCE;
                    return ((ParticleRequest.Integer) payload).getValue();
                case CUSTOM:
                    if (!(payload instanceof ParticleRequest.CustomPayload) || descriptor.requiredType == null) {
                        return Unsupported.INSTANCE;
                    }
                    Object value = ((ParticleRequest.CustomPayload) payload).getValue();
                    return descriptor.requiredType.isInstance(value) ? value : Unsupported.INSTANCE;
                default:
                    return Unsupported.INSTANCE;
            }
        } catch (Exception failure) {
            diagnose(descriptor.name, failure);
            return Unsupported.INSTANCE;
        } catch (LinkageError failure) {
            diagnose(descriptor.name, failure);
            return Unsupported.INSTANCE;
        }
    }

    private Object color(ParticleColor value) throws Exception {
        if (value.getAlpha() != 255 && colorFromArgb != null) {
            return colorFromArgb.invoke(null, (value.getAlpha() << 24) | (value.getRed() << 16)
                | (value.getGreen() << 8) | value.getBlue());
        }
        if (colorFromRgb == null) throw new IllegalStateException("Bukkit RGB color factory unavailable");
        return colorFromRgb.invoke(null, value.getRed(), value.getGreen(), value.getBlue());
    }

    private boolean invoke(Player viewer, Location location, RuntimeParticleDescriptor descriptor,
                           ParticleRequest request, Object data) {
        InvocationMode mode = descriptor.spawnMethods.select(data != null);
        if (mode == null) return false;
        if (data != null && !mode.accepts(data)) return false;
        return invokeMode(viewer, location, descriptor, request, mode, data, null);
    }

    private boolean invokeRedstone(Player viewer, Location location, RuntimeParticleDescriptor descriptor,
                                   ParticleRequest request) {
        InvocationMode mode = descriptor.spawnMethods.select(false);
        if (mode == null || !mode.offsets) return false;
        ParticleRequest.Color payload = (ParticleRequest.Color) request.getPayload();
        return invokeMode(viewer, location, descriptor, request, mode, null, payload.getColor());
    }

    private boolean invokeMode(Player viewer, Location location, RuntimeParticleDescriptor descriptor,
                               ParticleRequest request, InvocationMode mode, Object data, ParticleColor offsetColor) {
        try {
            Object[] args = new Object[mode.parameterTypes.length];
            args[0] = descriptor.constant;
            if (mode.location) {
                args[mode.locationIndex] = location;
            } else {
                args[mode.xIndex] = number(mode.parameterTypes[mode.xIndex], request.getPosition().getX());
                args[mode.yIndex] = number(mode.parameterTypes[mode.yIndex], request.getPosition().getY());
                args[mode.zIndex] = number(mode.parameterTypes[mode.zIndex], request.getPosition().getZ());
            }
            args[mode.countIndex] = request.getCount();
            if (mode.offsets) {
                double x = request.getOffsetX();
                double y = request.getOffsetY();
                double z = request.getOffsetZ();
                if (offsetColor != null) {
                    x = offsetColor.getRed() / 255D;
                    y = offsetColor.getGreen() / 255D;
                    z = offsetColor.getBlue() / 255D;
                }
                args[mode.offsetXIndex] = number(mode.parameterTypes[mode.offsetXIndex], x);
                args[mode.offsetYIndex] = number(mode.parameterTypes[mode.offsetYIndex], y);
                args[mode.offsetZIndex] = number(mode.parameterTypes[mode.offsetZIndex], z);
                if (mode.extraIndex >= 0) {
                    args[mode.extraIndex] = number(mode.parameterTypes[mode.extraIndex], request.getExtra());
                }
            }
            if (mode.dataIndex >= 0) args[mode.dataIndex] = data;
            if (mode.forceIndex >= 0) args[mode.forceIndex] = Boolean.TRUE;
            mode.method.invoke(viewer, args);
            return true;
        } catch (Exception failure) {
            diagnose(descriptor.name, failure);
            return false;
        } catch (LinkageError failure) {
            diagnose(descriptor.name, failure);
            return false;
        }
    }

    private static Object number(Class<?> type, double value) {
        if (type == Float.TYPE) {
            return Float.valueOf((float) value);
        }
        return Double.valueOf(value);
    }

    private void diagnose(String key, Throwable failure) {
        synchronized (diagnostics) {
            if (diagnostics.size() >= MAX_DIAGNOSTICS || !diagnostics.add(key)) return;
        }
        logger.log(Level.FINE, "Reflective particle capability unavailable: " + key, failure);
    }

    private enum Unsupported { INSTANCE }

    static final class RuntimeBindings {
        private final Class<?> particleClass;
        private final Class<?> colorClass;
        private final Method dataType;
        private final Method keyMethod;
        private final Method colorFromRgb;
        private final Method colorFromArgb;
        private final Method createBlockData;
        private final Object[] constants;
        private final SpawnMethodSet spawnMethods;
        private final Map<String, RuntimeParticleDescriptor> suppliedDescriptors;

        static RuntimeBindings production() {
            try {
                Class<?> particle = Class.forName("org.bukkit.Particle");
                Class<?> color = loadOptional("org.bukkit.Color");
                Object[] constants = particle.getEnumConstants();
                if (constants == null) throw new IllegalStateException("Particle class is not an enum");
                return new RuntimeBindings(particle, color, findMethod(particle, "getDataType"),
                    findMethod(particle, "getKey"), findMethod(color, "fromRGB", int.class, int.class, int.class),
                    findMethod(color, "fromARGB", int.class), findMethod(Material.class, "createBlockData"),
                    constants, null, Collections.<String, RuntimeParticleDescriptor>emptyMap());
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Bukkit particle API unavailable", failure);
            } catch (LinkageError failure) {
                throw new IllegalStateException("Bukkit particle API unavailable", failure);
            }
        }

        RuntimeBindings(Class<?> particleClass, Class<?> colorClass, Method dataType, Method keyMethod,
                        Method colorFromRgb, Method colorFromArgb, Method createBlockData, Object[] constants,
                        SpawnMethodSet spawnMethods, Map<String, RuntimeParticleDescriptor> descriptors) {
            this.particleClass = particleClass;
            this.colorClass = colorClass;
            this.dataType = dataType;
            this.keyMethod = keyMethod;
            this.colorFromRgb = colorFromRgb;
            this.colorFromArgb = colorFromArgb;
            this.createBlockData = createBlockData;
            this.constants = constants == null ? null : constants.clone();
            this.spawnMethods = spawnMethods;
            Map<String, RuntimeParticleDescriptor> copy = descriptors == null
                ? Collections.<String, RuntimeParticleDescriptor>emptyMap()
                : new LinkedHashMap<String, RuntimeParticleDescriptor>(descriptors);
            this.suppliedDescriptors = Collections.unmodifiableMap(copy);
        }

        private static Class<?> loadOptional(String name) {
            try {
                return Class.forName(name);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
    }

    static final class RuntimeParticleDescriptor {
        final Object constant;
        final String name;
        final String key;
        final Class<?> requiredType;
        final ParticleDataKind kind;
        final SpawnMethodSet spawnMethods;
        final Constructor<?> constructor;

        RuntimeParticleDescriptor(Object constant, String name, String key, Class<?> requiredType,
                                  ParticleDataKind kind, SpawnMethodSet spawnMethods, Constructor<?> constructor) {
            this.constant = constant;
            this.name = name;
            this.key = key;
            this.requiredType = requiredType;
            this.kind = kind;
            this.spawnMethods = spawnMethods == null ? new SpawnMethodSet(Collections.<InvocationMode>emptyList()) : spawnMethods;
            this.constructor = constructor;
        }

        RuntimeParticleDescriptor(Object constant, String name, String key, Class<?> requiredType,
                                  ParticleDataKind kind, Constructor<?> constructor, SpawnMethodSet spawnMethods) {
            this(constant, name, key, requiredType, kind, spawnMethods, constructor);
        }
    }

    static final class SpawnMethodSet {
        private final List<InvocationMode> modes;

        SpawnMethodSet(List<InvocationMode> modes) {
            this.modes = Collections.unmodifiableList(new ArrayList<InvocationMode>(modes));
        }

        InvocationMode select(boolean data) {
            InvocationMode selected = null;
            int selectedScore = -1;
            for (InvocationMode mode : modes) {
                if (mode.data != data) continue;
                int score = (mode.offsets ? 8 : 0) + (mode.location ? 2 : 0) + (mode.force ? 1 : 0);
                if (score > selectedScore) {
                    selected = mode;
                    selectedScore = score;
                }
            }
            return selected;
        }
    }

    static final class InvocationMode {
        final Method method;
        final Class<?>[] parameterTypes;
        final boolean location;
        final boolean offsets;
        final boolean data;
        final boolean force;
        final int locationIndex;
        final int xIndex;
        final int yIndex;
        final int zIndex;
        final int countIndex;
        final int offsetXIndex;
        final int offsetYIndex;
        final int offsetZIndex;
        final int extraIndex;
        final int dataIndex;
        final int forceIndex;

        private InvocationMode(Method method, boolean location, int locationIndex, int xIndex, int yIndex,
                               int zIndex, int countIndex, int offsetXIndex, int offsetYIndex, int offsetZIndex,
                               int extraIndex, int dataIndex, int forceIndex) {
            this.method = method;
            this.parameterTypes = method.getParameterTypes();
            this.location = location;
            this.offsets = offsetXIndex >= 0;
            this.data = dataIndex >= 0;
            this.force = forceIndex >= 0;
            this.locationIndex = locationIndex;
            this.xIndex = xIndex;
            this.yIndex = yIndex;
            this.zIndex = zIndex;
            this.countIndex = countIndex;
            this.offsetXIndex = offsetXIndex;
            this.offsetYIndex = offsetYIndex;
            this.offsetZIndex = offsetZIndex;
            this.extraIndex = extraIndex;
            this.dataIndex = dataIndex;
            this.forceIndex = forceIndex;
        }

        static InvocationMode resolve(Method method, Class<?> particleType) {
            if (!"spawnParticle".equals(method.getName())) return null;
            Class<?>[] types = method.getParameterTypes();
            if (types.length < 3 || types[0] != particleType) return null;
            boolean location = types[1] == Location.class;
            int countIndex;
            int locationIndex = location ? 1 : -1;
            int xIndex = -1;
            int yIndex = -1;
            int zIndex = -1;
            if (location) {
                if (types[2] != int.class) return null;
                countIndex = 2;
            } else {
                if (types.length < 5 || !numeric(types[1]) || !numeric(types[2]) || !numeric(types[3])
                    || types[4] != int.class) return null;
                xIndex = 1;
                yIndex = 2;
                zIndex = 3;
                countIndex = 4;
            }
            int numericCount = 0;
            int offsetXIndex = -1;
            int offsetYIndex = -1;
            int offsetZIndex = -1;
            int extraIndex = -1;
            int dataIndex = -1;
            int forceIndex = -1;
            for (int i = countIndex + 1; i < types.length; i++) {
                Class<?> type = types[i];
                if (numeric(type)) {
                    numericCount++;
                    if (numericCount == 1) offsetXIndex = i;
                    else if (numericCount == 2) offsetYIndex = i;
                    else if (numericCount == 3) offsetZIndex = i;
                    else if (numericCount == 4) extraIndex = i;
                } else if (type == boolean.class && forceIndex < 0) {
                    forceIndex = i;
                } else if (!type.isPrimitive() && dataIndex < 0) {
                    dataIndex = i;
                } else {
                    return null;
                }
            }
            if (numericCount != 0 && numericCount != 3 && numericCount != 4) return null;
            return new InvocationMode(method, location, locationIndex, xIndex, yIndex, zIndex, countIndex,
                offsetXIndex, offsetYIndex, offsetZIndex, extraIndex, dataIndex, forceIndex);
        }

        boolean accepts(Object value) {
            if (dataIndex < 0 || value == null) return false;
            Class<?> type = parameterTypes[dataIndex];
            if (!type.isPrimitive()) return type.isInstance(value);
            return (type == float.class && value instanceof java.lang.Float)
                || (type == double.class && value instanceof java.lang.Double)
                || (type == int.class && value instanceof java.lang.Integer)
                || (type == long.class && value instanceof java.lang.Long)
                || (type == short.class && value instanceof java.lang.Short)
                || (type == byte.class && value instanceof java.lang.Byte)
                || (type == boolean.class && value instanceof java.lang.Boolean)
                || (type == char.class && value instanceof java.lang.Character);
        }

        private static boolean numeric(Class<?> type) {
            return type == double.class || type == float.class;
        }
    }
}
