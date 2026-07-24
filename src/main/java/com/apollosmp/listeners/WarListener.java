package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import com.apollosmp.invest.BusinessBlock;
import com.apollosmp.town.Town;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What changes while two towns are at war: you can fight on their land, open
 * their chests, and raid their businesses. Blocks stay protected either way,
 * so a war can't flatten someone's build.
 */
public class WarListener implements Listener {

    private final ApolloSMP plugin;
    /** business key -> when it can be raided again */
    private final Map<String, Long> raidCooldown = new ConcurrentHashMap<>();

    public WarListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    private boolean pvpProtected() {
        return plugin.getConfig().getBoolean("towns.pvp-protection", true);
    }

    /** Towns are safe havens - unless you're at war. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!pvpProtected()) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) attacker = p;
        else if (event.getDamager() instanceof Projectile proj
                && proj.getShooter() instanceof Player p) attacker = p;
        if (attacker == null || attacker.equals(victim)) return;

        Town here = plugin.towns().getTownAtLoc(victim.getLocation());
        if (here == null) return; // wilderness - always fair game

        if (plugin.wars().atWar(attacker, victim)) return; // war is on

        event.setCancelled(true);
        plugin.msg().send(attacker, "<red>You can't fight inside <white>" + here.name()
                + "</white> unless your towns are at war.");
    }

    /** Raiding: steal a share of an enemy business's stored goods. */
    public boolean tryRaid(Player raider, BusinessBlock block) {
        Town owningTown = plugin.towns().getTownAtLoc(
                new org.bukkit.Location(plugin.getServer().getWorld(block.worldName()),
                        block.x(), block.y(), block.z()));
        if (owningTown == null) return false;

        Town raiderTown = plugin.towns().getTownOf(raider.getUniqueId());
        if (raiderTown == null) return false;
        if (!plugin.wars().atWar(raiderTown.name(), owningTown.name())) return false;

        long now = System.currentTimeMillis();
        Long ready = raidCooldown.get(block.key());
        if (ready != null && ready > now) {
            long left = (ready - now) / 1000;
            plugin.msg().send(raider, "<red>This business has already been raided. "
                    + left + "s until it can be hit again.");
            return true;
        }

        plugin.businesses().updateProduction(block);
        double share = Math.max(0.05, Math.min(1.0,
                plugin.getConfig().getDouble("towns.war.raid-share", 0.5)));

        int taken = 0;
        for (Map.Entry<Material, Integer> e : new ArrayList<>(block.storage().entrySet())) {
            int amount = (int) Math.floor(e.getValue() * share);
            if (amount <= 0) continue;
            int left = e.getValue() - amount;
            if (left <= 0) block.storage().remove(e.getKey());
            else block.storage().put(e.getKey(), left);

            int give = amount;
            while (give > 0) {
                int chunk = Math.min(e.getKey().getMaxStackSize(), give);
                com.apollosmp.util.Items.give(raider, new ItemStack(e.getKey(), chunk));
                give -= chunk;
            }
            taken += amount;
        }
        plugin.businesses().save();

        long cooldown = Math.max(1, plugin.getConfig().getLong("towns.war.raid-cooldown-minutes", 5)) * 60_000L;
        raidCooldown.put(block.key(), now + cooldown);

        if (taken == 0) {
            plugin.msg().send(raider, "<gray>There was nothing worth taking.");
            return true;
        }
        plugin.msg().send(raider, "<red>Raided <white>" + taken + "</white> goods from <white>"
                + owningTown.name() + "</white>!");

        // Let the owner know they've been hit.
        Player owner = plugin.getServer().getPlayer(block.owner());
        if (owner != null) {
            plugin.msg().send(owner, "<red><bold>Raided!</bold></red> <gray>" + raider.getName()
                    + " took <white>" + taken + "</white> goods from your business.");
        }
        return true;
    }
}
