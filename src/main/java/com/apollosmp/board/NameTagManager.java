package com.apollosmp.board;

import com.apollosmp.ApolloSMP;
import com.apollosmp.town.Town;
import com.apollosmp.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows a [Town] tag above players' heads and in the tab list.
 *
 * Nameplates come from scoreboard teams, and every player has their own
 * sidebar scoreboard, so the teams have to be mirrored onto each viewer's board.
 */
public class NameTagManager {

    private final ApolloSMP plugin;
    /** player name -> town name ("" when they aren't in a town). */
    private Map<String, String> lastDesired = new HashMap<>();
    /** Viewers already showing the current set of tags. */
    private final Set<UUID> synced = ConcurrentHashMap.newKeySet();

    public NameTagManager(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("towns.name-tag", true);
    }

    public void updateAll() {
        if (!enabled()) return;

        Map<String, String> desired = new HashMap<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Town town = plugin.towns().getTownOf(player.getUniqueId());
            desired.put(player.getName(), town == null ? "" : town.name());
        }

        // Only push packets when something actually changed.
        if (!desired.equals(lastDesired)) {
            lastDesired = desired;
            synced.clear();
        }

        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (!synced.add(viewer.getUniqueId())) continue;
            apply(viewer, lastDesired);
        }
    }

    private void apply(Player viewer, Map<String, String> desired) {
        Scoreboard board = viewer.getScoreboard();
        if (board == null) return;

        for (Map.Entry<String, String> entry : desired.entrySet()) {
            String name = entry.getKey();
            if (name.length() > 16) continue; // team names are capped at 16 characters
            Team team = board.getTeam(name);
            if (team == null) {
                try {
                    team = board.registerNewTeam(name);
                } catch (Exception ex) {
                    continue;
                }
            }
            if (!team.hasEntry(name)) team.addEntry(name);

            String tag = entry.getValue();
            team.prefix(tag.isEmpty()
                    ? Component.empty()
                    : Msg.mm("<gray>[</gray><#f9d423>" + tag + "</#f9d423><gray>] </gray>"));
        }
    }

    /** Force every viewer to be re-synced (used on join and on reload). */
    public void invalidate() {
        synced.clear();
    }

    public void remove(Player player) {
        synced.remove(player.getUniqueId());
        lastDesired.remove(player.getName());
    }
}
