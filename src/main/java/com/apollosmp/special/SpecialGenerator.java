package com.apollosmp.special;

import org.bukkit.Material;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/** Rolls a fresh, never-quite-the-same special business for each auction. */
public class SpecialGenerator {

    /** industry -> block, known items, hidden items, name nouns. */
    private record Industry(String name, Material block,
                            Material[] known, Material[] hidden, String[] nouns) {}

    private static final Industry[] INDUSTRIES = {
            new Industry("Mining", Material.DEEPSLATE,
                    new Material[]{Material.STONE, Material.DEEPSLATE, Material.COBBLESTONE, Material.IRON_ORE},
                    new Material[]{Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.ANCIENT_DEBRIS},
                    new String[]{"Quarry", "Shaft", "Excavation", "Dig", "Lode"}),

            new Industry("Farming", Material.HAY_BLOCK,
                    new Material[]{Material.WHEAT, Material.CARROT, Material.POTATO, Material.BEETROOT},
                    new Material[]{Material.GOLDEN_CARROT, Material.HONEYCOMB, Material.SWEET_BERRIES, Material.GLOW_BERRIES},
                    new String[]{"Fields", "Estate", "Homestead", "Acres", "Grange"}),

            new Industry("Forestry", Material.OAK_WOOD,
                    new Material[]{Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.DARK_OAK_LOG},
                    new Material[]{Material.APPLE, Material.HONEYCOMB, Material.COCOA_BEANS, Material.AMETHYST_SHARD},
                    new String[]{"Timberworks", "Grove", "Sawmill", "Woodlot", "Stand"}),

            new Industry("Fishing", Material.PRISMARINE,
                    new Material[]{Material.COD, Material.SALMON, Material.KELP, Material.TROPICAL_FISH},
                    new Material[]{Material.PRISMARINE_SHARD, Material.NAUTILUS_SHELL, Material.HEART_OF_THE_SEA, Material.INK_SAC},
                    new String[]{"Fishery", "Docks", "Wharf", "Trawl", "Hatchery"}),

            new Industry("Nether Trade", Material.NETHER_BRICKS,
                    new Material[]{Material.NETHERRACK, Material.QUARTZ, Material.SOUL_SAND, Material.MAGMA_BLOCK},
                    new Material[]{Material.BLAZE_ROD, Material.GHAST_TEAR, Material.NETHERITE_SCRAP, Material.GOLD_BLOCK},
                    new String[]{"Foundry", "Bastion", "Outpost", "Forge", "Crucible"}),

            new Industry("Alchemy", Material.BREWING_STAND,
                    new Material[]{Material.GLASS_BOTTLE, Material.NETHER_WART, Material.REDSTONE, Material.GLOWSTONE_DUST},
                    new Material[]{Material.GHAST_TEAR, Material.PHANTOM_MEMBRANE, Material.DRAGON_BREATH, Material.GLISTERING_MELON_SLICE},
                    new String[]{"Laboratory", "Apothecary", "Distillery", "Workshop", "Sanctum"}),
    };

    private static final String[] PREFIXES = {
            "Atlas", "Ironhold", "Sable", "Vermilion", "Kestrel", "Obsidian", "Halcyon",
            "Meridian", "Ashvale", "Cobalt", "Wraith", "Solace", "Vanguard", "Thornwood",
            "Gilded", "Hollow", "Tempest", "Lumen", "Drifter", "Nimbus", "Verdant", "Onyx"
    };

    private record Rarity(String name, double weight, double profitScale, double rateScale) {}

    private static final List<Rarity> RARITIES = List.of(
            new Rarity("Uncommon", 40, 1.0, 1.0),
            new Rarity("Rare", 32, 1.5, 1.3),
            new Rarity("Epic", 20, 2.2, 1.7),
            new Rarity("Legendary", 8, 3.4, 2.3)
    );

    private final Random random = new Random();

