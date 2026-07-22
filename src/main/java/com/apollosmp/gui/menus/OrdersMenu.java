package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.orders.Order;
import com.apollosmp.orders.OrderManager;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class OrdersMenu extends Gui {

    private static final int PAGE_SIZE = 45;

    private final boolean mine;
    private final int page;
    private List<Order> pageItems = new ArrayList<>();

    public OrdersMenu(ApolloSMP plugin, Player viewer, boolean mine, int page) {
        super(plugin, viewer, 6, mine
                ? "<#ff4e50><bold>My Buy Orders</bold>"
                : "<gradient:#f9d423:#ff4e50><bold>Buy Orders</bold></gradient>");
        this.mine = mine;
        this.page = Math.max(0, page);
    }

    @Override
    protected void build() {
        List<Order> all = mine
                ? plugin.orders().byBuyer(viewer.getUniqueId())
                : plugin.orders().active();

        int from = page * PAGE_SIZE;
        int to = Math.min(all.size(), from + PAGE_SIZE);
        pageItems = (from >= all.size()) ? new ArrayList<>() : new ArrayList<>(all.subList(from, to));

        for (int i = 0; i < pageItems.size(); i++) {
            Order order = pageItems.get(i);
            int display = Math.max(1, Math.min(order.material().getMaxStackSize(), order.remaining()));
            List<String> lore = new ArrayList<>();
            lore.add("<dark_gray>―――――――――――");
            lore.add("<gray>Buyer: <white>" + order.buyerName() + "</white>");
            lore.add("<gray>Wants: <white>" + order.remaining() + "</white> <gray>more</gray>");
            lore.add("<gray>Pays: <#f9d423>" + plugin.msg().money(order.pricePer()) + "</#f9d423> <gray>each");
            lore.add("<gray>Total left: <#f9d423>" + plugin.msg().money(order.remainingValue()) + "</#f9d423>");
            lore.add("");
            if (mine) {
                lore.add("<red>Click to cancel & refund");
            } else if (order.buyer().equals(viewer.getUniqueId())) {
                lore.add("<dark_gray>This is your order");
            } else {
                lore.add("<yellow>Click to sell what you have");
            }
            inventory.setItem(i, Items.of(order.material(), display)
                    .name("<#f9d423><bold>" + Items.pretty(order.material()) + "</bold>")
                    .lore(lore).hideAttributes().build());
        }

        for (int i = PAGE_SIZE; i < 54; i++) {
            inventory.setItem(i, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
        }
        inventory.setItem(45, Items.of(Material.ARROW).name("<gray>Previous Page").build());
        inventory.setItem(46, Items.of(Material.BARRIER).name("<gray>Back to Menu").build());
        inventory.setItem(47, Items.of(mine ? Material.CHEST : Material.WRITABLE_BOOK)
                .name(mine ? "<#f9d423>Browse All" : "<#f9d423>My Orders")
                .lore("<gray>Active orders: <white>"
                        + plugin.orders().countByBuyer(viewer.getUniqueId()) + "</white>").build());
        inventory.setItem(49, Items.of(Material.PAPER)
                .name("<#f9d423><bold>Page " + (page + 1) + "</bold>")
                .lore("<gray>Create one holding an item:",
                        "<white>/orders create <price> [amount]</white>").build());
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
            Order order = pageItems.get(slot);
            if (mine) {
                if (plugin.orders().cancel(player.getUniqueId(), order.id())) {
                    plugin.msg().send(player, "<green>Order cancelled. Remaining funds refunded.");
                }
                redraw();
            } else {
                OrderManager.FulfilResult r = plugin.orders().fulfil(player, order.id(), order.remaining());
                if (!r.any()) {
                    plugin.msg().send(player, "<red>You don't have any "
                            + Items.pretty(order.material()) + " to sell.");
                } else {
                    plugin.msg().send(player, "<green>Sold <white>" + r.filled()
                            + "</white> for <#f9d423>" + plugin.msg().money(r.earned()) + "</#f9d423>.");
                }
                redraw();
            }
            return;
        }
        switch (slot) {
            case 45 -> { if (page > 0) new OrdersMenu(plugin, player, mine, page - 1).open(); }
            case 46 -> new MainMenu(plugin, player).open();
            case 47 -> new OrdersMenu(plugin, player, !mine, 0).open();
            case 51 -> {
                int n = plugin.mailbox().collect(player);
                plugin.msg().send(player, n == 0 ? "<gray>Your collection box is empty."
                        : "<green>Collected <white>" + n + "</white> item stack(s).");
                redraw();
            }
            case 53 -> new OrdersMenu(plugin, player, mine, page + 1).open();
            default -> { /* no-op */ }
        }
    }
}
