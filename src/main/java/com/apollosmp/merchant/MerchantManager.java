package com.apollosmp.merchant;

import com.apollosmp.ApolloSMP;
import com.apollosmp.invest.Business;
import com.apollosmp.invest.Businesses;
import com.apollosmp.util.Items;
import com.apollosmp.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** A travelling merchant whose three offers rotate once a day. */
public class MerchantManager {

    public enum BuyResult { SUCCESS, NO_FUNDS, ALREADY_BOUGHT, GONE }

    private static final String[] SPAWNER_MOBS =
            {"ZOMBIE", "SKELETON", "SPIDER", "CAVE_SPIDER", "BLAZE", "ENDERMAN"};
    private static final Material[] JUNK =
            {Material.DIRT, Material.ROTTEN_FLESH, Material.POISONOUS_POTATO,
             Material.COBBLESTONE, Material.STICK, Material.WHEAT_SEEDS};

    private final ApolloSMP plugin;
    private final File file;
    private final NamespacedKey toolKey;
    private final NamespacedKey expiryKey;

    private String stockDate = "";
    private final List<MerchantOffer> offers = new ArrayList<>();
    /** "date:uuid:index" for offers already bought. */
    private final Set<String> purchases = new LinkedHashSet<>();

    public MerchantManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "merchant.yml");
        this.toolKey = new NamespacedKey(plugin, "apollo_merchant_tool");
        this.expiryKey = new NamespacedKey(plugin, "apollo_expires");
        load();
        refreshIfNeeded();
    }

    public NamespacedKey toolKey() { return toolKey; }
    public NamespacedKey expiryKey() { return expiryKey; }

    private String today() { return LocalDate.now().toString(); }

    /** Roll a new set of offers when the day changes. */
    public void refreshIfNeeded() {
        if (today().equals(stockDate) && offers.size() == 3) return;
        stockDate = today();
        offers.clear();
        // Same seed all day, so every player sees the same stall.
        Random random = new Random(stockDate.hashCode() * 31L
                + plugin.getConfig().getLong("merchant.seed", 7L));
        for (int i = 0; i < 3; i++) offers.add(roll(random));
        purchases.removeIf(p -> !p.startsWith(stockDate + ":"));
        save();
        plugin.getLogger().info("Merchant stock rolled for " + stockDate + ".");
    }

    private MerchantOffer roll(Random random) {
        int weight = random.nextInt(100);
        double scale = plugin.getConfig().getDouble("merchant.price-multiplier", 1.0);

        if (weight < 12) {
            String mob = SPAWNER_MOBS[random.nextInt(SPAWNER_MOBS.length)];
            return new MerchantOffer(MerchantOffer.Kind.SPAWNER,
                    round(250_000 + random.nextInt(200_000), scale), mob);
        }
        if (weight < 27) {
            return new MerchantOffer(MerchantOffer.Kind.DRILL,
                    round(160_000 + random.nextInt(80_000), scale), null);
        }
        if (weight < 42) {
            return new MerchantOffer(MerchantOffer.Kind.TREE_AXE,
                    round(140_000 + random.nextInt(70_000), scale), null);
        }
        if (weight < 57) {
            return new MerchantOffer(MerchantOffer.Kind.GOD_APPLE,
                    round(110_000 + random.nextInt(60_000), scale), null);
        }
        if (weight < 72) {
            List<Business> all = new ArrayList<>(Businesses.all());
            if (!all.isEmpty()) {
                Business pick = all.get(random.nextInt(all.size()));
                return new MerchantOffer(MerchantOffer.Kind.BUSINESS,
                        round(200_000 + random.nextInt(150_000), scale), pick.id());
            }
        }
        if (weight < 85) {
            return new MerchantOffer(MerchantOffer.Kind.TOTEM,
                    round(90_000 + random.nextInt(50_000), scale), null);
        }
        Material junk = JUNK[random.nextInt(JUNK.length)];
        return new MerchantOffer(MerchantOffer.Kind.JUNK,
                round(120_000 + random.nextInt(180_000), scale), junk.name());
    }

    private double round(double base, double scale) {
        return Math.round(base * scale / 1000.0) * 1000.0;
    }

    /** Admin testing: roll new stock immediately. */
    public void forceReroll() {
        stockDate = "";
        offers.clear();
        refreshIfNeeded();
    }

    /** Admin testing: let everyone buy again today. */
    public void clearPurchases() {
        purchases.clear();
        save();
    }

    public String stockDate() { return stockDate; }

    public List<MerchantOffer> offers() {
        refreshIfNeeded();
        return new ArrayList<>(offers);
    }

    public boolean hasBought(Player player, int index) {
        return purchases.contains(stockDate + ":" + player.getUniqueId() + ":" + index);
    }

    /** Milliseconds until the stall changes over. */
    public long millisUntilRotation() {
        LocalDateTime tomorrow = LocalDate.now().plusDays(1).atStartOfDay();
        long next = tomorrow.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return Math.max(0, next - System.currentTimeMillis());
    }

    public BuyResult buy(Player player, int index) {
        refreshIfNeeded();
        if (index < 0 || index >= offers.size()) return BuyResult.GONE;
        if (hasBought(player, index)) return BuyResult.ALREADY_BOUGHT;

        MerchantOffer offer = offers.get(index);
        if (!plugin.economy().has(player.getUniqueId(), offer.price())) return BuyResult.NO_FUNDS;

        ItemStack item = build(offer);
        if (item == null) return BuyResult.GONE;

        plugin.economy().withdraw(player.getUniqueId(), offer.price());
        purchases.add(stockDate + ":" + player.getUniqueId() + ":" + index);
        save();
        Items.give(player, item);
        return BuyResult.SUCCESS;
    }

    // ---- item building ----
    public ItemStack build(MerchantOffer offer) {
        return switch (offer.kind()) {
            case SPAWNER -> buildSpawner(offer.data());
            case DRILL -> buildTool(true);
            case TREE_AXE -> buildTool(false);
            case GOD_APPLE -> new ItemStack(Material.ENCHANTED_GOLDEN_APPLE);
            case TOTEM -> new ItemStack(Material.TOTEM_OF_UNDYING);
            case BUSINESS -> {
                Business def = Businesses.get(offer.data());
                yield def == null ? null : plugin.businesses().createItem(def, 3);
            }
            case JUNK -> {
                Material m = Material.matchMaterial(offer.data() == null ? "DIRT" : offer.data());
                yield new ItemStack(m == null ? Material.DIRT : m);
            }
        };
    }

    private ItemStack buildSpawner(String mob) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String label = pretty(mob);
            meta.displayName(Msg.lore("<#e94fd0>" + label + " Spawner</#e94fd0>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Msg.lore("<gray>Spawns: <white>" + label + "</white>"));
            lore.add(Msg.lore("<dark_gray>Bought from the travelling merchant"));
            meta.lore(lore);
            try {
                EntityType.valueOf(mob);
                meta.getPersistentDataContainer().set(
                        new NamespacedKey(plugin, "apollo_spawner_type"),
                        PersistentDataType.STRING, mob);
            } catch (IllegalArgumentException ignored) {
                // leave it blank rather than fail
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /** The 48-hour tools. drill = 3x3 pickaxe, otherwise the tree-feller axe. */
    private ItemStack buildTool(boolean drill) {
        long hours = plugin.getConfig().getLong("merchant.tool-hours", 48);
        long expiry = System.currentTimeMillis() + hours * 3600_000L;

        ItemStack item = Items.of(drill ? Material.NETHERITE_PICKAXE : Material.NETHERITE_AXE)
                .name(drill
                        ? "<gradient:#f9d423:#ff4e50><bold>Merchant's Drill</bold></gradient>"
                        : "<gradient:#f9d423:#ff4e50><bold>Merchant's Feller</bold></gradient>")
                .lore(drill
                                ? "<gray>Breaks a 3x3 face of blocks."
                                : "<gray>Fells an entire tree in one swing.",
                        "<dark_gray>Bought from the travelling merchant",
                        "<red>Crumbles in " + hours + "h 0m</red>")
                .glow(true).hideAttributes().build();

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING,
                    drill ? "drill" : "feller");
            meta.getPersistentDataContainer().set(expiryKey, PersistentDataType.LONG, expiry);
            meta.setUnbreakable(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    public String toolType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer()
                .get(toolKey, PersistentDataType.STRING);
    }

    public long expiryOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0L;
        Long value = item.getItemMeta().getPersistentDataContainer()
                .get(expiryKey, PersistentDataType.LONG);
        return value == null ? 0L : value;
    }

    public String pretty(String raw) {
        if (raw == null) return "Unknown";
        String[] words = raw.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("date", stockDate);
        List<String> raw = new ArrayList<>();
        for (MerchantOffer offer : offers) raw.add(offer.serialize());
        cfg.set("offers", raw);
        cfg.set("purchases", new ArrayList<>(purchases));
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save merchant.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        stockDate = cfg.getString("date", "");
        for (String raw : cfg.getStringList("offers")) {
            MerchantOffer offer = MerchantOffer.deserialize(raw);
            if (offer != null) offers.add(offer);
        }
        purchases.addAll(cfg.getStringList("purchases"));
    }
}
