package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import com.apollosmp.util.Msg;
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
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Lets players mine spawners with Silk Touch and keep the mob type. */
public class SpawnerListener implements Listener {

    private final ApolloSMP plugin;
    private final NamespacedKey typeKey;

    public SpawnerListener(ApolloSMP plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "apollo_spawner_type");
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("spawners.silk-touch-pickup", true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!enabled()) return;
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        Enchantment silk = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("silk_touch"));
        if (silk == null || tool == null || !tool.containsEnchantment(silk)) {
            plugin.msg().send(player, "<gray>You need a <white>Silk Touch</white> pickaxe to collect a spawner.");
            return;
        }

        EntityType spawned = null;
        BlockState state = block.getState();
        if (state instanceof CreatureSpawner spawner) {
            try {
                spawned = spawner.getSpawnedType();
            } catch (Exception ignored) {
                spawned = null;
            }
        }

        event.setExpToDrop(0);
        event.setDropItems(false);
        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), buildSpawner(spawned));

        plugin.msg().send(player, "<green>Collected a <#e94fd0>"
                + (spawned == null ? "Empty" : pretty(spawned.name())) + " Spawner</#e94fd0>.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (!enabled()) return;
        if (event.getBlockPlaced().getType() != Material.SPAWNER) return;

        ItemStack placed = event.getItemInHand();
        if (placed == null || placed.getItemMeta() == null) return;
        String stored = placed.getItemMeta().getPersistentDataContainer()
                .get(typeKey, PersistentDataType.STRING);
        if (stored == null || stored.isEmpty()) return;

        EntityType type;
        try {
            type = EntityType.valueOf(stored);
        } catch (IllegalArgumentException ex) {
            return;
        }

        BlockState state = event.getBlockPlaced().getState();
        if (state instanceof CreatureSpawner spawner) {
            spawner.setSpawnedType(type);
            spawner.update(true, false);
        }
    }

    /** Build a spawner item that remembers which mob it spawns. */
    private ItemStack buildSpawner(EntityType type) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String label = type == null ? "Empty" : pretty(type.name());
        meta.displayName(Msg.lore("<#e94fd0>" + label + " Spawner</#e94fd0>"));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Msg.lore("<gray>Spawns: <white>" + label + "</white>"));
        lore.add(Msg.lore("<dark_gray>Place it down to keep this mob"));
        meta.lore(lore);

        if (type != null) {
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.name());
        }
        item.setItemMeta(meta);
        return item;
    }

    private String pretty(String raw) {
        String[] words = raw.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
