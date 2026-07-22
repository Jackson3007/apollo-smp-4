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
    private final UUID owner;
    private final String ownerName;
    private long lastGen;
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
    public long lastGen() { return lastGen; }
    public void setLastGen(long lastGen) { this.lastGen = lastGen; }
    public Map<Material, Integer> storage() { return storage; }

    public boolean isEmpty() {
        for (int amount : storage.values()) if (amount > 0) return true;
        return false;
    }
}
