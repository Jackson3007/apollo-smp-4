package com.apollosmp.board;

import com.apollosmp.ApolloSMP;
import com.apollosmp.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws the branded Apollo sidebar. Uses one scoreboard per player with a team
 * per line, so values update in place without the classic flicker.
 */
public class BoardManager {

    private final ApolloSMP plugin;
    private final Map<UUID, Scoreboard> boards = new ConcurrentHashMap<>();

    // Stable, invisible per-line entry keys built from colour codes.
    private static final String[] ENTRY_KEYS = buildEntryKeys();

    public BoardManager(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    private static String[] buildEntryKeys() {
        ChatColor[] colors = {
                ChatColor.BLACK, ChatColor.DARK_BLUE, ChatColor.DARK_GREEN, ChatColor.DARK_AQUA,
                ChatColor.DARK_RED, ChatColor.DARK_PURPLE, ChatColor.GOLD, ChatColor.GRAY,
                ChatColor.DARK_GRAY, ChatColor.BLUE, ChatColor.GREEN, ChatColor.AQUA,
                ChatColor.RED, ChatColor.LIGHT_PURPLE, ChatColor.YELLOW, ChatColor.WHITE
        };
        String[] keys = new String[colors.length];
        for (int i = 0; i < colors.length; i++) {
            keys[i] = colors[i].toString() + ChatColor.RESET;
        }
        return keys;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("scoreboard.enabled", true);
    }

    /** Build and assign a fresh sidebar for the player. */
    public void create(Player player) {
        if (!enabled()) return;
        if (Bukkit.getScoreboardManager() == null) return;

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("apollo", Criteria.DUMMY,
                Msg.mm(plugin.getConfig().getString("scoreboard.title", "APOLLO SMP")));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        // Hide the red score numbers on the right for a clean sidebar.
        obj.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());

        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        int total = Math.min(lines.size(), ENTRY_KEYS.length);
        for (int i = 0; i < total; i++) {
            String entry = ENTRY_KEYS[i];
            Team team = board.registerNewTeam("line_" + i);
            team.addEntry(entry);
            obj.getScore(entry).setScore(total - i);
        }

        boards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
        update(player);
    }

    public void remove(Player player) {
        boards.remove(player.getUniqueId());
    }

    /** Refresh line contents for one player. */
    public void update(Player player) {
        if (!enabled()) return;
        Scoreboard board = boards.get(player.getUniqueId());
        if (board == null) return;

        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        int total = Math.min(lines.size(), ENTRY_KEYS.length);
        for (int i = 0; i < total; i++) {
            Team team = board.getTeam("line_" + i);
            if (team == null) continue;
            Component content = Msg.mm(resolve(player, lines.get(i)));
            team.prefix(content);
        }
    }

    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    }

    private String resolve(Player player, String line) {
        Location loc = player.getLocation();
        UUID id = player.getUniqueId();
        double bal = plugin.economy().getBalance(id);
        return line
                .replace("%player%", player.getName())
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%world%", player.getWorld().getName())
                .replace("%balance_raw%", plugin.msg().moneyRaw(bal))
                .replace("%balance%", plugin.msg().money(bal))
                .replace("%homes%", String.valueOf(plugin.homes().count(id)))
                .replace("%listings%", String.valueOf(plugin.auctions().countBySeller(id)))
                .replace("%businesses%", String.valueOf(plugin.businesses().countOwnedBy(id)))
                .replace("%x%", String.valueOf(loc.getBlockX()))
                .replace("%y%", String.valueOf(loc.getBlockY()))
                .replace("%z%", String.valueOf(loc.getBlockZ()));
    }
}
