package com.apollosmp.merchant;

import com.apollosmp.ApolloSMP;
import com.apollosmp.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Crumbles the merchant's tools when their time is up, and keeps the countdown fresh. */
public class ToolExpiryTask {

    private final ApolloSMP plugin;

    public ToolExpiryTask(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            ItemStack[] contents = player.getInventory().getContents();
            boolean changed = false;
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item == null) continue;
                long expiry = plugin.merchant().expiryOf(item);
                if (expiry <= 0) continue;

                if (now >= expiry) {
                    player.getInventory().setItem(i, null);
                    plugin.msg().send(player, "<red>Your merchant tool crumbled to dust.");
                    player.playSound(player.getLocation(),
                            org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
                    changed = true;
                } else {
                    updateCountdown(item, expiry - now);
                }
            }
            if (changed) player.updateInventory();
        }
    }

    /** Rewrite the last lore line with the time left. */
    private void updateCountdown(ItemStack item, long remaining) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<Component> lore = meta.lore();
        if (lore == null || lore.isEmpty()) return;

        String text = "<red>Crumbles in " + format(remaining) + "</red>";
        List<Component> updated = new ArrayList<>(lore);
        updated.set(updated.size() - 1, Msg.lore(text));
        meta.lore(updated);
        item.setItemMeta(meta);
    }

    private String format(long millis) {
        long minutes = millis / 60000;
        long hours = minutes / 60;
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        return minutes + "m";
    }
}
