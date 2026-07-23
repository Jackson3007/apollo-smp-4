package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import com.apollosmp.spawner.SpawnerManager;
import com.apollosmp.util.Msg;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Merges spawner mobs into stacks so a busy spawner room stays a handful of
 * entities instead of hundreds. Killing one peels a single mob off the stack.
 */
public class MobStackListener implements Listener {

    private final ApolloSMP plugin;
    private final NamespacedKey stackKey;

    public MobStackListener(ApolloSMP plugin) {
        this.plugin = plugin;
        this.stackKey = new NamespacedKey(plugin, "apollo_mob_stack");
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("mob-stacking.enabled", true);
    }

    private int maxStack() {
        return Math.max(2, plugin.getConfig().getInt("mob-stacking.max-stack", 50));
    }

    private double radius() {
        return Math.max(1.0, plugin.getConfig().getDouble("mob-stacking.merge-radius", 6.0));
    }

    // ---- stack data ----
    public int stackOf(Entity entity) {
        Integer value = entity.getPersistentDataContainer().get(stackKey, PersistentDataType.INTEGER);
        return value == null ? 1 : Math.max(1, value);
    }

    private void setStack(LivingEntity entity, int size) {
        entity.getPersistentDataContainer().set(stackKey, PersistentDataType.INTEGER, Math.max(1, size));
        if (size > 1) {
            entity.customName(Msg.mm("<#f9d423>" + SpawnerManager.pretty(entity.getType())
                    + "</#f9d423> <gray>x</gray><white>" + size + "</white>"));
            entity.setCustomNameVisible(true);
        } else {
            entity.customName(null);
            entity.setCustomNameVisible(false);
        }
    }

    /**
     * Everything happens here: this event knows which spawner produced the mob,
     * so the stack size and the merge are handled in one place.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (!enabled()) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;

        int weight = 1;
        if (event.getSpawner() != null) {
            SpawnerManager.Placed placed = plugin.spawners().at(event.getSpawner().getLocation());
            if (placed != null) weight = Math.max(1, placed.stack);
        }

        // Fold this spawn into a nearby stack if there's one with room.
        LivingEntity host = findHost(living.getLocation(), living.getType(), living);
        if (host != null) {
            int combined = Math.min(maxStack(), stackOf(host) + weight);
            if (combined > stackOf(host)) {
                setStack(host, combined);
                event.setCancelled(true);
                return;
            }
        }

        // No host - this mob becomes the stack.
        if (weight > 1) setStack(living, Math.min(maxStack(), weight));
    }

    private LivingEntity findHost(Location loc, EntityType type, Entity exclude) {
        double r = radius();
        for (Entity nearby : loc.getWorld().getNearbyEntities(loc, r, r, r)) {
            if (nearby.equals(exclude)) continue;
            if (nearby.getType() != type) continue;
            if (!(nearby instanceof LivingEntity living)) continue;
            if (living.isDead()) continue;
            if (stackOf(living) >= maxStack()) continue;
            return living;
        }
        return null;
    }

    /** Killing a stacked mob peels one off and leaves the rest standing. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        if (!enabled()) return;
        LivingEntity dead = event.getEntity();
        int stack = stackOf(dead);
        if (stack <= 1) return;

        Location loc = dead.getLocation();
        EntityType type = dead.getType();
        int remaining = stack - 1;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                Entity spawned = loc.getWorld().spawnEntity(loc, type,
                        CreatureSpawnEvent.SpawnReason.CUSTOM);
                if (spawned instanceof LivingEntity living) setStack(living, remaining);
            } catch (Exception ex) {
                plugin.getLogger().warning("Couldn't rebuild a mob stack: " + ex.getMessage());
            }
        });
    }
}
