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
        showPlotOwner(player, to);
        Town town = plugin.towns().getTownAtLoc(to);
        String now = town == null ? "" : town.name();
        String previous = lastTown.get(player.getUniqueId());
        if (previous != null && previous.equals(now)) return;
        lastTown.put(player.getUniqueId(), now);

        // Don't announce "Wilderness" the very first time we see someone.
        if (previous == null && now.isEmpty()) return;

        if (town != null) {
            if (plugin.getConfig().getBoolean("towns.border-flash-on-enter", true)) {
                plugin.borders().flash(player, 4000L);
            }
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

    /** Action-bar note about who owns the chunk you just walked onto. */
    private void showPlotOwner(Player player, Location to) {
        Town town = plugin.towns().getTownAtLoc(to);
        if (town == null) return;
        String key = com.apollosmp.town.TownManager.chunkKey(to);
        java.util.UUID owner = town.plotOwner(key);

        // An unclaimed plot that's on the market gets a proper announcement.
        if (owner == null && town.isListed(key)) {
            announceListing(player, town, key);
            return;
        }

        if (owner == null) {
            player.sendActionBar(Msg.mm("<gray>" + town.name() + " <dark_gray>|</dark_gray> town land"));
            return;
        }
        String name = plugin.getServer().getOfflinePlayer(owner).getName();
        if (name == null) name = "Unknown";
        if (owner.equals(player.getUniqueId())) {
            player.sendActionBar(Msg.mm("<gray>" + town.name()
                    + " <dark_gray>|</dark_gray> <green>your plot</green>"));
        } else {
            player.sendActionBar(Msg.mm("<gray>" + town.name()
                    + " <dark_gray>|</dark_gray> <#e94fd0>" + name + "'s plot</#e94fd0>"));
        }
    }

    /** Big on-screen prompt for a plot that's for sale or rent. */
    private void announceListing(Player player, Town town, String key) {
        Double sale = town.plotPrice(key);
        Double rent = town.rentPrice(key);
        boolean member = town.isMember(player.getUniqueId());

        String heading;
        String priceText;
        String command;
        if (sale != null) {
            heading = "<green><bold>Plot For Sale</bold></green>";
            priceText = "<#f9d423>" + plugin.msg().money(sale) + "</#f9d423>";
            command = "/town buyplot";
        } else if (rent != null) {
            heading = "<#5ad1e8><bold>Plot For Rent</bold></#5ad1e8>";
            priceText = "<#f9d423>" + plugin.msg().money(rent) + "</#f9d423> <gray>per "
                    + plugin.towns().rentPeriodLabel() + "</gray>";
            command = "/town rentplot";
        } else {
            return;
        }

        String subtitle = member
                ? priceText + " <dark_gray>|</dark_gray> <white>" + command + "</white>"
                : priceText + " <dark_gray>|</dark_gray> <gray>residents only</gray>";

        player.showTitle(Title.title(Msg.mm(heading), Msg.mm(subtitle), TIMES));

        plugin.msg().send(player, "<gray>This plot in <white>" + town.name() + "</white> is "
                + (sale != null ? "for sale at " : "for rent at ") + priceText + ".");
        if (member) {
            plugin.msg().send(player, "<click:run_command:'" + command
                    + "'><hover:show_text:'Click to run " + command + "'>"
                    + "<green><u>Click here to " + (sale != null ? "buy" : "rent")
                    + " it</u></green></hover></click> <dark_gray>or type " + command + "</dark_gray>");
        } else {
            plugin.msg().send(player, "<gray>You'd need to be a resident of <white>"
                    + town.name() + "</white> to take it.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastTown.remove(event.getPlayer().getUniqueId());
        plugin.borders().forget(event.getPlayer());
    }
}
