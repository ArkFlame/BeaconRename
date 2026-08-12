package com.arkflame.flameforge.forge;

import com.arkflame.flameforge.persistence.AuditLogService;
import com.arkflame.flameforge.persistence.PendingDeliveryRepository;
import com.arkflame.flameforge.compat.scheduler.SchedulerBridge;
import com.arkflame.flameforge.text.TextBridge;
import com.arkflame.flameforge.testfakes.FakeSchedulerBridge;
import com.arkflame.flameforge.testfakes.FakeTextBridge;
import com.arkflame.flameforge.testfakes.TaskHandleStub;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ItemReturnPolicyTest {
    private DeliveryService delivery;
    private PendingDeliveryRepository repository;
    private AuditLogService audit;
    private final Queue<Runnable> auditAsyncTasks = new ArrayDeque<>();

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger(ItemReturnPolicyTest.class.getName()));
        FakeSchedulerBridge scheduler = new FakeSchedulerBridge();
        repository = new PendingDeliveryRepository(plugin, scheduler, tempDir);
        TextBridge text = new FakeTextBridge();
        SchedulerBridge auditScheduler = mock(SchedulerBridge.class);
        when(auditScheduler.runAsync(any(JavaPlugin.class), any(Runnable.class))).thenAnswer(invocation -> {
            auditAsyncTasks.add(invocation.getArgument(1));
            return TaskHandleStub.INSTANCE;
        });
        audit = new AuditLogService(plugin, auditScheduler, tempDir, 100);
        delivery = new DeliveryService(plugin, scheduler, repository, text, audit, new HashMap<>());
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        CompletableFuture<Void> close = audit.closeAsync();
        Runnable writerTask = auditAsyncTasks.poll();
        if (writerTask == null) {
            throw new AssertionError("Audit writer task was not scheduled");
        }
        Thread writer = new Thread(writerTask, "item-return-policy-audit-writer");
        writer.start();
        try {
            close.join();
        } finally {
            writer.join();
        }
    }

    @Test
    void returnPolicyPreservesCustodyAcrossOnlineOfflineAndFailurePaths() {
        UUID onlineId = UUID.randomUUID();
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.addItem(any(ItemStack.class))).thenReturn(new HashMap<>());
        Player online = player(onlineId, true, inventory);
        ItemStack onlineItem = new ItemStack(Material.DIAMOND, 1);
        assertTrue(delivery.deliverItem(onlineItem, online, null, "online-return"));
        assertTrue(delivery.hasBeenProcessed("online-return"));
        verify(inventory).addItem(onlineItem);

        World world = mock(World.class);
        Location location = new Location(world, 1, 2, 3);
        Player offline = player(UUID.randomUUID(), false, inventory);
        ItemStack offlineItem = new ItemStack(Material.EMERALD, 1);
        assertTrue(delivery.deliverItem(offlineItem, offline, location, "offline-return"));
        verify(world).dropItemNaturally(location, offlineItem);

        assertFalse(delivery.deliverItem(new ItemStack(Material.AIR), offline, location, "failed-return"));
        assertFalse(delivery.hasBeenProcessed("failed-return"));
        assertTrue(delivery.queuePendingDelivery("failed-return", offline.getUniqueId(), offlineItem, Collections.emptyList()));
        assertTrue(delivery.isDeliveryPending("failed-return"));
        assertFalse(delivery.queuePendingDelivery("failed-return", offline.getUniqueId(), offlineItem, null));
    }

    private static Player player(UUID id, boolean online, PlayerInventory inventory) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(online);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getName()).thenReturn("forge-player");
        return player;
    }
}
