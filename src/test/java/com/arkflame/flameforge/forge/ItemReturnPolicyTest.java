package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.compat.scheduler.TaskHandle;
import com.arkflame.flameforge.persistence.AuditLogService;
import com.arkflame.flameforge.persistence.PendingDelivery;
import com.arkflame.flameforge.persistence.PendingDeliveryRepository;
import com.arkflame.flameforge.text.TextBridge;
import com.arkflame.flameforge.testfakes.FakeTextBridge;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

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
        fakePlugin = mock(JavaPlugin.class);
        when(fakePlugin.getLogger()).thenReturn(Logger.getLogger(ItemReturnPolicyTest.class.getName()));
        fakeScheduler = new FakeSchedulerBridge();
        repository = new PendingDeliveryRepository(fakePlugin, fakeScheduler, tempDir);

        textBridge = new FakeTextBridge();

        auditLog = new AuditLogService(fakePlugin, fakeScheduler, tempDir, 100);

        deliveryService = new DeliveryService(
            fakePlugin, fakeScheduler, repository,
            textBridge, auditLog, new HashMap<>()
        );
    }

    @Test
    void invalidNullOrAirDeliveryIsRejectedWithoutSideEffects() {
        boolean nullResult = deliveryService.deliverItem(null, null, null, "id1");
        assertFalse(nullResult);

        ItemStack air = new ItemStack(Material.AIR);
        boolean airResult = deliveryService.deliverItem(air, null, null, "id2");
        assertFalse(airResult);

        assertFalse(deliveryService.hasBeenProcessed("id1"));
        assertFalse(deliveryService.hasBeenProcessed("id2"));
    }

    @Test
    void deliveryWithoutIdDeliversNormally() {
        FakeInventory inventory = new FakeInventory();
        Player player = createFakePlayerWithInventory(inventory);
        ItemStack item = new ItemStack(Material.DIAMOND, 1);

        boolean result = deliveryService.deliverItem(item, player, null, null);

        assertTrue(result);
        assertEquals(1, inventory.itemCount);
    }

    @Test
    void deliveryIdIsProcessedOnceAndDifferentIdsRemainIndependent() {
        FakeInventory inventory = new FakeInventory();
        Player player = createFakePlayerWithInventory(inventory);
        ItemStack item = new ItemStack(Material.DIAMOND, 1);

        boolean first = deliveryService.deliverItem(item, player, null, "unique-id-a");
        boolean second = deliveryService.deliverItem(item, player, null, "unique-id-b");

        assertTrue(first);
        assertTrue(second);
        assertTrue(deliveryService.hasBeenProcessed("unique-id-a"));
        assertTrue(deliveryService.hasBeenProcessed("unique-id-b"));

        ItemStack item2 = new ItemStack(Material.GOLD_INGOT, 1);
        boolean third = deliveryService.deliverItem(item2, player, null, "unique-id-a");
        assertFalse(third);
    }

    @Test
    void pendingDeliveryRejectsNullAndDuplicateIds() {
        UUID playerUuid = UUID.randomUUID();
        ItemStack item = createMockItemStack(Material.DIAMOND, 1);

        boolean nullResult = deliveryService.queuePendingDelivery(null, playerUuid, item, null);
        assertFalse(nullResult);

        String duplicateId = "dup-id";
        deliveryService.queuePendingDelivery(duplicateId, playerUuid, item, null);
        boolean dupResult = deliveryService.queuePendingDelivery(duplicateId, playerUuid, item, null);
        assertFalse(dupResult);
    }

    @Test
    void pendingDeliveryStoresExactRecipientItemAndLocation() {
        UUID playerUuid = UUID.randomUUID();
        String deliveryId = "pending-item-test";
        ItemStack item = createMockItemStack(Material.EMERALD, 5);
        List<String> commands = Arrays.asList("/say delivered");

        deliveryService.queuePendingDelivery(deliveryId, playerUuid, item, commands);

        assertTrue(deliveryService.isDeliveryPending(deliveryId));

        List<PendingDelivery> all = repository.getAllSnapshot();
        assertEquals(1, all.size());
        PendingDelivery delivery = all.get(0);
        assertEquals(deliveryId, delivery.getDeliveryId());
        assertEquals(playerUuid, delivery.getTargetPlayer());
        assertEquals("EMERALD", delivery.getItemSnapshot().get("material"));
    }

    @Test
    void generatedDeliveryIdsAreUniqueAndProcessedStateReflectsSuccess() {
        FakeInventory inventory = new FakeInventory();
        Player player = createFakePlayerWithInventory(inventory);
        ItemStack item = new ItemStack(Material.DIAMOND, 1);

        String id1 = deliveryService.generateDeliveryId(player, "outcome1");
        String id2 = deliveryService.generateDeliveryId(player, "outcome1");

        assertNotEquals(id1, id2);

        boolean delivered = deliveryService.deliverItem(item, player, null, id1);
        assertTrue(delivered);
        assertTrue(deliveryService.hasBeenProcessed(id1));
        assertFalse(deliveryService.hasBeenProcessed(id2));

        boolean duplicateDelivery = deliveryService.deliverItem(item, player, null, id1);
        assertFalse(duplicateDelivery);
    }

    private static ItemStack createMockItemStack(Material material, int amount) {
        return new TestItemStack(material, amount);
    }

    private static class TestItemStack extends ItemStack implements java.io.Serializable {
        private final String materialName;
        private final int amount;

        TestItemStack(Material material, int amount) {
            super(material, amount);
            this.materialName = material.name();
            this.amount = amount;
        }

        private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
            out.defaultWriteObject();
            out.writeUTF(materialName);
            out.writeInt(amount);
        }

        private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
            in.defaultReadObject();
        }

        @Override
        public Material getType() {
            return Material.valueOf(materialName);
        }

        @Override
        public int getAmount() {
            return amount;
        }
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
