package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.persistence.AuditLogService;
import com.arkflame.flameforge.persistence.PendingDelivery;
import com.arkflame.flameforge.persistence.PendingDeliveryRepository;
import com.arkflame.flameforge.text.TextBridge;
import com.arkflame.flameforge.text.TextPlaceholders;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ItemReturnPolicyTest {

    private DeliveryService deliveryService;
    private JavaPlugin fakePlugin;
    private FakeSchedulerBridge fakeScheduler;
    private PendingDeliveryRepository repository;
    private TextBridge textBridge;
    private AuditLogService auditLog;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        fakePlugin = UnsafeJavaPlugin.createFakePlugin(FakePlugin.class);
        fakeScheduler = new FakeSchedulerBridge();
        repository = new PendingDeliveryRepository(fakePlugin, fakeScheduler, tempDir);

        if (!TextBridge.isInitialized()) {
            try {
                textBridge = TextBridge.create(fakePlugin, TextPlaceholders.getInstance());
            } catch (Exception e) {
                textBridge = (TextBridge) Proxy.newProxyInstance(
                    TextBridge.class.getClassLoader(),
                    new Class<?>[] { TextBridge.class },
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (name.equals("parse")) return net.kyori.adventure.text.Component.text((String) args[0]);
                        if (name.equals("send") || name.equals("sendAll")) return null;
                        if (name.equals("toString")) return "FakeTextBridge{}";
                        if (name.equals("equals")) return proxy == args[0];
                        if (name.equals("hashCode")) return System.identityHashCode(proxy);
                        return null;
                    }
                );
            }
        } else {
            textBridge = TextBridge.getInstance();
        }

        auditLog = new AuditLogService(fakePlugin, fakeScheduler, tempDir, 100);

        deliveryService = new DeliveryService(
            fakePlugin, fakeScheduler, repository,
            textBridge, auditLog, new HashMap<>()
        );
    }

    @Test
    void deliverItem_nullItem_returnsFalse() {
        boolean result = deliveryService.deliverItem(null, null, null, "id1");
        assertFalse(result);
    }

    @Test
    void deliverItem_airItem_returnsFalse() {
        ItemStack air = new ItemStack(Material.AIR);
        boolean result = deliveryService.deliverItem(air, null, null, "id1");
        assertFalse(result);
    }

    @Test
    void deliverItem_nullDeliveryId_deliversToPlayer() {
        Player player = createFakePlayerWithInventory(new FakeInventory());
        ItemStack item = new ItemStack(Material.DIAMOND, 1);

        boolean result = deliveryService.deliverItem(item, player, null, null);

        assertTrue(result);
        assertEquals(1, ((FakeInventory) player.getInventory()).itemCount);
    }

    @Test
    void deliverItem_duplicateDeliveryId_returnsFalse() {
        Player player = createFakePlayerWithInventory(new FakeInventory());
        ItemStack item = new ItemStack(Material.DIAMOND, 1);

        deliveryService.deliverItem(item, player, null, "same-id");
        boolean second = deliveryService.deliverItem(item, player, null, "same-id");

        assertFalse(second);
    }

    @Test
    void deliverItem_differentDeliveryIds_bothSucceed() {
        Player player = createFakePlayerWithInventory(new FakeInventory());
        ItemStack item = new ItemStack(Material.DIAMOND, 1);

        boolean first = deliveryService.deliverItem(item, player, null, "id-a");
        boolean second = deliveryService.deliverItem(item, player, null, "id-b");

        assertTrue(first);
        assertTrue(second);
    }

    @Test
    void deliverItem_nullPlayerAndNullLocation_returnsFalse() {
        ItemStack item = new ItemStack(Material.DIAMOND, 1);
        boolean result = deliveryService.deliverItem(item, null, null, "no-target");
        assertFalse(result);
    }

    @Test
    void queuePendingDelivery_nullId_returnsFalse() {
        boolean result = deliveryService.queuePendingDelivery(null, UUID.randomUUID(),
            new ItemStack(Material.DIAMOND), null);
        assertFalse(result);
    }

    @Test
    void queuePendingDelivery_validIdAddedAndContains() {
        String deliveryId = "pending-1";
        ItemStack item = new ItemStack(Material.DIAMOND);

        boolean result = deliveryService.queuePendingDelivery(deliveryId, UUID.randomUUID(),
            item, Arrays.asList("/say hello", "/give %player% diamond"));

        assertTrue(result);
        assertTrue(deliveryService.isDeliveryPending(deliveryId));
    }

    @Test
    void queuePendingDelivery_duplicateId_returnsFalse() {
        String deliveryId = "dup-pending";
        ItemStack item = new ItemStack(Material.DIAMOND);

        deliveryService.queuePendingDelivery(deliveryId, UUID.randomUUID(), item, null);
        boolean second = deliveryService.queuePendingDelivery(deliveryId, UUID.randomUUID(), item, null);

        assertFalse(second);
    }

    @Test
    void generateDeliveryId_isUnique() {
        Player player = createFakePlayerWithInventory(new FakeInventory());

        String id1 = deliveryService.generateDeliveryId(player, "outcome1");
        String id2 = deliveryService.generateDeliveryId(player, "outcome1");

        assertNotEquals(id1, id2);
        assertTrue(id1.contains("outcome1"));
        assertTrue(id2.contains("outcome1"));
    }

    @Test
    void hasBeenProcessed_afterSuccessfulDelivery_true() {
        FakeInventory inventory = new FakeInventory();
        Player player = createFakePlayerWithInventory(inventory);
        ItemStack item = new ItemStack(Material.DIAMOND, 1);
        String deliveryId = "processed-1";

        deliveryService.deliverItem(item, player, null, deliveryId);

        assertTrue(deliveryService.hasBeenProcessed(deliveryId));
    }

    @Test
    void hasBeenProcessed_beforeDelivery_false() {
        assertFalse(deliveryService.hasBeenProcessed("never-seen-id"));
    }

    @Test
    void queuePendingDelivery_containsCorrectData() {
        UUID playerUuid = UUID.randomUUID();
        List<String> commands = Arrays.asList("/eco give %player% 100");
        String deliveryId = "cq-1";

        deliveryService.queuePendingDelivery(deliveryId, playerUuid,
            new ItemStack(Material.DIAMOND, 1), commands);

        List<PendingDelivery> all = repository.getAllSnapshot();
        assertEquals(1, all.size());
        PendingDelivery delivery = all.get(0);
        assertEquals(deliveryId, delivery.getDeliveryId());
        assertEquals(playerUuid, delivery.getTargetPlayer());
    }

    private static Player createFakePlayerWithInventory(PlayerInventory inventory) {
        return createFakePlayerWithInventoryAt(inventory, null);
    }

    private static Player createFakePlayerWithInventoryAt(PlayerInventory inventory, Location location) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            switch (name) {
                case "getInventory": return inventory;
                case "getLocation": return location != null ? location : new Location(null, 0, 0, 0);
                case "getWorld": return location != null ? location.getWorld() : null;
                case "isOnline": return true;
                case "getName": return "FakePlayer";
                case "getUniqueId": return UUID.randomUUID();
                case "getAddress": return null;
                case "getDisplayName": return "FakePlayer";
                case "getPlayerListName": return "FakePlayer";
                case "getPlayer": return proxy;
                case "isBanned": return false;
                case "isWhitelisted": return false;
                case "hasPlayedBefore": return true;
                case "getFirstPlayed": return 0L;
                case "getLastPlayed": return 0L;
                case "getBedSpawnLocation": return null;
                case "setBedSpawnLocation": return null;
                case "getEnderChest": return null;
                case "isPermissionSet": return false;
                case "hasPermission": return false;
                case "isOp": return false;
                case "setOp": return null;
                case "sendMessage": return null;
                case "getServer": return null;
                case "getEffectivePermissions": return Collections.emptySet();
                case "recalculatePermissions": return null;
                case "addAttachment": return null;
                case "removeAttachment": return null;
                case "isConversing": return false;
                case "acceptConversationInput": return null;
                case "abandonConversation": return null;
                case "beginConversation": return false;
                case "sendRawMessage": return null;
                case "sendPluginMessage": return null;
                case "getListeningPluginChannels": return Collections.emptySet();
                case "performCommand": return true;
                case "equals": return proxy == args[0];
                case "hashCode": return System.identityHashCode(proxy);
                case "toString": return "FakePlayer{}";
                case "spigot": return null;
                case "getLevel": return 0;
                case "setLevel": return null;
                default:
                    if (name.startsWith("get")) return null;
                    if (name.startsWith("is")) return false;
                    if (name.startsWith("set")) return null;
                    return false;
            }
        };

        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[] { Player.class },
            handler
        );
    }

    private static class FakePlugin extends JavaPlugin {
        @Override public void onEnable() {}
        @Override public void onDisable() {}
    }

    private static class FakeSchedulerBridge implements SchedulerBridge {
        @Override
        public TaskHandle runGlobal(JavaPlugin plugin, Runnable task) { return TaskHandleStub.INSTANCE; }
        @Override
        public TaskHandle runGlobalLater(JavaPlugin plugin, Runnable task, long delay) { return TaskHandleStub.INSTANCE; }
        @Override
        public TaskHandle runEntity(org.bukkit.entity.Entity entity, Runnable runnable, Runnable retireCallback) { return TaskHandleStub.INSTANCE; }
        @Override
        public TaskHandle runEntityLater(org.bukkit.entity.Entity entity, Runnable runnable, Runnable retireCallback, long delay) { return TaskHandleStub.INSTANCE; }
        @Override
        public TaskHandle runRegion(Location location, Runnable task) { return TaskHandleStub.INSTANCE; }
        @Override
        public TaskHandle runRegionLater(Location location, Runnable task, long delay) { return TaskHandleStub.INSTANCE; }
        @Override
        public TaskHandle runAsync(JavaPlugin plugin, Runnable task) { return TaskHandleStub.INSTANCE; }
        @Override
        public void cancelAll(JavaPlugin plugin) {}
        @Override
        public boolean isFolia() { return false; }
    }

    private enum TaskHandleStub implements TaskHandle {
        INSTANCE;
        @Override public void cancel() {}
        @Override public boolean isCancelled() { return false; }
    }

    private static class FakeInventory implements PlayerInventory {
        int itemCount = 0;
        boolean alwaysFull = false;
        private final Map<Integer, ItemStack> slots = new HashMap<>();

        @Override
        public HashMap<Integer, ItemStack> addItem(ItemStack... items) {
            if (alwaysFull) {
                HashMap<Integer, ItemStack> overflow = new HashMap<>();
                for (int i = 0; i < items.length; i++) {
                    overflow.put(i, items[i]);
                }
                return overflow;
            }
            for (ItemStack item : items) {
                if (item != null) itemCount += item.getAmount();
            }
            return new HashMap<>();
        }
        @Override public ItemStack[] getContents() { return new ItemStack[0]; }
        @Override public void setContents(ItemStack[] items) {}
        @Override public ItemStack getItem(int index) { return slots.get(index); }
        @Override public void setItem(int index, ItemStack item) { slots.put(index, item); }
        @Override public boolean contains(Material material) { return false; }
        @Override public boolean contains(int materialId) { return false; }
        @Override public boolean contains(Material material, int amount) { return false; }
        @Override public boolean contains(ItemStack item) { return false; }
        @Override public boolean contains(int materialId, int amount) { return false; }
        @Override public boolean contains(ItemStack item, int amount) { return false; }
        @Override public int getSize() { return 36; }
        @Override public org.bukkit.entity.HumanEntity getHolder() { return null; }
        @Override public ItemStack getHelmet() { return null; }
        @Override public ItemStack getChestplate() { return null; }
        @Override public ItemStack getLeggings() { return null; }
        @Override public ItemStack getBoots() { return null; }
        @Override public void setHelmet(ItemStack item) {}
        @Override public void setChestplate(ItemStack item) {}
        @Override public void setLeggings(ItemStack item) {}
        @Override public void setBoots(ItemStack item) {}
        @Override public void setArmorContents(ItemStack[] items) {}
        @Override public ItemStack[] getArmorContents() { return new ItemStack[4]; }
        @Override public void clear() {}
        @Override public void clear(int slot) {}
        @Override public boolean containsAtLeast(ItemStack item, int amount) { return false; }
        @Override public int clear(int slot, int amount) { return 0; }
        @Override public HashMap<Integer, ItemStack> removeItem(ItemStack... items) { return new HashMap<>(); }
        @Override public HashMap<Integer, ? extends ItemStack> all(int materialId) { return new HashMap<>(); }
        @Override public HashMap<Integer, ? extends ItemStack> all(Material material) { return new HashMap<>(); }
        @Override public HashMap<Integer, ? extends ItemStack> all(ItemStack item) { return new HashMap<>(); }
        @Override public int first(int materialId) { return -1; }
        @Override public int first(Material material) { return -1; }
        @Override public int first(ItemStack item) { return -1; }
        @Override public int firstEmpty() { return 0; }
        @Override public void remove(int materialId) {}
        @Override public void remove(Material material) {}
        @Override public void remove(ItemStack item) {}
        @Override public int getMaxStackSize() { return 64; }
        @Override public void setMaxStackSize(int size) {}
        @Override public String getName() { return "FakeInventory"; }
        @Override public String getTitle() { return "FakeInventory"; }
        @Override public List<org.bukkit.entity.HumanEntity> getViewers() { return new ArrayList<>(); }
        @Override public ItemStack getItemInHand() { return null; }
        @Override public void setItemInHand(ItemStack item) {}
        @Override public int getHeldItemSlot() { return 0; }
        @Override public void setHeldItemSlot(int slot) {}
        @Override public ListIterator<ItemStack> iterator() { return new ArrayList<ItemStack>().listIterator(); }
        @Override public org.bukkit.event.inventory.InventoryType getType() { return null; }
        @Override public ListIterator<ItemStack> iterator(int index) { return new ArrayList<ItemStack>().listIterator(index); }
    }
}
