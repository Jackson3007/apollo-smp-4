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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Keeps the auction house looking busy by seeding a large pool of listings from
 * believable "player" names. They behave exactly like real player listings.
 *
 * Design goals (tuned to feel like a real, active server):
 *   - A big, varied pool (hundreds of listings) from junk (cobble, dirt, rotten
 *     flesh) through mid-tier gear up to, rarely, premium items.
 *   - Only a small handful of genuinely good items exist at once (see epic-max), so
 *     the market isn't flooded with elytras and netherite.
 *   - Everything is priced high, the way real players over-price - nothing is ever
 *     cheap, and every price is ALWAYS above the item's own sell value, so nobody
 *     can buy a fake listing and flip it to the server for profit.
 *   - Listings age out over a day or two and are replaced, so the board churns.
 */
public class FakeAuctionManager {

    private enum Kind { PLAIN, BOOK, GEAR }
    private enum Tier { COMMON, UNCOMMON, RARE, EPIC }

    private record Template(String material, int minAmount, int maxAmount,
                            Kind kind, String enchant, int enchantLevel, Tier tier) {
        static Template c(String m, int a, int b) { return new Template(m, a, b, Kind.PLAIN, null, 0, Tier.COMMON); }
        static Template u(String m, int a, int b) { return new Template(m, a, b, Kind.PLAIN, null, 0, Tier.UNCOMMON); }
        static Template r(String m, int a, int b) { return new Template(m, a, b, Kind.PLAIN, null, 0, Tier.RARE); }
        static Template e(String m, int a, int b) { return new Template(m, a, b, Kind.PLAIN, null, 0, Tier.EPIC); }
        static Template book(Tier t, String en, int lv) { return new Template("ENCHANTED_BOOK", 1, 1, Kind.BOOK, en, lv, t); }
        static Template gear(Tier t, String m, String en, int lv) { return new Template(m, 1, 1, Kind.GEAR, en, lv, t); }
    }

