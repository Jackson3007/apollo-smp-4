package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.rtp.RtpManager;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class MainMenu extends Gui {

    public MainMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 3, "<gradient:#f9d423:#ff4e50><bold>Apollo Menu</bold></gradient>");
    }

    @Override
    protected void build() {
        double bal = plugin.economy().getBalance(viewer.getUniqueId());
        int mail = plugin.mailbox().size(viewer.getUniqueId());

        inventory.setItem(4, Items.of(Material.SUNFLOWER)
                .name("<#f9d423><bold>Your Balance</bold>")
                .lore("<gray>You have <white>" + plugin.msg().money(bal) + "</white>",
                        "<dark_gray>Use /pay to send money")
                .glow(true).hideAttributes().build());

        inventory.setItem(10, Items.of(Material.HOPPER)
                .name("<#ff4e50><bold>Sell to Server</bold>")
                .lore("<gray>Instantly sell your loot", "", "<yellow>Click to open")
                .hideAttributes().build());

        inventory.setItem(12, Items.of(Material.CHEST)
                .name("<#f9d423><bold>Auction House</bold>")
                .lore("<gray>Buy & sell with other players", "", "<yellow>Click to open")
                .hideAttributes().build());

        inventory.setItem(14, Items.of(Material.WRITABLE_BOOK)
                .name("<#f9d423><bold>Buy Orders</bold>")
                .lore("<gray>Request items at your price", "", "<yellow>Click to open")
                .hideAttributes().build());

        inventory.setItem(16, Items.of(Material.RED_BED)
                .name("<#ff4e50><bold>Homes</bold>")
                .lore("<gray>Teleport to your homes", "", "<yellow>Click to open")
                .hideAttributes().build());

        inventory.setItem(20, Items.of(Material.ENDER_PEARL)
                .name("<#f9d423><bold>Random Teleport</bold>")
                .lore("<gray>Warp to the wilderness", "", "<yellow>Click to teleport")
                .hideAttributes().build());

        inventory.setItem(22, Items.of(Material.CHEST_MINECART)
                .name("<#f9d423><bold>Collection Box</bold>")
                .lore("<gray>Waiting items: <white>" + mail + "</white>",
                        "<dark_gray>Expired auctions & fulfilled orders", "",
                        "<yellow>Click to collect")
                .glow(mail > 0).hideAttributes().build());

        inventory.setItem(24, Items.of(Material.GOLD_BLOCK)
                .name("<#f9d423><bold>Baltop</bold>")
                .lore("<gray>See the richest players", "", "<yellow>Click to view")
                .hideAttributes().build());

        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        switch (slot) {
            case 10 -> new SellMenu(plugin, player).open();
            case 12 -> new AuctionMenu(plugin, player, false, 0).open();
            case 14 -> new OrdersMenu(plugin, player, false, 0).open();
            case 16 -> new HomesMenu(plugin, player).open();
            case 20 -> {
                player.closeInventory();
                RtpManager.Result r = plugin.rtp().attempt(player, false);
                switch (r) {
                    case SUCCESS -> plugin.msg().send(player, "<green>Teleported to the wild!");
                    case COOLDOWN -> plugin.msg().send(player, "<red>Please wait "
                            + plugin.rtp().cooldownLeft(player.getUniqueId()) + "s before using RTP again.");
                    case NO_FUNDS -> plugin.msg().send(player, "<red>You can't afford a random teleport.");
                    case NO_WORLD -> plugin.msg().send(player, "<red>RTP world is not loaded.");
                    case FAILED -> plugin.msg().send(player, "<red>Couldn't find a safe spot. Try again.");
                }
            }
            case 22 -> {
                int collected = plugin.mailbox().collect(player);
                if (collected == 0) plugin.msg().send(player, "<gray>Your collection box is empty.");
                else plugin.msg().send(player, "<green>Collected <white>" + collected + "</white> item stack(s).");
                redraw();
            }
            case 24 -> {
                player.closeInventory();
                showBaltop(player);
            }
            default -> { /* ignore */ }
        }
    }

    private void showBaltop(Player player) {
        List<java.util.Map.Entry<java.util.UUID, Double>> top = plugin.economy().top(10);
        plugin.msg().sendRaw(player, "<gradient:#f9d423:#ff4e50><bold>Top Balances</bold></gradient>");
        int rank = 1;
        for (java.util.Map.Entry<java.util.UUID, Double> e : top) {
            plugin.msg().sendRaw(player, " <#f9d423>" + rank + ".</#f9d423> <white>"
                    + plugin.economy().nameOf(e.getKey()) + "</white> <gray>-</gray> "
                    + plugin.msg().money(e.getValue()));
            rank++;
        }
    }
}
