package com.apollosmp.invest;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** A single placed business block in the world. */
public class BusinessBlock {

    private final String worldName;
    private final int x, y, z;
    private final String businessId;
    private UUID owner;
    private String ownerName;
    private long lastGen;
    private int level = 1;
    private long producedSinceUpgrade = 0;
    private final Map<Material, Integer> storage = new EnumMap<>(Material.class);

    public BusinessBlock(String worldName, int x, int y, int z, String businessId,
                         UUID owner, String ownerName, long lastGen) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.businessId = businessId;
        this.owner = owner;
        this.ownerName = ownerName;
        this.lastGen = lastGen;
    }

    public static String key(String world, int x, int y, int z) {
        return world + ";" + x + ";" + y + ";" + z;
    }

    public static String key(Location loc) {
        return key(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public String key() { return key(worldName, x, y, z); }

    public String worldName() { return worldName; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public String businessId() { return businessId; }

    public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }
    public void setOwner(UUID owner, String ownerName) { this.owner = owner; this.ownerName = ownerName; }

    public long lastGen() { return lastGen; }
    public void setLastGen(long lastGen) { this.lastGen = lastGen; }

    public int level() { return level; }
    public void setLevel(int level) { this.level = Math.max(1, level); }

    public long producedSinceUpgrade() { return producedSinceUpgrade; }
    public void setProducedSinceUpgrade(long value) { this.producedSinceUpgrade = Math.max(0, value); }
    public void addProduced(long amount) { this.producedSinceUpgrade += Math.max(0, amount); }

    public Map<Material, Integer> storage() { return storage; }
}
