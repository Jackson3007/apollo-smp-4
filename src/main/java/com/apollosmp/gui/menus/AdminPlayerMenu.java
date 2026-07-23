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

        inventory.setItem(9, Items.of(Material.CHEST)
                .name("<#5ad1e8><bold>View Inventory</bold>")
                .lore(online != null
                                ? "<gray>Opens their live inventory."
                                : "<gray>Opens their last logout snapshot.",
                        online != null
                                ? "<yellow>You can edit it directly."
                                : "<dark_gray>Read-only while they're offline.",
                        "", "<yellow>Click to view")
                .hideAttributes().build());

        inventory.setItem(14, Items.of(Material.EMERALD)
                .name("<green><bold>Give Money</bold>")
                .lore("<gray>Add funds to their balance.",
                        "", "<yellow>Click, then type an amount")
                .build());

        inventory.setItem(15, Items.of(Material.REDSTONE)
                .name("<#ff4e50><bold>Take Money</bold>")
                .lore("<gray>Remove funds from their balance.",
                        "", "<yellow>Click, then type an amount")
                .build());

        inventory.setItem(16, Items.of(Material.GOLD_INGOT)
                .name("<#f9d423><bold>Set Balance</bold>")
                .lore("<gray>Set their balance exactly.",
                        "", "<yellow>Click, then type an amount")
                .build());

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

    /** Prompt for an amount, then apply it to the target's balance. */
    private void askAmount(Player admin, String action) {
        String name = plugin.economy().nameOf(target);
        admin.closeInventory();
        plugin.msg().send(admin, "<#f9d423>Type the amount to " + action
                + " <white>" + name + "</white></#f9d423> <gray>(or 'cancel').");
        plugin.prompts().await(admin, input -> {
            double amount;
            try {
                amount = Double.parseDouble(input);
            } catch (NumberFormatException ex) {
                plugin.msg().send(admin, "<red>That's not a number.");
                new AdminPlayerMenu(plugin, admin, target).open();
                return;
            }
            if (amount < 0) {
                plugin.msg().send(admin, "<red>Use a positive number.");
                new AdminPlayerMenu(plugin, admin, target).open();
                return;
            }

            switch (action) {
                case "give" -> {
                    plugin.economy().deposit(target, amount);
                    plugin.msg().send(admin, "<green>Gave " + plugin.msg().money(amount)
                            + " to <white>" + name + "</white>.");
                    notifyTarget("<green>An admin gave you " + plugin.msg().money(amount) + ".");
                }
                case "take" -> {
                    if (!plugin.economy().withdraw(target, amount)) {
                        plugin.msg().send(admin, "<red>They don't have that much.");
                    } else {
                        plugin.msg().send(admin, "<yellow>Took " + plugin.msg().money(amount)
                                + " from <white>" + name + "</white>.");
                        notifyTarget("<yellow>An admin took " + plugin.msg().money(amount) + " from you.");
                    }
                }
                case "set" -> {
                    plugin.economy().set(target, amount);
                    plugin.msg().send(admin, "<green>Set <white>" + name + "</white>'s balance to "
                            + plugin.msg().money(amount) + ".");
                    notifyTarget("<gray>An admin set your balance to " + plugin.msg().money(amount) + ".");
                }
                default -> { /* nothing */ }
            }
            new AdminPlayerMenu(plugin, admin, target).open();
        });
    }

    private void notifyTarget(String message) {
        Player online = plugin.getServer().getPlayer(target);
        if (online != null) plugin.msg().send(online, message);
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
            case 9 -> {
                Player online = plugin.getServer().getPlayer(target);
                if (online != null) {
                    player.closeInventory();
                    player.openInventory(online.getInventory());
                    plugin.msg().send(player, "<gray>Viewing <white>" + online.getName()
                            + "</white>'s live inventory - edits apply immediately.");
                } else {
                    new AdminInventoryMenu(plugin, player, target).open();
                }
            }
            case 14 -> askAmount(player, "give");
            case 15 -> askAmount(player, "take");
            case 16 -> askAmount(player, "set");
            case 45 -> new AdminPlayersMenu(plugin, player, 0, false).open();
            case 49 -> player.closeInventory();
            default -> { /* no-op */ }
        }
    }
}
