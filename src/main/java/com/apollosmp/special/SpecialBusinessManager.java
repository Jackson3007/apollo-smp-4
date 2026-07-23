package com.apollosmp.special;

import com.apollosmp.ApolloSMP;
import com.apollosmp.util.Items;
import com.apollosmp.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Placed special businesses. Production is worked out from timestamps, never ticked. */
public class SpecialBusinessManager {

    private final ApolloSMP plugin;
    private final File file;
    private final NamespacedKey dataKey;
    private final Random random = new Random();

    /** location key -> business */
    private final Map<String, SpecialBusiness> placed = new ConcurrentHashMap<>();
    /** Businesses sitting in an item, waiting to be placed. */
    private final Map<String, SpecialBusiness> carried = new ConcurrentHashMap<>();

    public SpecialBusinessManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "specialbusinesses.yml");
        this.dataKey = new NamespacedKey(plugin, "apollo_special_id");
        load();
    }

    // ---- item form ----
    public ItemStack createItem(SpecialBusiness b) {
        carried.put(b.id(), b);
        saveCarried();

        ItemStack item = new ItemStack(b.block());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.lore("<gradient:#f9d423:#ff4e50><bold>" + b.name() + "</bold></gradient>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Msg.lore("<gray>" + b.description()));
            lore.add(Msg.lore("<dark_gray>―――――――――――"));
            lore.add(Msg.lore("<gray>Rarity: <white>" + b.rarity() + "</white>"));
            lore.add(Msg.lore("<gray>Industry: <white>" + b.industry() + "</white>"));
            lore.add(Msg.lore("<gray>Trait: <#e94fd0>" + b.trait().display() + "</#e94fd0>"));
            lore.add(Msg.lore("<gray>Produces every <white>"
                    + SpecialAuctionManager.formatSeconds(b.effectiveInterval()) + "</white>:"));
            lore.add(Msg.lore("  <dark_gray>+</dark_gray> <white>" + b.effectiveKnownAmount() + "x "
                    + SpecialAuctionManager.pretty(b.knownItem()) + "</white>"));
            lore.add(Msg.lore("  <dark_gray>+</dark_gray> <white>" + b.effectiveHiddenAmount() + "x "
                    + SpecialAuctionManager.pretty(b.hiddenItem()) + "</white>"));
            lore.add(Msg.lore("<gray>Storage: <white>" + b.effectiveStorage() + "</white> items"));
            lore.add(Msg.lore("<gray>Est. profit: <#f9d423>"
                    + plugin.msg().money(b.exactProfit()) + "</#f9d423>/day"));
            lore.add(Msg.lore(""));
            lore.add(Msg.lore("<yellow>Place it down to start production."));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(dataKey, PersistentDataType.STRING, b.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    public String readId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.get(dataKey, PersistentDataType.STRING);
    }

    public SpecialBusiness carried(String id) {
        return id == null ? null : carried.get(id);
    }

    // ---- placement ----
    public boolean place(Location loc, String id, Player player) {
        SpecialBusiness b = carried.get(id);
        if (b == null) return false;
        b.setLocation(loc);
        b.setOwner(player.getUniqueId());
        b.setOwnerName(player.getName());
        b.setLastGen(System.currentTimeMillis());
        placed.put(b.locationKey(), b);
        carried.remove(id);
        save();
        return true;
    }

    public SpecialBusiness at(Location loc) {
        return placed.get(SpecialBusiness.key(loc));
    }

    public boolean isSpecial(Location loc) {
        return placed.containsKey(SpecialBusiness.key(loc));
    }

    /** Pick the block back up, keeping whatever it has stored. */
    public ItemStack pickUp(SpecialBusiness b) {
        accrue(b);
        placed.remove(b.locationKey());
        b.setLocation(null, 0, 0, 0);
        save();
        return createItem(b);
    }

    public Collection<SpecialBusiness> all() {
        return new ArrayList<>(placed.values());
    }

    public int countOwnedBy(UUID owner) {
        int n = 0;
        for (SpecialBusiness b : placed.values()) {
            if (owner.equals(b.owner())) n++;
        }
        return n;
    }

    // ---- production ----
    /**
     * Bring a business up to date. Only ever called when someone looks at it,
     * so idle businesses cost nothing.
     */
    public void accrue(SpecialBusiness b) {
        long interval = b.effectiveInterval() * 1000L;
        if (interval <= 0) return;
        long now = System.currentTimeMillis();
        long elapsed = now - b.lastGen();
        if (elapsed < interval) return;

        long cycles = elapsed / interval;
        int cap = b.effectiveStorage();
        boolean ownerOnline = b.owner() != null && plugin.getServer().getPlayer(b.owner()) != null;

        addProduct(b, b.knownItem(), b.effectiveKnownAmount(), cycles, cap, ownerOnline);
        addProduct(b, b.hiddenItem(), b.effectiveHiddenAmount(), cycles, cap, ownerOnline);

        b.setLastGen(b.lastGen() + cycles * interval);
        save();
    }

    private void addProduct(SpecialBusiness b, Material material, int perCycle,
                            long cycles, int cap, boolean ownerOnline) {
        double multiplier = 1.0;
        if (b.trait() == SpecialTrait.AUTOMATED && !ownerOnline) multiplier = 1.25;
        if (b.trait() == SpecialTrait.UNSTABLE) multiplier *= 0.6 + random.nextDouble() * 0.8;

        long add = (long) Math.floor(perCycle * cycles * multiplier);
        if (add <= 0) return;
        int current = b.storage().getOrDefault(material, 0);
        if (current >= cap) return;
        b.storage().put(material, (int) Math.min(cap, current + add));
    }

    /** Total items sitting in the business. */
    public int stored(SpecialBusiness b) {
        int total = 0;
        for (int v : b.storage().values()) total += v;
        return total;
    }

    /** Hand the stored goods to the owner. */
    public int collect(Player player, SpecialBusiness b) {
        accrue(b);
        int given = 0;
        for (Map.Entry<Material, Integer> e : new ArrayList<>(b.storage().entrySet())) {
            int amount = e.getValue();
            while (amount > 0) {
                int chunk = Math.min(e.getKey().getMaxStackSize(), amount);
                Items.give(player, new ItemStack(e.getKey(), chunk));
                amount -= chunk;
                given += chunk;
            }
        }
        b.storage().clear();
        save();
        return given;
    }

    /** Seconds until the next batch lands. */
    public long secondsUntilNext(SpecialBusiness b) {
        long interval = b.effectiveInterval() * 1000L;
        long due = b.lastGen() + interval;
        return Math.max(0, (due - System.currentTimeMillis()) / 1000);
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        int i = 0;
        for (SpecialBusiness b : placed.values()) {
            write(cfg, "placed." + i, b, true);
            i++;
        }
        int j = 0;
        for (SpecialBusiness b : carried.values()) {
            write(cfg, "carried." + j, b, false);
            j++;
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save specialbusinesses.yml: " + ex.getMessage());
        }
    }

    private void saveCarried() {
        save();
    }

    private void write(FileConfiguration cfg, String path, SpecialBusiness b, boolean withPlacement) {
        plugin.specialAuction().writeBusiness(cfg, path, b);
        if (withPlacement && b.isPlaced()) {
            cfg.set(path + ".world", b.worldName());
            cfg.set(path + ".x", b.x());
            cfg.set(path + ".y", b.y());
            cfg.set(path + ".z", b.z());
            cfg.set(path + ".lastGen", b.lastGen());
            List<String> stored = new ArrayList<>();
            for (Map.Entry<Material, Integer> e : b.storage().entrySet()) {
                stored.add(e.getKey().name() + "=" + e.getValue());
            }
            cfg.set(path + ".stored", stored);
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection placedSection = cfg.getConfigurationSection("placed");
        if (placedSection != null) {
            for (String key : placedSection.getKeys(false)) {
                SpecialBusiness b = plugin.specialAuction().readBusiness(cfg, "placed." + key);
                if (b == null) continue;
                String world = cfg.getString("placed." + key + ".world");
                if (world == null) continue;
                b.setLocation(world, cfg.getInt("placed." + key + ".x"),
                        cfg.getInt("placed." + key + ".y"), cfg.getInt("placed." + key + ".z"));
                b.setLastGen(cfg.getLong("placed." + key + ".lastGen", System.currentTimeMillis()));
                for (String entry : cfg.getStringList("placed." + key + ".stored")) {
                    String[] parts = entry.split("=");
                    if (parts.length != 2) continue;
                    Material m = Material.matchMaterial(parts[0]);
                    if (m == null) continue;
                    try {
                        b.storage().put(m, Integer.parseInt(parts[1]));
                    } catch (NumberFormatException ignored) {
                        // skip
                    }
                }
                placed.put(b.locationKey(), b);
            }
        }

        ConfigurationSection carriedSection = cfg.getConfigurationSection("carried");
        if (carriedSection != null) {
            for (String key : carriedSection.getKeys(false)) {
                SpecialBusiness b = plugin.specialAuction().readBusiness(cfg, "carried." + key);
                if (b != null) carried.put(b.id(), b);
            }
        }
    }
}