    private static final List<Template> TEMPLATES = List.of(
            // ---- COMMON: junk & cheap-to-farm (still priced high, like real sellers) ----
            Template.c("COBBLESTONE", 32, 64), Template.c("DIRT", 32, 64), Template.c("GRAVEL", 16, 64),
            Template.c("SAND", 32, 64), Template.c("RED_SAND", 16, 48), Template.c("GRANITE", 16, 64),
            Template.c("DIORITE", 16, 64), Template.c("ANDESITE", 16, 64), Template.c("NETHERRACK", 32, 64),
            Template.c("OAK_LOG", 16, 64), Template.c("SPRUCE_LOG", 16, 64), Template.c("BIRCH_LOG", 16, 64),
            Template.c("OAK_PLANKS", 32, 64), Template.c("STICK", 16, 64), Template.c("TORCH", 16, 64),
            Template.c("COAL", 16, 64), Template.c("CHARCOAL", 16, 64), Template.c("RAW_IRON", 8, 48),
            Template.c("RAW_COPPER", 16, 64), Template.c("IRON_NUGGET", 16, 64), Template.c("WHEAT", 16, 64),
            Template.c("CARROT", 16, 64), Template.c("POTATO", 16, 64), Template.c("SUGAR_CANE", 16, 64),
            Template.c("BAMBOO", 16, 64), Template.c("KELP", 16, 64), Template.c("CACTUS", 16, 64),
            Template.c("PUMPKIN", 8, 32), Template.c("MELON_SLICE", 16, 64), Template.c("ROTTEN_FLESH", 16, 64),
            Template.c("BONE", 16, 64), Template.c("STRING", 16, 64), Template.c("GUNPOWDER", 8, 48),
            Template.c("SPIDER_EYE", 8, 32), Template.c("WHEAT_SEEDS", 16, 64), Template.c("EGG", 8, 16),
            Template.c("FEATHER", 16, 64), Template.c("LEATHER", 8, 48), Template.c("COOKED_BEEF", 16, 64),
            Template.c("BREAD", 16, 64), Template.c("APPLE", 16, 64), Template.c("FLINT", 16, 64),
            Template.c("CLAY_BALL", 16, 64), Template.c("DRIED_KELP", 16, 64), Template.c("WHITE_WOOL", 16, 64),
            Template.c("ICE", 16, 64), Template.c("SNOWBALL", 16, 64), Template.c("PAPER", 16, 64),
            Template.c("GLASS", 16, 64), Template.c("SLIME_BALL", 8, 32), Template.c("INK_SAC", 8, 32),

            // ---- UNCOMMON: mid-tier metals, blocks and useful bits ----
            Template.u("IRON_INGOT", 8, 64), Template.u("GOLD_INGOT", 8, 64), Template.u("COPPER_INGOT", 16, 64),
            Template.u("IRON_BLOCK", 2, 16), Template.u("GOLD_BLOCK", 2, 12), Template.u("COPPER_BLOCK", 4, 16),
            Template.u("COAL_BLOCK", 4, 32), Template.u("REDSTONE", 16, 64), Template.u("REDSTONE_BLOCK", 4, 16),
            Template.u("LAPIS_LAZULI", 16, 64), Template.u("QUARTZ", 16, 64), Template.u("GLOWSTONE", 8, 32),
            Template.u("OBSIDIAN", 8, 32), Template.u("ENDER_PEARL", 8, 16), Template.u("BLAZE_ROD", 8, 32),
            Template.u("HONEY_BOTTLE", 8, 16), Template.u("HONEYCOMB", 8, 32), Template.u("AMETHYST_SHARD", 8, 32),
            Template.u("PRISMARINE_SHARD", 8, 32), Template.u("PRISMARINE_CRYSTALS", 8, 32), Template.u("SEA_LANTERN", 4, 16),
            Template.u("GLOWSTONE_DUST", 16, 64), Template.u("EMERALD", 8, 32), Template.u("BOOKSHELF", 4, 16),
            Template.u("ANVIL", 1, 3), Template.u("ENCHANTING_TABLE", 1, 2), Template.u("NAME_TAG", 1, 4),
            Template.u("SADDLE", 1, 3), Template.u("GOLDEN_APPLE", 2, 8), Template.u("EXPERIENCE_BOTTLE", 16, 64),
            Template.u("TNT", 4, 32), Template.u("IRON_HORSE_ARMOR", 1, 2), Template.u("GOLDEN_CARROT", 16, 64),
            Template.u("FIREWORK_ROCKET", 16, 64), Template.u("SLIME_BLOCK", 4, 16), Template.u("HAY_BLOCK", 8, 32),

            // ---- RARE: diamonds, enchanted gear/books, shulkers ----
            Template.r("DIAMOND", 8, 48), Template.r("DIAMOND_BLOCK", 1, 6), Template.r("EMERALD_BLOCK", 1, 6),
            Template.r("NETHERITE_SCRAP", 1, 6), Template.r("ANCIENT_DEBRIS", 2, 8), Template.r("SHULKER_BOX", 1, 4),
            Template.r("SPONGE", 4, 16), Template.r("HEART_OF_THE_SEA", 1, 2), Template.r("DIAMOND_HORSE_ARMOR", 1, 2),
            Template.r("TRIDENT", 1, 1), Template.r("QUARTZ_BLOCK", 8, 32),
            Template.gear(Tier.RARE, "DIAMOND_PICKAXE", "efficiency", 5),
            Template.gear(Tier.RARE, "DIAMOND_SWORD", "sharpness", 5),
            Template.gear(Tier.RARE, "DIAMOND_AXE", "efficiency", 5),
            Template.gear(Tier.RARE, "DIAMOND_CHESTPLATE", "protection", 4),
            Template.gear(Tier.RARE, "DIAMOND_HELMET", "protection", 4),
            Template.gear(Tier.RARE, "DIAMOND_LEGGINGS", "protection", 4),
            Template.gear(Tier.RARE, "DIAMOND_BOOTS", "feather_falling", 4),
            Template.gear(Tier.RARE, "BOW", "power", 5),
            Template.book(Tier.RARE, "efficiency", 5), Template.book(Tier.RARE, "unbreaking", 3),
            Template.book(Tier.RARE, "fortune", 3), Template.book(Tier.RARE, "protection", 4),
            Template.book(Tier.RARE, "sharpness", 5), Template.book(Tier.RARE, "feather_falling", 4),
            Template.book(Tier.RARE, "silk_touch", 1), Template.book(Tier.RARE, "power", 5),
            Template.book(Tier.RARE, "looting", 3), Template.book(Tier.RARE, "mending", 1),

            // ---- EPIC: the genuinely good stuff (kept scarce by epic-max) ----
            Template.e("ELYTRA", 1, 1), Template.e("ENCHANTED_GOLDEN_APPLE", 1, 3),
            Template.e("TOTEM_OF_UNDYING", 1, 3), Template.e("NETHERITE_INGOT", 1, 3),
            Template.e("NETHERITE_BLOCK", 1, 1), Template.e("NETHER_STAR", 1, 2),
            Template.e("BEACON", 1, 1), Template.e("DRAGON_HEAD", 1, 1), Template.e("CONDUIT", 1, 1),
            Template.gear(Tier.EPIC, "NETHERITE_PICKAXE", "efficiency", 5),
            Template.gear(Tier.EPIC, "NETHERITE_SWORD", "sharpness", 5),
            Template.gear(Tier.EPIC, "NETHERITE_AXE", "efficiency", 5),
            Template.gear(Tier.EPIC, "NETHERITE_CHESTPLATE", "protection", 4),
            Template.gear(Tier.EPIC, "NETHERITE_HELMET", "protection", 4),
            Template.gear(Tier.EPIC, "NETHERITE_LEGGINGS", "protection", 4),
            Template.gear(Tier.EPIC, "NETHERITE_BOOTS", "protection", 4)
    );

