package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.town.TownManager;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Everything about the chunk you're standing in, plus claiming. Written so the
 * only buttons shown are ones that actually apply right now.
 */
public class TownLandMenu extends Gui {

    private static final int STATUS = 4;
    private static final int CLAIM = 19;
    private static final int UNCLAIM = 20;
    private static final int ACTION = 22;      // buy / rent / end rental - whatever fits
    private static final int LIST_SALE = 24;
    private static final int LIST_RENT = 25;
    private static final int ALL_PLOTS = 31;
    private static final int BACK = 40;

    public TownLandMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 5, "<green><bold>Land</bold>");
    }

    @Override
    protected void build() {
        Town myTown = plugin.towns().getTownOf(viewer.getUniqueId());
        if (myTown == null) { viewer.closeInventory(); return; }

        String here = TownManager.chunkKey(viewer.getLocation());
        Town hereTown = plugin.towns().getTownAtLoc(viewer.getLocation());
        boolean ourLand = hereTown != null && hereTown.name().equalsIgnoreCase(myTown.name());
        UUID plotOwner = hereTown == null ? null : hereTown.plotOwner(here);
        Double salePrice = hereTown == null ? null : hereTown.plotPrice(here);
        Double rentPrice = hereTown == null ? null : hereTown.rentPrice(here);
        boolean mine = plotOwner != null && plotOwner.equals(viewer.getUniqueId());

        // ---- what am I standing on? ----
        List<String> status = new ArrayList<>();
        if (hereTown == null) {
            status.add("<gray>Unclaimed wilderness");
            status.add("<gray>Nobody owns this chunk.");
        } else if (!ourLand) {
            status.add("<gray>Owned by <#ff4e50>" + hereTown.name() + "</#ff4e50>");
            status.add("<gray>You can't build or trade here.");
        } else {
            status.add("<gray>Part of <#f9d423>" + hereTown.name() + "</#f9d423>");
            if (plotOwner == null) {
                status.add("<gray>Town land - no plot owner");
            } else {
                String name = plugin.getServer().getOfflinePlayer(plotOwner).getName();
                status.add(mine
                        ? "<green>This plot is yours"
                        : "<gray>Rented/owned by <#e94fd0>" + (name == null ? "Unknown" : name) + "</#e94fd0>");
            }
            if (salePrice != null) {
                status.add("<gray>For sale: <#f9d423>" + plugin.msg().money(salePrice) + "</#f9d423>");
            }
            if (rentPrice != null) {
                status.add("<gray>For rent: <#f9d423>" + plugin.msg().money(rentPrice)
                        + "</#f9d423> <gray>per " + plugin.towns().rentPeriodLabel() + "</gray>");
            }
        }
        status.add("");
        status.add("<gray>Land used: <white>" + myTown.claims().size() + " / "
                + plugin.towns().claimLimit(myTown) + "</white> chunks");

        inventory.setItem(STATUS, Items.of(Material.MAP)
                .name("<#f9d423><bold>Where You're Standing</bold>")
                .lore(status).glow(true).hideAttributes().build());

        // ---- claiming ----
        inventory.setItem(CLAIM, Items.of(Material.GRASS_BLOCK)
                .name("<green><bold>Claim This Chunk</bold>")
                .lore("<gray>Cost: <#f9d423>"
                                + plugin.msg().money(plugin.getConfig().getDouble("towns.claim-cost", 500.0))
                                + "</#f9d423>",
                        "<gray>Must touch land you already own.",
                        "", hereTown == null ? "<yellow>Click to claim" : "<dark_gray>Already claimed")
                .build());

        inventory.setItem(UNCLAIM, Items.of(Material.DIRT)
                .name("<yellow><bold>Unclaim This Chunk</bold>")
                .lore("<gray>Hand it back to the wilderness.",
                        "", ourLand ? "<yellow>Click to unclaim" : "<dark_gray>Not your land")
                .build());

        // ---- one contextual action ----
        inventory.setItem(ACTION, buildAction(ourLand, plotOwner, mine, salePrice, rentPrice));

        // ---- listing, for anyone allowed to sell plots ----
        boolean canList = ourLand && myTown.hasPerm(viewer.getUniqueId(),
                com.apollosmp.town.TownPerm.SELL_PLOT);
        if (canList) {
            boolean listed = hereTown.isListed(here);
            inventory.setItem(LIST_SALE, Items.of(Material.OAK_SIGN)
                    .name("<#f9d423><bold>Sell This Plot</bold>")
                    .lore("<gray>One payment, then it's theirs.",
                            salePrice == null ? "" : "<gray>Listed at <#f9d423>"
                                    + plugin.msg().money(salePrice) + "</#f9d423>",
                            "", "<yellow>Click to set a price")
                    .glow(salePrice != null).hideAttributes().build());

            inventory.setItem(LIST_RENT, Items.of(Material.PAPER)
                    .name("<#5ad1e8><bold>Rent Out This Plot</bold>")
                    .lore("<gray>They pay every "
                                    + plugin.towns().rentPeriodLabel() + ", forever.",
                            "<gray>Money goes to the town bank.",
                            rentPrice == null ? "" : "<gray>Listed at <#f9d423>"
                                    + plugin.msg().money(rentPrice) + "</#f9d423>",
                            "", listed && rentPrice == null
                                    ? "<yellow>Click to switch to renting"
                                    : "<yellow>Click to set a price")
                    .glow(rentPrice != null).hideAttributes().build());
        }

        inventory.setItem(ALL_PLOTS, Items.of(Material.FILLED_MAP)
                .name("<#e94fd0><bold>All Plots</bold>")
                .lore("<gray>Every plot in town and who has it.",
                        "", "<yellow>Click to browse").build());


        inventory.setItem(BACK, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    /** One button that does whatever makes sense on this chunk. */
    private ItemStack buildAction(boolean ourLand, UUID plotOwner, boolean mine,
                                  Double salePrice, Double rentPrice) {
        if (!ourLand) {
            return Items.of(Material.GRAY_DYE)
                    .name("<dark_gray><bold>Nothing to do here</bold>")
                    .lore("<gray>Stand on your own town's land.").build();
        }
        if (mine && rentPrice != null) {
            return Items.of(Material.IRON_DOOR)
                    .name("<yellow><bold>End Your Rental</bold>")
                    .lore("<gray>Stop paying rent and give the plot back.",
                            "", "<yellow>Click to hand it in").build();
        }
        if (mine) {
            return Items.of(Material.LIME_WOOL)
                    .name("<green><bold>This Plot Is Yours</bold>")
                    .lore("<gray>Only you and the mayor can build here.").build();
        }
        if (plotOwner != null) {
            return Items.of(Material.RED_WOOL)
                    .name("<red><bold>Already Taken</bold>")
                    .lore("<gray>Someone else holds this plot.").build();
        }
        if (salePrice != null) {
            return Items.of(Material.EMERALD)
                    .name("<green><bold>Buy This Plot</bold>")
                    .lore("<gray>One payment of <#f9d423>" + plugin.msg().money(salePrice) + "</#f9d423>",
                            "<gray>and it's yours for good.",
                            "", "<yellow>Click to buy").build();
        }
        if (rentPrice != null) {
            return Items.of(Material.EMERALD)
                    .name("<#5ad1e8><bold>Rent This Plot</bold>")
                    .lore("<gray><#f9d423>" + plugin.msg().money(rentPrice) + "</#f9d423> "
                                    + "<gray>every " + plugin.towns().rentPeriodLabel() + "</gray>",
                            "<gray>First payment is due now.",
                            "", "<yellow>Click to rent").build();
        }
        return Items.of(Material.GRAY_DYE)
                .name("<dark_gray><bold>Not For Sale</bold>")
                .lore("<gray>The town hasn't listed this chunk.").build();
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        Town myTown = plugin.towns().getTownOf(player.getUniqueId());
        if (myTown == null) { player.closeInventory(); return; }
        String here = TownManager.chunkKey(player.getLocation());
        Town hereTown = plugin.towns().getTownAtLoc(player.getLocation());

        switch (slot) {
            case CLAIM -> { plugin.towns().claimHere(player); redraw(); }
            case UNCLAIM -> { plugin.towns().unclaimHere(player); redraw(); }
            case ALL_PLOTS -> new TownPlotsMenu(plugin, player, 0).open();
            case BACK -> new TownManageMenu(plugin, player).open();

            case ACTION -> {
                if (hereTown == null) return;
                UUID owner = hereTown.plotOwner(here);
                if (owner != null && owner.equals(player.getUniqueId())) {
                    if (hereTown.rentPrice(here) != null) plugin.towns().endRentHere(player);
                    redraw();
                    return;
                }
                if (owner != null) return;
                if (hereTown.plotPrice(here) != null) plugin.towns().buyPlotHere(player);
                else if (hereTown.rentPrice(here) != null) plugin.towns().rentPlotHere(player);
                redraw();
            }

            case LIST_SALE -> {
                player.closeInventory();
                plugin.msg().send(player, "<#f9d423>Type the sale price</#f9d423> <gray>(or 'cancel').");
                plugin.prompts().await(player, s -> {
                    try { plugin.towns().sellPlotHere(player, Double.parseDouble(s)); }
                    catch (NumberFormatException e) { plugin.msg().send(player, "<red>That's not a number."); }
                    new TownLandMenu(plugin, player).open();
                });
            }
            case LIST_RENT -> {
                player.closeInventory();
                plugin.msg().send(player, "<#f9d423>Type the rent per "
                        + plugin.towns().rentPeriodLabel() + "</#f9d423> <gray>(or 'cancel').");
                plugin.prompts().await(player, s -> {
                    try { plugin.towns().rentOutPlotHere(player, Double.parseDouble(s)); }
                    catch (NumberFormatException e) { plugin.msg().send(player, "<red>That's not a number."); }
                    new TownLandMenu(plugin, player).open();
                });
            }
            default -> { /* display only */ }
        }
    }
}
