package com.apollosmp.gui;

import com.apollosmp.ApolloSMP;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class GuiListener implements Listener {

    private final ApolloSMP plugin;

    public GuiListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof Gui gui)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof Gui;

        if (gui.allowsDrop()) {
            if (clickedTop) {
                if (gui.isDropSlot(event.getSlot())) {
                    // Allow placing / taking items in the drop zone.
                    scheduleDropRefresh(gui, player);
                    return;
                }
                // A control button (e.g. the Sell button): cancel and route.
                event.setCancelled(true);
                gui.onClick(player, event.getSlot(), event.getCurrentItem(), event.getClick());
                return;
            }
            // Click in the player's own inventory: allow (including shift-click into the drop zone).
            scheduleDropRefresh(gui, player);
            return;
        }

        // Standard menu: nothing is movable.
        event.setCancelled(true);
        if (clickedTop) {
            gui.onClick(player, event.getSlot(), event.getCurrentItem(), event.getClick());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof Gui gui)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (gui.allowsDrop()) {
            int topSize = event.getView().getTopInventory().getSize();
            for (int raw : event.getRawSlots()) {
                // If the drag touches a top-inventory slot that isn't a drop slot, block it.
                if (raw < topSize && !gui.isDropSlot(raw)) {
                    event.setCancelled(true);
                    return;
                }
            }
            scheduleDropRefresh(gui, player);
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof Gui gui && event.getPlayer() instanceof Player player) {
            gui.onClose(player);
        }
    }

    private void scheduleDropRefresh(Gui gui, Player player) {
        // Run next tick so the inventory reflects the completed click/drag.
        plugin.getServer().getScheduler().runTask(plugin, () -> gui.onDropChanged(player));
    }
}
