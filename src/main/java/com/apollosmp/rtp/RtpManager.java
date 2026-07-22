package com.apollosmp.rtp;

import com.apollosmp.ApolloSMP;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class RtpManager {

    public enum Result { SUCCESS, COOLDOWN, NO_FUNDS, NO_WORLD, FAILED }

    private final ApolloSMP plugin;
    private final java.util.Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public RtpManager(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    private String worldName() { return plugin.getConfig().getString("rtp.world", "world"); }
    private int maxRadius() { return plugin.getConfig().getInt("rtp.max-radius", 4000); }
    private int minRadius() { return plugin.getConfig().getInt("rtp.min-radius", 200); }
    private int cooldownSeconds() { return plugin.getConfig().getInt("rtp.cooldown-seconds", 30); }
    private double cost() { return plugin.getConfig().getDouble("rtp.cost", 0.0); }

    private Set<Material> avoidBlocks() {
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String name : plugin.getConfig().getStringList("rtp.avoid-blocks")) {
            Material m = Material.matchMaterial(name);
            if (m != null) set.add(m);
        }
        return set;
    }

    public long cooldownLeft(UUID id) {
        Long until = cooldowns.get(id);
        if (until == null) return 0;
        long left = (until - System.currentTimeMillis()) / 1000L;
        return Math.max(0, left);
    }

    /** Full /rtp attempt with cooldown + cost. */
    public Result attempt(Player player, boolean bypassCooldownAndCost) {
        World world = plugin.getServer().getWorld(worldName());
        if (world == null) return Result.NO_WORLD;

        if (!bypassCooldownAndCost) {
            if (cooldownLeft(player.getUniqueId()) > 0) return Result.COOLDOWN;
            if (cost() > 0 && !plugin.economy().has(player.getUniqueId(), cost())) return Result.NO_FUNDS;
        }

        Location target = findSafe(world);
        if (target == null) return Result.FAILED;

        if (!bypassCooldownAndCost) {
            if (cost() > 0) plugin.economy().withdraw(player.getUniqueId(), cost());
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + cooldownSeconds() * 1000L);
        }
        player.teleport(target);
        return Result.SUCCESS;
    }

    /** Teleport a (usually brand new) player to a random safe spot, no cost/cooldown. */
    public boolean randomSpawn(Player player) {
        return attempt(player, true) == Result.SUCCESS;
    }

    private Location findSafe(World world) {
        Set<Material> avoid = avoidBlocks();
        Set<Material> unsafeGround = new HashSet<>(avoid);

        WorldBorder border = world.getWorldBorder();
        double borderRadius = border.getSize() / 2.0 - 16;
        double centerX = border.getCenter().getX();
        double centerZ = border.getCenter().getZ();

        int max = (int) Math.max(minRadius() + 1, Math.min(maxRadius(), borderRadius));
        int min = Math.min(minRadius(), max - 1);

        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double dist = ThreadLocalRandom.current().nextDouble(min, max);
            int x = (int) (centerX + Math.cos(angle) * dist);
            int z = (int) (centerZ + Math.sin(angle) * dist);

            // Loads the chunk synchronously.
            int y = world.getHighestBlockYAt(x, z);
            if (y <= world.getMinHeight() + 1) continue;

            Block ground = world.getBlockAt(x, y, z);
            Block feet = world.getBlockAt(x, y + 1, z);
            Block head = world.getBlockAt(x, y + 2, z);

            if (ground.getType().isAir()) continue;
            if (unsafeGround.contains(ground.getType())) continue;
            if (ground.isLiquid()) continue;
            if (!feet.getType().isAir() || !head.getType().isAir()) continue;

            return new Location(world, x + 0.5, y + 1, z + 0.5,
                    ThreadLocalRandom.current().nextFloat() * 360f, 0f);
        }
        return null;
    }
}
