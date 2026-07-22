package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.invest.Business;
import com.apollosmp.invest.Businesses;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Browse and buy the 7 businesses. */
public class BusinessShopMenu extends Gui {

    private final List<Business> order = new ArrayList<>();

    public BusinessShopMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 5, "<gradient:#f9d423:#ff4e50><bold>Buy a Business</bold></gradient>");
    }

    @Override
    protected void build() {
        order.clear();
        order.addAll(Businesses.all());

        // Centered-ish slots for up to 7 businesses.
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < order.size() && i < slots.length; i++) {
            Business b = order.get(i);
            double bal = plugin.economy().getBalance(viewer.getUniqueId());
            boolean canAfford = bal >= b.price();

            List<String> lore = new ArrayList<>();
            lore.add(b.tagline());
            lore.add("<dark_gray>―――――――――――");
            lore.add("<gray>Price: <#f9d423>" + plugin.msg().money(b.price()) + "</#f9d423>");
            lore.add("<gray>Income: <green>" + plugin.msg().money(b.hourlyValue(plugin.sell())) + "/hr</green>");
            lore.add("<gray>Produces per hour:");
            for (Business.Product p : b.products()) {
                lore.add("  <dark_gray>+</dark_gray> <white>" + Items.pretty(p.material()) + "</white> "
                        + "<gray>(<green>" + b.perHour(p) + "/hr</green>)");
            }
            lore.add("");
            lore.add(canAfford ? "<green>Click to buy" : "<red>You can't afford this yet");

            inventory.setItem(slots[i], Items.of(b.block())
                    .name(b.displayName())
                    .lore(lore)
                    .glow(canAfford)
                    .hideAttributes()
                    .build());
        }

        for (int i = 36; i < 45; i++) {
            inventory.setItem(i, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
        }
        inventory.setItem(40, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == 40) {
            new InvestMenu(plugin, player).open();
            return;
        }
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        int index = -1;
        for (int i = 0; i < slots.length; i++) if (slots[i] == slot) index = i;
        if (index < 0 || index >= order.size()) return;

        Business b = order.get(index);
        if (!plugin.economy().has(player.getUniqueId(), b.price())) {
            plugin.msg().send(player, "<red>You need " + plugin.msg().money(b.price())
                    + " to buy that business.");
            return;
        }
        plugin.economy().withdraw(player.getUniqueId(), b.price());
        Items.give(player, plugin.businesses().createItem(b));
        plugin.msg().send(player, "<green>You bought <reset>" + b.displayName()
                + "<green>! Place the block to start earning.");
        player.closeInventory();
    }
}
