package com.apollosmp.town;

import com.apollosmp.ApolloSMP;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

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
    /** Players who toggled the outline on permanently. */
    private final Set<UUID> always = ConcurrentHashMap.newKeySet();
    /** Players getting a temporary flash, mapped to when it expires. */
    private final Map<UUID, Long> flashUntil = new ConcurrentHashMap<>();

    public BorderVisualizer(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    /** Toggle the persistent outline. Returns the new state. */
    public boolean toggle(Player player) {
        if (always.remove(player.getUniqueId())) return false;
        always.add(player.getUniqueId());
        return true;
    }

    public boolean isOn(Player player) {
        return always.contains(player.getUniqueId());
    }

    /** Briefly show the outline, e.g. when walking into a town. */
    public void flash(Player player, long millis) {
        flashUntil.put(player.getUniqueId(), System.currentTimeMillis() + millis);
    }

    public void forget(Player player) {
        always.remove(player.getUniqueId());
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
            if (!flashing && !always.contains(id)) continue;
            draw(player);
        }
    }

    /** Draw the exposed edges of every claim near the player. */
    private void draw(Player player) {
        World world = player.getWorld();
        int radius = 3; // chunks
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
                for (double step = 0; step <= 16; step += 2) {
                    if (north) column(player, minX + step, baseY, minZ, color);
                    if (south) column(player, minX + step, baseY, minZ + 16, color);
                    if (west) column(player, minX, baseY, minZ + step, color);
                    if (east) column(player, minX + 16, baseY, minZ + step, color);
                }
            }
        }
    }

    private boolean sameTown(World world, int cx, int cz, Town town) {
        Town other = plugin.towns().getTownAt(TownManager.chunkKey(world, cx, cz));
        return other != null && other.name().equalsIgnoreCase(town.name());
    }

    /** A short vertical stack of particles so the edge reads as a wall. */
    private void column(Player player, double x, double baseY, double z, Color color) {
        Particle.DustOptions options = new Particle.DustOptions(color, 1.2f);
        for (double dy = 0; dy <= 2.5; dy += 1.25) {
            player.spawnParticle(DUST, new Location(player.getWorld(), x, baseY + dy, z),
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
