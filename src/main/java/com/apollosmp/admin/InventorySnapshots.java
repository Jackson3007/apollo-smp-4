package com.apollosmp.admin;

import com.apollosmp.ApolloSMP;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps a copy of each player's inventory from their last logout, so staff can
 * inspect offline players. Bukkit gives no way to read an offline inventory
 * directly, so we record it on the way out.
 */
public class InventorySnapshots {

    public record Snapshot(ItemStack[] contents, ItemStack[] armor, ItemStack offHand, long taken) {}

    private final ApolloSMP plugin;
    private final File file;
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();

    public InventorySnapshots(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "inventories.yml");
        load();
    }

    public void capture(Player player) {
        snapshots.put(player.getUniqueId(), new Snapshot(
                player.getInventory().getContents().clone(),
                player.getInventory().getArmorContents().clone(),
                player.getInventory().getItemInOffHand().clone(),
                System.currentTimeMillis()));
    }

    public void captureAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) capture(player);
    }

    public Snapshot get(UUID id) {
        return snapshots.get(id);
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Snapshot> e : snapshots.entrySet()) {
            String base = "players." + e.getKey();
            Snapshot s = e.getValue();
            cfg.set(base + ".contents", new ArrayList<>(Arrays.asList(s.contents())));
            cfg.set(base + ".armor", new ArrayList<>(Arrays.asList(s.armor())));
            cfg.set(base + ".offhand", s.offHand());
            cfg.set(base + ".taken", s.taken());
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save inventories.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("players");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                ItemStack[] contents = readList(cfg, "players." + key + ".contents");
                ItemStack[] armor = readList(cfg, "players." + key + ".armor");
                ItemStack offHand = cfg.getItemStack("players." + key + ".offhand");
                long taken = cfg.getLong("players." + key + ".taken");
                snapshots.put(id, new Snapshot(contents, armor, offHand, taken));
            } catch (Exception ignored) {
                plugin.getLogger().warning("Skipped a malformed inventory snapshot: " + key);
            }
        }
    }

    private ItemStack[] readList(FileConfiguration cfg, String path) {
        List<?> raw = cfg.getList(path);
        if (raw == null) return new ItemStack[0];
        ItemStack[] out = new ItemStack[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            Object o = raw.get(i);
            out[i] = (o instanceof ItemStack stack) ? stack : null;
        }
        return out;
    }
}
