package com.apollosmp.homes;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;

public class Home {
    private final String name;
    private final String world;
    private final double x, y, z;
    private final float yaw, pitch;
    private final Material icon;

    public Home(String name, String world, double x, double y, double z, float yaw, float pitch, Material icon) {
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.icon = icon == null ? Material.RED_BED : icon;
    }

    public static Home fromLocation(String name, Location loc, Material icon) {
        return new Home(name, loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(), icon);
    }

    public String name() { return name; }
    public String world() { return world; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }
    public Material icon() { return icon; }

    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }
}
