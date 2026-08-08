package com.arkflame.flameforge.menu;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ForgeMenuViewResolver {
    private final ForgeMenuRegistry registry;

    public ForgeMenuViewResolver(ForgeMenuRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    public ResolvedView resolve(Player player, InventoryView view) {
        if (view == null) {
            return ResolvedView.notForge(null);
        }
        Inventory topInventory = view.getTopInventory();
        if (!(topInventory.getHolder() instanceof ForgeInventoryHolder)) {
            return ResolvedView.notForge(view.getBottomInventory());
        }
        ForgeInventoryHolder holder = (ForgeInventoryHolder) topInventory.getHolder();
        UUID playerId = player.getUniqueId();

        if (!holder.getPlayerId().equals(playerId)) {
            return ResolvedView.stale(holder, topInventory, view.getBottomInventory());
        }

        Optional<ForgeMenuContext> contextOpt = registry.getCurrent(playerId, holder.getMenuId());
        if (!contextOpt.isPresent()) {
            return ResolvedView.stale(holder, topInventory, view.getBottomInventory());
        }

        ForgeMenuContext context = contextOpt.get();
        if (!context.getStationId().equals(holder.getStationId())) {
            return ResolvedView.stale(holder, topInventory, view.getBottomInventory());
        }

        return ResolvedView.current(holder, topInventory, view.getBottomInventory(), context);
    }

    public boolean isStillCurrent(Player player, ForgeInventoryHolder expectedHolder) {
        if (!player.isOnline()) {
            return false;
        }
        InventoryView currentView = player.getOpenInventory();
        if (currentView == null) {
            return false;
        }
        Inventory topInventory = currentView.getTopInventory();
        if (!(topInventory.getHolder() instanceof ForgeInventoryHolder)) {
            return false;
        }
        ForgeInventoryHolder actualHolder = (ForgeInventoryHolder) topInventory.getHolder();
        if (!actualHolder.getPlayerId().equals(expectedHolder.getPlayerId())) {
            return false;
        }
        if (!actualHolder.getMenuId().equals(expectedHolder.getMenuId())) {
            return false;
        }
        if (!actualHolder.getStationId().equals(expectedHolder.getStationId())) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        return registry.getCurrent(playerId, expectedHolder.getMenuId()).isPresent();
    }

    public enum Status {
        NOT_FORGE,
        CURRENT,
        STALE
    }

    public static final class ResolvedView {
        private final Status status;
        private final Inventory topInventory;
        private final Inventory bottomInventory;
        private final ForgeInventoryHolder holder;
        private final ForgeMenuContext context;

        private ResolvedView(Status status, Inventory topInventory, Inventory bottomInventory,
                             ForgeInventoryHolder holder, ForgeMenuContext context) {
            this.status = status;
            this.topInventory = topInventory;
            this.bottomInventory = bottomInventory;
            this.holder = holder;
            this.context = context;
        }

        static ResolvedView notForge(Inventory bottomInventory) {
            return new ResolvedView(Status.NOT_FORGE, null, bottomInventory, null, null);
        }

        static ResolvedView stale(ForgeInventoryHolder holder, Inventory topInventory, Inventory bottomInventory) {
            return new ResolvedView(Status.STALE, topInventory, bottomInventory, holder, null);
        }

        static ResolvedView current(ForgeInventoryHolder holder, Inventory topInventory,
                                    Inventory bottomInventory, ForgeMenuContext context) {
            return new ResolvedView(Status.CURRENT, topInventory, bottomInventory, holder, context);
        }

        public Status getStatus() {
            return status;
        }

        public Inventory getTopInventory() {
            return topInventory;
        }

        public Inventory getBottomInventory() {
            return bottomInventory;
        }

        public ForgeInventoryHolder getHolder() {
            return holder;
        }

        public ForgeMenuContext getContext() {
            return context;
        }
    }
}