    private static final List<String> DEFAULT_NAMES = List.of(
            "Zephyr_Craft", "mango_miner", "PixelNomad", "AshfallV", "QuartzFox",
            "BrickByBrick", "lunar_dgot", "EmberWisp", "TangoV2", "SootySpud",
            "Craftwork_", "VelvetPine", "NovaDrift", "kelp_king", "GraniteGoose",
            "SilentBrook", "FrostyLoot", "cobble_cat", "MythicMoss", "DapperDodo",
            "vexil", "OreGoblin", "hollowB", "SunkenRelic", "PebbleDash",
            "trickle_", "GildedYak", "MossyStone42", "driftwood_", "CoalCanary",
            "SaltyPixel", "TinkerToad", "WillowByte", "OpalOtter", "grindset_",
            "BogTrotter", "CinderCub", "LacyFern", "TumbleweedTom", "RuneWisp");

    /** Materials that count toward the "premium items" cap. */
    private final Set<Material> premiumMaterials = new HashSet<>();
    private final Map<Tier, List<Template>> byTier = new EnumMap<>(Tier.class);

    private final ApolloSMP plugin;

    public FakeAuctionManager(ApolloSMP plugin) {
        this.plugin = plugin;
        for (Tier t : Tier.values()) byTier.put(t, new ArrayList<>());
        for (Template t : TEMPLATES) {
            byTier.get(t.tier()).add(t);
            if (t.tier() == Tier.EPIC) {
                Material m = Material.matchMaterial(t.material());
                if (m != null) premiumMaterials.add(m);
            }
        }
    }

