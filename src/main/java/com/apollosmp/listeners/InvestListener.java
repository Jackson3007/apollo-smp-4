package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.menus.BusinessMenu;
import com.apollosmp.invest.Business;
import com.apollosmp.invest.BusinessBlock;
import com.apollosmp.invest.Businesses;
import com.apollosmp.util.Items;
import org.bukkit.Material;
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
        int level = plugin.businesses().readBusinessLevel(event.getItemInHand());
        Player player = event.getPlayer();
        plugin.businesses().register(event.getBlockPlaced().getLocation(), id, level,
                player.getUniqueId(), player.getName());
        long carried = plugin.businesses().readProduced(event.getItemInHand());
        if (carried > 0) {
            com.apollosmp.invest.BusinessBlock placed =
                    plugin.businesses().getAt(event.getBlockPlaced().getLocation());
            if (placed != null) placed.setProducedSinceUpgrade(carried);
        }
        plugin.msg().send(player, "<green>You set up <reset>" + def.displayName()
                + " <gray>[L" + level + "]</gray> <green>here! Right-click it to manage.");
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        BusinessBlock block = plugin.businesses().getAt(event.getBlock().getLocation());
        if (block == null) return;
        Player player = event.getPlayer();

        // Businesses are stealable: whoever breaks it takes the block (at its level) + stored goods.
        plugin.businesses().updateProduction(block);
        Business def = Businesses.get(block.businessId());
        event.setDropItems(false);

        if (def != null) {
            Items.give(player, plugin.businesses().createItem(def, block.level(),
                    block.producedSinceUpgrade()));
        }
        for (Map.Entry<Material, Integer> e : block.storage().entrySet()) {
            int amount = e.getValue();
            while (amount > 0) {
                int chunk = Math.min(e.getKey().getMaxStackSize(), amount);
                Items.give(player, new ItemStack(e.getKey(), chunk));
                amount -= chunk;
            }
        }
        plugin.businesses().remove(block);

        boolean stole = !block.owner().equals(player.getUniqueId());
        if (stole) {
            plugin.msg().send(player, "<gold>You seized <white>" + block.ownerName()
                    + "</white>'s business! It's yours to place now.");
            Player victim = plugin.getServer().getPlayer(block.owner());
            if (victim != null) {
                plugin.msg().send(victim, "<red><white>" + player.getName()
                        + "</white> stole your " + (def != null ? def.displayName() : "business") + "<red>!");
            }
        } else {
            plugin.msg().send(player, "<yellow>Business collected. Place it again to relocate.");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return;
        BusinessBlock block = plugin.businesses().getAt(clicked.getLocation());
        if (block == null) return;

        // If they're holding a vote key, let the key handler open the crate instead.
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if ("votekey".equals(plugin.customItems().readId(hand))) return;

        // Anyone can open a business panel (they're not locked to one person).
        event.setCancelled(true);
        Player player = event.getPlayer();
        plugin.businesses().updateProduction(block);
        if (!player.getUniqueId().equals(block.owner())
                && plugin.warListener().tryRaid(player, block)) {
            return; // raided instead of opened
        }
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
