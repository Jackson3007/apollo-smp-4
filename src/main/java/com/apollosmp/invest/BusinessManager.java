package com.apollosmp.invest;

import com.apollosmp.ApolloSMP;
import com.apollosmp.util.Items;
import com.apollosmp.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
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

/** Tracks placed business blocks: production, upgrades, persistence, and the buyable item. */
public class BusinessManager {

    public enum UpgradeResult { SUCCESS, MAXED, NOT_ENOUGH_PRODUCED, NO_FUNDS, ERROR }

    private final ApolloSMP plugin;
    private final File file;
    private final NamespacedKey idKey;
    private final NamespacedKey levelKey;
    private final Map<String, BusinessBlock> blocks = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public BusinessManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "businesses.yml");
        this.idKey = new NamespacedKey(plugin, "business_id");
        this.levelKey = new NamespacedKey(plugin, "business_level");
        load();
    }

    // ---- the buyable / tradeable item ----

    public ItemStack createItem(Business business) {
        return createItem(business, 1);
    }

    /** Build the placeable item for a business at a given level (level travels with it). */
    public ItemStack createItem(Business business, int level) {
        ItemStack item = new ItemStack(business.block());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = business.displayName()
                    + (level > 1 ? " <gray>[<white>L" + level + "</white>]</gray>" : "");
            meta.displayName(Msg.lore(name));
            List<Component> lore = new ArrayList<>();
            lore.add(Msg.lore(business.tagline()));
            lore.add(Msg.lore("<dark_gray>―――――――――――"));
            lore.add(Msg.lore("<gray>Level: <#f9d423>L" + level + "</#f9d423>"));
            lore.add(Msg.lore("<gray>Income: <#f9d423>"
                    + plugin.msg().money(business.hourlyValueAtLevel(plugin.sell(), level)) + "/hr</#f9d423>"));
            lore.add(Msg.lore("<gray>Produces per hour:"));
            for (Business.Product p : business.products()) {
                lore.add(Msg.lore("  <dark_gray>+</dark_gray> <white>" + Items.pretty(p.material())
                        + "</white> <gray>(<green>" + business.perHourAtLevel(p, level) + "/hr</green>)"));
            }
            lore.add(Msg.lore(""));
            lore.add(Msg.lore("<yellow>Place to start your business!"));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, business.id());
            meta.getPersistentDataContainer().set(levelKey, PersistentDataType.INTEGER, level);
            item.setItemMeta(meta);
        }
        return item;
    }

    public String readBusinessId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public int readBusinessLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 1;
        Integer level = item.getItemMeta().getPersistentDataContainer()
                .get(levelKey, PersistentDataType.INTEGER);
        return level == null ? 1 : Math.max(1, level);
    }

    // ---- placed blocks ----

    public BusinessBlock getAt(Location loc) {
        return blocks.get(BusinessBlock.key(loc));
    }

    public boolean isBusiness(Location loc) {
        return blocks.containsKey(BusinessBlock.key(loc));
    }

    public void register(Location loc, String businessId, int level, UUID owner, String ownerName) {
        BusinessBlock block = new BusinessBlock(loc.getWorld().getName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(),
                businessId, owner, ownerName, System.currentTimeMillis());
        block.setLevel(level);
        blocks.put(block.key(), block);
        dirty = true;
        save();
    }

    public void remove(BusinessBlock block) {
        blocks.remove(block.key());
        dirty = true;
        save();
    }

    /** Advance production up to the current time, scaled by the block's level. */
    public void updateProduction(BusinessBlock block) {
        Business def = Businesses.get(block.businessId());
        if (def == null) return;
        long interval = def.intervalMillis();
        if (interval <= 0) return;
        long now = System.currentTimeMillis();
        long elapsed = now - block.lastGen();
        if (elapsed < interval) return;

        long intervals = elapsed / interval;
        long producedThisRun = 0;
        double townBoost = townBoostAt(block);
        for (Business.Product p : def.products()) {
            int perInterval = def.amountAtLevel(p, block.level());
            int cap = def.capacityForAtLevel(p, block.level());
            int current = block.storage().getOrDefault(p.material(), 0);
            if (current >= cap) continue;
            long add = (long) Math.floor(perInterval * intervals * townBoost);
            int next = (int) Math.min(cap, current + add);
            producedThisRun += (next - current);
            block.storage().put(p.material(), next);
        }
        block.addProduced(producedThisRun);
        block.setLastGen(block.lastGen() + intervals * interval);
        dirty = true;
    }

    /** Industry upgrade bonus for a business standing on town land. */
    private double townBoostAt(BusinessBlock block) {
        try {
            org.bukkit.World world = plugin.getServer().getWorld(block.worldName());
            if (world == null) return 1.0;
            String key = com.apollosmp.town.TownManager.chunkKey(world, block.x() >> 4, block.z() >> 4);
            com.apollosmp.town.Town town = plugin.towns().getTownAt(key);
            return town == null ? 1.0 : town.productionMultiplier();
        } catch (Exception ex) {
            return 1.0;
        }
    }

    public boolean canUpgrade(BusinessBlock block) {
        Business def = Businesses.get(block.businessId());
        if (def == null || block.level() >= Business.MAX_LEVEL) return false;
        return block.producedSinceUpgrade() >= def.unitsToUpgrade(block.level());
    }

    public UpgradeResult tryUpgrade(Player player, BusinessBlock block) {
        Business def = Businesses.get(block.businessId());
        if (def == null) return UpgradeResult.ERROR;
        if (block.level() >= Business.MAX_LEVEL) return UpgradeResult.MAXED;
        if (block.producedSinceUpgrade() < def.unitsToUpgrade(block.level())) {
            return UpgradeResult.NOT_ENOUGH_PRODUCED;
        }
        double cost = def.upgradeCost(block.level());
        if (!plugin.economy().has(player.getUniqueId(), cost)) return UpgradeResult.NO_FUNDS;
        plugin.economy().withdraw(player.getUniqueId(), cost);
        block.setLevel(block.level() + 1);
        block.setProducedSinceUpgrade(0);
        dirty = true;
        save();
        return UpgradeResult.SUCCESS;
    }

    public int countOwnedBy(UUID owner) {
        int n = 0;
        for (BusinessBlock b : blocks.values()) if (b.owner().equals(owner)) n++;
        return n;
    }

    public Collection<BusinessBlock> all() {
        return blocks.values();
    }

    // ---- ambient particles ----

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
            cfg.set(base + ".level", b.level());
            cfg.set(base + ".produced", b.producedSinceUpgrade());
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
                block.setLevel(s.getInt("level", 1));
                block.setProducedSinceUpgrade(s.getLong("produced", 0));
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
