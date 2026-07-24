package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.shop.ShopManager;
import com.apollosmp.town.Town;
import com.apollosmp.town.TownPerm;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** A market stall: buy from it, or stock it if it's your town's. */
public class TownShopMenu extends Gui {

    private static final int OFFER_START = 0;   // slots 0-26
    private static final int OFFER_COUNT = 27;
    private static final int INFO = 49;
    private static final int STOCK = 47;
    private static final int CLOSE = 51;

    private final ShopManager.Stall stall;

    public TownShopMenu(ApolloSMP plugin, Player viewer, ShopManager.Stall stall) {
        super(plugin, viewer, 6, "<#5ad1e8><bold>" + stall.town + " Market</bold>");
        this.stall = stall;
    }

    @Override
    protected void build() {
        ShopManager shops = plugin.shops();
        Town town = plugin.towns().townByName(stall.town);
        Town mine = plugin.towns().getTownOf(viewer.getUniqueId());
        boolean resident = town != null && town.isMember(viewer.getUniqueId());
        boolean allied = mine != null && plugin.diplomacy() != null
                && plugin.diplomacy().allied(mine.name(), stall.town);


        if (stall.offers.isEmpty()) {
            inventory.setItem(13, Items.of(Material.BARRIER)
                    .name("<gray>Nothing for sale")
                    .lore(resident
                            ? "<gray>Stock it with the button below."
                            : "<gray>Come back when they've restocked.").build());
        }

        for (int i = 0; i < stall.offers.size() && i < OFFER_COUNT; i++) {
            ShopManager.Offer offer = stall.offers.get(i);
            double each = shops.priceFor(viewer, stall, offer);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Price: <#f9d423>" + plugin.msg().money(each) + "</#f9d423> each");
            if (each < offer.price) {
                lore.add("<dark_gray>Normally " + plugin.msg().money(offer.price) + "</dark_gray>");
            }
            lore.add("<gray>In stock: <white>" + offer.stock + "</white>");
            lore.add("");
            lore.add("<yellow>Left-click:</yellow> <gray>buy one</gray>");
            lore.add("<yellow>Right-click:</yellow> <gray>buy a stack</gray>");
            if (resident) lore.add("<red>Shift-click:</red> <gray>take it off the shelf</gray>");

            inventory.setItem(OFFER_START + i,
                    Items.of(offer.material, Math.max(1, Math.min(64, offer.stock)))
                            .name("<white>" + Items.pretty(offer.material))
                            .lore(lore).hideAttributes().build());
        }

        if (resident && town != null && town.hasPerm(viewer.getUniqueId(), TownPerm.SELL_PLOT)) {
            inventory.setItem(STOCK, Items.of(Material.CHEST)
                    .name("<green><bold>Stock the Stall</bold>")
                    .lore("<gray>Hold what you want to sell, then",
                            "<gray>click here and name your price.",
                            "<gray>Slots used: <white>" + stall.offers.size()
                                    + " / " + ShopManager.MAX_OFFERS + "</white>",
                            "", "<yellow>Click to add stock")
                    .build());
        }

        for (int s = 45; s < 54; s++) {
            inventory.setItem(s, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
        }
        inventory.setItem(INFO, Items.of(Material.EMERALD)
                .name("<#5ad1e8><bold>" + stall.town + "'s Market</bold>")
                .lore("<gray>Everything here is sold by the town.",
                        allied
                                ? "<green>Ally discount: " + (int) shops.allyDiscount() + "% off</green>"
                                : "<dark_gray>Allies of this town get a discount.",
                        "",
                        "<gray>Takings so far: <#f9d423>"
                                + plugin.msg().money(stall.earned) + "</#f9d423>")
                .glow(true).hideAttributes().build());
        inventory.setItem(CLOSE, Items.of(Material.BARRIER).name("<red>Close").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == CLOSE) { player.closeInventory(); return; }

        Town town = plugin.towns().townByName(stall.town);
        boolean resident = town != null && town.isMember(player.getUniqueId());

        if (slot == STOCK) {
            if (!resident) return;
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held == null || held.getType().isAir()) {
                plugin.msg().send(player, "<red>Hold what you want to sell first.");
                return;
            }
            player.closeInventory();
            plugin.msg().send(player, "<#f9d423>Type the price per item</#f9d423> <gray>(or 'cancel').");
            plugin.prompts().await(player, s -> {
                try {
                    plugin.shops().stock(player, stall, Double.parseDouble(s));
                } catch (NumberFormatException e) {
                    plugin.msg().send(player, "<red>That's not a number.");
                }
                new TownShopMenu(plugin, player, stall).open();
            });
            return;
        }

        for (int i = 0; i < OFFER_COUNT; i++) {
            if (OFFER_START + i != slot || i >= stall.offers.size()) continue;
            if (click.isShiftClick() && resident) {
                plugin.shops().unstock(player, stall, i);
            } else {
                plugin.shops().buy(player, stall, i, click.isRightClick() ? 64 : 1);
            }
            redraw();
            return;
        }
    }
}
