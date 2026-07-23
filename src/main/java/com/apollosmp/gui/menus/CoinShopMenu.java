package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.items.CustomItems;
import com.apollosmp.util.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Spend Sky Coins on exclusive gear. */
public class CoinShopMenu extends Gui {

    // Parallel arrays: item id + price + display slot.
    private static final String[] IDS = {
            CustomItems.DRILL, CustomItems.BLADE, CustomItems.CLEAVER,
            CustomItems.BOW, CustomItems.BOOTS, CustomItems.VOTE_KEY
    };
    private static final int[] PRICES = {60, 50, 45, 35, 40, 15};
    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15};

    public CoinShopMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 4, "<gradient:#5ad1e8:#7d3cff><bold>Coin Shop</bold></gradient>");
    }

    @Override
    protected void build() {
        int balance = plugin.skyCoins().get(viewer.getUniqueId());
        inventory.setItem(4, Items.of(Material.NETHER_STAR)
                .name("<#5ad1e8><bold>Sky Coins: " + balance + "</bold>")
                .glow(true).hideAttributes().build());

        for (int i = 0; i < IDS.length; i++) {
            ItemStack display = plugin.customItems().build(IDS[i]);
            if (display == null) continue;
            int price = PRICES[i];
            boolean afford = balance >= price;

            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(com.apollosmp.util.Msg.lore(""));
                lore.add(com.apollosmp.util.Msg.lore("<gray>Price: <#5ad1e8>" + price + " coins</#5ad1e8>"));
                lore.add(com.apollosmp.util.Msg.lore(afford ? "<green>Click to buy" : "<red>Not enough coins"));
                meta.lore(lore);
                display.setItemMeta(meta);
            }
            inventory.setItem(SLOTS[i], display);
        }

        inventory.setItem(31, Items.of(Material.ARROW).name("<gray>Back to Vote").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == 31) { new VoteMenu(plugin, player).open(); return; }

        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == slot) {
                int price = PRICES[i];
                if (!plugin.skyCoins().has(player.getUniqueId(), price)) {
                    plugin.msg().send(player, "<red>You need " + price + " Sky Coins for that.");
                    return;
                }
                ItemStack item = plugin.customItems().build(IDS[i]);
                if (item == null) return;
                plugin.skyCoins().take(player.getUniqueId(), price);
                Items.give(player, item);
                plugin.msg().send(player, "<green>Purchased! <gray>(-" + price + " Sky Coins)");
                redraw();
                return;
            }
        }
    }
}
