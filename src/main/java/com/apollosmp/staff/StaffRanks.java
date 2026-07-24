package com.apollosmp.staff;

import com.apollosmp.ApolloSMP;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who holds which server role. Mods and owners get admin powers automatically;
 * YouTuber is purely a badge.
 */
public class StaffRanks {

    private final ApolloSMP plugin;
    private final File file;
    private final Map<UUID, StaffRank> ranks = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    public StaffRanks(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ranks.yml");
        load();
    }

    public StaffRank of(UUID id) {
        return id == null ? null : ranks.get(id);
    }

    public StaffRank of(Player player) {
        return player == null ? null : ranks.get(player.getUniqueId());
    }

    public Map<UUID, StaffRank> all() {
        return new LinkedHashMap<>(ranks);
    }

    /** Give or clear someone's role. Pass null to remove it. */
    public void set(UUID id, StaffRank rank) {
        if (rank == null) ranks.remove(id);
        else ranks.put(id, rank);
        save();

        Player online = plugin.getServer().getPlayer(id);
        if (online != null) {
            applyPermissions(online);
            plugin.nameTags().invalidate();
        }
    }

    /** Mods and owners get apollo.admin for as long as they're online. */
    public void applyPermissions(Player player) {
        PermissionAttachment existing = attachments.remove(player.getUniqueId());
        if (existing != null) {
            try {
                player.removeAttachment(existing);
            } catch (Exception ignored) {
                // attachment may already be gone
            }
        }

        StaffRank rank = of(player);
        if (rank == null || !rank.isStaff()) return;

        PermissionAttachment attachment = player.addAttachment(plugin);
        attachment.setPermission("apollo.admin", true);
        attachments.put(player.getUniqueId(), attachment);
    }

    public void clearAttachment(Player player) {
        PermissionAttachment existing = attachments.remove(player.getUniqueId());
        if (existing == null) return;
        try {
            player.removeAttachment(existing);
        } catch (Exception ignored) {
            // player may be gone already
        }
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, StaffRank> e : ranks.entrySet()) {
            cfg.set("ranks." + e.getKey(), e.getValue().name());
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save ranks.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("ranks");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            StaffRank rank = StaffRank.fromString(cfg.getString("ranks." + key));
            if (rank == null) continue;
            try {
                ranks.put(UUID.fromString(key), rank);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipped a malformed rank entry: " + key);
            }
        }
    }
}
