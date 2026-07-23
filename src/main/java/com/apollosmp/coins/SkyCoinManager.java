package com.apollosmp.coins;

import com.apollosmp.ApolloSMP;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** A simple second currency: Sky Coins (whole-number, earned by voting). */
public class SkyCoinManager {

    private final ApolloSMP plugin;
    private final File file;
    private final Map<UUID, Integer> coins = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public SkyCoinManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "skycoins.yml");
        load();
    }

    public int get(UUID id) {
        return coins.getOrDefault(id, 0);
    }

    public boolean has(UUID id, int amount) {
        return get(id) >= amount;
    }

    public void add(UUID id, int amount) {
        if (amount <= 0) return;
        coins.merge(id, amount, Integer::sum);
        dirty = true;
        save();
    }

    public boolean take(UUID id, int amount) {
        if (amount <= 0) return true;
        int current = get(id);
        if (current < amount) return false;
        coins.put(id, current - amount);
        dirty = true;
        save();
        return true;
    }

    public void set(UUID id, int amount) {
        coins.put(id, Math.max(0, amount));
        dirty = true;
        save();
    }

    public void save() {
        if (!dirty) return;
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> e : coins.entrySet()) {
            cfg.set(e.getKey().toString(), e.getValue());
        }
        try {
            cfg.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save skycoins.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            try {
                coins.put(UUID.fromString(key), cfg.getInt(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
