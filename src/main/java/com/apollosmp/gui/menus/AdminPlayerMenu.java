package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.homes.Home;
import com.apollosmp.town.Town;
import com.apollosmp.town.TownRank;
import com.apollosmp.util.Items;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One player's details: their homes, town, and quick teleports. */
public class AdminPlayerMenu extends Gui {

    private static final int HOME_START = 18;
    private static final int HOME_MAX = 27; // slots 18..44

    private final UUID target;
    private final List<Home> homes = new ArrayList<>();

    public AdminPlayerMenu(ApolloSMP plugin, Player viewer, UUID target) {
        super(plugin, viewer, 6, "<#ff4e50><bold>Admin - Player</bold>");
        this.target = target;
    }

    @Override
    protected void build() {
        homes.clear();
        String name = plugin.economy().nameOf(target);
        if (name == null) name = "Unknown";
        Player online = plugin.getServer().getPlayer(target);
        Town town = plugin.towns().getTownOf(target);
        TownRank rank = town == null ? null : town.rankOf(target);

        List<String> info = new ArrayList<>();
        info.add(online != null ? "<green>Online now" : "<dark_gray>Offline");
        info.add("<gray>Balance: <#f9d423>" + plugin.msg().money(plugin.economy().getBalance(target)) + "</#f9d423>");
        info.add("<gray>Town: <white>" + (town == null ? "None" : town.name()) + "</white>"
                + (rank == null ? "" : " <gray>(" + rank.display() + ")</gray>"));
        info.add("<gray>Businesses: <white>" + plugin.businesses().countOwnedBy(target) + "</white>");
        info.add("<gray>Auction listings: <white>" + plugin.auctions().countBySeller(target) + "</white>");
        info.add("<gray>Collection box: <white>" + plugin.mailbox().size(target) + "</white>");
        info.add("<gray>UUID: <dark_gray>" + target + "</dark_gray>");

        ItemStack head = online != null
                ? Items.playerHead(online, "<#f9d423><bold>" + name + "</bold>", info)
                : Items.of(Material.SKELETON_SKULL).name("<#f9d423><bold>" + name + "</bold>")
                    .lore(info.toArray(new String[0])).build();
        inventory.setItem(4, head);

        if (online != null) {
            inventory.setItem(10, Items.of(Material.ENDER_PEARL)
                    .name("<green><bold>Teleport to Player</bold>")
                    .lore("<gray>Go to where they are now.").build());
            inventory.setItem(11, Items.of(Material.ENDER_EYE)
                    .name("<#5ad1e8><bold>Bring Player Here</bold>")
                    .lore("<gray>Teleport them to you.").build());
        }
        if (town != null && town.spawn() != null) {
            inventory.setItem(12, Items.of(Material.WHITE_BANNER)
                    .name("<#f9d423><bold>Go to " + town.name() + "</bold>")
                    .lore("<gray>Teleport to their town spawn.").hideAttributes().build());
        }

        homes.addAll(plugin.homes().getHomes(target));
        inventory.setItem(13, Items.of(Material.RED_BED)
                .name("<#ff4e50><bold>Homes: " + homes.size() + "</bold>")
                .lore("<gray>Listed below - click one to",
                        "<gray>teleport straight to it.").build());

        for (int i = 0; i < homes.size() && i < HOME_MAX; i++) {
            Home home = homes.get(i);
            Location loc = home.toLocation();
            List<String> lore = new ArrayList<>();
            if (loc == null) {
                lore.add("<red>World not loaded: <white>" + home.world() + "</white>");
            } else {
                lore.add("<gray>World: <white>" + home.world() + "</white>");
                lore.add("<gray>x <white>" + loc.getBlockX() + "</white>  y <white>"
                        + loc.getBlockY() + "</white>  z <white>" + loc.getBlockZ() + "</white>");
                lore.add("");
                lore.add("<yellow>Click to teleport");
            }
            inventory.setItem(HOME_START + i, Items.of(
                            home.icon() == null ? Material.RED_BED : home.icon())
                    .name("<#f9d423>" + home.name() + "</#f9d423>")
                    .lore(lore.toArray(new String[0])).hideAttributes().build());
        }

        inventory.setItem(45, Items.of(Material.ARROW).name("<gray>Back to Players").build());
        inventory.setItem(49, Items.of(Material.BARRIER).name("<red>Close").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (!player.hasPermission("apollo.admin")) { player.closeInventory(); return; }

        if (slot >= HOME_START && slot < HOME_START + HOME_MAX) {
            int index = slot - HOME_START;
            if (index >= homes.size()) return;
            Location loc = homes.get(index).toLocation();
            if (loc == null) {
                plugin.msg().send(player, "<red>That home's world isn't loaded.");
                return;
            }
            player.teleport(loc);
            plugin.msg().send(player, "<green>Teleported to <white>"
                    + plugin.economy().nameOf(target) + "</white>'s home <white>"
                    + homes.get(index).name() + "</white>.");
            player.closeInventory();
            return;
        }

        switch (slot) {
            case 10 -> {
                Player online = plugin.getServer().getPlayer(target);
                if (online == null) { plugin.msg().send(player, "<red>They're offline."); return; }
                player.teleport(online.getLocation());
                plugin.msg().send(player, "<green>Teleported to <white>" + online.getName() + "</white>.");
                player.closeInventory();
            }
            case 11 -> {
                Player online = plugin.getServer().getPlayer(target);
                if (online == null) { plugin.msg().send(player, "<red>They're offline."); return; }
                online.teleport(player.getLocation());
                plugin.msg().send(player, "<green>Brought <white>" + online.getName() + "</white> to you.");
                plugin.msg().send(online, "<yellow>You were teleported by an admin.");
                player.closeInventory();
            }
            case 12 -> {
                Town town = plugin.towns().getTownOf(target);
                if (town == null || town.spawn() == null) return;
                player.teleport(town.spawn());
                plugin.msg().send(player, "<green>Teleported to <white>" + town.name() + "</white>.");
                player.closeInventory();
            }
            case 45 -> new AdminPlayersMenu(plugin, player, 0, false).open();
            case 49 -> player.closeInventory();
            default -> { /* no-op */ }
        }
    }
}
