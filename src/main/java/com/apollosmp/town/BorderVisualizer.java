package com.apollosmp.town;

import com.apollosmp.ApolloSMP;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws particle walls along the edges of nearby town claims.
 * Particles are sent to a single player, so nobody else sees the outline.
 */
public class BorderVisualizer {

    /** Renamed across versions, so look it up rather than hard-coding. */
    private static final Particle DUST = resolveDust();

    private static Particle resolveDust() {
        for (String name : new String[]{"DUST", "REDSTONE"}) {
            try {
                return Particle.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // try the next name
            }
        }
        return null;
    }

    private static final Color OWN = Color.fromRGB(90, 209, 232);      // Apollo cyan
    private static final Color OTHER = Color.fromRGB(255, 78, 80);     // Apollo red
    private static final Color PLOT = Color.fromRGB(233, 79, 208);     // Apollo purple

    private final ApolloSMP plugin;
    /** Players who have explicitly chosen. Anyone absent uses the server default. */
    private final Map<UUID, Boolean> choices = new ConcurrentHashMap<>();
    /** Players getting a temporary flash, mapped to when it expires. */
    private final Map<UUID, Long> flashUntil = new ConcurrentHashMap<>();

    private final File file;

    public BorderVisualizer(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "borders.yml");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String raw : cfg.getStringList("showing")) {
            try {
                choices.put(UUID.fromString(raw), true);
            } catch (IllegalArgumentException ignored) {
                // skip bad entries
            }
        }
        for (String raw : cfg.getStringList("hidden")) {
            try {
                choices.put(UUID.fromString(raw), false);
            } catch (IllegalArgumentException ignored) {
                // skip bad entries
            }
        }
    }

    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        List<String> on = new ArrayList<>();
        List<String> off = new ArrayList<>();
        for (Map.Entry<UUID, Boolean> e : choices.entrySet()) {
            (e.getValue() ? on : off).add(e.getKey().toString());
        }
        cfg.set("showing", on);
        cfg.set("hidden", off);
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save borders.yml: " + ex.getMessage());
        }
    }

    /** Borders are on unless the player turned them off. */
    private boolean defaultOn() {
        return plugin.getConfig().getBoolean("towns.border.default-on", true);
    }

    /** Toggle the persistent outline. Returns the new state. */
    public boolean toggle(Player player) {
        boolean on = !isOn(player);
        choices.put(player.getUniqueId(), on);
        save();
        return on;
    }

    public boolean isOn(Player player) {
        return choices.getOrDefault(player.getUniqueId(), defaultOn());
    }

    private double spacing() { return Math.max(0.25, plugin.getConfig().getDouble("towns.border.spacing", 1.0)); }
    private double height() { return Math.max(1.0, plugin.getConfig().getDouble("towns.border.height", 2.0)); }
    private float dotSize() { return (float) Math.max(0.5, plugin.getConfig().getDouble("towns.border.size", 2.0)); }
    private int radius() { return Math.max(1, Math.min(6, plugin.getConfig().getInt("towns.border.radius", 2))); }
    private double cornerHeight() { return Math.max(height(), plugin.getConfig().getDouble("towns.border.corner-height", 6.0)); }

    /** Briefly show the outline, e.g. when walking into a town. */
    public void flash(Player player, long millis) {
        flashUntil.put(player.getUniqueId(), System.currentTimeMillis() + millis);
    }

    /** Clears the temporary flash only - the on/off choice is remembered. */
    public void forget(Player player) {
        flashUntil.remove(player.getUniqueId());
    }

    /** Called on a timer. */
    public void tick() {
        if (DUST == null) return;
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            Long until = flashUntil.get(id);
            boolean flashing = until != null && until > now;
            if (until != null && !flashing) flashUntil.remove(id);
            if (!flashing && !isOn(player)) continue;
            draw(player);
        }
    }

    /** Draw the exposed edges of every claim near the player. */
    private void draw(Player player) {
        World world = player.getWorld();
        int radius = radius();
        int pcx = player.getLocation().getBlockX() >> 4;
        int pcz = player.getLocation().getBlockZ() >> 4;
        double baseY = player.getLocation().getY();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                String key = TownManager.chunkKey(world, cx, cz);
                Town town = plugin.towns().getTownAt(key);
                if (town == null) continue;

                Color color;
                UUID plotOwner = town.plotOwner(key);
                if (plotOwner != null) color = PLOT;
                else if (town.isMember(player.getUniqueId())) color = OWN;
                else color = OTHER;

                boolean north = !sameTown(world, cx, cz - 1, town);
                boolean south = !sameTown(world, cx, cz + 1, town);
                boolean west = !sameTown(world, cx - 1, cz, town);
                boolean east = !sameTown(world, cx + 1, cz, town);

                double minX = cx * 16.0;
                double minZ = cz * 16.0;
                double gap = spacing();
                for (double step = 0; step <= 16; step += gap) {
                    // Chunk corners get a tall pillar so the outline reads from a distance.
                    boolean corner = step < 0.01 || step > 15.99;
                    if (north) column(player, minX + step, baseY, minZ, color, corner);
                    if (south) column(player, minX + step, baseY, minZ + 16, color, corner);
                    if (west) column(player, minX, baseY, minZ + step, color, corner);
                    if (east) column(player, minX + 16, baseY, minZ + step, color, corner);
                }
            }
        }
    }

    private boolean sameTown(World world, int cx, int cz, Town town) {
        Town other = plugin.towns().getTownAt(TownManager.chunkKey(world, cx, cz));
        return other != null && other.name().equalsIgnoreCase(town.name());
    }

    /** A vertical stack of particles so the edge reads as a solid wall. */
    private void column(Player player, double x, double baseY, double z, Color color, boolean corner) {
        Particle.DustOptions options = new Particle.DustOptions(color, dotSize());
        double top = corner ? cornerHeight() : height();
        for (double dy = 0; dy <= top; dy += 1.0) {
            player.spawnParticle(DUST, new Location(player.getWorld(), x, baseY + dy + 0.2, z),
                    1, 0, 0, 0, 0, options);
        }
    }

    /** A small text map of claims around the player. */
    public void sendMap(Player player) {
        World world = player.getWorld();
        int pcx = player.getLocation().getBlockX() >> 4;
        int pcz = player.getLocation().getBlockZ() >> 4;
        Town mine = plugin.towns().getTownOf(player.getUniqueId());

        plugin.msg().sendRaw(player, "<gradient:#f9d423:#ff4e50><bold>Land Around You</bold></gradient>");
        for (int dz = -4; dz <= 4; dz++) {
            StringBuilder row = new StringBuilder(" ");
            for (int dx = -6; dx <= 6; dx++) {
                int cx = pcx + dx;
                int cz = pcz + dz;
                Town town = plugin.towns().getTownAt(TownManager.chunkKey(world, cx, cz));
                boolean here = (dx == 0 && dz == 0);
                if (town == null) {
                    row.append(here ? "<white>+</white>" : "<dark_gray>-</dark_gray>");
                } else if (mine != null && town.name().equalsIgnoreCase(mine.name())) {
                    row.append(here ? "<white>+</white>" : "<#5ad1e8>\u25a0</#5ad1e8>");
                } else {
                    row.append(here ? "<white>+</white>" : "<#ff4e50>\u25a0</#ff4e50>");
                }
            }
            plugin.msg().sendRaw(player, row.toString());
        }
        plugin.msg().sendRaw(player, "<white>+</white> <gray>you</gray>  "
                + "<#5ad1e8>\u25a0</#5ad1e8> <gray>your town</gray>  "
                + "<#ff4e50>\u25a0</#ff4e50> <gray>other town</gray>  "
                + "<dark_gray>-</dark_gray> <gray>wild</gray>");
    }
}
