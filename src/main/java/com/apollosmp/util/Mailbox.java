package com.apollosmp.util;

import com.apollosmp.ApolloSMP;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A per-player collection box for items that need to reach a player who may be
 * offline (expired/cancelled auctions, fulfilled buy orders). Items are handed
 * over the next time the player collects.
 */
public class Mailbox {

    private final ApolloSMP plugin;
    private final File file;
    private final Map<UUID, List<ItemStack>> box = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public Mailbox(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mailbox.yml");
        load();
    }

    public void add(UUID id, ItemStack item) {
        box.computeIfAbsent(id, k -> new ArrayList<>()).add(item);
        dirty = true;
        save();
    }

    public int size(UUID id) {
        List<ItemStack> list = box.get(id);
        return list == null ? 0 : list.size();
    }

    /** Deliver everything to the player, dropping overflow at their feet. */
    public int collect(Player player) {
        List<ItemStack> list = box.remove(player.getUniqueId());
        if (list == null || list.isEmpty()) return 0;
        for (ItemStack item : list) Items.give(player, item);
        dirty = true;
        save();
        return list.size();
    }

    public void save() {
        if (!dirty) return;
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, List<ItemStack>> e : box.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            List<String> encoded = new ArrayList<>();
            for (ItemStack it : e.getValue()) encoded.add(Items.toBase64(it));
            cfg.set(e.getKey().toString(), encoded);
        }
        try {
            cfg.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save mailbox.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            if (!(cfg.get(key) instanceof List)) continue;
            try {
                UUID id = UUID.fromString(key);
                List<ItemStack> items = new ArrayList<>();
                for (String enc : cfg.getStringList(key)) items.add(Items.fromBase64(enc));
                if (!items.isEmpty()) box.put(id, items);
            } catch (Exception ignored) {
            }
        }
    }
}
