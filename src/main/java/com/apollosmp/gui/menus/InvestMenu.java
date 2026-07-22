package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** The /invest hub. Right now: buy a business. More options later. */
public class InvestMenu extends Gui {

    public InvestMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 3, "<gradient:#f9d423:#ff4e50><bold>Investments</bold></gradient>");
    }

    @Override
    protected void build() {
        double bal = plugin.economy().getBalance(viewer.getUniqueId());
        inventory.setItem(4, Items.of(Material.SUNFLOWER)
                .name("<#f9d423><bold>Your Balance</bold>")
                .lore("<gray>You have <white>" + plugin.msg().money(bal) + "</white>")
                .glow(true).hideAttributes().build());

        inventory.setItem(11, Items.of(Material.EMERALD_BLOCK)
                .name("<gradient:#b7f542:#3dbb2f><bold>Buy a Business</bold></gradient>")
                .lore("<gray>Purchase a business block that",
                        "<gray>generates goods over time.",
                        "<gray>Place it, then collect or sell.",
                        "",
                        "<yellow>Click to browse businesses")
                .glow(true).hideAttributes().build());

        inventory.setItem(13, Items.of(Material.GOLD_INGOT)
                .name("<#f9d423><bold>Owned Businesses</bold>")
                .lore("<gray>You own <white>"
                        + plugin.businesses().countOwnedBy(viewer.getUniqueId()) + "</white> business(es)",
                        "<dark_gray>Right-click a placed block to manage it")
                .hideAttributes().build());

        inventory.setItem(15, Items.of(Material.GRAY_DYE)
                .name("<dark_gray><bold>Stock Market</bold>")
                .lore("<dark_gray>Coming soon...")
                .build());

        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == 11) {
            new BusinessShopMenu(plugin, player).open();
        }
    }
}
