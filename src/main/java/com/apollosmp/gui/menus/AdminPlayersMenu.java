package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Admin view of everyone who has ever joined. */
public class AdminPlayersMenu extends Gui {

    private static final int PAGE_SIZE = 45;

    private final int page;
    private final boolean onlineOnly;
    private final List<UUID> shown = new ArrayList<>();

    public AdminPlayersMenu(ApolloSMP plugin, Player viewer, int page, boolean onlineOnly) {
        super(plugin, viewer, 6, "<#ff4e50><bold>Admin - Players</bold>");
        this.page = Math.max(0, page);
        this.onlineOnly = onlineOnly;
    }

    @Override
    protected void build() {
        shown.clear();

        List<Map.Entry<UUID, String>> everyone =
                new ArrayList<>(plugin.economy().knownNames().entrySet());
        if (onlineOnly) {
            everyone.removeIf(e -> plugin.getServer().getPlayer(e.getKey()) == null);
        }
        everyone.sort(Comparator.comparing(e -> e.getValue().toLowerCase()));

        int from = page * PAGE_SIZE;
        int to = Math.min(everyone.size(), from + PAGE_SIZE);
        List<Map.Entry<UUID, String>> pageItems =
                from >= everyone.size() ? new ArrayList<>() : everyone.subList(from, to);

        if (pageItems.isEmpty()) {
            inventory.setItem(22, Items.of(Material.BARRIER)
                    .name("<gray>Nobody to show").build());
        }

        for (int i = 0; i < pageItems.size(); i++) {
            UUID id = pageItems.get(i).getKey();
            String name = pageItems.get(i).getValue();
            Player online = plugin.getServer().getPlayer(id);
            Town town = plugin.towns().getTownOf(id);

            List<String> lore = new ArrayList<>();
            lore.add(online != null ? "<green>Online now" : "<dark_gray>Offline");
            lore.add("<gray>Balance: <#f9d423>" + plugin.msg().money(plugin.economy().getBalance(id)) + "</#f9d423>");
            lore.add("<gray>Homes: <white>" + plugin.homes().count(id) + "</white>");
            lore.add("<gray>Town: <white>" + (town == null ? "None" : town.name()) + "</white>");
            lore.add("<gray>Businesses: <white>" + plugin.businesses().countOwnedBy(id) + "</white>");
            OfflinePlayer off = plugin.getServer().getOfflinePlayer(id);
            long lastSeen = off.getLastSeen();
            if (online == null && lastSeen > 0) {
                lore.add("<gray>Last seen: <white>"
                        + new SimpleDateFormat("MMM d, yyyy").format(new Date(lastSeen)) + "</white>");
            }
            lore.add("");
            lore.add("<yellow>Click to inspect");

            ItemStack icon = online != null
                    ? Items.playerHead(online, "<white>" + name + "</white>", lore)
                    : Items.of(Material.SKELETON_SKULL).name("<gray>" + name + "</gray>")
                        .lore(lore.toArray(new String[0])).build();
            inventory.setItem(i, icon);
            shown.add(id);
        }

        for (int i = PAGE_SIZE; i < 54; i++) {
            inventory.setItem(i, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
        }

        inventory.setItem(45, Items.of(Material.ARROW).name("<gray>Previous Page").build());
        inventory.setItem(48, Items.of(onlineOnly ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(onlineOnly ? "<green>Showing: Online only" : "<gray>Showing: Everyone")
                .lore("<yellow>Click to switch").build());
        inventory.setItem(49, Items.of(Material.PAPER)
                .name("<#f9d423>Page " + (page + 1) + "</#f9d423>")
                .lore("<gray>Players: <white>" + everyone.size() + "</white>").build());
        boolean staff = plugin.staffMode().isStaff(viewer);
        inventory.setItem(47, Items.of(staff ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(staff ? "<green><bold>Staff Mode: On</bold>" : "<gray><bold>Staff Mode: Off</bold>")
                .lore("<gray>Swap between playing and moderating.",
                        staff ? "<gray>Your survival gear is stored safely."
                                : "<gray>Creative, flight and vanish.",
                        "<dark_gray>/staff",
                        "", "<yellow>Click to toggle")
                .glow(staff).hideAttributes().build());

        inventory.setItem(50, Items.of(Material.BARRIER).name("<red>Close").build());
        inventory.setItem(53, Items.of(Material.ARROW).name("<gray>Next Page").build());
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (!player.hasPermission("apollo.admin")) { player.closeInventory(); return; }

        if (slot >= 0 && slot < PAGE_SIZE) {
            if (slot >= shown.size()) return;
            new AdminPlayerMenu(plugin, player, shown.get(slot)).open();
            return;
        }
        switch (slot) {
            case 45 -> { if (page > 0) new AdminPlayersMenu(plugin, player, page - 1, onlineOnly).open(); }
            case 47 -> {
                boolean now = plugin.staffMode().toggle(player);
                if (now) player.closeInventory();
                else redraw();
            }
            case 48 -> new AdminPlayersMenu(plugin, player, 0, !onlineOnly).open();
            case 50 -> player.closeInventory();
            case 53 -> new AdminPlayersMenu(plugin, player, page + 1, onlineOnly).open();
            default -> { /* no-op */ }
        }
    }
}
