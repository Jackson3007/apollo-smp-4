package com.apollosmp.economy;

import com.apollosmp.ApolloSMP;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A standalone economy. No Vault dependency required, so the plugin is fully
 * self-contained. Balances are cached in memory and flushed to economy.yml.
 */
public class EconomyManager {

    private final ApolloSMP plugin;
    private final File file;
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public EconomyManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "economy.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (cfg.getConfigurationSection("balances") == null) return;
        for (String key : cfg.getConfigurationSection("balances").getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                balances.put(id, cfg.getDouble("balances." + key + ".money"));
                String name = cfg.getString("balances." + key + ".name");
                if (name != null) names.put(id, name);
            } catch (IllegalArgumentException ignored) {
                // skip malformed uuid
            }
        }
    }

    public void save() {
        if (!dirty) return;
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Double> e : balances.entrySet()) {
            String base = "balances." + e.getKey();
            cfg.set(base + ".money", e.getValue());
            cfg.set(base + ".name", names.get(e.getKey()));
        }
        try {
            cfg.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save economy.yml: " + ex.getMessage());
        }
    }

    /** Ensure an account exists; seed the starting balance for new players. */
    public void ensureAccount(UUID id, String name) {
        names.put(id, name);
        if (!balances.containsKey(id)) {
            double start = plugin.getConfig().getDouble("economy.starting-balance", 0.0);
            balances.put(id, start);
            dirty = true;
        } else {
            dirty = true; // refresh cached name
        }
    }

    public boolean hasAccount(UUID id) {
        return balances.containsKey(id);
    }

    public double getBalance(UUID id) {
        return balances.getOrDefault(id, 0.0);
    }

    public boolean has(UUID id, double amount) {
        return getBalance(id) >= amount;
    }

    public void deposit(UUID id, double amount) {
        if (amount <= 0) return;
        balances.merge(id, round(amount), Double::sum);
        dirty = true;
    }

    /** Returns true if the withdrawal succeeded (sufficient funds). */
    public boolean withdraw(UUID id, double amount) {
        if (amount <= 0) return true;
        double current = getBalance(id);
        if (current < amount) return false;
        balances.put(id, round(current - amount));
        dirty = true;
        return true;
    }

    public void set(UUID id, double amount) {
        balances.put(id, round(Math.max(0, amount)));
        dirty = true;
    }

    public String nameOf(UUID id) {
        return names.getOrDefault(id, "Unknown");
    }

    /** Top N accounts by balance, as ordered (name -> balance) pairs. */
    public List<Map.Entry<UUID, Double>> top(int limit) {
        List<Map.Entry<UUID, Double>> list = new ArrayList<>(balances.entrySet());
        list.sort(Comparator.comparingDouble((Map.Entry<UUID, Double> e) -> e.getValue()).reversed());
        if (list.size() > limit) return new ArrayList<>(list.subList(0, limit));
        return list;
    }

    public Map<UUID, String> knownNames() {
        return new LinkedHashMap<>(names);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
