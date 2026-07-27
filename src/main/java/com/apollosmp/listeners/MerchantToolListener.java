package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Behaviour for the merchant's two limited-time tools. */
public class MerchantToolListener implements Listener {

    private static final int MAX_LOGS = 180;

    private final ApolloSMP plugin;
    private final Set<java.util.UUID> working = new HashSet<>();

    public MerchantToolListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (working.contains(player.getUniqueId())) return;

        ItemStack tool = player.getInventory().getItemInMainHand();
        String type = plugin.merchant().toolType(tool);
        if (type == null) return;

        // Expired tools do nothing special; the sweeper will clear them shortly.
        long expiry = plugin.merchant().expiryOf(tool);
        if (expiry > 0 && System.currentTimeMillis() > expiry) return;

        working.add(player.getUniqueId());
        try {
            if (type.equals("drill")) drill(player, event.getBlock(), tool);
            else if (type.equals("feller")) fell(player, event.getBlock(), tool);
        } finally {
            working.remove(player.getUniqueId());
        }
    }

    /** Break a 3x3 face around the target block. */
    private void drill(Player player, Block center, ItemStack tool) {
        boolean zAxis = switch (player.getFacing()) {
            case NORTH, SOUTH -> true;
            default -> false;
        };
        for (int a = -1; a <= 1; a++) {
            for (int b = -1; b <= 1; b++) {
                if (a == 0 && b == 0) continue;
                Block target = zAxis
                        ? center.getRelative(a, b, 0)
                        : center.getRelative(0, b, a);
                if (!canBreak(player, target)) continue;
                target.breakNaturally(tool);
            }
        }
    }

    /** Flood-fill connected logs so the whole tree comes down. */
    private void fell(Player player, Block start, ItemStack tool) {
        if (!start.getType().name().endsWith("_LOG") && !start.getType().name().endsWith("_STEM")) return;
        Material wood = start.getType();

        List<Block> found = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(key(start));

        while (!queue.isEmpty() && found.size() < MAX_LOGS) {
            Block current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block next = current.getRelative(dx, dy, dz);
                        if (next.getType() != wood) continue;
                        if (Math.abs(next.getX() - start.getX()) > 8) continue;
                        if (Math.abs(next.getZ() - start.getZ()) > 8) continue;
                        if (next.getY() < start.getY()) continue;
                        if (!seen.add(key(next))) continue;
                        if (!canBreak(player, next)) continue;
                        found.add(next);
                        queue.add(next);
                    }
                }
            }
        }
        for (Block log : found) log.breakNaturally(tool);
    }

    private String key(Block b) {
        return b.getX() + "," + b.getY() + "," + b.getZ();
    }

    private boolean canBreak(Player player, Block block) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid()) return false;
        if (type == Material.BEDROCK || type == Material.SPAWNER
                || type == Material.OBSIDIAN || type == Material.END_PORTAL_FRAME) return false;
        if (plugin.businesses().isBusiness(block.getLocation())) return false;
        return plugin.towns().canBuild(player, block.getLocation());
    }

    /** Unused helper kept for readability of the drill maths. */
    private BlockFace facing(Player player) {
        return player.getFacing();
    }
}
