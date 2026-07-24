package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.menus.TownShopMenu;
import com.apollosmp.shop.ShopManager;
import com.apollosmp.town.Town;
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

/** Placing, opening and breaking town banks and market stalls. */
public class TownBlockListener implements Listener {

    private final ApolloSMP plugin;

    public TownBlockListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.shops().isStallItem(event.getItemInHand())) {
            if (!plugin.shops().place(event.getBlockPlaced().getLocation(), event.getPlayer())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();


        ShopManager.Stall stall = plugin.shops().at(event.getBlock().getLocation());
        if (stall != null) {
            Town town = plugin.towns().townByName(stall.town);
            if (town != null && !town.isMember(player.getUniqueId())
                    && !player.hasPermission("apollo.admin")) {
                event.setCancelled(true);
                plugin.msg().send(player, "<red>That auction barrel belongs to <white>" + stall.town + "</white>.");
                return;
            }
            // Hand back anything left on the shelves.
            while (!stall.offers.isEmpty()) plugin.shops().unstock(player, stall, 0);
            event.setDropItems(false);
            plugin.shops().remove(event.getBlock().getLocation());
            Items.give(player, plugin.shops().createBlock());
            plugin.msg().send(player, "<yellow>Auction barrel packed up.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;


        ShopManager.Stall stall = plugin.shops().at(event.getClickedBlock().getLocation());
        if (stall != null) {
            event.setCancelled(true);
            new TownShopMenu(plugin, event.getPlayer(), stall).open();
        }
    }
}
