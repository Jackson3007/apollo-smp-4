package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.menus.MainMenu;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** A compass that opens the server hub, so new players always have a way in. */
public class MenuItemListener implements Listener {

    private final ApolloSMP plugin;
    private final NamespacedKey key;

    public MenuItemListener(ApolloSMP plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "apollo_menu_item");
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("menu-compass.enabled", true);
    }

    /** Give the compass if the player doesn't already have one. */
    public void give(Player player) {
        if (!enabled()) return;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isMenuItem(stack)) return;
        }
        ItemStack compass = build();
        int slot = plugin.getConfig().getInt("menu-compass.slot", 8);
        if (slot < 0 || slot > 8) slot = 8;
        if (player.getInventory().getItem(slot) == null) {
            player.getInventory().setItem(slot, compass);
        } else {
            Items.give(player, compass);
        }
    }

    private ItemStack build() {
        ItemStack compass = Items.of(Material.COMPASS)
                .name("<gradient:#f9d423:#ff4e50><bold>Apollo Menu</bold></gradient>")
                .lore("<gray>Right-click to open the server hub.",
                        "<dark_gray>Everything in one place.")
                .glow(true).hideAttributes().build();
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            compass.setItemMeta(meta);
        }
        return compass;
    }

    public boolean isMenuItem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.COMPASS) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (!isMenuItem(event.getItem())) return;

        event.setCancelled(true);
        new MainMenu(plugin, event.getPlayer()).open();
    }
}
