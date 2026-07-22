package com.apollosmp.invest;

import org.bukkit.Material;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The catalog of the 7 buyable businesses. */
public final class Businesses {

    private static final Map<String, Business> MAP = new LinkedHashMap<>();

    private Businesses() {}

    static {
        add(new Business("bakery",
                "<gradient:#ffcf7a:#ff8a3d><bold>Apollo Bakery</bold></gradient>",
                "<gray>Fresh bread, cookies & pies",
                "FLAME", Material.SMOKER, 5000, 300, 12,
                List.of(new Business.Product(Material.BREAD, 2),
                        new Business.Product(Material.COOKIE, 5),
                        new Business.Product(Material.PUMPKIN_PIE, 1))));

        add(new Business("farm",
                "<gradient:#b7f542:#3dbb2f><bold>Sunrise Farmstead</bold></gradient>",
                "<gray>Wheat, carrots & potatoes",
                "HAPPY_VILLAGER", Material.HAY_BLOCK, 3500, 300, 12,
                List.of(new Business.Product(Material.WHEAT, 3),
                        new Business.Product(Material.CARROT, 2),
                        new Business.Product(Material.POTATO, 2))));

        add(new Business("lumber",
                "<gradient:#c9a36a:#7a5230><bold>Evergreen Lumber Mill</bold></gradient>",
                "<gray>Logs & planks by the crate",
                "CHERRY_LEAVES", Material.STRIPPED_SPRUCE_LOG, 4500, 300, 12,
                List.of(new Business.Product(Material.OAK_LOG, 3),
                        new Business.Product(Material.OAK_PLANKS, 4))));

        add(new Business("quarry",
                "<gradient:#c0c0c0:#6e6e6e><bold>Deepstone Quarry</bold></gradient>",
                "<gray>Stone, cobble & iron",
                "CRIT", Material.DEEPSLATE, 8000, 300, 12,
                List.of(new Business.Product(Material.COBBLESTONE, 30),
                        new Business.Product(Material.COBBLED_DEEPSLATE, 20),
                        new Business.Product(Material.IRON_INGOT, 1))));

        add(new Business("fishery",
                "<gradient:#5ad1e8:#1f6fb0><bold>Abyssal Fishery</bold></gradient>",
                "<gray>Cod, salmon & prismarine",
                "SPLASH", Material.BARREL, 6000, 300, 12,
                List.of(new Business.Product(Material.COD, 2),
                        new Business.Product(Material.SALMON, 2),
                        new Business.Product(Material.PRISMARINE_SHARD, 1))));

        add(new Business("goldmine",
                "<gradient:#ffe259:#ffa751><bold>Gilded Gold Mine</bold></gradient>",
                "<gray>Gold ingots & raw gold",
                "WAX_ON", Material.RAW_GOLD_BLOCK, 15000, 300, 12,
                List.of(new Business.Product(Material.GOLD_INGOT, 1),
                        new Business.Product(Material.RAW_GOLD, 1))));

        add(new Business("gemmine",
                "<gradient:#e94fd0:#7d3cff><bold>Prismatic Gem Mine</bold></gradient>",
                "<gray>Diamonds, emeralds & amethyst",
                "WITCH", Material.AMETHYST_BLOCK, 30000, 600, 12,
                List.of(new Business.Product(Material.DIAMOND, 1),
                        new Business.Product(Material.EMERALD, 1),
                        new Business.Product(Material.AMETHYST_SHARD, 3))));
    }

    private static void add(Business b) {
        MAP.put(b.id(), b);
    }

    public static Business get(String id) {
        return id == null ? null : MAP.get(id);
    }

    public static Collection<Business> all() {
        return MAP.values();
    }

    public static Business byBlock(Material material) {
        for (Business b : MAP.values()) {
            if (b.block() == material) return b;
        }
        return null;
    }
}
