package com.arkflame.flameforge.compat;

import java.util.LinkedHashMap;
import java.util.Map;

public final class RuntimeCapabilities {
    private static final RuntimeCapabilities INSTANCE = new RuntimeCapabilities();

    private final Map<String, Boolean> capabilities = new LinkedHashMap<>();
    private volatile boolean initialized = false;

    private RuntimeCapabilities() {
    }

    public static RuntimeCapabilities getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        detect("adventure.BukkitAudiences", () -> exists("net.kyori.adventure.platform.bukkit.BukkitAudiences"));
        detect("adventure.Component", () -> exists("net.kyori.adventure.text.Component"));
        detect("adventure.TextComponent", () -> exists("net.kyori.adventure.text.TextComponent"));
        detect("adventure.Style", () -> exists("net.kyori.adventure.text.style.Style"));
        detect("adventure.MiniMessage", () -> exists("net.kyori.adventure.text.minimessage.MiniMessage"));
        detect("nms.NBTBlockPosition", () -> exists("net.minecraft.core.BlockPosition"));
        detect("nms.NBTTagCompound", () -> exists("net.minecraft.nbt.NBTTagCompound"));
        detect("nms.NMSHandler", () -> hasNMSHandler());
        detect("packet.PlayOutChat", () -> hasPacketPlayOutChat());
        initialized = true;
    }

    private void detect(final String key, final java.util.function.Supplier<Boolean> detector) {
        capabilities.put(key, detector.get());
    }

    private boolean exists(final String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean hasNMSHandler() {
        return exists("net.minecraft.server.v1_8_R3.NBTCompressedStreamTools")
                || exists("net.minecraft.server.v1_12_R1.NBTCompressedStreamTools")
                || exists("net.minecraft.server.v1_15_R1.NBTCompressedStreamTools")
                || exists("net.minecraft.server.v1_16_R3.NBTCompressedStreamTools")
                || exists("net.minecraft.server.v1_17_R1.NBTCompressedStreamTools")
                || exists("net.minecraft.server.v1_18_R2.NBTCompressedStreamTools")
                || exists("net.minecraft.server.v1_19_R1.NBTCompressedStreamTools")
                || exists("net.minecraft.server.v1_20_R1.NBTCompressedStreamTools")
                || exists("net.minecraft.server.v1_20_R3.NBTCompressedStreamTools")
                || exists("net.minecraft.server.v1_21_R1.NBTCompressedStreamTools");
    }

    private boolean hasPacketPlayOutChat() {
        return exists("net.minecraft.server.v1_8_R3.PacketPlayOutChat")
                || exists("net.minecraft.server.v1_12_R1.PacketPlayOutChat")
                || exists("net.minecraft.server.v1_15_R1.PacketPlayOutChat")
                || exists("net.minecraft.server.v1_16_R3.PacketPlayOutChat");
    }

    public boolean isSupported(final String capability) {
        if (!initialized) {
            initialize();
        }
        return capabilities.getOrDefault(capability, false);
    }

    public boolean isAdventureSupported() {
        return isSupported("adventure.BukkitAudiences");
    }

    public boolean isNMSupported() {
        return isSupported("nms.NMSHandler");
    }

    public boolean isModernChatSupported() {
        return isSupported("packet.PlayOutChat");
    }

    public Map<String, Boolean> getCapabilities() {
        if (!initialized) {
            initialize();
        }
        return new LinkedHashMap<>(capabilities);
    }

    @Override
    public String toString() {
        if (!initialized) {
            initialize();
        }
        final StringBuilder sb = new StringBuilder("RuntimeCapabilities{\n");
        for (final Map.Entry<String, Boolean> entry : capabilities.entrySet()) {
            sb.append("  ").append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
        }
        sb.append("}");
        return sb.toString();
    }
}
