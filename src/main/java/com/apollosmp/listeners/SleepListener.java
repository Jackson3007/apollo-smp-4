package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;

/** Tells the world how many more people need to sleep to skip the night. */
public class SleepListener implements Listener {

    private final ApolloSMP plugin;

    public SleepListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        if (!plugin.getConfig().getBoolean("sleep.announce", true)) return;

        Player sleeper = event.getPlayer();
        // Run a tick later so the player actually counts as sleeping.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            World world = sleeper.getWorld();
            if (world.getEnvironment() != World.Environment.NORMAL) return;

            int counted = 0;
            int sleeping = 0;
            for (Player p : world.getPlayers()) {
                if (p.getGameMode() == GameMode.SPECTATOR) continue;
                counted++;
                if (p.isSleeping()) sleeping++;
            }
            int percentage = plugin.getConfig().getInt("sleep.percentage", 25);
            int needed = Math.max(1, (int) Math.ceil(counted * percentage / 100.0));
            if (sleeping >= needed) return; // night is already skipping

            for (Player p : world.getPlayers()) {
                plugin.msg().send(p, "<gray>" + sleeper.getName() + " is sleeping. <white>"
                        + sleeping + "/" + needed + "</white> needed to skip the night.");
            }
        }, 1L);
    }
}
