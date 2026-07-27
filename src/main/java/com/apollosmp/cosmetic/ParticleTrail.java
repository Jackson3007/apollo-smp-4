package com.apollosmp.cosmetic;

import com.apollosmp.ApolloSMP;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A cosmetic particle trail that follows Apollo+ players. Purely visual - no gameplay
 * effect. On by default for Apollo+; players can turn it off with /trail.
 */
public class ParticleTrail {

    private final ApolloSMP plugin;
    private final File file;
    /** Players who have explicitly turned their trail OFF (default is on). */
    private final Set<UUID> disabled = ConcurrentHashMap.newKeySet();

    public ParticleTrail(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "trails.yml");
        load();
    }

    /** True if this player should currently show a trail. */
    public boolean isOn(Player player) {
        return player.hasPermission("apollo.plus")
                && !disabled.contains(player.getUniqueId());
    }

    /** Flip a player's trail on/off. Returns the new state. */
    public boolean toggle(Player player) {
        UUID id = player.getUniqueId();
        boolean nowOn;
        if (disabled.remove(id)) {
            nowOn = true;
        } else {
            disabled.add(id);
            nowOn = false;
        }
        save();
        return nowOn;
    }

    private Particle particle() {
        String name = plugin.getConfig().getString("ranks.apollo-plus.trail-particle", "HAPPY_VILLAGER");
        try {
            return Particle.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Particle.HAPPY_VILLAGER;
        }
    }

    /** Spawn a few particles at each eligible player's feet. Called on a timer. */
    public void tick() {
        Particle particle = particle();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!isOn(player)) continue;
            Location at = player.getLocation().add(0, 0.1, 0);
            try {
                player.getWorld().spawnParticle(particle, at, 4, 0.2, 0.05, 0.2, 0.0);
            } catch (Throwable ignored) {
                // never let a cosmetic break the tick loop
            }
        }
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("disabled", new ArrayList<>(disabled.stream().map(UUID::toString).toList()));
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save trails.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String s : cfg.getStringList("disabled")) {
            try {
                disabled.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
                // skip malformed entry
            }
        }
    }
}
