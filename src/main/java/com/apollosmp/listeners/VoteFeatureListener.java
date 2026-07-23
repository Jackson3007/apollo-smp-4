package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.menus.CrateMenu;
import com.apollosmp.items.CustomItems;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class VoteFeatureListener implements Listener {

    private final ApolloSMP plugin;

    public VoteFeatureListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    // ---- Sky Drill: break a 3x3 plane ----

    @EventHandler(ignoreCancelled = true)
    public void onDrill(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!CustomItems.DRILL.equals(plugin.customItems().readId(tool))) return;

        Block center = event.getBlock();
        BlockFace facing = player.getFacing();
        boolean zAxis = facing == BlockFace.NORTH || facing == BlockFace.SOUTH;

        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue; // center handled by the event itself
                Block target = zAxis
                        ? center.getRelative(a, b, 0)   // facing N/S -> break across X and Y
                        : center.getRelative(0, b, a);  // facing E/W -> break across Z and Y
                if (!canDrill(player, target)) continue;
                target.breakNaturally(tool);
            }
        }
    }

    private boolean canDrill(Player player, Block block) {
        Material type = block.getType();
        if (type.isAir()) return false;
        if (block.isLiquid()) return false;
        if (type == Material.BEDROCK || type == Material.SPAWNER
                || type == Material.OBSIDIAN || type == Material.END_PORTAL_FRAME) return false;
        if (plugin.businesses().isBusiness(block.getLocation())) return false;
        return plugin.towns().canBuild(player, block.getLocation());
    }

    // ---- Vote Key: right-click to open the crate ----

    @EventHandler
    public void onKeyUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!CustomItems.VOTE_KEY.equals(plugin.customItems().readId(item))) return;

        event.setCancelled(true);
        int newAmount = item.getAmount() - 1; // consume one key
        if (newAmount <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(newAmount);
            player.getInventory().setItemInMainHand(item);
        }
        new CrateMenu(plugin, player).open();
    }
}
