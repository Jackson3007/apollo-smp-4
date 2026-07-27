package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.special.SpecialAuctionManager;
import com.apollosmp.special.SpecialBusiness;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The bidding screen for today's mystery business. */
public class SpecialAuctionMenu extends Gui {

    private static final int LOT = 13;
    private static final int INFO = 11;
    private static final int BID = 15;
    private static final int CLAIM = 30;
    private static final int CLOSE = 32;

    public SpecialAuctionMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 4, "<gradient:#f9d423:#ff4e50><bold>Mystery Business Auction</bold></gradient>");
    }

    @Override
    protected void build() {
        SpecialAuctionManager auction = plugin.specialAuction();
        SpecialBusiness lot = auction.lot();

        if (lot == null) {
            inventory.setItem(LOT, Items.of(Material.BARRIER)
                    .name("<gray>No auction running")
                    .lore("<gray>A new lot goes up on the next cycle.").build());
        } else {
            List<String> lore = new ArrayList<>();
            lore.add("<gray>" + lot.description());
            lore.add("<dark_gray>―――――――――――");
            lore.add("<gray>Rarity: <white>" + lot.rarity() + "</white>");
            lore.add("<gray>Industry: <white>" + lot.industry() + "</white>");
            lore.add("<gray>Known product: <white>"
                    + SpecialAuctionManager.pretty(lot.knownItem()) + "</white>");
            lore.add("<gray>Mystery product: <#e94fd0>???</#e94fd0>");
            lore.add("<gray>Est. daily profit: <#f9d423>" + plugin.msg().money(lot.profitMin())
                    + " - " + plugin.msg().money(lot.profitMax()) + "</#f9d423>");
            lore.add("");
            lore.add("<gray>Current bid: <#f9d423>"
                    + (auction.hasBid() ? plugin.msg().money(auction.currentBid()) : "none yet") + "</#f9d423>");
            lore.add("<gray>Highest bidder: <white>"
                    + (auction.bidderName() == null ? "-" : auction.bidderName()) + "</white>");
            lore.add("<gray>Time left: <white>" + auction.timeLeftText() + "</white>");

            inventory.setItem(LOT, Items.of(lot.block())
                    .name("<gradient:#f9d423:#ff4e50><bold>" + lot.name() + "</bold></gradient>")
                    .lore(lore.toArray(new String[0]))
                    .glow(true).hideAttributes().build());

            inventory.setItem(INFO, Items.of(Material.BOOK)
                    .name("<#5ad1e8><bold>Business Information</bold>")
                    .lore("<gray>What's known, and what stays",
                            "<gray>sealed until the hammer falls.",
                            "", "<yellow>Click to read")
                    .build());

            inventory.setItem(BID, Items.of(Material.GOLD_INGOT)
                    .name("<#f9d423><bold>Place a Bid</bold>")
                    .lore("<gray>Next minimum: <#f9d423>"
                                    + plugin.msg().money(auction.nextMinimumBid()) + "</#f9d423>",
                            "<gray>Your balance: <white>"
                                    + plugin.msg().money(plugin.economy().getBalance(viewer.getUniqueId()))
                                    + "</white>",
                            "", "<yellow>Click to choose an amount")
                    .build());
        }

        int pending = plugin.specialAuction().claimsFor(viewer.getUniqueId()).size();
        inventory.setItem(CLAIM, Items.of(Material.CHEST_MINECART)
                .name("<#f9d423>Unclaimed Wins")
                .lore("<gray>Waiting: <white>" + pending + "</white>",
                        "<gray>Businesses you won but couldn't carry.",
                        "", "<yellow>Click to collect")
                .glow(pending > 0).hideAttributes().build());

        inventory.setItem(CLOSE, Items.of(Material.BARRIER).name("<red>Close").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        switch (slot) {
            case CLOSE -> player.closeInventory();
            case INFO -> { player.closeInventory(); sendInfo(player); }
            case BID -> {
                if (plugin.specialAuction().lot() != null) new SpecialBidMenu(plugin, player).open();
            }
            case CLAIM -> {
                int given = plugin.specialAuction().claim(player);
                plugin.msg().send(player, given == 0
                        ? "<gray>Nothing waiting for you."
                        : "<green>Collected <white>" + given + "</white> business(es).");
                redraw();
            }
            default -> { /* no-op */ }
        }
    }

    private void sendInfo(Player player) {
        SpecialBusiness lot = plugin.specialAuction().lot();
        if (lot == null) return;
        var msg = plugin.msg();
        msg.sendRaw(player, "<gradient:#f9d423:#ff4e50><bold>" + lot.name() + "</bold></gradient>");
        msg.sendRaw(player, "<gray>" + lot.description());
        msg.sendRaw(player, "");
        msg.sendRaw(player, "<#f9d423>Known</#f9d423>");
        msg.sendRaw(player, " <gray>Rarity: <white>" + lot.rarity() + "</white>");
        msg.sendRaw(player, " <gray>Industry: <white>" + lot.industry() + "</white>");
        msg.sendRaw(player, " <gray>Product: <white>"
                + SpecialAuctionManager.pretty(lot.knownItem()) + "</white>");
        msg.sendRaw(player, " <gray>Est. daily profit: <#f9d423>"
                + plugin.msg().money(lot.profitMin()) + " - "
                + plugin.msg().money(lot.profitMax()) + "</#f9d423>");
        msg.sendRaw(player, "");
        msg.sendRaw(player, "<#e94fd0>Sealed until you win</#e94fd0>");
        msg.sendRaw(player, " <dark_gray>Second product, exact output, cycle time,</dark_gray>");
        msg.sendRaw(player, " <dark_gray>storage size, special trait, exact profit.</dark_gray>");
    }
}
