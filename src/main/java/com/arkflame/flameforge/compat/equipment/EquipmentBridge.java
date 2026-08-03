package com.arkflame.flameforge.compat.equipment;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

public final class EquipmentBridge {
    public enum Slot {
        MAINHAND,
        OFFHAND,
        HEAD,
        CHEST,
        LEGS,
        FEET
    }

    private final Method getItemInOffhandMethod;
    private final boolean offhandSupported;

    public EquipmentBridge() {
        Method method = null;
        boolean supported = false;
        try {
            method = Player.class.getMethod("getItemInOffhand");
            supported = true;
        } catch (NoSuchMethodException e) {
            method = null;
            supported = false;
        }
        this.getItemInOffhandMethod = method;
        this.offhandSupported = supported;
    }

    public ItemStack getItem(Player player, Slot slot) {
        if (player == null || slot == null) {
            return null;
        }
        switch (slot) {
            case MAINHAND:
                return getMainHand(player);
            case OFFHAND:
                return getOffHand(player);
            case HEAD:
            case CHEST:
            case LEGS:
            case FEET:
                return getArmorSlot(player, slot);
            default:
                return null;
        }
    }

    public ItemStack getMainHand(Player player) {
        if (player == null) {
            return null;
        }
        return player.getInventory().getItemInHand();
    }

    public ItemStack getOffHand(Player player) {
        if (player == null) {
            return null;
        }
        if (offhandSupported && getItemInOffhandMethod != null) {
            try {
                return (ItemStack) getItemInOffhandMethod.invoke(player);
            } catch (Exception e) {
                return null;
            }
        }
        return getOffHandFallback(player);
    }

    private ItemStack getOffHandFallback(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor.length > 3) {
            return armor[3];
        }
        return new ItemStack(Material.AIR);
    }

    public ItemStack getArmorSlot(Player player, Slot slot) {
        if (player == null || slot == null) {
            return null;
        }
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor == null || armor.length < 4) {
            return new ItemStack(Material.AIR);
        }
        switch (slot) {
            case HEAD:
                return armor[3];
            case CHEST:
                return armor[2];
            case LEGS:
                return armor[1];
            case FEET:
                return armor[0];
            default:
                return new ItemStack(Material.AIR);
        }
    }

    public ItemStack getHelmet(Player player) {
        return getArmorSlot(player, Slot.HEAD);
    }

    public ItemStack getChestplate(Player player) {
        return getArmorSlot(player, Slot.CHEST);
    }

    public ItemStack getLeggings(Player player) {
        return getArmorSlot(player, Slot.LEGS);
    }

    public ItemStack getBoots(Player player) {
        return getArmorSlot(player, Slot.FEET);
    }

    public ItemStack[] getArmorContents(Player player) {
        if (player == null) {
            return new ItemStack[4];
        }
        return player.getInventory().getArmorContents();
    }

    public boolean isOffhandSupported() {
        return offhandSupported;
    }
}
