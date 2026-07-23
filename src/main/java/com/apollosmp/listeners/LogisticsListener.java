package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.menus.LogisticsMenu;
import com.apollosmp.logistics.LogisticsManager;
import com.apollosmp.util.Items;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Placing, opening and breaking the logistics blocks. */
public class LogisticsListener implements Listener {

    private final ApolloSMP plugin;

    public LogisticsListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String type = plugin.logistics().readType(event.getItemInHand());
        if (type == null) return;
        Player player = event.getPlayer();
        plugin.logistics().place(event.getBlockPlaced().getLocation(), type, player);
        plugin.msg().send(player, type.equals("distribution")
                ? "<green>Distribution Block placed. <gray>Right-click it to see what it reaches."
                : "<green>Wholesale Block placed. <gray>It'll start selling on the next cycle.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        LogisticsManager.Node node = plugin.logistics().anyAt(event.getBlock().getLocation());
        if (node == null) return;

        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(node.owner) && !player.hasPermission("apollo.admin")) {
            event.setCancelled(true);
            plugin.msg().send(player, "<red>That belongs to <white>" + node.ownerName + "</white>.");
            return;
        }
        boolean distributor = plugin.logistics().isDistributor(event.getBlock().getLocation());
        event.setDropItems(false);
        plugin.logistics().remove(event.getBlock().getLocation());
        Items.give(player, distributor
                ? plugin.logistics().createDistribution()
                : plugin.logistics().createWholesale());
        plugin.msg().send(player, "<yellow>Picked it back up.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;

        LogisticsManager.Node node = plugin.logistics().anyAt(event.getClickedBlock().getLocation());
        if (node == null) return;

        event.setCancelled(true);
        boolean distributor = plugin.logistics().isDistributor(event.getClickedBlock().getLocation());
        new LogisticsMenu(plugin, event.getPlayer(), node, !distributor).open();
    }
}
