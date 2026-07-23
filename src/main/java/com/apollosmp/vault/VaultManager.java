package com.apollosmp.vault;

import com.apollosmp.ApolloSMP;
import com.apollosmp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Personal storage vaults that survive restarts. */
public class VaultManager {

    private final ApolloSMP plugin;
    private final File file;
    /** "uuid:index" -> saved contents */
    private final Map<String, ItemStack[]> vaults = new ConcurrentHashMap<>();

    public VaultManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "vaults.yml");
        load();
    }

    public int vaultCount() {
        return Math.max(1, Math.min(9, plugin.getConfig().getInt("vaults.count", 3)));
    }

    public int rows() {
        return Math.max(1, Math.min(6, plugin.getConfig().getInt("vaults.rows", 6)));
    }

    private String key(UUID owner, int index) {
        return owner + ":" + index;
    }

    public void open(Player player, int index) {
        int size = rows() * 9;
        VaultHolder holder = new VaultHolder(player.getUniqueId(), index);
        Inventory inv = Bukkit.createInventory(holder, size,
                Msg.mm("<gradient:#f9d423:#ff4e50><bold>Vault " + index + "</bold></gradient>"));
        holder.setInventory(inv);

        ItemStack[] saved = vaults.get(key(player.getUniqueId(), index));
        if (saved != null) {
            for (int i = 0; i < Math.min(saved.length, size); i++) {
                inv.setItem(i, saved[i]);
            }
        }
        player.openInventory(inv);
    }

    /** Called when a vault inventory closes. */
    public void store(UUID owner, int index, ItemStack[] contents) {
        vaults.put(key(owner, index), Arrays.copyOf(contents, contents.length));
        save();
    }

    public int used(UUID owner, int index) {
        ItemStack[] contents = vaults.get(key(owner, index));
        if (contents == null) return 0;
        int n = 0;
        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir()) n++;
        }
        return n;
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, ItemStack[]> e : vaults.entrySet()) {
            String[] parts = e.getKey().split(":");
            if (parts.length != 2) continue;
            List<ItemStack> items = new ArrayList<>(Arrays.asList(e.getValue()));
            cfg.set("vaults." + parts[0] + "." + parts[1], items);
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save vaults.yml: " + ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("vaults");
        if (root == null) return;

        for (String owner : root.getKeys(false)) {
            ConfigurationSection ownerSection = root.getConfigurationSection(owner);
            if (ownerSection == null) continue;
            for (String index : ownerSection.getKeys(false)) {
                try {
                    List<?> raw = cfg.getList("vaults." + owner + "." + index);
                    if (raw == null) continue;
                    ItemStack[] contents = new ItemStack[raw.size()];
                    for (int i = 0; i < raw.size(); i++) {
                        Object o = raw.get(i);
                        contents[i] = (o instanceof ItemStack stack) ? stack : null;
                    }
                    vaults.put(owner + ":" + index, contents);
                } catch (Exception ignored) {
                    plugin.getLogger().warning("Skipped a malformed vault: " + owner + "/" + index);
                }
            }
        }
    }
}
