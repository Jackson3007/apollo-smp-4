package com.apollosmp.logistics;

import com.apollosmp.ApolloSMP;
import com.apollosmp.invest.BusinessBlock;
import com.apollosmp.special.SpecialBusiness;
import com.apollosmp.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distribution blocks gather from nearby businesses; wholesale blocks sell
 * whatever the distribution blocks can reach. Linking is by proximity, so
 * there's nothing to wire up manually.
 */
public class LogisticsManager {

    public static final Material DISTRIBUTION_BLOCK = Material.LODESTONE;
    public static final Material WHOLESALE_BLOCK = Material.EMERALD_BLOCK;

    /** A placed logistics block. */
    public static class Node {
        public final String world;
        public final int x, y, z;
        public final UUID owner;
        public String ownerName;
        public boolean notify = true;
        public double lifetimeEarned;
        public long lastSale;

        Node(String world, int x, int y, int z, UUID owner, String ownerName) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.owner = owner;
            this.ownerName = ownerName;
        }

        public String key() { return LogisticsManager.key(world, x, y, z); }
        public Location toLocation(World w) { return new Location(w, x + 0.5, y, z + 0.5); }
    }

    private final ApolloSMP plugin;
    private final File file;
    private final NamespacedKey typeKey;

    private final Map<String, Node> distributors = new ConcurrentHashMap<>();
    private final Map<String, Node> wholesalers = new ConcurrentHashMap<>();

    public LogisticsManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "logistics.yml");
        this.typeKey = new NamespacedKey(plugin, "apollo_logistics");
        load();
    }

    public static String key(String world, int x, int y, int z) {
        return world + "," + x + "," + y + "," + z;
    }

    public static String key(Location loc) {
        return key(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    // ---- config ----
    public int businessRadius() { return plugin.getConfig().getInt("logistics.business-radius", 8); }
    public int hubRadius() { return plugin.getConfig().getInt("logistics.hub-radius", 16); }
    public int intervalMinutes() {
        return Math.max(1, plugin.getConfig().getInt("logistics.sell-interval-minutes", 5));
    }
    public double distributionPrice() {
        return plugin.getConfig().getDouble("logistics.distribution-price", 25000.0);
    }
    public double wholesalePrice() {
        return plugin.getConfig().getDouble("logistics.wholesale-price", 60000.0);
    }
    /** Cut the wholesaler takes, so automation isn't strictly better than selling by hand. */
    public double feePercent() {
        return Math.max(0, Math.min(50, plugin.getConfig().getDouble("logistics.fee-percent", 8.0)));
    }

    // ---- items ----
    public ItemStack createDistribution() {
        return build(DISTRIBUTION_BLOCK, "distribution",
                "<#5ad1e8><bold>Distribution Block</bold>",
                "<gray>Place it near your businesses.",
                "<gray>It gathers from every business you",
                "<gray>own within <white>" + businessRadius() + "</white> blocks.",
                "<gray>Feed it into a Wholesale Block to",
                "<gray>sell automatically.");
    }

    public ItemStack createWholesale() {
        return build(WHOLESALE_BLOCK, "wholesale",
                "<#f9d423><bold>Wholesale Block</bold>",
                "<gray>Sells everything your Distribution",
                "<gray>Blocks can reach, every <white>"
                        + intervalMinutes() + "</white> minutes.",
                "<gray>Range: <white>" + hubRadius() + "</white> blocks.",
                "<gray>Takes a <white>" + (int) feePercent() + "%</white> handling fee.");
    }

    private ItemStack build(Material material, String type, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.lore(name));
            List<Component> lines = new ArrayList<>();
            for (String l : lore) lines.add(Msg.lore(l));
            lines.add(Msg.lore(""));
            lines.add(Msg.lore("<yellow>Place it down to get started."));
            meta.lore(lines);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type);
            item.setItemMeta(meta);
        }
        return item;
    }

    public String readType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
    }

    // ---- placement ----
    public void place(Location loc, String type, Player player) {
        Node node = new Node(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(),
                loc.getBlockZ(), player.getUniqueId(), player.getName());
        if (type.equals("distribution")) distributors.put(node.key(), node);
        else wholesalers.put(node.key(), node);
        save();
    }

    public Node distributorAt(Location loc) { return distributors.get(key(loc)); }
    public Node wholesalerAt(Location loc) { return wholesalers.get(key(loc)); }

    public Node anyAt(Location loc) {
        Node n = distributors.get(key(loc));
        return n != null ? n : wholesalers.get(key(loc));
    }

    public boolean isDistributor(Location loc) { return distributors.containsKey(key(loc)); }

    public void remove(Location loc) {
        String k = key(loc);
        distributors.remove(k);
        wholesalers.remove(k);
        save();
    }

    // ---- linking by proximity ----
    /** Distribution blocks a wholesaler can reach, owned by the same player. */
    public List<Node> linkedDistributors(Node wholesaler) {
        List<Node> out = new ArrayList<>();
        int r = hubRadius();
        for (Node d : distributors.values()) {
            if (!d.world.equals(wholesaler.world)) continue;
            if (!d.owner.equals(wholesaler.owner)) continue;
            if (Math.abs(d.x - wholesaler.x) > r) continue;
            if (Math.abs(d.y - wholesaler.y) > r) continue;
            if (Math.abs(d.z - wholesaler.z) > r) continue;
            out.add(d);
        }
        return out;
    }

    /** Normal businesses this distribution block covers. */
    public List<BusinessBlock> linkedBusinesses(Node distributor) {
        List<BusinessBlock> out = new ArrayList<>();
        int r = businessRadius();
        for (BusinessBlock b : plugin.businesses().all()) {
            if (!b.worldName().equals(distributor.world)) continue;
            if (!distributor.owner.equals(b.owner())) continue;
            if (Math.abs(b.x() - distributor.x) > r) continue;
            if (Math.abs(b.y() - distributor.y) > r) continue;
            if (Math.abs(b.z() - distributor.z) > r) continue;
            out.add(b);
        }
        return out;
    }

    /** Special businesses this distribution block covers. */
    public List<SpecialBusiness> linkedSpecials(Node distributor) {
        List<SpecialBusiness> out = new ArrayList<>();
        int r = businessRadius();
        for (SpecialBusiness b : plugin.specialBusinesses().all()) {
            if (b.worldName() == null || !b.worldName().equals(distributor.world)) continue;
            if (!distributor.owner.equals(b.owner())) continue;
            if (Math.abs(b.x() - distributor.x) > r) continue;
            if (Math.abs(b.y() - distributor.y) > r) continue;
            if (Math.abs(b.z() - distributor.z) > r) continue;
            out.add(b);
        }
        return out;
    }

    /** What a wholesaler would earn right now, before the fee. */
    public double pendingValue(Node wholesaler) {
        double total = 0;
        for (Node d : linkedDistributors(wholesaler)) {
            for (BusinessBlock b : linkedBusinesses(d)) {
                plugin.businesses().updateProduction(b);
                for (Map.Entry<Material, Integer> e : b.storage().entrySet()) {
                    total += plugin.sell().priceOf(e.getKey()) * e.getValue();
                }
            }
            for (SpecialBusiness b : linkedSpecials(d)) {
                plugin.specialBusinesses().accrue(b);
                for (Map.Entry<Material, Integer> e : b.storage().entrySet()) {
                    total += plugin.sell().priceOf(e.getKey()) * e.getValue();
                }
            }
        }
        return total;
    }

    // ---- selling ----
    /** Empty every reachable business and pay the owner. Returns what they got. */
    public double sell(Node wholesaler, boolean announce) {
        double gross = 0;
        int items = 0;

        for (Node d : linkedDistributors(wholesaler)) {
            for (BusinessBlock b : linkedBusinesses(d)) {
                plugin.businesses().updateProduction(b);
                for (Map.Entry<Material, Integer> e : new ArrayList<>(b.storage().entrySet())) {
                    gross += plugin.sell().priceOf(e.getKey()) * e.getValue();
                    items += e.getValue();
                }
                b.storage().clear();
            }
            for (SpecialBusiness b : linkedSpecials(d)) {
                plugin.specialBusinesses().accrue(b);
                for (Map.Entry<Material, Integer> e : new ArrayList<>(b.storage().entrySet())) {
                    gross += plugin.sell().priceOf(e.getKey()) * e.getValue();
                    items += e.getValue();
                }
                b.storage().clear();
            }
        }
        if (items == 0) return 0;

        plugin.businesses().save();
        plugin.specialBusinesses().save();

        double fee = gross * (feePercent() / 100.0);
        double net = gross - fee;
        plugin.economy().deposit(wholesaler.owner, net);
        wholesaler.lifetimeEarned += net;
        wholesaler.lastSale = System.currentTimeMillis();
        save();

        if (announce && wholesaler.notify) {
            Player online = plugin.getServer().getPlayer(wholesaler.owner);
            if (online != null) {
                plugin.msg().send(online, "<#f9d423>Wholesale:</#f9d423> <gray>sold <white>"
                        + items + "</white> goods for <green>" + plugin.msg().money(net)
                        + "</green> <dark_gray>(after " + (int) feePercent() + "% fee)</dark_gray>");
            }
        }
        return net;
    }

    /** Timer entry point. */
    public void tick() {
        long now = System.currentTimeMillis();
        long interval = intervalMinutes() * 60_000L;
        for (Node w : new ArrayList<>(wholesalers.values())) {
            if (now - w.lastSale < interval) continue;
            // Stagger the first run so a fresh block doesn't fire instantly.
            if (w.lastSale == 0) {
                w.lastSale = now;
                continue;
            }
            sell(w, true);
        }
    }

    public long secondsUntilSale(Node wholesaler) {
        long interval = intervalMinutes() * 60_000L;
        long due = wholesaler.lastSale + interval;
        return Math.max(0, (due - System.currentTimeMillis()) / 1000);
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        writeAll(cfg, "distributors", distributors);
        writeAll(cfg, "wholesalers", wholesalers);
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save logistics.yml: " + ex.getMessage());
        }
    }

    private void writeAll(FileConfiguration cfg, String path, Map<String, Node> nodes) {
        int i = 0;
        for (Node n : nodes.values()) {
            String base = path + "." + i++;
            cfg.set(base + ".world", n.world);
            cfg.set(base + ".x", n.x);
            cfg.set(base + ".y", n.y);
            cfg.set(base + ".z", n.z);
            cfg.set(base + ".owner", n.owner.toString());
            cfg.set(base + ".ownerName", n.ownerName);
            cfg.set(base + ".notify", n.notify);
            cfg.set(base + ".earned", n.lifetimeEarned);
            cfg.set(base + ".lastSale", n.lastSale);
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        readAll(cfg, "distributors", distributors);
        readAll(cfg, "wholesalers", wholesalers);
    }

    private void readAll(FileConfiguration cfg, String path, Map<String, Node> into) {
        ConfigurationSection root = cfg.getConfigurationSection(path);
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                String base = path + "." + key;
                Node n = new Node(cfg.getString(base + ".world"),
                        cfg.getInt(base + ".x"), cfg.getInt(base + ".y"), cfg.getInt(base + ".z"),
                        UUID.fromString(cfg.getString(base + ".owner")),
                        cfg.getString(base + ".ownerName", "Unknown"));
                n.notify = cfg.getBoolean(base + ".notify", true);
                n.lifetimeEarned = cfg.getDouble(base + ".earned");
                n.lastSale = cfg.getLong(base + ".lastSale");
                into.put(n.key(), n);
            } catch (Exception ignored) {
                plugin.getLogger().warning("Skipped a malformed logistics node.");
            }
        }
    }
}
