package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import com.apollosmp.spawner.SpawnerManager;
import com.apollosmp.util.Items;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Placing, stacking, picking up and breaking spawners. */
public class SpawnerListener implements Listener {

    private final ApolloSMP plugin;

    public SpawnerListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("spawners.silk-touch-pickup", true);
    }

    // ------------------------------------------------ placing
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() != Material.SPAWNER) return;

        EntityType type = plugin.spawners().readType(event.getItemInHand());
        if (type == null) {
            // A plain spawner item - read whatever the block ended up as.
            BlockState state = event.getBlockPlaced().getState();
            if (state instanceof CreatureSpawner cs) {
                try {
                    type = cs.getSpawnedType();
                } catch (Exception ignored) {
                    type = null;
                }
            }
            if (type == null) return;
        }

        final EntityType placedType = type;
        plugin.spawners().register(event.getBlockPlaced().getLocation(), placedType, 1);
        // Re-apply a tick later; some servers reset the block state on place.
        plugin.getServer().getScheduler().runTask(plugin, () ->
                plugin.spawners().applyToBlock(event.getBlockPlaced(), placedType));

        plugin.msg().send(event.getPlayer(), "<green>Placed a <#e94fd0>"
                + SpawnerManager.pretty(placedType) + " Spawner</#e94fd0>. "
                + "<gray>Right-click with another to stack it.");
    }

    // ------------------------------------------------ stacking & pickup
    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (event.getClickedBlock().getType() != Material.SPAWNER) return;

        Block block = event.getClickedBlock();
        SpawnerManager.Placed placed = plugin.spawners().at(block.getLocation());
        if (placed == null) return; // a natural spawner - leave it be

        Player player = event.getPlayer();
        if (!plugin.towns().canBuild(player, block.getLocation())) {
            plugin.msg().send(player, "<red>You can't touch spawners on this land.");
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        ItemStack held = player.getInventory().getItemInMainHand();
        EntityType heldType = plugin.spawners().readType(held);

        // Holding a matching spawner: stack it on.
        if (heldType != null) {
            if (heldType != placed.type) {
                plugin.msg().send(player, "<red>That's a <white>" + SpawnerManager.pretty(heldType)
                        + "</white> spawner - it won't stack onto a <white>"
                        + SpawnerManager.pretty(placed.type) + "</white> one.");
                return;
            }
            if (placed.stack >= plugin.spawners().maxStack()) {
                plugin.msg().send(player, "<yellow>That stack is already at the limit ("
                        + plugin.spawners().maxStack() + ").");
                return;
            }
            int room = plugin.spawners().maxStack() - placed.stack;
            int adding = Math.min(room, held.getAmount());
            plugin.spawners().setStack(placed, placed.stack + adding);
            if (player.getGameMode() != GameMode.CREATIVE) {
                held.setAmount(held.getAmount() - adding);
                player.getInventory().setItemInMainHand(held.getAmount() <= 0 ? null : held);
            }
            plugin.msg().send(player, "<green>Stacked <white>+" + adding + "</white>. Now <#f9d423>x"
                    + placed.stack + "</#f9d423>.");
            return;
        }

        // Empty hand: take one back.
        if (held == null || held.getType().isAir()) {
            plugin.spawners().setStack(placed, placed.stack - 1);
            Items.give(player, plugin.spawners().createItem(placed.type, 1));
            if (placed.stack <= 0) {
                plugin.spawners().unregister(placed);
                block.setType(Material.AIR);
                plugin.msg().send(player, "<yellow>Picked up the last <#e94fd0>"
                        + SpawnerManager.pretty(placed.type) + " Spawner</#e94fd0>.");
            } else {
                plugin.msg().send(player, "<yellow>Took one. <#f9d423>x" + placed.stack
                        + "</#f9d423> left.");
            }
        }
    }

    // ------------------------------------------------ breaking
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();
        SpawnerManager.Placed placed = plugin.spawners().at(block.getLocation());

        // Player-placed spawners always come back, tool or not.
        if (placed != null) {
            event.setExpToDrop(0);
            event.setDropItems(false);
            int amount = Math.max(1, placed.stack);
            int left = amount;
            while (left > 0) {
                int chunk = Math.min(64, left);
                Items.give(player, plugin.spawners().createItem(placed.type, chunk));
                left -= chunk;
            }
            plugin.spawners().unregister(placed);
            plugin.msg().send(player, "<green>Collected <#f9d423>x" + amount + "</#f9d423> <#e94fd0>"
                    + SpawnerManager.pretty(placed.type) + " Spawner</#e94fd0>.");
            return;
        }

        // Natural spawners still need silk touch.
        if (!enabled()) return;
        ItemStack tool = player.getInventory().getItemInMainHand();
        Enchantment silk = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("silk_touch"));
        if (silk == null || tool == null || !tool.containsEnchantment(silk)) {
            plugin.msg().send(player, "<gray>You need a <white>Silk Touch</white> pickaxe to collect a spawner.");
            return;
        }

        EntityType spawned = null;
        if (block.getState() instanceof CreatureSpawner spawner) {
            try {
                spawned = spawner.getSpawnedType();
            } catch (Exception ignored) {
                spawned = null;
            }
        }
        event.setExpToDrop(0);
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5),
                plugin.spawners().createItem(spawned == null ? EntityType.ZOMBIE : spawned, 1));
        plugin.msg().send(player, "<green>Collected a <#e94fd0>"
                + SpawnerManager.pretty(spawned) + " Spawner</#e94fd0>.");
    }
}
