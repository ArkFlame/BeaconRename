package com.arkflame.flameforge.compat.effect.particle;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ParticleRequest {
    private static final int MAX_CANDIDATES = 32;
    private final ParticlePosition position;
    private final List<String> candidates;
    private final int count;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final double extra;
    private final Payload payload;

    public ParticleRequest(ParticlePosition position, List<String> candidates, int count,
                           double offsetX, double offsetY, double offsetZ, double extra,
                           Payload payload) {
        if (position == null || candidates == null || candidates.size() > MAX_CANDIDATES) {
            throw new IllegalArgumentException("Invalid particle request");
        }
        if (count < 0 || !finite(offsetX) || !finite(offsetY) || !finite(offsetZ) || !finite(extra)) {
            throw new IllegalArgumentException("Invalid particle request values");
        }
        if (payload == null) {
            throw new IllegalArgumentException("Particle payload cannot be null");
        }
        List<String> copy = new ArrayList<String>(candidates.size());
        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) {
                throw new IllegalArgumentException("Particle candidate cannot be blank");
            }
            copy.add(candidate);
        }
        this.position = position;
        this.candidates = Collections.unmodifiableList(copy);
        this.count = count;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.extra = extra;
        this.payload = payload;
    }

    public ParticleRequest(ParticlePosition position, List<String> candidates, int count,
                           double offsetX, double offsetY, double offsetZ, double extra) {
        this(position, candidates, count, offsetX, offsetY, offsetZ, extra, new None());
    }

    public ParticlePosition getPosition() { return position; }
    public List<String> getCandidates() { return candidates; }
    public int getCount() { return count; }
    public double getOffsetX() { return offsetX; }
    public double getOffsetY() { return offsetY; }
    public double getOffsetZ() { return offsetZ; }
    public double getExtra() { return extra; }
    public Payload getPayload() { return payload; }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public static final class ParticlePosition {
        private final World world;
        private final double x;
        private final double y;
        private final double z;

        public ParticlePosition(World world, double x, double y, double z) {
            if (world == null || !finite(x) || !finite(y) || !finite(z)) {
                throw new IllegalArgumentException("Invalid particle position");
            }
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public World getWorld() { return world; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
    }

    public abstract static class Payload {
        protected Payload() { }
    }

    public static final class None extends Payload { }

    public static final class Color extends Payload {
        private final ParticleColor color;
        private final float size;
        public Color(ParticleColor color, float size) {
            if (color == null || java.lang.Float.isNaN(size) || java.lang.Float.isInfinite(size)) {
                throw new IllegalArgumentException("Invalid particle color payload");
            }
            this.color = color;
            this.size = size;
        }
        public ParticleColor getColor() { return color; }
        public float getSize() { return size; }
    }

    public static final class DustTransition extends Payload {
        private final ParticleColor from;
        private final ParticleColor to;
        private final float size;
        public DustTransition(ParticleColor from, ParticleColor to, float size) {
            if (from == null || to == null || java.lang.Float.isNaN(size) || java.lang.Float.isInfinite(size)) {
                throw new IllegalArgumentException("Invalid dust transition payload");
            }
            this.from = from;
            this.to = to;
            this.size = size;
        }
        public ParticleColor getFrom() { return from; }
        public ParticleColor getTo() { return to; }
        public float getSize() { return size; }
    }

    public static final class Block extends Payload {
        private final Material material;
        private final byte data;
        public Block(Material material, byte data) {
            if (material == null) throw new IllegalArgumentException("Block material cannot be null");
            this.material = material;
            this.data = data;
        }
        public Material getMaterial() { return material; }
        public byte getData() { return data; }
    }

    public static final class Item extends Payload {
        private final ItemStack item;
        public Item(ItemStack item) {
            if (item == null) throw new IllegalArgumentException("Item cannot be null");
            this.item = item.clone();
        }
        public ItemStack getItem() { return item.clone(); }
    }

    public static final class Float extends Payload {
        private final float value;
        public Float(float value) {
            if (java.lang.Float.isNaN(value) || java.lang.Float.isInfinite(value)) {
                throw new IllegalArgumentException("Float payload must be finite");
            }
            this.value = value;
        }
        public float getValue() { return value; }
    }

    public static final class Integer extends Payload {
        private final int value;
        public Integer(int value) { this.value = value; }
        public int getValue() { return value; }
    }

    public static class TrailPayload extends Payload {
        private final ParticlePosition target;
        private final ParticleColor color;
        private final int durationTicks;
        public TrailPayload(ParticlePosition target, ParticleColor color, int durationTicks) {
            if (target == null || color == null || durationTicks < 0) {
                throw new IllegalArgumentException("Invalid trail payload");
            }
            this.target = target;
            this.color = color;
            this.durationTicks = durationTicks;
        }
        public ParticlePosition getTarget() { return target; }
        public ParticleColor getColor() { return color; }
        public int getDurationTicks() { return durationTicks; }
    }

    public static final class Trail extends TrailPayload {
        public Trail(ParticlePosition target, ParticleColor color) {
            this(target, color, 0);
        }

        public Trail(ParticlePosition target, ParticleColor color, int durationTicks) {
            super(target, color, durationTicks);
        }
    }

    public static class CustomPayload extends Payload {
        private final Object value;
        public CustomPayload(Object value) {
            if (value == null) throw new IllegalArgumentException("Custom payload cannot be null");
            this.value = value;
        }
        public Object getValue() { return value; }
    }

    public static final class Custom extends CustomPayload {
        public Custom(Object value) {
            super(value);
        }
    }
}
