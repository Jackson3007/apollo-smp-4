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

import java.util.ArrayList;
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

    /** Minecraft only renders 15 sidebar rows. */
    private static final int MAX_LINES = 15;

    /**
     * The sidebar lines to actually draw. If the config predates the town lines,
     * they get slotted in automatically so existing setups don't miss out.
     */
    private List<String> effectiveLines() {
        List<String> lines = new ArrayList<>(plugin.getConfig().getStringList("scoreboard.lines"));
        if (lines.isEmpty()) return lines;

        boolean hasOwn = false;
        boolean hasHere = false;
        for (String line : lines) {
            if (line.contains("%town_here%")) hasHere = true;
            else if (line.contains("%town%")) hasOwn = true;
        }

        if (!hasOwn && !hasHere) {
            int at = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("Stats")) { at = i + 1; break; }
            }
            if (at < 0) at = Math.max(0, lines.size() - 2);
            at = Math.min(at, lines.size());
            lines.add(at, " <gray>Standing in:</gray> <white>%town_here%</white>");
            lines.add(at, " <gray>Bank:</gray> <#f9d423>%town_bank%</#f9d423>");
            lines.add(at, " <gray>Town:</gray> <white>%town%</white> <gray>(<white>%town_rank%</white>)</gray>");
        }

        // Over the limit? Drop blank spacers first, never the header or footer.
        while (lines.size() > MAX_LINES) {
            int blank = -1;
            for (int i = lines.size() - 2; i > 0; i--) {
                if (lines.get(i).isBlank()) { blank = i; break; }
            }
            if (blank < 0) break;
            lines.remove(blank);
        }
        // Still too long: drop indented stat rows from the bottom up.
        while (lines.size() > MAX_LINES) {
            int row = -1;
            for (int i = lines.size() - 2; i > 0; i--) {
                if (lines.get(i).startsWith(" ")) { row = i; break; }
            }
            if (row < 0) break;
            lines.remove(row);
        }
        // Last resort, keeping the footer line intact.
        while (lines.size() > MAX_LINES) lines.remove(lines.size() - 2);
        return lines;
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

        List<String> lines = effectiveLines();
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

        List<String> lines = effectiveLines();
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
        String ip = plugin.serverIp();
        com.apollosmp.town.Town ownTown = plugin.towns().getTownOf(id);
        com.apollosmp.town.Town hereTown = plugin.towns().getTownAtLoc(loc);
        return line
                // Older configs had the placeholder IP hard-coded into the line.
                .replace("play.apollosmp.net", ip)
                .replace("%ip%", ip)
                .replace("%town_here%", hereTown == null ? "Wilderness" : hereTown.name())
                .replace("%town_rank%", ownTown == null || ownTown.rankOf(id) == null
                        ? "-" : ownTown.rankOf(id).display())
                .replace("%town_bank%", ownTown == null
                        ? "-" : plugin.msg().money(ownTown.bank()))
                .replace("%town%", ownTown == null ? "None" : ownTown.name())
                .replace("%player%", player.getName())
                .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%world%", player.getWorld().getName())
                .replace("%balance_raw%", plugin.msg().moneyRaw(bal))
                .replace("%balance%", plugin.msg().money(bal))
                .replace("%homes%", String.valueOf(plugin.homes().count(id)))
                .replace("%listings%", String.valueOf(plugin.auctions().countBySeller(id)))
                .replace("%businesses%", String.valueOf(plugin.businesses().countOwnedBy(id)))
                .replace("%skycoins%", String.valueOf(plugin.skyCoins().get(id)))
                .replace("%x%", String.valueOf(loc.getBlockX()))
                .replace("%y%", String.valueOf(loc.getBlockY()))
                .replace("%z%", String.valueOf(loc.getBlockZ()));
    }
}
