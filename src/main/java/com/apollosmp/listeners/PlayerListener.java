package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    private final ApolloSMP plugin;

    public PlayerListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean firstJoin = !player.hasPlayedBefore();

        plugin.economy().ensureAccount(player.getUniqueId(), player.getName());
        plugin.board().create(player);

        boolean wildEveryJoin = plugin.getConfig().getBoolean("rtp.wild-on-join", false);
        boolean wildFirstJoin = firstJoin
                && plugin.getConfig().getBoolean("rtp.random-spawn-on-first-join", true);

        if (wildEveryJoin || wildFirstJoin) {
            // Delay so the world is fully ready before we search for a spot.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    boolean ok = plugin.rtp().randomSpawn(player);
                    if (ok && firstJoin) {
                        plugin.msg().send(player, "<green>Welcome to <#f9d423>Apollo SMP</#f9d423>! "
                                + "You've spawned in the wild.");
                    }
                }
            }, 20L);
        }

        int mail = plugin.mailbox().size(player.getUniqueId());
        if (mail > 0) {
            plugin.msg().send(player, "<gray>You have <white>" + mail
                    + "</white> item(s) waiting. Collect them with <white>/menu</white>.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.board().remove(event.getPlayer());
    }
}
