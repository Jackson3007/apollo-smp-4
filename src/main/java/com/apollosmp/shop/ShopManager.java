package com.apollosmp.shop;

import com.apollosmp.ApolloSMP;
import com.apollosmp.town.Town;
import com.apollosmp.util.Items;
import com.apollosmp.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Market stalls: a block a town places to sell goods to anyone passing through.
 * Residents stock it, travellers buy, and the money lands in the town bank.
 */
public class ShopManager {

    public static final Material STALL_BLOCK = Material.BARREL;
    public static final int MAX_OFFERS = 27;

    /** One thing a stall is selling. */
    public static class Offer {
        public Material material;
        public double price;   // per item
        public int stock;

        public Offer(Material material, double price, int stock) {
            this.material = material;
            this.price = price;
            this.stock = stock;
        }
    }

    /** A placed stall. */
    public static class Stall {
        public final String world;
        public final int x, y, z;
        public final String town;
        public final List<Offer> offers = new ArrayList<>();
        public double earned;

        Stall(String world, int x, int y, int z, String town) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.town = town;
        }

        public String key() { return ShopManager.key(world, x, y, z); }
    }

    private final ApolloSMP plugin;
    private final File file;
    private final NamespacedKey blockKey;
    private final Map<String, Stall> stalls = new ConcurrentHashMap<>();

    public ShopManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shops.yml");
        this.blockKey = new NamespacedKey(plugin, "apollo_town_stall");
        load();
    }

    public static String key(String world, int x, int y, int z) {
        return world + "," + x + "," + y + "," + z;
    }

    public static String key(Location loc) {
        return key(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public double stallPrice() { return plugin.getConfig().getDouble("towns.shop.stall-price", 500.0); }

    /** Allies pay less - one of the perks of an alliance. */
    public double allyDiscount() {
        return Math.max(0, Math.min(50, plugin.getConfig().getDouble("towns.shop.ally-discount", 10.0)));
    }

    // ---- the block ----
    public ItemStack createBlock() {
        ItemStack item = new ItemStack(STALL_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.lore("<#5ad1e8><bold>Market Stall</bold>"));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(Msg.lore("<gray>Place it in your town and stock it."));
            lore.add(Msg.lore("<gray>Anyone can buy; the money goes"));
            lore.add(Msg.lore("<gray>into your town bank."));
            lore.add(Msg.lore(""));
            lore.add(Msg.lore("<yellow>Right-click once placed."));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(blockKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isStallItem(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer()
                        .has(blockKey, PersistentDataType.BYTE);
    }

    public Stall at(Location loc) { return stalls.get(key(loc)); }

    public List<Stall> allStalls() { return new ArrayList<>(stalls.values()); }

    public int stockCount(Stall stall) {
        int total = 0;
        for (Offer o : stall.offers) total += o.stock;
        return total;
    }

    /** A quiet shimmer so stalls are findable. */
    public void spawnParticles() {
        for (Stall stall : stalls.values()) {
            org.bukkit.World world = plugin.getServer().getWorld(stall.world);
            if (world == null) continue;
            if (!world.isChunkLoaded(stall.x >> 4, stall.z >> 4)) continue;
            Location loc = new Location(world, stall.x + 0.5, stall.y + 1.1, stall.z + 0.5);

            org.bukkit.Particle glow = particle("WAX_ON", "HAPPY_VILLAGER");
            if (glow != null) world.spawnParticle(glow, loc, 3, 0.25, 0.12, 0.25, 0.01);

            if (!stall.offers.isEmpty()) {
                org.bukkit.Particle ring = particle("ENCHANT", "ENCHANTMENT_TABLE");
                if (ring != null) {
                    double turn = (System.currentTimeMillis() % 5000L) / 5000.0 * Math.PI * 2;
                    for (int i = 0; i < 3; i++) {
                        double angle = turn + (Math.PI * 2 * i / 3);
                        world.spawnParticle(ring,
                                loc.clone().add(Math.cos(angle) * 0.6, -0.25, Math.sin(angle) * 0.6),
                                1, 0, 0, 0, 0);
                    }
                }
            }
        }
    }

    private org.bukkit.Particle particle(String... names) {
        for (String name : names) {
            try {
                return org.bukkit.Particle.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // try the next
            }
        }
        return null;
    }

    public boolean place(Location loc, Player player) {
        Town town = plugin.towns().getTownAtLoc(loc);
        Town mine = plugin.towns().getTownOf(player.getUniqueId());
        if (town == null || mine == null || !town.name().equalsIgnoreCase(mine.name())) {
            plugin.msg().send(player, "<red>A stall has to stand on your own town's land.");
            return false;
        }
        Stall stall = new Stall(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(),
                loc.getBlockZ(), town.name());
        stalls.put(stall.key(), stall);
        save();
        plugin.msg().send(player, "<green>Stall opened. Right-click it to stock the shelves.");
        return true;
    }

    public void remove(Location loc) {
        stalls.remove(key(loc));
        save();
    }

    // ---- stocking ----
    /** Add what the player is holding to the stall at a set price. */
    public boolean stock(Player player, Stall stall, double pricePerItem) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            plugin.msg().send(player, "<red>Hold what you want to sell first.");
            return false;
        }
        if (plugin.worthTags() != null) plugin.worthTags().strip(held);

        Offer existing = null;
        for (Offer o : stall.offers) {
            if (o.material == held.getType()) { existing = o; break; }
        }
        if (existing == null && stall.offers.size() >= MAX_OFFERS) {
            plugin.msg().send(player, "<red>This stall is already selling " + MAX_OFFERS + " things.");
            return false;
        }

        int amount = held.getAmount();
        if (existing == null) {
            stall.offers.add(new Offer(held.getType(), pricePerItem, amount));
        } else {
            existing.stock += amount;
            existing.price = pricePerItem;
        }
        player.getInventory().setItemInMainHand(null);
        save();

        plugin.msg().send(player, "<green>Stocked <white>" + amount + "x "
                + Items.pretty(held.getType()) + "</white> at <#f9d423>"
                + plugin.msg().money(pricePerItem) + "</#f9d423> each.");
        return true;
    }

    /** Take unsold stock back off the shelf. */
    public boolean unstock(Player player, Stall stall, int index) {
        if (index < 0 || index >= stall.offers.size()) return false;
        Offer offer = stall.offers.remove(index);
        int left = offer.stock;
        while (left > 0) {
            int chunk = Math.min(offer.material.getMaxStackSize(), left);
            Items.give(player, new ItemStack(offer.material, chunk));
            left -= chunk;
        }
        save();
        plugin.msg().send(player, "<yellow>Pulled <white>" + offer.stock + "x "
                + Items.pretty(offer.material) + "</white> off the shelf.");
        return true;
    }

    /** What this player pays per item, after any ally discount. */
    public double priceFor(Player buyer, Stall stall, Offer offer) {
        Town theirs = plugin.towns().getTownOf(buyer.getUniqueId());
        if (theirs != null && plugin.diplomacy() != null
                && plugin.diplomacy().allied(theirs.name(), stall.town)) {
            return offer.price * (1 - allyDiscount() / 100.0);
        }
        return offer.price;
    }

    /** Buy some of an offer. */
    public boolean buy(Player buyer, Stall stall, int index, int wanted) {
        if (index < 0 || index >= stall.offers.size()) return false;
        Offer offer = stall.offers.get(index);
        if (offer.stock <= 0) {
            plugin.msg().send(buyer, "<gray>That's sold out.");
            return false;
        }
        int amount = Math.min(wanted, offer.stock);
        double each = priceFor(buyer, stall, offer);
        double total = each * amount;

        if (!plugin.economy().has(buyer.getUniqueId(), total)) {
            // Buy as many as they can afford instead of failing outright.
            amount = each <= 0 ? 0 : (int) Math.floor(
                    plugin.economy().getBalance(buyer.getUniqueId()) / each);
            amount = Math.min(amount, offer.stock);
            if (amount <= 0) {
                plugin.msg().send(buyer, "<red>You can't afford any of those.");
                return false;
            }
            total = each * amount;
        }

        plugin.economy().withdraw(buyer.getUniqueId(), total);
        Town town = plugin.towns().townByName(stall.town);
        if (town != null) {
            town.depositBank(total);
            plugin.towns().markDirty();
        }
        stall.earned += total;
        offer.stock -= amount;
        if (offer.stock <= 0) stall.offers.remove(index);
        save();

        int give = amount;
        while (give > 0) {
            int chunk = Math.min(offer.material.getMaxStackSize(), give);
            Items.give(buyer, new ItemStack(offer.material, chunk));
            give -= chunk;
        }
        plugin.msg().send(buyer, "<green>Bought <white>" + amount + "x "
                + Items.pretty(offer.material) + "</white> for <#f9d423>"
                + plugin.msg().money(total) + "</#f9d423>.");
        return true;
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        int i = 0;
        for (Stall stall : stalls.values()) {
            String base = "stalls." + i++;
            cfg.set(base + ".world", stall.world);
            cfg.set(base + ".x", stall.x);
            cfg.set(base + ".y", stall.y);
            cfg.set(base + ".z", stall.z);
            cfg.set(base + ".town", stall.town);
            cfg.set(base + ".earned", stall.earned);
            List<String> offers = new ArrayList<>();
            for (Offer o : stall.offers) {
                offers.add(o.material.name() + ";" + o.price + ";" + o.stock);
            }
            cfg.set(base + ".offers", offers);
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save shops.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("stalls");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            String base = "stalls." + id;
            try {
                Stall stall = new Stall(cfg.getString(base + ".world"), cfg.getInt(base + ".x"),
                        cfg.getInt(base + ".y"), cfg.getInt(base + ".z"), cfg.getString(base + ".town"));
                stall.earned = cfg.getDouble(base + ".earned");
                for (String raw : cfg.getStringList(base + ".offers")) {
                    String[] parts = raw.split(";");
                    if (parts.length != 3) continue;
                    Material m = Material.matchMaterial(parts[0]);
                    if (m == null) continue;
                    stall.offers.add(new Offer(m, Double.parseDouble(parts[1]),
                            Integer.parseInt(parts[2])));
                }
                stalls.put(stall.key(), stall);
            } catch (Exception ignored) {
                plugin.getLogger().warning("Skipped a malformed stall: " + id);
            }
        }
    }
}
