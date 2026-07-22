package com.apollosmp.invest;

import com.apollosmp.ApolloSMP;
import com.apollosmp.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks placed business blocks: production, persistence, and the buyable item. */
public class BusinessManager {

    private final ApolloSMP plugin;
    private final File file;
    private final NamespacedKey idKey;
    private final Map<String, BusinessBlock> blocks = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public BusinessManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "businesses.yml");
        this.idKey = new NamespacedKey(plugin, "business_id");
        load();
    }

    // ---- the buyable item ----

    /** Build the placeable item that represents a business. */
    public ItemStack createItem(Business business) {
        ItemStack item = new ItemStack(business.block());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.lore(business.displayName()));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Msg.lore(business.tagline()));
            lore.add(Msg.lore("<dark_gray>―――――――――――"));
            lore.add(Msg.lore("<gray>Income: <#f9d423>" + plugin.msg().money(business.hourlyValue(plugin.sell()))
                    + "/hr</#f9d423>"));
            lore.add(Msg.lore("<gray>Produces per hour:"));
            for (Business.Product p : business.products()) {
                lore.add(Msg.lore("  <dark_gray>+</dark_gray> <white>"
                        + com.apollosmp.util.Items.pretty(p.material()) + "</white> "
                        + "<gray>(<green>" + business.perHour(p) + "/hr</green>)"));
            }
            lore.add(Msg.lore(""));
            lore.add(Msg.lore("<yellow>Place to start your business!"));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, business.id());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Returns the business id stored on an item, or null if it isn't a business item. */
    public String readBusinessId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(idKey, PersistentDataType.STRING);
    }

    // ---- placed blocks ----

    public BusinessBlock getAt(Location loc) {
        return blocks.get(BusinessBlock.key(loc));
    }

    public boolean isBusiness(Location loc) {
        return blocks.containsKey(BusinessBlock.key(loc));
    }

    public void register(Location loc, String businessId, UUID owner, String ownerName) {
        BusinessBlock block = new BusinessBlock(loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                businessId, owner, ownerName, System.currentTimeMillis());
        blocks.put(block.key(), block);
        dirty = true;
        save();
    }

    public void remove(BusinessBlock block) {
        blocks.remove(block.key());
        dirty = true;
        save();
    }

    /** Advance production up to the current time (and cap at storage limits). */
    public void updateProduction(BusinessBlock block) {
        Business def = Businesses.get(block.businessId());
        if (def == null) return;
        long interval = def.intervalMillis();
        if (interval <= 0) return;
        long now = System.currentTimeMillis();
        long elapsed = now - block.lastGen();
        if (elapsed < interval) return;

        long intervals = elapsed / interval;
        for (Business.Product p : def.products()) {
            int cap = def.capacityFor(p);
            int current = block.storage().getOrDefault(p.material(), 0);
            if (current >= cap) continue;
            long add = (long) p.amountPerInterval() * intervals;
            int next = (int) Math.min(cap, current + add);
            block.storage().put(p.material(), next);
        }
        block.setLastGen(block.lastGen() + intervals * interval);
        dirty = true;
    }

    public int countOwnedBy(UUID owner) {
        int n = 0;
        for (BusinessBlock b : blocks.values()) if (b.owner().equals(owner)) n++;
        return n;
    }

    public Collection<BusinessBlock> all() {
        return blocks.values();
    }

    /** Spawn ambient particles above every placed business block (called on a timer). */
    public void spawnParticles() {
        for (BusinessBlock b : blocks.values()) {
            World world = Bukkit.getWorld(b.worldName());
            if (world == null) continue;
            if (!world.isChunkLoaded(b.x() >> 4, b.z() >> 4)) continue;
            Business def = Businesses.get(b.businessId());
            if (def == null) continue;
            Particle particle = resolveParticle(def.particleName());
            if (particle == null) continue;
            Location loc = new Location(world, b.x() + 0.5, b.y() + 1.15, b.z() + 0.5);
            world.spawnParticle(particle, loc, 6, 0.25, 0.2, 0.25, 0.01);
        }
    }

    private Particle resolveParticle(String name) {
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            try {
                return Particle.valueOf("HAPPY_VILLAGER");
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    // ---- persistence ----

    public void save() {
        if (!dirty) return;
        FileConfiguration cfg = new YamlConfiguration();
        for (BusinessBlock b : blocks.values()) {
            String base = "blocks." + b.key().replace(";", "_");
            cfg.set(base + ".world", b.worldName());
            cfg.set(base + ".x", b.x());
            cfg.set(base + ".y", b.y());
            cfg.set(base + ".z", b.z());
            cfg.set(base + ".business", b.businessId());
            cfg.set(base + ".owner", b.owner().toString());
            cfg.set(base + ".ownerName", b.ownerName());
            cfg.set(base + ".lastGen", b.lastGen());
            for (Map.Entry<Material, Integer> e : b.storage().entrySet()) {
                if (e.getValue() > 0) cfg.set(base + ".storage." + e.getKey().name(), e.getValue());
            }
        }
        try {
            cfg.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save businesses.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("blocks");
        if (root == null) return;
        for (String entry : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(entry);
            if (s == null) continue;
            try {
                BusinessBlock block = new BusinessBlock(
                        s.getString("world"),
                        s.getInt("x"), s.getInt("y"), s.getInt("z"),
                        s.getString("business"),
                        UUID.fromString(s.getString("owner")),
                        s.getString("ownerName", "Unknown"),
                        s.getLong("lastGen"));
                ConfigurationSection store = s.getConfigurationSection("storage");
                if (store != null) {
                    for (String matName : store.getKeys(false)) {
                        Material mat = Material.matchMaterial(matName);
                        if (mat != null) block.storage().put(mat, store.getInt(matName));
                    }
                }
                blocks.put(block.key(), block);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipped bad business entry: " + entry);
            }
        }
    }
}
