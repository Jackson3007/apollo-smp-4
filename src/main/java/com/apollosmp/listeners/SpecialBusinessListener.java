package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.menus.SpecialBusinessMenu;
import com.apollosmp.special.SpecialBusiness;
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
import org.bukkit.inventory.ItemStack;

/** Placing, opening and reclaiming special business blocks. */
public class SpecialBusinessListener implements Listener {

    private final ApolloSMP plugin;

    public SpecialBusinessListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String id = plugin.specialBusinesses().readId(event.getItemInHand());
        if (id == null) return;
        SpecialBusiness b = plugin.specialBusinesses().carried(id);
        if (b == null) {
            plugin.msg().send(event.getPlayer(), "<red>That business record is missing.");
            event.setCancelled(true);
            return;
        }
        plugin.specialBusinesses().place(event.getBlockPlaced().getLocation(), id, event.getPlayer());
        plugin.msg().send(event.getPlayer(), "<green><white>" + b.name()
                + "</white> is open for business. Right-click it to collect.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        SpecialBusiness b = plugin.specialBusinesses().at(event.getBlock().getLocation());
        if (b == null) return;

        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(b.owner()) && !player.hasPermission("apollo.admin")) {
            event.setCancelled(true);
            plugin.msg().send(player, "<red>Only <white>" + b.ownerName()
                    + "</white> can pick up this business.");
            return;
        }
        event.setDropItems(false);
        ItemStack item = plugin.specialBusinesses().pickUp(b);
        Items.give(player, item);
        plugin.msg().send(player, "<yellow>You packed up <white>" + b.name()
                + "</white>. Stored goods came with it.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;

        SpecialBusiness b = plugin.specialBusinesses().at(event.getClickedBlock().getLocation());
        if (b == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(b.owner())) {
            plugin.msg().send(player, "<gray>This is <white>" + b.ownerName()
                    + "</white>'s <white>" + b.name() + "</white>.");
            return;
        }
        new SpecialBusinessMenu(plugin, player, b).open();
    }
}
