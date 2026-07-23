package com.apollosmp.sell;

import com.apollosmp.ApolloSMP;
import com.apollosmp.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Writes a small price line onto items while they're in a player's inventory,
 * and takes it back off again the moment they leave.
 *
 * The line shows the price of ONE item, not the stack, so identical items still
 * have identical lore and stack together normally.
 */
public class WorthTags implements Listener {

    private final ApolloSMP plugin;
    private final NamespacedKey key;

    public WorthTags(ApolloSMP plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "apollo_worth");
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("sell.worth-lore", true);
    }

    // ---- the periodic pass ----
    public void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            PlayerInventory inv = player.getInventory();
            for (int slot = 0; slot < 41; slot++) {
                ItemStack stack = inv.getItem(slot);
                if (stack == null || stack.getType().isAir()) continue;
                boolean changed = enabled() ? mark(stack) : strip(stack);
                if (changed) inv.setItem(slot, stack);
            }
        }
    }

    /** Add or refresh the price line. Returns true if the item changed. */
    public boolean mark(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        if (!plugin.sell().isSellable(stack)) return strip(stack);

        double unit = plugin.sell().priceOf(stack.getType());
        if (unit <= 0) return strip(stack);

        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;

        Double written = meta.getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
        if (written != null && Math.abs(written - unit) < 0.0001) return false; // already right

        List<Component> lore = meta.lore();
        lore = lore == null ? new ArrayList<>() : new ArrayList<>(lore);
        // Our line is always last, so refreshing means dropping the old one first.
        if (written != null && !lore.isEmpty()) lore.remove(lore.size() - 1);
        lore.add(Msg.lore("<#f9d423>" + plugin.msg().money(unit) + "</#f9d423>"));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.DOUBLE, unit);
        stack.setItemMeta(meta);
        return true;
    }

    /** Take the price line back off. Returns true if the item changed. */
    public boolean strip(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        if (!meta.getPersistentDataContainer().has(key, PersistentDataType.DOUBLE)) return false;

        List<Component> lore = meta.lore();
        if (lore != null && !lore.isEmpty()) {
            List<Component> trimmed = new ArrayList<>(lore);
            trimmed.remove(trimmed.size() - 1);
            meta.lore(trimmed.isEmpty() ? null : trimmed);
        }
        meta.getPersistentDataContainer().remove(key);
        stack.setItemMeta(meta);
        return true;
    }

    // ---- keep the tag inside the player's own inventory ----
    /** Tag on pickup so the incoming stack merges with what's already held. */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!enabled()) return;
        if (!(event.getEntity() instanceof Player)) return;
        ItemStack stack = event.getItem().getItemStack();
        if (mark(stack)) event.getItem().setItemStack(stack);
    }

    /** Dropped items go back to normal. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        ItemStack stack = event.getItemDrop().getItemStack();
        if (strip(stack)) event.getItemDrop().setItemStack(stack);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        for (ItemStack stack : event.getDrops()) strip(stack);
    }

    /** Anything moved into a chest, furnace or any other container gets cleaned. */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        sweepLater(event.getView().getTopInventory());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        sweepLater(event.getView().getTopInventory());
    }

    /** Clean a container a tick later, once the move has actually happened. */
    private void sweepLater(Inventory inventory) {
        if (inventory == null) return;
        if (inventory instanceof PlayerInventory) return;
        if (inventory.getHolder() instanceof Player) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack[] contents = inventory.getContents();
            boolean changed = false;
            for (int i = 0; i < contents.length; i++) {
                ItemStack stack = contents[i];
                if (stack == null) continue;
                if (strip(stack)) {
                    inventory.setItem(i, stack);
                    changed = true;
                }
            }
            if (changed) inventory.getViewers().forEach(v -> {
                if (v instanceof Player p) p.updateInventory();
            });
        });
    }
}
