package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** A little storefront: shows Apollo+ and links players to the webstore to buy it. */
public class BuyMenu extends Gui {

    private static final int APOLLO_PLUS = 13;
    private static final int CLOSE = 22;

    public BuyMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 3, "<gradient:#f9d423:#ff4e50><bold>Apollo Store</bold></gradient>");
    }

    @Override
    protected void build() {
        boolean owns = viewer.hasPermission("apollo.plus");

        inventory.setItem(4, Items.of(Material.SUNFLOWER)
                .name("<gradient:#f9d423:#ff4e50><bold>Support Apollo SMP</bold></gradient>")
                .lore("<gray>Grab a rank to support the server",
                        "<gray>and unlock some nice perks.")
                .hideAttributes().build());

        inventory.setItem(APOLLO_PLUS, Items.of(Material.NETHER_STAR)
                .name("<#ffd54a><bold>Apollo+</bold></#ffd54a> <gray>-</gray> <white>$4.99<gray>/month</gray>")
                .lore("<gray>The donor rank. Includes:",
                        "<#ffd54a>\u2726</#ffd54a> <gray>10 homes <dark_gray>(up from 3)</dark_gray>",
                        "<#ffd54a>\u2726</#ffd54a> <gray>Gold <#ffd54a>[Apollo+]</#ffd54a> <gray>tag in chat & tab",
                        "<#ffd54a>\u2726</#ffd54a> <gray>+10% on everything you sell",
                        "<#ffd54a>\u2726</#ffd54a> <gray>Instant <white>/home</white> <gray>& no <white>/rtp</white> <gray>cooldown",
                        "<#ffd54a>\u2726</#ffd54a> <gray>Custom <white>/nick</white><gray>, particle <white>/trail</white>",
                        "<#ffd54a>\u2726</#ffd54a> <gray><white>/craft</white><gray>, <white>/ec</white> <gray>& personal vaults <white>/pv</white>",
                        "<#ffd54a>\u2726</#ffd54a> <gray>Custom home icons & a join announcement",
                        "",
                        owns ? "<green>\u2714 You already have Apollo+ - thank you!"
                             : "<yellow>Click to open the store and subscribe.")
                .glow(true).hideAttributes().build());

        inventory.setItem(CLOSE, Items.of(Material.BARRIER).name("<red>Close").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot != APOLLO_PLUS) return;

        player.closeInventory();
        if (player.hasPermission("apollo.plus")) {
            plugin.msg().send(player, "<green>You already have <#ffd54a>Apollo+</#ffd54a><green>! Thanks for supporting the server.");
            return;
        }
        String url = plugin.storeUrl();
        plugin.msg().send(player, "<gradient:#f9d423:#ff4e50><bold>Apollo+</bold></gradient> <gray>-</gray> <white>$4.99<gray>/month</gray>");
        plugin.msg().send(player, "<gray>Complete your purchase here:");
        plugin.msg().send(player, "<click:open_url:'" + url + "'><hover:show_text:'Click to open the store'>"
                + "<#5ad1e8><u>" + url + "</u></#5ad1e8></hover></click>");
        plugin.msg().send(player, "<dark_gray>Your rank is applied automatically after checkout.");
    }
}
