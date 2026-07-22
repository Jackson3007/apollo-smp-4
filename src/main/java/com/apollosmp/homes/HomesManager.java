package com.apollosmp.homes;

import com.apollosmp.ApolloSMP;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HomesManager {

    private final ApolloSMP plugin;
    private final File file;
    // uuid -> (lowercased name -> Home)
    private final Map<UUID, Map<String, Home>> homes = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public HomesManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("homes");
        if (root == null) return;
        for (String uuidKey : root.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(uuidKey);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            ConfigurationSection playerSec = root.getConfigurationSection(uuidKey);
            if (playerSec == null) continue;
            Map<String, Home> map = new LinkedHashMap<>();
            for (String homeName : playerSec.getKeys(false)) {
                ConfigurationSection h = playerSec.getConfigurationSection(homeName);
                if (h == null) continue;
                Material icon = Material.matchMaterial(h.getString("icon", "RED_BED"));
                Home home = new Home(
                        h.getString("display", homeName),
                        h.getString("world", "world"),
                        h.getDouble("x"), h.getDouble("y"), h.getDouble("z"),
                        (float) h.getDouble("yaw"), (float) h.getDouble("pitch"),
                        icon == null ? Material.RED_BED : icon);
                map.put(homeName.toLowerCase(Locale.ROOT), home);
            }
            homes.put(id, map);
        }
    }

    public void save() {
        if (!dirty) return;
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Home>> playerEntry : homes.entrySet()) {
            String base = "homes." + playerEntry.getKey();
            for (Map.Entry<String, Home> homeEntry : playerEntry.getValue().entrySet()) {
                Home h = homeEntry.getValue();
                String p = base + "." + homeEntry.getKey();
                cfg.set(p + ".display", h.name());
                cfg.set(p + ".world", h.world());
                cfg.set(p + ".x", h.x());
                cfg.set(p + ".y", h.y());
                cfg.set(p + ".z", h.z());
                cfg.set(p + ".yaw", h.yaw());
                cfg.set(p + ".pitch", h.pitch());
                cfg.set(p + ".icon", h.icon().name());
            }
        }
        try {
            cfg.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save homes.yml: " + e.getMessage());
        }
    }

    public List<Home> getHomes(UUID id) {
        Map<String, Home> map = homes.get(id);
        if (map == null) return new ArrayList<>();
        return new ArrayList<>(map.values());
    }

    public Home getHome(UUID id, String name) {
        Map<String, Home> map = homes.get(id);
        if (map == null) return null;
        return map.get(name.toLowerCase(Locale.ROOT));
    }

    public int count(UUID id) {
        Map<String, Home> map = homes.get(id);
        return map == null ? 0 : map.size();
    }

    public boolean exists(UUID id, String name) {
        return getHome(id, name) != null;
    }

    /** Add or overwrite a home. */
    public void setHome(UUID id, String name, Location loc, Material icon) {
        homes.computeIfAbsent(id, k -> new LinkedHashMap<>())
                .put(name.toLowerCase(Locale.ROOT), Home.fromLocation(name, loc, icon));
        dirty = true;
        save();
    }

    public boolean deleteHome(UUID id, String name) {
        Map<String, Home> map = homes.get(id);
        if (map == null) return false;
        boolean removed = map.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) {
            dirty = true;
            save();
        }
        return removed;
    }

    /** Highest home limit granted by the player's permissions. */
    public int limitFor(Player player) {
        int limit = plugin.getConfig().getInt("homes.default-limit", 5);
        ConfigurationSection limits = plugin.getConfig().getConfigurationSection("homes.limits");
        if (limits != null) {
            for (String perm : limits.getKeys(false)) {
                if (player.hasPermission(perm)) {
                    limit = Math.max(limit, limits.getInt(perm));
                }
            }
        }
        return limit;
    }
}