    // ---- config ----
    private boolean enabled() { return plugin.getConfig().getBoolean("fake-auctions.enabled", true); }
    private int targetCount() { return Math.max(0, plugin.getConfig().getInt("fake-auctions.count", 800)); }
    private int epicMax() { return Math.max(0, Math.min(20, plugin.getConfig().getInt("fake-auctions.epic-max", 5))); }
    private int churnPercent() {
        return Math.max(0, Math.min(100, plugin.getConfig().getInt("fake-auctions.churn-percent", 15)));
    }
    private double markupMin() { return plugin.getConfig().getDouble("fake-auctions.markup-min", 2.0); }
    private double markupMax() { return plugin.getConfig().getDouble("fake-auctions.markup-max", 4.0); }
    private double floor(Tier tier) {
        return switch (tier) {
            case COMMON -> plugin.getConfig().getDouble("fake-auctions.floor-common", 100.0);
            case UNCOMMON -> plugin.getConfig().getDouble("fake-auctions.floor-uncommon", 500.0);
            case RARE -> plugin.getConfig().getDouble("fake-auctions.floor-rare", 2500.0);
            case EPIC -> plugin.getConfig().getDouble("fake-auctions.floor-epic", 12000.0);
        };
    }

    private List<String> names() {
        List<String> configured = plugin.getConfig().getStringList("fake-auctions.seller-names");
        return (configured == null || configured.isEmpty()) ? DEFAULT_NAMES : configured;
    }

    // ---- lifecycle ----
    public void seed() {
        plugin.auctions().clearFakes();
        if (enabled()) topUp();
    }

    public void topUp() {
        int target = targetCount();
        int guard = 0;
        int max = target * 6 + 50;
        while (plugin.auctions().fakeCount() < target && guard++ < max) {
            Listing listing = makeListing();
            if (listing != null) plugin.auctions().injectFake(listing);
        }
    }

    public void tick() {
        if (!enabled()) {
            plugin.auctions().clearFakes();
            return;
        }
        for (Listing l : plugin.auctions().fakes()) {
            if (l.isExpired()) plugin.auctions().removeFake(l.id());
        }
        // Retire a small random slice each cycle so the board keeps changing.
        List<Listing> current = plugin.auctions().fakes();
        if (!current.isEmpty()) {
            int retire = Math.max(1, current.size() * churnPercent() / 100);
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            for (int i = 0; i < retire && !current.isEmpty(); i++) {
                plugin.auctions().removeFake(current.get(rng.nextInt(current.size())).id());
                current = plugin.auctions().fakes();
            }
        }
        topUp();
    }

    // ---- building listings ----

    private int premiumCount() {
        int n = 0;
        for (Listing l : plugin.auctions().fakes()) {
            if (premiumMaterials.contains(l.item().getType())) n++;
        }
        return n;
    }

    private Tier rollTier() {
        int roll = ThreadLocalRandom.current().nextInt(100);
        Tier tier;
        if (roll < 55) tier = Tier.COMMON;
        else if (roll < 83) tier = Tier.UNCOMMON;
        else if (roll < 97) tier = Tier.RARE;
        else tier = Tier.EPIC;
        // Keep the genuinely good stuff scarce.
        if (tier == Tier.EPIC && premiumCount() >= epicMax()) tier = Tier.RARE;
        return tier;
    }

    private Listing makeListing() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        Template template = null;
        for (int attempt = 0; attempt < 8 && template == null; attempt++) {
            Tier tier = rollTier();
            List<Template> pool = byTier.get(tier);
            if (pool.isEmpty()) continue;
            Template pick = pool.get(rng.nextInt(pool.size()));
            if (Material.matchMaterial(pick.material()) != null) template = pick;
        }
        if (template == null) return null;

        ItemStack item = build(template);
        if (item == null) return null;

        double base = plugin.sell().valueOf(item);
        double markup = markupMin() + rng.nextDouble() * Math.max(0, markupMax() - markupMin());
        double price = Math.max(base * markup, floor(template.tier()));
        price = niceRound(price);

        String seller = names().get(rng.nextInt(names().size())).trim();
        if (seller.isEmpty()) seller = "trader";
        UUID sellerId = UUID.nameUUIDFromBytes(("ApolloFakeSeller:" + seller).getBytes(StandardCharsets.UTF_8));

        long now = System.currentTimeMillis();
        long ageMs = (long) (rng.nextDouble() * 40.0 * 3600_000L); // posted 0-40h ago
        long createdAt = now - ageMs;
        long expiresAt = createdAt + 48L * 3600_000L;

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
