package com.apollosmp.special;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** A one-off business: its definition, and its state once placed. */
public class SpecialBusiness {

    // ---- definition ----
    private String id;
    private String name;
    private String description;
    private String industry;
    private String rarity;
    private Material block;

    private Material knownItem;
    private int knownAmount;
    private Material hiddenItem;
    private int hiddenAmount;

    private int intervalSeconds;
    private int maxStorage;

    private double profitMin;
    private double profitMax;
    private double exactProfit;
    private SpecialTrait trait;

    // ---- placement state ----
    private UUID owner;
    private String ownerName;
    private String worldName;
    private int x, y, z;
    private long lastGen;
    private final Map<Material, Integer> storage = new EnumMap<>(Material.class);

    public SpecialBusiness() {
    }

    // ---- definition accessors ----
    public String id() { return id; }
    public void setId(String id) { this.id = id; }
    public String name() { return name; }
    public void setName(String name) { this.name = name; }
    public String description() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String industry() { return industry; }
    public void setIndustry(String industry) { this.industry = industry; }
    public String rarity() { return rarity; }
    public void setRarity(String rarity) { this.rarity = rarity; }
    public Material block() { return block; }
    public void setBlock(Material block) { this.block = block; }

    public Material knownItem() { return knownItem; }
    public void setKnownItem(Material knownItem) { this.knownItem = knownItem; }
    public int knownAmount() { return knownAmount; }
    public void setKnownAmount(int knownAmount) { this.knownAmount = knownAmount; }
    public Material hiddenItem() { return hiddenItem; }
    public void setHiddenItem(Material hiddenItem) { this.hiddenItem = hiddenItem; }
    public int hiddenAmount() { return hiddenAmount; }
    public void setHiddenAmount(int hiddenAmount) { this.hiddenAmount = hiddenAmount; }

    public int intervalSeconds() { return intervalSeconds; }
    public void setIntervalSeconds(int intervalSeconds) { this.intervalSeconds = intervalSeconds; }
    public int maxStorage() { return maxStorage; }
    public void setMaxStorage(int maxStorage) { this.maxStorage = maxStorage; }

    public double profitMin() { return profitMin; }
    public void setProfitMin(double profitMin) { this.profitMin = profitMin; }
    public double profitMax() { return profitMax; }
    public void setProfitMax(double profitMax) { this.profitMax = profitMax; }
    public double exactProfit() { return exactProfit; }
    public void setExactProfit(double exactProfit) { this.exactProfit = exactProfit; }
    public SpecialTrait trait() { return trait; }
    public void setTrait(SpecialTrait trait) { this.trait = trait; }

    // ---- placement accessors ----
    public UUID owner() { return owner; }
    public void setOwner(UUID owner) { this.owner = owner; }
    public String ownerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String worldName() { return worldName; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public long lastGen() { return lastGen; }
    public void setLastGen(long lastGen) { this.lastGen = lastGen; }
    public Map<Material, Integer> storage() { return storage; }

    public void setLocation(Location loc) {
        this.worldName = loc.getWorld().getName();
        this.x = loc.getBlockX();
        this.y = loc.getBlockY();
        this.z = loc.getBlockZ();
    }

    public void setLocation(String world, int x, int y, int z) {
        this.worldName = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean isPlaced() { return worldName != null; }

    public String locationKey() {
        return worldName == null ? null : key(worldName, x, y, z);
    }

    public static String key(String world, int x, int y, int z) {
        return world + "," + x + "," + y + "," + z;
    }

    public static String key(Location loc) {
        return key(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public Location toLocation(World world) {
        return world == null ? null : new Location(world, x + 0.5, y, z + 0.5);
    }

    // ---- trait-adjusted values ----
    public int effectiveInterval() {
        if (trait == SpecialTrait.EFFICIENT) return Math.max(10, (int) Math.round(intervalSeconds * 0.7));
        return Math.max(10, intervalSeconds);
    }

    public int effectiveKnownAmount() {
        if (trait == SpecialTrait.HIGH_YIELD) return Math.max(1, (int) Math.round(knownAmount * 1.5));
        return Math.max(1, knownAmount);
    }

    public int effectiveHiddenAmount() {
        if (trait == SpecialTrait.RARE_DEPOSIT) return Math.max(1, hiddenAmount * 2);
        return Math.max(1, hiddenAmount);
    }

    public int effectiveStorage() {
        if (trait == SpecialTrait.EXPANDED_STORAGE) return maxStorage * 2;
        return maxStorage;
    }
}
