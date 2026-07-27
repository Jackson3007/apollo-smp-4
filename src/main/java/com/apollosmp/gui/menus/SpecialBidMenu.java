package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.special.SpecialAuctionManager;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Preset bid amounts, then a confirmation screen. */
public class SpecialBidMenu extends Gui {

    private static final int MINIMUM = 10;
    private static final int PLUS_10K = 12;
    private static final int PLUS_25K = 14;
    private static final int CUSTOM = 16;
    private static final int BACK = 22;

    public SpecialBidMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 3, "<#f9d423><bold>Place a Bid</bold>");
    }

    @Override
    protected void build() {
        SpecialAuctionManager auction = plugin.specialAuction();
        double min = auction.nextMinimumBid();
        double balance = plugin.economy().getBalance(viewer.getUniqueId());

        inventory.setItem(4, Items.of(Material.GOLD_BLOCK)
                .name("<#f9d423><bold>Your Balance: " + plugin.msg().money(balance) + "</bold>")
                .lore("<gray>Current bid: <white>"
                        + (auction.hasBid() ? plugin.msg().money(auction.currentBid()) : "none") + "</white>")
                .glow(true).hideAttributes().build());

        offer(MINIMUM, Material.IRON_INGOT, "Minimum Bid", min, balance);
        offer(PLUS_10K, Material.GOLD_INGOT, "Bid +$10,000", min + 10000, balance);
        offer(PLUS_25K, Material.DIAMOND, "Bid +$25,000", min + 25000, balance);

        inventory.setItem(CUSTOM, Items.of(Material.NAME_TAG)
                .name("<#5ad1e8><bold>Custom Amount</bold>")
                .lore("<gray>Click, then type your bid in chat.",
                        "<gray>Minimum: <#f9d423>" + plugin.msg().money(min) + "</#f9d423>")
                .build());

        inventory.setItem(BACK, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    private void offer(int slot, Material icon, String label, double amount, double balance) {
        boolean afford = balance >= amount;
        inventory.setItem(slot, Items.of(icon)
                .name((afford ? "<#f9d423>" : "<red>") + "<bold>" + label + "</bold>")
                .lore("<gray>Bid: <#f9d423>" + plugin.msg().money(amount) + "</#f9d423>",
                        afford ? "<gray>After: <white>" + plugin.msg().money(balance - amount) + "</white>"
                                : "<red>You can't afford this",
                        "", afford ? "<yellow>Click to review" : "<dark_gray>Not enough money")
                .hideAttributes().build());
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        SpecialAuctionManager auction = plugin.specialAuction();
        double min = auction.nextMinimumBid();

        switch (slot) {
            case BACK -> new SpecialAuctionMenu(plugin, player).open();
            case MINIMUM -> confirm(player, min);
            case PLUS_10K -> confirm(player, min + 10000);
            case PLUS_25K -> confirm(player, min + 25000);
            case CUSTOM -> {
                player.closeInventory();
                plugin.msg().send(player, "<#f9d423>Type your bid</#f9d423> <gray>(minimum "
                        + plugin.msg().money(min) + ", or 'cancel').");
                plugin.prompts().await(player, input -> {
                    try {
                        confirm(player, Double.parseDouble(input.replace(",", "").replace("$", "")));
                    } catch (NumberFormatException ex) {
                        plugin.msg().send(player, "<red>That's not a number.");
                        new SpecialBidMenu(plugin, player).open();
                    }
                });
            }
            default -> { /* no-op */ }
        }
    }

    /** Show exactly what the bid costs before anything is taken. */
    private void confirm(Player player, double amount) {
        double balance = plugin.economy().getBalance(player.getUniqueId());
        String lotName = plugin.specialAuction().lot() == null
                ? "the lot" : plugin.specialAuction().lot().name();

        new ConfirmMenu(plugin, player,
                "<#f9d423><bold>Confirm Bid</bold>",
                "Bid on " + lotName + "?",
                List.of("<gray>Bid amount: <#f9d423>" + plugin.msg().money(amount) + "</#f9d423>",
                        "<gray>Your balance: <white>" + plugin.msg().money(balance) + "</white>",
                        "<gray>After bidding: <white>"
                                + plugin.msg().money(Math.max(0, balance - amount)) + "</white>",
                        "",
                        "<gray>Held in escrow. Refunded in full",
                        "<gray>the moment someone outbids you."),
                () -> {
                    SpecialAuctionManager.BidResult result = plugin.specialAuction().bid(player, amount);
                    switch (result) {
                        case SUCCESS -> {
                            plugin.msg().send(player, "<green>You're the highest bidder at <#f9d423>"
                                    + plugin.msg().money(amount) + "</#f9d423>.");
                            new SpecialAuctionMenu(plugin, player).open();
                        }
                        case TOO_LOW -> {
                            plugin.msg().send(player, "<red>Someone got there first - bid higher.");
                            new SpecialBidMenu(plugin, player).open();
                        }
                        case NO_FUNDS -> {
                            plugin.msg().send(player, "<red>You can't afford that.");
                            new SpecialBidMenu(plugin, player).open();
                        }
                        case ALREADY_LEADING -> {
                            plugin.msg().send(player, "<yellow>You're already the highest bidder.");
                            new SpecialAuctionMenu(plugin, player).open();
                        }
                        case ENDED, NO_AUCTION -> {
                            plugin.msg().send(player, "<red>That auction has closed.");
                            player.closeInventory();
                        }
                    }
                },
                () -> new SpecialBidMenu(plugin, player).open()
        ).open();
    }
}
