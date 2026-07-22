package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.auction.AuctionManager;
import com.apollosmp.auction.Listing;
import com.apollosmp.gui.Gui;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class AuctionMenu extends Gui {

    private static final int PAGE_SIZE = 45;

    private final boolean mine;
    private final int page;
    private List<Listing> pageItems = new ArrayList<>();

    public AuctionMenu(ApolloSMP plugin, Player viewer, boolean mine, int page) {
        super(plugin, viewer, 6, mine
                ? "<#ff4e50><bold>My Listings</bold>"
                : "<gradient:#f9d423:#ff4e50><bold>Auction House</bold></gradient>");
        this.mine = mine;
        this.page = Math.max(0, page);
    }

    @Override
    protected void build() {
        List<Listing> all = mine
                ? plugin.auctions().bySeller(viewer.getUniqueId())
                : plugin.auctions().active();

        int from = page * PAGE_SIZE;
        int to = Math.min(all.size(), from + PAGE_SIZE);
        pageItems = (from >= all.size()) ? new ArrayList<>() : new ArrayList<>(all.subList(from, to));

        for (int i = 0; i < pageItems.size(); i++) {
            Listing listing = pageItems.get(i);
            ItemStack icon = listing.item();
            ItemMeta meta = icon.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(com.apollosmp.util.Msg.lore("<dark_gray>―――――――――――"));
            lore.add(com.apollosmp.util.Msg.lore("<gray>Seller: <white>" + listing.sellerName() + "</white>"));
            lore.add(com.apollosmp.util.Msg.lore("<gray>Price: <#f9d423>" + plugin.msg().money(listing.price()) + "</#f9d423>"));
            lore.add(com.apollosmp.util.Msg.lore("<gray>Time left: <white>" + formatDuration(listing.millisLeft()) + "</white>"));
            lore.add(com.apollosmp.util.Msg.lore(""));
            if (mine) {
                lore.add(com.apollosmp.util.Msg.lore("<red>Click to cancel & reclaim"));
            } else if (listing.seller().equals(viewer.getUniqueId())) {
                lore.add(com.apollosmp.util.Msg.lore("<dark_gray>This is your listing"));
            } else {
                lore.add(com.apollosmp.util.Msg.lore("<yellow>Click to buy"));
            }
            if (meta != null) {
                // preserve existing lore then append info
                List<net.kyori.adventure.text.Component> existing = meta.lore();
                if (existing != null) {
                    existing.addAll(lore);
                    meta.lore(existing);
                } else {
                    meta.lore(lore);
                }
                icon.setItemMeta(meta);
            }
            inventory.setItem(i, icon);
        }

        for (int i = PAGE_SIZE; i < 54; i++) {
            inventory.setItem(i, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
        }

        inventory.setItem(45, Items.of(Material.ARROW).name("<gray>Previous Page").build());
        inventory.setItem(46, Items.of(Material.BARRIER).name("<gray>Back to Menu").build());
        inventory.setItem(47, Items.of(mine ? Material.CHEST : Material.PLAYER_HEAD)
                .name(mine ? "<#f9d423>Browse All" : "<#f9d423>My Listings")
                .lore("<gray>Active listings: <white>"
                        + plugin.auctions().countBySeller(viewer.getUniqueId()) + "</white>")
                .build());
        inventory.setItem(49, Items.of(Material.PAPER)
                .name("<#f9d423><bold>Page " + (page + 1) + "</bold>")
                .lore("<gray>Sell an item with <white>/ah sell <price></white>").build());
        inventory.setItem(51, Items.of(Material.CHEST_MINECART)
                .name("<#f9d423>Collection Box")
                .lore("<gray>Waiting items: <white>"
                        + plugin.mailbox().size(viewer.getUniqueId()) + "</white>",
                        "<yellow>Click to collect").build());
        inventory.setItem(53, Items.of(Material.ARROW).name("<gray>Next Page").build());
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot >= 0 && slot < PAGE_SIZE) {
            if (slot >= pageItems.size()) return;
            Listing listing = pageItems.get(slot);
            if (mine) {
                if (plugin.auctions().cancel(player.getUniqueId(), listing.id())) {
                    plugin.msg().send(player, "<green>Listing cancelled. Item is in your collection box.");
                }
                redraw();
            } else {
                handleBuy(player, listing);
            }
            return;
        }
        switch (slot) {
            case 45 -> { if (page > 0) new AuctionMenu(plugin, player, mine, page - 1).open(); }
            case 46 -> new MainMenu(plugin, player).open();
            case 47 -> new AuctionMenu(plugin, player, !mine, 0).open();
            case 51 -> {
                int n = plugin.mailbox().collect(player);
                plugin.msg().send(player, n == 0 ? "<gray>Your collection box is empty."
                        : "<green>Collected <white>" + n + "</white> item stack(s).");
                redraw();
            }
            case 53 -> new AuctionMenu(plugin, player, mine, page + 1).open();
            default -> { /* no-op */ }
        }
    }

    private void handleBuy(Player player, Listing listing) {
        AuctionManager.BuyResult result = plugin.auctions().buy(player, listing.id());
        switch (result) {
            case SUCCESS -> {
                plugin.msg().send(player, "<green>Purchased <white>" + Items.displayName(listing.item())
                        + "</white> for <#f9d423>" + plugin.msg().money(listing.price()) + "</#f9d423>.");
                redraw();
            }
            case NOT_FOUND -> {
                plugin.msg().send(player, "<red>That listing is no longer available.");
                redraw();
            }
            case OWN_LISTING -> plugin.msg().send(player, "<red>You can't buy your own listing.");
            case NO_FUNDS -> plugin.msg().send(player, "<red>You can't afford that.");
        }
    }

    private String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}
