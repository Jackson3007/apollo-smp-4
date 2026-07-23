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
        plugin.nameTags().invalidate();
        sendWelcome(player);
        plugin.auctions().flushNotifications(player);

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

    private void sendWelcome(Player player) {
        var msg = plugin.msg();
        msg.send(player, "<#f9d423>\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501</#f9d423>");
        msg.send(player, "<gradient:#f9d423:#ff4e50><bold>  Welcome to Apollo SMP!</bold></gradient>");
        msg.send(player, "<gray>  Server IP: <#5ad1e8>" + plugin.serverIp() + "</#5ad1e8>");
        msg.send(player, "");
        msg.send(player, "<#f9d423>Handy commands:</#f9d423>");
        msg.send(player, "  <white>/menu</white> <dark_gray>-</dark_gray> <gray>the main hub</gray>");
        msg.send(player, "  <white>/sell</white> <dark_gray>-</dark_gray> <gray>sell items for money</gray>");
        msg.send(player, "  <white>/ah</white> <dark_gray>-</dark_gray> <gray>browse the auction house</gray>");
        msg.send(player, "  <white>/invest</white> <dark_gray>-</dark_gray> <gray>buy & manage businesses</gray>");
        msg.send(player, "  <white>/town</white> <dark_gray>-</dark_gray> <gray>create & manage a town</gray>");
        msg.send(player, "  <white>/vote</white> <dark_gray>-</dark_gray> <gray>support the server</gray>");
        msg.send(player, "  <white>/discord</white> <dark_gray>-</dark_gray> <gray>join the community</gray>");
        msg.send(player, "  <white>/sethome</white> <dark_gray>&</dark_gray> <white>/home</white> <dark_gray>-</dark_gray> <gray>set & travel home</gray>");
        msg.send(player, "  <white>/rtp</white> <dark_gray>-</dark_gray> <gray>teleport into the wild</gray>");
        msg.send(player, "  <white>/tpa</white> <dark_gray>-</dark_gray> <gray>teleport to a friend</gray>");
        msg.send(player, "<#f9d423>\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501</#f9d423>");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.board().remove(event.getPlayer());
        plugin.nameTags().remove(event.getPlayer());
    }
}
