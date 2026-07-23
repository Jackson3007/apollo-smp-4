package com.apollosmp.logistics;

import com.apollosmp.ApolloSMP;
import com.apollosmp.invest.BusinessBlock;
import com.apollosmp.special.SpecialBusiness;
import com.apollosmp.util.Msg;
import net.kyori.adventure.text.Component;
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

    public static final Material DISTRIBUTION_BLOCK = Material.CRYING_OBSIDIAN;
    public static final Material WHOLESALE_BLOCK = Material.GILDED_BLACKSTONE;

    /** A placed logistics block. */
    public static class Node {
        public final String world;
        public final int x, y, z;
        public final UUID owner;
        public String ownerName;
        public boolean notify = true;
        public boolean autoSell = true;
        public double lifetimeEarned;
        public long lastSale;
        /** Goods pulled in from the businesses, waiting to be sold or collected. */
        public final Map<Material, Integer> storage = new java.util.LinkedHashMap<>();
        /** Who the stored value belongs to: town name, or "" for the owner. */
        public final Map<String, Double> ledger = new java.util.LinkedHashMap<>();

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
    public int storageCap() {
        return Math.max(64, plugin.getConfig().getInt("logistics.storage-cap", 4096));
    }

    public int stored(Node node) {
        int total = 0;
        for (int v : node.storage.values()) total += v;
        return total;
    }

    public double storedValue(Node node) {
        double total = 0;
        for (Map.Entry<Material, Integer> e : node.storage.entrySet()) {
            total += plugin.sell().priceOf(e.getKey()) * e.getValue();
        }
        return total;
    }

    public double feePercent() {
        return Math.max(0, Math.min(50, plugin.getConfig().getDouble("logistics.fee-percent", 8.0)));
    }

    // ---- items ----
    public ItemStack createDistribution() {
        return build(DISTRIBUTION_BLOCK, "distribution",
                "<#5ad1e8><bold>Distribution Block</bold>",
                "<gray>Gathers from every business you own",
                "<gray>within <white>" + businessRadius() + "</white> blocks of it.",
                "",
                "<gray>Must be <white>touching</white> a Wholesale Block,",
                "<gray>or touching another Distribution Block",
                "<gray>that leads back to one.");
    }

    public ItemStack createWholesale() {
        return build(WHOLESALE_BLOCK, "wholesale",
                "<#f9d423><bold>Wholesale Block</bold>",
                "<gray>Sells everything its attached",
                "<gray>Distribution Blocks gather, every",
                "<white>" + intervalMinutes() + "</white> <gray>minutes.",
                "",
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

    /** Every placed logistics block, for the label pass. */
    public List<Node> allNodes() {
        List<Node> out = new ArrayList<>(distributors.values());
        out.addAll(wholesalers.values());
        return out;
    }

    public boolean isDistributorNode(Node node) {
        return distributors.containsKey(node.key());
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
    /**
     * Distribution blocks physically attached to the wholesaler. Chains outward,
     * so a line of touching distribution blocks all feed the same wholesaler.
     */
    public List<Node> linkedDistributors(Node wholesaler) {
        List<Node> found = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.Deque<int[]> queue = new java.util.ArrayDeque<>();
        queue.add(new int[]{wholesaler.x, wholesaler.y, wholesaler.z});
        seen.add(wholesaler.key());

        int[][] faces = {{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}};
        while (!queue.isEmpty() && found.size() < 64) {
            int[] at = queue.poll();
            for (int[] face : faces) {
                int nx = at[0] + face[0];
                int ny = at[1] + face[1];
                int nz = at[2] + face[2];
                String k = key(wholesaler.world, nx, ny, nz);
                if (!seen.add(k)) continue;
                Node node = distributors.get(k);
                if (node == null) continue;
                if (!node.owner.equals(wholesaler.owner)) continue;
                found.add(node);
                queue.add(new int[]{nx, ny, nz});
            }
        }
        return found;
    }

    /** The wholesaler a distribution block is attached to, if any. */
    public Node wholesalerFor(Node distributor) {
        for (Node w : wholesalers.values()) {
            if (!w.world.equals(distributor.world)) continue;
            if (!w.owner.equals(distributor.owner)) continue;
            for (Node linked : linkedDistributors(w)) {
                if (linked.key().equals(distributor.key())) return w;
            }
        }
        return null;
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

    /**
     * Drag everything the attached businesses have produced into the wholesale
     * block's own storage. Returns how many items moved.
     */
    public int pull(Node wholesaler) {
        int cap = storageCap();
        int room = cap - stored(wholesaler);
        if (room <= 0) return 0;
        int moved = 0;

        for (Node d : linkedDistributors(wholesaler)) {
            for (BusinessBlock b : linkedBusinesses(d)) {
                plugin.businesses().updateProduction(b);
                String destination = b.town() == null ? "" : b.town();
                moved += drain(b.storage(), wholesaler, room - moved, destination);
                if (moved >= room) break;
            }
            for (SpecialBusiness b : linkedSpecials(d)) {
                plugin.specialBusinesses().accrue(b);
                moved += drain(b.storage(), wholesaler, room - moved, "");
                if (moved >= room) break;
            }
        }
        if (moved > 0) {
            plugin.businesses().save();
            plugin.specialBusinesses().save();
            save();
        }
        return moved;
    }

    /** Move up to {@code room} items out of a business into the wholesaler. */
    private int drain(Map<Material, Integer> from, Node into, int room, String destination) {
        if (room <= 0) return 0;
        int moved = 0;
        for (Map.Entry<Material, Integer> e : new ArrayList<>(from.entrySet())) {
            if (moved >= room) break;
            int take = Math.min(e.getValue(), room - moved);
            if (take <= 0) continue;
            into.storage.merge(e.getKey(), take, Integer::sum);
            into.ledger.merge(destination, plugin.sell().priceOf(e.getKey()) * take, Double::sum);
            int left = e.getValue() - take;
            if (left <= 0) from.remove(e.getKey());
            else from.put(e.getKey(), left);
            moved += take;
        }
        return moved;
    }

    /** Value of everything waiting - both in storage and still in the businesses. */
    public double pendingValue(Node wholesaler) {
        double total = storedValue(wholesaler);
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

    /** Hand the stored goods to a player instead of selling them. */
    public int collect(Player player, Node wholesaler) {
        pull(wholesaler);
        int given = 0;
        for (Map.Entry<Material, Integer> e : new ArrayList<>(wholesaler.storage.entrySet())) {
            int amount = e.getValue();
            while (amount > 0) {
                int chunk = Math.min(e.getKey().getMaxStackSize(), amount);
                com.apollosmp.util.Items.give(player, new ItemStack(e.getKey(), chunk));
                amount -= chunk;
                given += chunk;
            }
        }
        wholesaler.storage.clear();
        wholesaler.ledger.clear();
        save();
        return given;
    }

    // ---- selling ----
    /** Pull anything outstanding, then sell the lot and pay whoever it's owed to. */
    public double sell(Node wholesaler, boolean announce) {
        pull(wholesaler);
        if (wholesaler.storage.isEmpty()) return 0;

        double gross = 0;
        int items = 0;
        for (Map.Entry<Material, Integer> e : wholesaler.storage.entrySet()) {
            gross += plugin.sell().priceOf(e.getKey()) * e.getValue();
            items += e.getValue();
        }
        wholesaler.storage.clear();
        if (items == 0) {
            wholesaler.ledger.clear();
            return 0;
        }

        double fee = gross * (feePercent() / 100.0);
        double net = gross - fee;

        // Split by whoever the goods were earned for.
        double ledgerTotal = 0;
        for (double v : wholesaler.ledger.values()) ledgerTotal += v;

        double toOwner = net;
        Map<String, Double> townPayouts = new java.util.LinkedHashMap<>();
        if (ledgerTotal > 0) {
            toOwner = 0;
            for (Map.Entry<String, Double> e : wholesaler.ledger.entrySet()) {
                double share = net * (e.getValue() / ledgerTotal);
                if (e.getKey().isEmpty()) {
                    toOwner += share;
                } else {
                    townPayouts.merge(e.getKey(), share, Double::sum);
                }
            }
        }
        wholesaler.ledger.clear();

        if (toOwner > 0) plugin.economy().deposit(wholesaler.owner, toOwner);
        for (Map.Entry<String, Double> e : townPayouts.entrySet()) {
            com.apollosmp.town.Town town = plugin.towns().townByName(e.getKey());
            if (town != null) {
                town.depositBank(e.getValue());
                plugin.towns().markDirty();
            } else {
                // Town vanished - don't lose the money.
                plugin.economy().deposit(wholesaler.owner, e.getValue());
                toOwner += e.getValue();
            }
        }

        wholesaler.lifetimeEarned += net;
        wholesaler.lastSale = System.currentTimeMillis();
        save();

        if (announce && wholesaler.notify) {
            Player online = plugin.getServer().getPlayer(wholesaler.owner);
            if (online != null) {
                StringBuilder msg = new StringBuilder("<#f9d423>Wholesale:</#f9d423> <gray>sold <white>"
                        + items + "</white> goods for <green>" + plugin.msg().money(net) + "</green>");
                if (!townPayouts.isEmpty()) {
                    msg.append(" <dark_gray>(");
                    if (toOwner > 0) msg.append("you ").append(plugin.msg().money(toOwner)).append(", ");
                    boolean first = true;
                    for (Map.Entry<String, Double> e : townPayouts.entrySet()) {
                        if (!first) msg.append(", ");
                        msg.append(e.getKey()).append(" ").append(plugin.msg().money(e.getValue()));
                        first = false;
                    }
                    msg.append(")</dark_gray>");
                } else {
                    msg.append(" <dark_gray>(after ").append((int) feePercent()).append("% fee)</dark_gray>");
                }
                plugin.msg().send(online, msg.toString());
            }
        }
        return net;
    }

    /** A little shine so the machines feel alive. */
    public void spawnParticles() {
        for (Node d : distributors.values()) {
            World world = plugin.getServer().getWorld(d.world);
            if (world == null || !world.isChunkLoaded(d.x >> 4, d.z >> 4)) continue;
            Location loc = new Location(world, d.x + 0.5, d.y + 1.1, d.z + 0.5);
            Particle dust = particle("SOUL_FIRE_FLAME", "FLAME");
            if (dust != null) world.spawnParticle(dust, loc, 2, 0.22, 0.1, 0.22, 0.001);

            Particle ring = particle("ENCHANT", "ENCHANTMENT_TABLE");
            if (ring != null) {
                double turn = (System.currentTimeMillis() % 4000L) / 4000.0 * Math.PI * 2;
                for (int i = 0; i < 3; i++) {
                    double angle = turn + (Math.PI * 2 * i / 3);
                    world.spawnParticle(ring,
                            loc.clone().add(Math.cos(angle) * 0.55, -0.3, Math.sin(angle) * 0.55),
                            1, 0, 0, 0, 0);
                }
            }
        }

        for (Node w : wholesalers.values()) {
            World world = plugin.getServer().getWorld(w.world);
            if (world == null || !world.isChunkLoaded(w.x >> 4, w.z >> 4)) continue;
            Location loc = new Location(world, w.x + 0.5, w.y + 1.1, w.z + 0.5);
            Particle gold = particle("WAX_ON", "HAPPY_VILLAGER");
            if (gold != null) world.spawnParticle(gold, loc, 4, 0.28, 0.15, 0.28, 0.01);

            // A brighter crown when there's money waiting to be made.
            if (!linkedDistributors(w).isEmpty()) {
                Particle crown = particle("END_ROD", "FLAME");
                if (crown != null) {
                    double turn = (System.currentTimeMillis() % 3000L) / 3000.0 * Math.PI * 2;
                    for (int i = 0; i < 4; i++) {
                        double angle = turn + (Math.PI * 2 * i / 4);
                        world.spawnParticle(crown,
                                loc.clone().add(Math.cos(angle) * 0.7, 0.25, Math.sin(angle) * 0.7),
                                1, 0, 0, 0, 0);
                    }
                }
            }
        }
    }

    private Particle particle(String... names) {
        for (String name : names) {
            try {
                return Particle.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // try the next name
            }
        }
        return null;
    }

    /** Timer entry point: gather constantly, sell on schedule. */
    public void tick() {
        long now = System.currentTimeMillis();
        long interval = intervalMinutes() * 60_000L;
        for (Node w : new ArrayList<>(wholesalers.values())) {
            pull(w);
            if (!w.autoSell) continue;
            if (w.lastSale == 0) {
                // Don't fire the instant a block is placed.
                w.lastSale = now;
                continue;
            }
            if (now - w.lastSale < interval) continue;
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
            cfg.set(base + ".autoSell", n.autoSell);
            List<String> stored = new ArrayList<>();
            for (Map.Entry<Material, Integer> e : n.storage.entrySet()) {
                stored.add(e.getKey().name() + "=" + e.getValue());
            }
            cfg.set(base + ".stored", stored);
            List<String> ledger = new ArrayList<>();
            for (Map.Entry<String, Double> e : n.ledger.entrySet()) {
                ledger.add(e.getKey() + "=" + e.getValue());
            }
            cfg.set(base + ".ledger", ledger);
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
                n.autoSell = cfg.getBoolean(base + ".autoSell", true);
                for (String entry : cfg.getStringList(base + ".ledger")) {
                    int split = entry.lastIndexOf('=');
                    if (split < 0) continue;
                    try {
                        n.ledger.put(entry.substring(0, split),
                                Double.parseDouble(entry.substring(split + 1)));
                    } catch (NumberFormatException ignored) {
                        // skip
                    }
                }
                for (String entry : cfg.getStringList(base + ".stored")) {
                    String[] pr = entry.split("=");
                    if (pr.length != 2) continue;
                    Material m = Material.matchMaterial(pr[0]);
                    if (m == null) continue;
                    try {
                        n.storage.put(m, Integer.parseInt(pr[1]));
                    } catch (NumberFormatException ignored) {
                        // skip
                    }
                }
                n.lifetimeEarned = cfg.getDouble(base + ".earned");
                n.lastSale = cfg.getLong(base + ".lastSale");
                into.put(n.key(), n);
            } catch (Exception ignored) {
                plugin.getLogger().warning("Skipped a malformed logistics node.");
            }
        }
    }
}
