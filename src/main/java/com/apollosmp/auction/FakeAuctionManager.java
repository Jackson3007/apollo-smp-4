package com.apollosmp.auction;

import com.apollosmp.ApolloSMP;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps the auction house looking busy on a quiet server by seeding a handful of
 * "activity" listings that behave exactly like real player listings.
 *
 * Design notes:
 *   - Seeded listings are marked fake (see {@link Listing#fake()}). They are never
 *     saved to disk and never returned to a mailbox, so they can't leak into real data.
 *   - Prices are always set ABOVE the item's own sell value, so a player can't buy one
 *     and immediately resell it to the server for a profit. Buying is a pure money sink.
 *   - Each listing is given a realistic age/expiry (as if posted in the last day or two),
 *     so it shows a believable "time left" and ages out naturally, and a fresh one takes
 *     its place. That churn is what makes the board look alive.
 */
public class FakeAuctionManager {

    /** How an item template turns into a real ItemStack. */
    private enum Kind { PLAIN, BOOK, GEAR }

    private record Template(String material, int minAmount, int maxAmount, Kind kind,
                            String enchant, int enchantLevel) {
        static Template plain(String mat, int min, int max) {
            return new Template(mat, min, max, Kind.PLAIN, null, 0);
        }
        static Template book(String enchant, int level) {
            return new Template("ENCHANTED_BOOK", 1, 1, Kind.BOOK, enchant, level);
        }
        static Template gear(String mat, String enchant, int level) {
            return new Template(mat, 1, 1, Kind.GEAR, enchant, level);
        }
    }

    // A believable spread of what players actually list on an economy server.
    private static final List<Template> TEMPLATES = List.of(
            Template.plain("DIAMOND", 16, 64),
            Template.plain("DIAMOND_BLOCK", 1, 6),
            Template.plain("NETHERITE_INGOT", 1, 3),
            Template.plain("NETHERITE_SCRAP", 2, 6),
            Template.plain("ANCIENT_DEBRIS", 2, 8),
            Template.plain("EMERALD", 16, 64),
            Template.plain("EMERALD_BLOCK", 1, 5),
            Template.plain("GOLD_BLOCK", 2, 10),
            Template.plain("IRON_BLOCK", 4, 16),
            Template.plain("GOLDEN_APPLE", 4, 16),
            Template.plain("ENCHANTED_GOLDEN_APPLE", 1, 2),
            Template.plain("TOTEM_OF_UNDYING", 1, 4),
            Template.plain("SHULKER_SHELL", 2, 8),
            Template.plain("NETHER_STAR", 1, 2),
            Template.plain("ELYTRA", 1, 1),
            Template.plain("EXPERIENCE_BOTTLE", 16, 64),
            Template.plain("BLAZE_ROD", 16, 48),
            Template.plain("ENDER_PEARL", 16, 32),
            Template.plain("HONEY_BLOCK", 8, 32),
            Template.plain("SEA_LANTERN", 16, 64),
            Template.book("mending", 1),
            Template.book("efficiency", 5),
            Template.book("unbreaking", 3),
            Template.book("fortune", 3),
            Template.book("protection", 4),
            Template.book("sharpness", 5),
            Template.book("feather_falling", 4),
            Template.book("silk_touch", 1),
            Template.gear("DIAMOND_PICKAXE", "efficiency", 5),
            Template.gear("DIAMOND_SWORD", "sharpness", 5),
            Template.gear("NETHERITE_PICKAXE", "efficiency", 5),
            Template.gear("DIAMOND_CHESTPLATE", "protection", 4)
    );

    private static final List<String> DEFAULT_NAMES = List.of(
            "Zephyr_Craft", "mango_miner", "PixelNomad", "AshfallV", "QuartzFox",
            "BrickByBrick", "lunar_dgot", "EmberWisp", "TangoV2", "SootySpud",
            "Craftwork_", "VelvetPine", "NovaDrift", "kelp_king", "GraniteGoose",
            "SilentBrook", "FrostyLoot", "cobble_cat", "MythicMoss", "DapperDodo");

    private final ApolloSMP plugin;

    public FakeAuctionManager(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    // ---- config ----
    private boolean enabled() { return plugin.getConfig().getBoolean("fake-auctions.enabled", true); }
    private int targetCount() { return Math.max(0, plugin.getConfig().getInt("fake-auctions.count", 10)); }
    private int churnPercent() {
        return Math.max(0, Math.min(100, plugin.getConfig().getInt("fake-auctions.churn-percent", 25)));
    }
    private double markupMin() { return plugin.getConfig().getDouble("fake-auctions.markup-min", 1.3); }
    private double markupMax() { return plugin.getConfig().getDouble("fake-auctions.markup-max", 1.9); }

    private List<String> names() {
        List<String> configured = plugin.getConfig().getStringList("fake-auctions.seller-names");
        return (configured == null || configured.isEmpty()) ? DEFAULT_NAMES : configured;
    }

    // ---- lifecycle ----

    /** Called on enable: wipe any strays and fill up to the target. */
    public void seed() {
        plugin.auctions().clearFakes();
        if (enabled()) topUp();
    }

    /** Fill back up to the target count. */
    public void topUp() {
        int target = targetCount();
        int guard = 0;
        while (plugin.auctions().fakeCount() < target && guard++ < 500) {
            Listing listing = makeListing();
            if (listing != null) plugin.auctions().injectFake(listing);
        }
    }

    /** Timer tick: age out old listings, occasionally retire one early, then refill. */
    public void tick() {
        if (!enabled()) {
            plugin.auctions().clearFakes();
            return;
        }
        for (Listing l : plugin.auctions().fakes()) {
            if (l.isExpired()) plugin.auctions().removeFake(l.id());
        }
        List<Listing> current = plugin.auctions().fakes();
        if (!current.isEmpty() && ThreadLocalRandom.current().nextInt(100) < churnPercent()) {
            Listing pick = current.get(ThreadLocalRandom.current().nextInt(current.size()));
            plugin.auctions().removeFake(pick.id());
        }
        topUp();
    }

    // ---- building a single listing ----

    private Listing makeListing() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Template template = TEMPLATES.get(rng.nextInt(TEMPLATES.size()));
        ItemStack item = build(template);
        if (item == null) return null; // material not present on this version - skip

        double value = plugin.sell().valueOf(item);
        double markup = markupMin() + rng.nextDouble() * Math.max(0, markupMax() - markupMin());
        double price = Math.max(value * markup, plugin.getConfig().getDouble("auction-house.min-listing-price", 1.0));
        // A hard floor so an item with no sell value still lists for something sensible.
        if (price < 25) price = 25 + rng.nextInt(75);
        price = niceRound(price);

        String seller = names().get(rng.nextInt(names().size()));
        UUID sellerId = UUID.nameUUIDFromBytes(("ApolloFakeSeller:" + seller).getBytes(StandardCharsets.UTF_8));

        long now = System.currentTimeMillis();
        long ageMs = (long) (rng.nextDouble() * 40.0 * 3600_000L); // posted 0-40h ago
        long createdAt = now - ageMs;
        long expiresAt = createdAt + 48L * 3600_000L;             // standard 48h lifetime

        Listing listing = new Listing(UUID.randomUUID(), sellerId, seller, item, price, createdAt, expiresAt);
        listing.setFake(true);
        return listing;
    }

    private ItemStack build(Template template) {
        Material material = Material.matchMaterial(template.material());
        if (material == null) return null;
        int amount = template.minAmount() == template.maxAmount()
                ? template.minAmount()
                : ThreadLocalRandom.current().nextInt(template.minAmount(), template.maxAmount() + 1);
        ItemStack item = new ItemStack(material, Math.max(1, amount));

        switch (template.kind()) {
            case BOOK -> {
                Enchantment ench = enchant(template.enchant());
                if (ench != null && item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
                    meta.addStoredEnchant(ench, template.enchantLevel(), true);
                    item.setItemMeta(meta);
                }
            }
            case GEAR -> {
                Enchantment ench = enchant(template.enchant());
                if (ench != null) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.addEnchant(ench, template.enchantLevel(), true);
                        item.setItemMeta(meta);
                    }
                }
            }
            case PLAIN -> { /* nothing extra */ }
        }
        return item;
    }

    /** Resolve an enchantment by its vanilla key, tolerating version differences. */
    private Enchantment enchant(String key) {
        if (key == null) return null;
        try {
            return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
        } catch (Throwable ex) {
            return null;
        }
    }

    /** Round to a tidy, human-looking asking price. */
    private double niceRound(double value) {
        if (value >= 10000) return Math.round(value / 500.0) * 500.0;
        if (value >= 1000) return Math.round(value / 100.0) * 100.0;
        if (value >= 100) return Math.round(value / 25.0) * 25.0;
        return Math.round(value / 5.0) * 5.0;
    }
}
