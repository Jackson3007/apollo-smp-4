package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.homes.Home;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class HomesMenu extends Gui {

    private final List<Home> homes = new ArrayList<>();

    public HomesMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 6, "<#ff4e50><bold>Your Homes</bold>");
    }

    @Override
    protected void build() {
        homes.clear();
        homes.addAll(plugin.homes().getHomes(viewer.getUniqueId()));
        int limit = plugin.homes().limitFor(viewer);

        int slot = 0;
        for (Home home : homes) {
            if (slot >= 45) break;
            double cost = plugin.getConfig().getDouble("homes.teleport-cost", 0.0);
            List<String> lore = new ArrayList<>();
            lore.add("<gray>World: <white>" + home.world() + "</white>");
            lore.add("<gray>X: <white>" + (int) home.x() + "</white> "
                    + "<gray>Y: <white>" + (int) home.y() + "</white> "
                    + "<gray>Z: <white>" + (int) home.z() + "</white>");
            lore.add("");
            if (cost > 0) lore.add("<gray>Cost: <#f9d423>" + plugin.msg().money(cost) + "</#f9d423>");
            lore.add("<yellow>Click to teleport");
            inventory.setItem(slot, Items.of(home.icon())
                    .name("<#f9d423><bold>" + home.name() + "</bold>")
                    .lore(lore).hideAttributes().build());
            slot++;
        }

        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
        }
        inventory.setItem(45, Items.of(Material.ARROW).name("<gray>Back").build());
        inventory.setItem(49, Items.of(Material.NETHER_STAR)
                .name("<#f9d423><bold>Home Slots</bold>")
                .lore("<gray>Using <white>" + homes.size() + "</white> / <white>" + limit + "</white>",
                        "", "<dark_gray>Set homes with /sethome <name>")
                .glow(true).hideAttributes().build());
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == 45) {
            new MainMenu(plugin, player).open();
            return;
        }
        if (slot < 0 || slot >= 45 || slot >= homes.size()) return;
        Home home = homes.get(slot);
        player.closeInventory();
        double cost = plugin.getConfig().getDouble("homes.teleport-cost", 0.0);
        plugin.teleports().warmupTeleport(player, home.toLocation(), cost,
                "<green>Welcome to <#f9d423>" + home.name() + "</#f9d423>!");
    }
}
