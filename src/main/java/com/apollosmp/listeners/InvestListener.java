package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.menus.BusinessMenu;
import com.apollosmp.invest.Business;
import com.apollosmp.invest.BusinessBlock;
import com.apollosmp.invest.Businesses;
import com.apollosmp.util.Items;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;
import java.util.Map;

public class InvestListener implements Listener {

    private final ApolloSMP plugin;

    public InvestListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        String id = plugin.businesses().readBusinessId(event.getItemInHand());
        if (id == null) return;
        Business def = Businesses.get(id);
        if (def == null) return;
        Player player = event.getPlayer();
        plugin.businesses().register(event.getBlockPlaced().getLocation(), id,
                player.getUniqueId(), player.getName());
        plugin.msg().send(player, "<green>You founded <reset>" + def.displayName()
                + " <green>here! Right-click it to manage.");
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        BusinessBlock block = plugin.businesses().getAt(event.getBlock().getLocation());
        if (block == null) return;
        Player player = event.getPlayer();

        boolean isOwner = block.owner().equals(player.getUniqueId());
        if (!isOwner && !player.hasPermission("apollo.admin")) {
            event.setCancelled(true);
            plugin.msg().send(player, "<red>This business belongs to <white>"
                    + block.ownerName() + "</white>.");
            return;
        }

        // Owner (or admin) picks it up: return the business item and any stored product.
        plugin.businesses().updateProduction(block);
        Business def = Businesses.get(block.businessId());
        event.setDropItems(false);

        if (def != null) {
            Items.give(player, plugin.businesses().createItem(def));
        }
        for (Map.Entry<org.bukkit.Material, Integer> e : block.storage().entrySet()) {
            int amount = e.getValue();
            while (amount > 0) {
                int chunk = Math.min(e.getKey().getMaxStackSize(), amount);
                Items.give(player, new ItemStack(e.getKey(), chunk));
                amount -= chunk;
            }
        }
        plugin.businesses().remove(block);
        plugin.msg().send(player, "<yellow>Business collected. Place it again to relocate.");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        BusinessBlock block = plugin.businesses().getAt(clicked.getLocation());
        if (block == null) return;

        // It's a business block: take over the interaction.
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!block.owner().equals(player.getUniqueId()) && !player.hasPermission("apollo.admin")) {
            plugin.msg().send(player, "<red>This is <white>" + block.ownerName()
                    + "</white>'s business.");
            return;
        }
        plugin.businesses().updateProduction(block);
        new BusinessMenu(plugin, player, block).open();
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        protectFrom(event.blockList().iterator());
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        protectFrom(event.blockList().iterator());
    }

    private void protectFrom(Iterator<Block> it) {
        while (it.hasNext()) {
            Block b = it.next();
            if (plugin.businesses().isBusiness(b.getLocation())) it.remove();
        }
    }
}
