package com.arkflame.flameforge.compat.equipment;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class EquipmentBridge {
    public enum Slot {
        MAINHAND,
        OFFHAND,
        HEAD,
        CHEST,
        LEGS,
        FEET
        , INVENTORY
    }

    private final Method getItemInOffhandMethod;
    private final Method getStorageContentsMethod;
    private final boolean offhandSupported;

    public EquipmentBridge() {
        Method method = null;
        Method storageMethod = null;
        boolean supported = false;
        try {
            method = PlayerInventory.class.getMethod("getItemInOffHand");
            supported = true;
        } catch (NoSuchMethodException e) {
            method = null;
            supported = false;
        }
        try {
            storageMethod = PlayerInventory.class.getMethod("getStorageContents");
        } catch (NoSuchMethodException e) {
            storageMethod = null;
        }
        this.getItemInOffhandMethod = method;
        this.getStorageContentsMethod = storageMethod;
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
            case INVENTORY:
                return null;
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
                return (ItemStack) getItemInOffhandMethod.invoke(player.getInventory());
            } catch (Exception e) {
                return null;
            }
        }
        return getOffHandFallback(player);
    }

    private ItemStack getOffHandFallback(Player player) {
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

    public ItemStack[] getInventoryContents(Player player) {
        if (player == null || player.getInventory() == null) {
            return new ItemStack[0];
        }
        ItemStack[] contents = player.getInventory().getContents();
        if (contents == null) {
            return new ItemStack[0];
        }
        return contents.clone();
    }

    public ItemStack[] getStorageContents(Player player) {
        if (player == null || player.getInventory() == null) {
            return new ItemStack[0];
        }
        if (getStorageContentsMethod != null) {
            try {
                ItemStack[] storage = (ItemStack[]) getStorageContentsMethod.invoke(player.getInventory());
                if (storage == null) {
                    return new ItemStack[0];
                }
                return storage.clone();
            } catch (Exception e) {
                return new ItemStack[0];
            }
        }
        ItemStack[] contents = player.getInventory().getContents();
        if (contents == null) {
            return new ItemStack[0];
        }
        int count = Math.min(36, contents.length);
        ItemStack[] storage = new ItemStack[count];
        System.arraycopy(contents, 0, storage, 0, count);
        return storage;
    }

    public boolean isOffhandSupported() {
        return offhandSupported;
    }

    public boolean registerOffhandSwapListener(JavaPlugin plugin, Consumer<Player> callback) {
        if (plugin == null || callback == null) {
            return false;
        }
        final Class<?> eventClass;
        try {
            eventClass = Class.forName("org.bukkit.event.player.PlayerSwapHandItemsEvent",
                false, plugin.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            return false;
        }
        if (!Event.class.isAssignableFrom(eventClass)) {
            return false;
        }
        final Method getPlayer;
        try {
            getPlayer = eventClass.getMethod("getPlayer");
        } catch (NoSuchMethodException e) {
            return false;
        }
        final Class<?> resolvedEventClass = eventClass;
        final Consumer<Player> resolvedCallback = callback;
        try {
            plugin.getServer().getPluginManager().registerEvent(
                (Class) resolvedEventClass,
                new Listener() {
                },
                EventPriority.MONITOR,
                new EventExecutor() {
                    @Override
                    public void execute(Listener listener, Event event) {
                        try {
                            Object player = getPlayer.invoke(event);
                            if (player instanceof Player) {
                                resolvedCallback.accept((Player) player);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                },
                plugin
            );
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean registerDispenserArmorListener(JavaPlugin plugin, Consumer<Player> callback) {
        if (plugin == null || callback == null) {
            return false;
        }
        final Class<?> eventClass;
        try {
            eventClass = Class.forName("org.bukkit.event.block.BlockDispenseArmorEvent",
                false, plugin.getClass().getClassLoader());
        } catch (ClassNotFoundException e) {
            return false;
        }
        if (!Event.class.isAssignableFrom(eventClass)) {
            return false;
        }
        final Method getTargetEntity;
        try {
            getTargetEntity = eventClass.getMethod("getTargetEntity");
        } catch (NoSuchMethodException e) {
            return false;
        }
        final Class<?> resolvedEventClass = eventClass;
        final Consumer<Player> resolvedCallback = callback;
        try {
            plugin.getServer().getPluginManager().registerEvent(
                (Class) resolvedEventClass,
                new Listener() {
                },
                EventPriority.MONITOR,
                new EventExecutor() {
                    @Override
                    public void execute(Listener listener, Event event) {
                        if (event instanceof Cancellable && ((Cancellable) event).isCancelled()) {
                            return;
                        }
                        try {
                            Object target = getTargetEntity.invoke(event);
                            if (target instanceof Player) {
                                resolvedCallback.accept((Player) target);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                },
                plugin
            );
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
