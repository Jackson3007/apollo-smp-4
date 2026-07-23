package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import com.apollosmp.town.Town;
import com.apollosmp.util.Msg;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shows a big "Welcome to <Town>" title when a player crosses into town land. */
public class TownBorderListener implements Listener {

    private static final Title.Times TIMES = Title.Times.times(
            Duration.ofMillis(250), Duration.ofMillis(1600), Duration.ofMillis(600));

    private final ApolloSMP plugin;
    /** Last town name seen per player; "" means wilderness. */
    private final Map<UUID, String> lastTown = new ConcurrentHashMap<>();

    public TownBorderListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        // Only do real work when the player changes chunk.
        if (from.getWorld().equals(to.getWorld())
                && (from.getBlockX() >> 4) == (to.getBlockX() >> 4)
                && (from.getBlockZ() >> 4) == (to.getBlockZ() >> 4)) {
            return;
        }

        Player player = event.getPlayer();
        Town town = plugin.towns().getTownAtLoc(to);
        String now = town == null ? "" : town.name();
        String previous = lastTown.get(player.getUniqueId());
        if (previous != null && previous.equals(now)) return;
        lastTown.put(player.getUniqueId(), now);

        // Don't announce "Wilderness" the very first time we see someone.
        if (previous == null && now.isEmpty()) return;

        if (town != null) {
            String mayor = plugin.getServer().getOfflinePlayer(town.mayor()).getName();
            if (mayor == null) mayor = "Unknown";
            player.showTitle(Title.title(
                    Msg.mm("<gradient:#f9d423:#ff4e50><bold>Welcome to " + town.name() + "</bold></gradient>"),
                    Msg.mm("<gray>Mayor: <white>" + mayor + "</white>  <dark_gray>|</dark_gray>  <white>"
                            + town.memberCount() + "</white> resident" + (town.memberCount() == 1 ? "" : "s")),
                    TIMES));
        } else {
            player.showTitle(Title.title(
                    Msg.mm("<gray><bold>Wilderness</bold>"),
                    Msg.mm("<dark_gray>Unclaimed land"),
                    TIMES));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastTown.remove(event.getPlayer().getUniqueId());
    }
}
