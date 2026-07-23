package com.apollosmp.town;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.potion.PotionEffectType;

/** Perks a town can buy from its bank. */
public enum TownUpgrade {

    PRODUCTION("Industry", Material.GOLD_INGOT, 5, 4000, null,
            "Businesses inside your town produce",
            "faster. +15% per level."),

    HASTE("Haste", Material.DIAMOND_PICKAXE, 2, 5000, "haste",
            "Residents mine faster anywhere",
            "inside your town's borders."),

    SPEED("Swiftness", Material.FEATHER, 2, 5000, "speed",
            "Residents move faster inside",
            "your town's borders."),

    REGENERATION("Sanctuary", Material.GOLDEN_APPLE, 1, 12000, "regeneration",
            "Residents slowly heal while",
            "inside your town's borders."),

    RESISTANCE("Fortification", Material.SHIELD, 1, 15000, "resistance",
            "Residents take less damage",
            "inside your town's borders."),

    CLAIM_LIMIT("Surveying", Material.FILLED_MAP, 5, 3000, null,
            "Raises how much land your town",
            "may hold. +2 chunks per level.");

    private final String display;
    private final Material icon;
    private final int maxLevel;
    private final double baseCost;
    private final String effectKey;
    private final String[] description;

    TownUpgrade(String display, Material icon, int maxLevel, double baseCost,
                String effectKey, String... description) {
        this.display = display;
        this.icon = icon;
        this.maxLevel = maxLevel;
        this.baseCost = baseCost;
        this.effectKey = effectKey;
        this.description = description;
    }

    public String display() { return display; }
    public Material icon() { return icon; }
    public int maxLevel() { return maxLevel; }
    public double baseCost() { return baseCost; }
    public String[] description() { return description; }

    /** The potion effect this grants inside town borders, or null. */
    public PotionEffectType effect() {
        if (effectKey == null) return null;
        try {
            return PotionEffectType.getByKey(NamespacedKey.minecraft(effectKey));
        } catch (Exception ex) {
            return null;
        }
    }

    /** Cost to go from the given level to the next one. */
    public double costFor(int currentLevel) {
        return baseCost * (currentLevel + 1);
    }

    public static TownUpgrade fromString(String s) {
        try {
            return TownUpgrade.valueOf(s);
        } catch (Exception ex) {
            return null;
        }
    }
}
