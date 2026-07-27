package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.logistics.LogisticsManager;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Buy the two logistics blocks. */
public class LogisticsShopMenu extends Gui {

    private static final int DISTRIBUTION = 11;
    private static final int WHOLESALE = 15;
    private static final int BACK = 22;

    public LogisticsShopMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 3, "<#5ad1e8><bold>Logistics</bold>");
    }

    @Override
    protected void build() {
        LogisticsManager logistics = plugin.logistics();
        double balance = plugin.economy().getBalance(viewer.getUniqueId());

        inventory.setItem(4, Items.of(Material.SUNFLOWER)
                .name("<#f9d423><bold>Your Balance</bold>")
                .lore("<gray>You have <white>" + plugin.msg().money(balance) + "</white>",
                        "",
                        "<gray>Put a Distribution Block near your",
                        "<gray>businesses, then attach a Wholesale",
                        "<gray>Block right against it.")
                .glow(true).hideAttributes().build());

        double dPrice = logistics.distributionPrice();
        inventory.setItem(DISTRIBUTION, Items.of(LogisticsManager.DISTRIBUTION_BLOCK)
                .name("<#5ad1e8><bold>Distribution Block</bold>")
                .lore("<gray>Gathers from every business you own",
                        "<gray>within <white>" + logistics.businessRadius() + "</white> blocks.",
                        "<gray>Must touch a Wholesale Block, or",
                        "<gray>another Distribution Block that does.",
                        "",
                        "<gray>Price: <#f9d423>" + plugin.msg().money(dPrice) + "</#f9d423>",
                        balance >= dPrice ? "<yellow>Click to buy" : "<red>You can't afford this")
                .hideAttributes().build());

        double wPrice = logistics.wholesalePrice();
        inventory.setItem(WHOLESALE, Items.of(LogisticsManager.WHOLESALE_BLOCK)
                .name("<#f9d423><bold>Wholesale Block</bold>")
                .lore("<gray>Sells everything your distribution",
                        "<gray>blocks reach, every <white>"
                                + logistics.intervalMinutes() + "</white> minutes.",
                        "<gray>Must touch a Distribution Block.",
                        "<gray>Fee: <white>" + (int) logistics.feePercent() + "%</white> of each sale.",
                        "",
                        "<gray>Price: <#f9d423>" + plugin.msg().money(wPrice) + "</#f9d423>",
                        balance >= wPrice ? "<yellow>Click to buy" : "<red>You can't afford this")
                .hideAttributes().build());

        inventory.setItem(BACK, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == BACK) { new InvestMenu(plugin, player).open(); return; }

        LogisticsManager logistics = plugin.logistics();
        double price;
        ItemStack item;
        if (slot == DISTRIBUTION) {
            price = logistics.distributionPrice();
            item = logistics.createDistribution();
        } else if (slot == WHOLESALE) {
            price = logistics.wholesalePrice();
            item = logistics.createWholesale();
        } else {
            return;
        }

        if (!plugin.economy().has(player.getUniqueId(), price)) {
            plugin.msg().send(player, "<red>You can't afford that.");
            return;
        }
        plugin.economy().withdraw(player.getUniqueId(), price);
        Items.give(player, item);
        plugin.msg().send(player, "<green>Purchased for <#f9d423>"
                + plugin.msg().money(price) + "</#f9d423>.");
        redraw();
    }
}
