package com.apollosmp.util;

import com.apollosmp.ApolloSMP;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

/** Handles warmup teleports that cancel if the player moves. */
public class Teleports {

    private final ApolloSMP plugin;

    public Teleports(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    /**
     * Teleport after the configured warmup, charging {@code cost}. Cancels if the
     * player moves to a different block. Returns false if they can't afford it.
     */
    public boolean warmupTeleport(Player player, Location destination, double cost, String successMini) {
        if (destination == null) {
            plugin.msg().send(player, "<red>That destination is in an unloaded world.");
            return false;
        }
        if (cost > 0 && !plugin.economy().has(player.getUniqueId(), cost)) {
            plugin.msg().send(player, "<red>You need " + plugin.msg().money(cost) + " to teleport there.");
            return false;
        }

        int warmupSeconds = plugin.getConfig().getInt("homes.warmup-seconds", 3);
        if (warmupSeconds <= 0) {
            finish(player, destination, cost, successMini);
            return true;
        }

        plugin.msg().send(player, "<gray>Teleporting in <#f9d423>" + warmupSeconds
                + "s</#f9d423>. Don't move!");

        final Location start = player.getLocation().clone();
        final int totalTicks = warmupSeconds * 20;

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                Location now = player.getLocation();
                if (now.getBlockX() != start.getBlockX()
                        || now.getBlockY() != start.getBlockY()
                        || now.getBlockZ() != start.getBlockZ()) {
                    plugin.msg().send(player, "<red>Teleport cancelled - you moved.");
                    cancel();
                    return;
                }
                if (elapsed >= totalTicks) {
                    finish(player, destination, cost, successMini);
                    cancel();
                    return;
                }
                elapsed++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private void finish(Player player, Location destination, double cost, String successMini) {
        if (cost > 0 && !plugin.economy().withdraw(player.getUniqueId(), cost)) {
            plugin.msg().send(player, "<red>You can no longer afford that teleport.");
            return;
        }
        player.teleport(destination);
        if (successMini != null && !successMini.isEmpty()) {
            plugin.msg().send(player, successMini);
        }
    }
}
