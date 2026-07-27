package com.apollosmp.staff;

import com.apollosmp.ApolloSMP;
import com.apollosmp.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom nicknames (an Apollo+ perk) plus the rank badge shown in the tab list.
 *
 * Nicknames colour a player's name in chat and the tab list. The rank badge
 * ([Apollo+], [Owner], ...) is added in front of the tab-list name so ranks are
 * visible when you hold Tab.
 */
public class NickManager {

    private final ApolloSMP plugin;
    private final File file;
    private final Map<UUID, String> nicks = new ConcurrentHashMap<>();

    public NickManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "nicks.yml");
        load();
    }

    public String nickOf(UUID id) {
        return id == null ? null : nicks.get(id);
    }

    public boolean hasNick(UUID id) {
        return nicks.containsKey(id);
    }

    /** Set (or with null, clear) a player's nickname, then refresh their display. */
    public void set(Player player, String nick) {
        if (nick == null || nick.isBlank()) {
            nicks.remove(player.getUniqueId());
        } else {
            nicks.put(player.getUniqueId(), nick);
        }
        save();
        apply(player);
    }

    /** The MiniMessage badge shown before a player's tab name, e.g. "[Apollo+] ". */
    private String tabPrefix(Player player) {
        StaffRank rank = plugin.ranks().of(player);
        if (rank == null) return "";
        return "<gray>[</gray><" + rank.colour() + ">" + rank.display()
                + "</" + rank.colour() + "><gray>] </gray>";
    }

    /** Push a player's current nick + badge to their chat name and tab entry. */
    public void apply(Player player) {
        String nick = nickOf(player.getUniqueId());
        String shown = (nick == null || nick.isBlank()) ? player.getName() : nick;

        // Chat name (used by the chat renderer as the source name).
        Component display = Msg.mm(shown);
        try {
            player.displayName(display);
        } catch (Throwable ignored) {
            // very old builds - ignore
        }

        // Tab-list name: rank badge in front, nick/colour applied.
        String prefix = tabPrefix(player);
        if (prefix.isEmpty() && (nick == null || nick.isBlank())) {
            // Nothing special to show - fall back to the vanilla name.
            player.playerListName(null);
        } else {
            player.playerListName(Msg.mm(prefix + shown));
        }
    }

    public void applyAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) apply(p);
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, String> e : nicks.entrySet()) {
            cfg.set("nicks." + e.getKey(), e.getValue());
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save nicks.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("nicks");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                nicks.put(UUID.fromString(key), cfg.getString("nicks." + key));
            } catch (IllegalArgumentException ignored) {
                // skip malformed entry
            }
        }
    }
}
