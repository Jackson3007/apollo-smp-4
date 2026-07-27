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
    private final NamespacedKey producedKey;
    private final Map<String, BusinessBlock> blocks = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public BusinessManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "businesses.yml");
        this.idKey = new NamespacedKey(plugin, "business_id");
        this.levelKey = new NamespacedKey(plugin, "business_level");
        this.producedKey = new NamespacedKey(plugin, "apollo_business_produced");
        load();
    }

    // ---- the buyable / tradeable item ----

    public ItemStack createItem(Business business) {
        return createItem(business, 1);
    }

    public ItemStack createItem(Business business, int level, long produced) {
        ItemStack item = createItem(business, level);
        if (produced <= 0) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(producedKey, PersistentDataType.LONG, produced);
            List<Component> lore = meta.lore();
            if (lore == null) lore = new ArrayList<>();
            long needed = business.unitsToUpgrade(level);
            lore.add(Msg.lore("<gray>Upgrade progress kept: <#f9d423>" + produced
                    + (needed > 0 ? "/" + needed : "") + "</#f9d423>"));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Upgrade progress stored on a picked-up business item. */
    public long readProduced(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0L;
        Long value = item.getItemMeta().getPersistentDataContainer()
                .get(producedKey, PersistentDataType.LONG);
        return value == null ? 0L : Math.max(0L, value);
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
        if (plugin.holograms() != null) plugin.holograms().forget(block.key());
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

    /**
     * Pay out a business's earnings - to its town if it's been assigned to one,
     * otherwise to the owner. Returns the name of whoever got paid.
     */
    public String payOut(BusinessBlock block, double amount) {
        if (amount <= 0) return null;
        if (block.town() != null) {
            com.apollosmp.town.Town town = plugin.towns().townByName(block.town());
            if (town != null) {
                town.depositBank(amount);
                plugin.towns().markDirty();
                return town.name();
            }
            // Town is gone - fall back to the owner rather than losing the money.
            block.setTown(null);
        }
        plugin.economy().deposit(block.owner(), amount);
        return null;
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

            int level = Math.max(1, b.level());
            Location loc = new Location(world, b.x() + 0.5, b.y() + 1.15, b.z() + 0.5);

            // The base puff grows with level.
            int count = 3 + level;
            double spread = 0.22 + level * 0.02;
            world.spawnParticle(particle, loc, count, spread, 0.2, spread, 0.01);

            // Mid levels earn a slowly turning ring.
            if (level >= 3) {
                Particle ring = resolveParticle("ENCHANT");
                if (ring != null) {
                    int points = 4 + level;
                    double radius = 0.55 + level * 0.03;
                    double turn = (System.currentTimeMillis() % 6000L) / 6000.0 * Math.PI * 2;
                    for (int i = 0; i < points; i++) {
                        double angle = turn + (Math.PI * 2 * i / points);
                        world.spawnParticle(ring,
                                loc.clone().add(Math.cos(angle) * radius, -0.35, Math.sin(angle) * radius),
                                1, 0, 0, 0, 0);
                    }
                }
            }

            // High levels get a crown above the block.
            if (level >= 6) {
                Particle crown = resolveParticle("END_ROD");
                if (crown != null) {
                    world.spawnParticle(crown, loc.clone().add(0, 0.55, 0),
                            1 + (level - 5) / 2, 0.14, 0.05, 0.14, 0.004);
                }
            }

            // Maxed businesses shimmer.
            if (level >= Business.MAX_LEVEL) {
                Particle top = resolveParticle("FLAME");
                if (top != null) {
                    double turn = (System.currentTimeMillis() % 2400L) / 2400.0 * Math.PI * 2;
                    for (int i = 0; i < 3; i++) {
                        double angle = turn + (Math.PI * 2 * i / 3);
                        world.spawnParticle(top,
                                loc.clone().add(Math.cos(angle) * 0.75, 0.85, Math.sin(angle) * 0.75),
                                1, 0, 0, 0, 0);
                    }
                }
            }
        }
    }

    private Particle resolveParticle(String name) {
        if (name == null) return null;
        // Some particles were renamed between versions, so try the old name too.
        String[] candidates = switch (name) {
            case "ENCHANT" -> new String[]{"ENCHANT", "ENCHANTMENT_TABLE"};
            case "CRIT" -> new String[]{"CRIT", "CRIT_MAGIC"};
            case "WAX_ON" -> new String[]{"WAX_ON", "HAPPY_VILLAGER"};
            default -> new String[]{name};
        };
        for (String candidate : candidates) {
            try {
                return Particle.valueOf(candidate);
            } catch (IllegalArgumentException ignored) {
                // try the next one
            }
        }
        try {
            return Particle.valueOf("HAPPY_VILLAGER");
        } catch (Exception ignored) {
            return null;
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
            if (b.town() != null) cfg.set(base + ".town", b.town());
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
                block.setTown(s.getString("town"));
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
