package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Stops non-members from breaking, placing, or opening things on town land. */
public class TownProtectionListener implements Listener {

    private final ApolloSMP plugin;
    private final Map<UUID, Long> lastWarn = new ConcurrentHashMap<>();

    public TownProtectionListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!plugin.towns().canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), event.getBlock().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!plugin.towns().canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), event.getBlock().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (!isProtectedInteract(event.getClickedBlock().getType())) return;
        if (!plugin.towns().canBuild(event.getPlayer(), event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), event.getClickedBlock().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!plugin.towns().canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), event.getBlock().getLocation());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!plugin.towns().canBuild(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
            warn(event.getPlayer(), event.getBlock().getLocation());
        }
    }

    private boolean isProtectedInteract(Material m) {
        String n = m.name();
        return n.endsWith("_DOOR") || n.endsWith("_TRAPDOOR") || n.endsWith("_FENCE_GATE")
                || n.endsWith("_BUTTON") || n.endsWith("SHULKER_BOX")
                || n.equals("CHEST") || n.equals("TRAPPED_CHEST") || n.equals("BARREL")
                || n.equals("FURNACE") || n.equals("BLAST_FURNACE") || n.equals("SMOKER")
                || n.equals("HOPPER") || n.equals("DISPENSER") || n.equals("DROPPER")
                || n.equals("LEVER") || n.equals("BREWING_STAND") || n.equals("BEACON")
                || n.equals("GRINDSTONE") || n.contains("ANVIL") || n.equals("DECORATED_POT");
    }

    private void warn(Player player, Location loc) {
        long now = System.currentTimeMillis();
        Long last = lastWarn.get(player.getUniqueId());
        if (last != null && now - last < 2500L) return;
        lastWarn.put(player.getUniqueId(), now);
        var town = plugin.towns().getTownAtLoc(loc);
        plugin.msg().send(player, "<red>This land belongs to <white>"
                + (town != null ? town.name() : "a town") + "</white>. You can't build here.");
    }
}