    /** Build a brand new business. Nothing is reused between rolls. */
    public SpecialBusiness generate() {
        Industry industry = INDUSTRIES[random.nextInt(INDUSTRIES.length)];
        Rarity rarity = pickRarity();

        SpecialBusiness business = new SpecialBusiness();
        business.setId(UUID.randomUUID().toString().substring(0, 8));
        business.setIndustry(industry.name());
        business.setRarity(rarity.name());
        business.setBlock(industry.block());
        business.setName(PREFIXES[random.nextInt(PREFIXES.length)] + " "
                + industry.nouns()[random.nextInt(industry.nouns().length)]);
        business.setDescription(describe(industry, rarity));

        Material known = industry.known()[random.nextInt(industry.known().length)];
        Material hidden = industry.hidden()[random.nextInt(industry.hidden().length)];
        business.setKnownItem(known);
        business.setHiddenItem(hidden);

        int baseKnown = 4 + random.nextInt(9);                       // 4-12
        int baseHidden = 1 + random.nextInt(3);                      // 1-3
        business.setKnownAmount((int) Math.max(1, Math.round(baseKnown * rarity.rateScale())));
        business.setHiddenAmount((int) Math.max(1, Math.round(baseHidden * rarity.rateScale())));

        business.setIntervalSeconds(180 + random.nextInt(421));       // 3-10 minutes
        business.setMaxStorage(1024 + random.nextInt(3072));

        business.setTrait(SpecialTrait.values()[random.nextInt(SpecialTrait.values().length)]);

        // Rough daily value, then a deliberately fuzzy public range around it.
        double perDay = estimateDailyProfit(business, rarity);
        business.setExactProfit(round(perDay));
        double spread = 0.22 + random.nextDouble() * 0.15;            // 22-37% either side
        business.setProfitMin(round(perDay * (1 - spread)));
        business.setProfitMax(round(perDay * (1 + spread)));

        business.setLastGen(System.currentTimeMillis());
        return business;
    }

    private Rarity pickRarity() {
        double total = 0;
        for (Rarity r : RARITIES) total += r.weight();
        double roll = random.nextDouble() * total;
        for (Rarity r : RARITIES) {
            roll -= r.weight();
            if (roll <= 0) return r;
        }
        return RARITIES.get(0);
    }

    private String describe(Industry industry, Rarity rarity) {
        String[] openers = {
                "An old operation changing hands under quiet terms.",
                "Seized assets, sold on with no questions asked.",
                "A going concern with the books mostly in order.",
                "Left to the estate. The estate wants it gone.",
                "Recovered from a claim nobody survived to file.",
                "The previous owner has, let's say, moved on."
        };
        return openers[random.nextInt(openers.length)] + " "
                + rarity.name() + " " + industry.name().toLowerCase() + " venture.";
    }

    /** Value both products per day, scaled by rarity. */
    private double estimateDailyProfit(SpecialBusiness b, Rarity rarity) {
        double cyclesPerDay = 86400.0 / Math.max(10, b.intervalSeconds());
        double knownValue = guessValue(b.knownItem()) * b.knownAmount();
        double hiddenValue = guessValue(b.hiddenItem()) * b.hiddenAmount();
        double raw = cyclesPerDay * (knownValue + hiddenValue);
        // Storage caps how much can actually be banked in a day.
        double capped = Math.min(raw, b.maxStorage() * 6.0);
        return capped * rarity.profitScale();
    }

    /** Crude per-item worth, only used to size the profit estimate. */
    private double guessValue(Material material) {
        String n = material.name();
        if (n.contains("NETHERITE") || n.contains("ANCIENT_DEBRIS")) return 220;
        if (n.contains("DIAMOND") || n.contains("HEART_OF_THE_SEA")) return 140;
        if (n.contains("EMERALD") || n.contains("DRAGON_BREATH")) return 90;
        if (n.contains("GOLD") || n.contains("NAUTILUS") || n.contains("GHAST")) return 55;
        if (n.contains("BLAZE") || n.contains("AMETHYST") || n.contains("HONEYCOMB")) return 30;
        if (n.contains("IRON") || n.contains("QUARTZ") || n.contains("PRISMARINE")) return 18;
        if (n.contains("LOG") || n.contains("FISH") || n.contains("COD") || n.contains("SALMON")) return 8;
        return 4;
    }

    private double round(double value) {
        return Math.round(value / 100.0) * 100.0;
    }
}
