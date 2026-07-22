package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A drop-in sell panel: the player drops any items into the top 45 slots, sees a
 * live total of what they'll earn, and clicks SELL to confirm. Unsellable items
 * are left untouched and returned when the menu closes.
 */
public class SellMenu extends Gui {

    private static final int DROP_END = 45;   // slots 0..44 are the drop zone
    private static final int SELL_BUTTON = 49;
    private static final int CLOSE_BUTTON = 53;

    public SellMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 6, "<#ff4e50><bold>Sell</bold>  <dark_gray>|</dark_gray> <gray>drop items below");
    }

    @Override
    public boolean allowsDrop() {
        return true;
    }

    @Override
    public boolean isDropSlot(int slot) {
        return slot >= 0 && slot < DROP_END;
    }

    @Override
    protected void build() {
        // Control row background.
        for (int i = DROP_END; i < 54; i++) {
            inventory.setItem(i, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
        inventory.setItem(45, Items.of(Material.HOPPER)
                .name("<#f9d423><bold>How to sell</bold>")
                .lore("<gray>Drop items into the top area.",
                        "<gray>Sellable items show a total below.",
                        "<gray>Anything that can't be sold is",
                        "<gray>handed back when you close.")
                .hideAttributes().build());
        drawSellButton(0, 0, 0);
        inventory.setItem(CLOSE_BUTTON, Items.of(Material.BARRIER)
                .name("<red><bold>Close</bold>")
                .lore("<gray>Returns your items").build());
    }

    private void drawSellButton(int sellableCount, double total, int unsellableCount) {
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>―――――――――――");
        lore.add("<gray>Sellable items: <white>" + sellableCount + "</white>");
        lore.add("<gray>You'll earn: <#f9d423>" + plugin.msg().money(total) + "</#f9d423>");
        if (unsellableCount > 0) {
            lore.add("<red>Can't sell: <white>" + unsellableCount + "</white> <red>item(s)");
        }
        lore.add("");
        lore.add(total > 0 ? "<green><bold>Click to SELL</bold>" : "<dark_gray>Drop items above first");
        inventory.setItem(SELL_BUTTON, Items.of(total > 0 ? Material.EMERALD_BLOCK : Material.GRAY_DYE)
                .name("<#f9d423><bold>Sell Everything</bold>")
                .lore(lore).glow(total > 0).hideAttributes().build());
    }

    @Override
    public void onDropChanged(Player player) {
        int sellable = 0;
        int unsellable = 0;
        double total = 0;
        for (int i = 0; i < DROP_END; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            if (plugin.sell().isSellable(stack)) {
                sellable += stack.getAmount();
                total += plugin.sell().valueOf(stack);
            } else {
                unsellable += stack.getAmount();
            }
        }
        drawSellButton(sellable, total, unsellable);
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == SELL_BUTTON) {
            sellContents(player);
        } else if (slot == CLOSE_BUTTON) {
            player.closeInventory();
        }
    }

    private void sellContents(Player player) {
        int soldQty = 0;
        double earned = 0;
        for (int i = 0; i < DROP_END; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            if (!plugin.sell().isSellable(stack)) continue;
            soldQty += stack.getAmount();
            earned += plugin.sell().valueOf(stack);
            inventory.setItem(i, null);
        }
        if (soldQty == 0) {
            plugin.msg().send(player, "<red>There's nothing sellable in the panel.");
            return;
        }
        plugin.economy().deposit(player.getUniqueId(), earned);
        plugin.msg().send(player, "<green>Sold <white>" + soldQty + "</white> item(s) for <#f9d423>"
                + plugin.msg().money(earned) + "</#f9d423>.");
        onDropChanged(player);
    }

    @Override
    public void onClose(Player player) {
        for (int i = 0; i < DROP_END; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack == null || stack.getType().isAir()) continue;
            Items.give(player, stack);
            inventory.setItem(i, null);
        }
    }
}
