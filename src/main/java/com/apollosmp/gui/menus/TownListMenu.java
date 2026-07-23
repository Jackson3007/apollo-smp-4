package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Browse every town on the server and teleport to one. */
public class TownListMenu extends Gui {

    private static final int PAGE_SIZE = 45;

    private final int page;
    private final List<String> shown = new ArrayList<>();

    public TownListMenu(ApolloSMP plugin, Player viewer, int page) {
        super(plugin, viewer, 6, "<gradient:#f9d423:#ff4e50><bold>Towns</bold></gradient>");
        this.page = Math.max(0, page);
    }

    @Override
    protected void build() {
        shown.clear();
        List<Town> towns = plugin.towns().allTowns();
        towns.sort((a, b) -> Integer.compare(b.memberCount(), a.memberCount()));

        int from = page * PAGE_SIZE;
        int to = Math.min(towns.size(), from + PAGE_SIZE);
        List<Town> pageItems = from >= towns.size() ? new ArrayList<>() : towns.subList(from, to);

        if (pageItems.isEmpty()) {
            inventory.setItem(22, Items.of(Material.BARRIER)
                    .name("<gray>No towns yet")
                    .lore("<gray>Be the first - open <white>/town</white>.").build());
        }

        for (int i = 0; i < pageItems.size(); i++) {
            Town town = pageItems.get(i);
            String mayor = plugin.getServer().getOfflinePlayer(town.mayor()).getName();
            if (mayor == null) mayor = "Unknown";
            String founded = new SimpleDateFormat("MMM d, yyyy").format(new Date(town.founded()));
            boolean hasSpawn = town.spawn() != null;
            boolean canVisit = town.publicSpawn() || town.isMember(viewer.getUniqueId());

            inventory.setItem(i, Items.of(Material.WHITE_BANNER)
                    .name("<#f9d423><bold>" + town.name() + "</bold>")
                    .lore("<gray>Mayor: <white>" + mayor + "</white>",
                            "<gray>Residents: <white>" + town.memberCount() + "</white>",
                            "<gray>Land: <white>" + town.claims().size() + "</white> chunks",
                            "<gray>Founded: <white>" + founded + "</white>",
                            "<gray>Visitors: " + (town.publicSpawn() ? "<green>welcome" : "<red>blocked"),
                            "",
                            !hasSpawn ? "<dark_gray>No spawn set"
                                    : (canVisit ? "<yellow>Click to teleport" : "<dark_gray>Residents only"))
                    .hideAttributes().build());
            shown.add(town.name());
        }

        for (int i = PAGE_SIZE; i < 54; i++) {
            inventory.setItem(i, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
        inventory.setItem(45, Items.of(Material.ARROW).name("<gray>Previous Page").build());
        inventory.setItem(49, Items.of(Material.BARRIER).name("<gray>Back").build());
        inventory.setItem(53, Items.of(Material.ARROW).name("<gray>Next Page").build());
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot >= 0 && slot < PAGE_SIZE) {
            if (slot >= shown.size()) return;
            if (plugin.towns().teleportToTown(player, shown.get(slot))) player.closeInventory();
            return;
        }
        switch (slot) {
            case 45 -> { if (page > 0) new TownListMenu(plugin, player, page - 1).open(); }
            case 49 -> new TownMenu(plugin, player).open();
            case 53 -> new TownListMenu(plugin, player, page + 1).open();
            default -> { /* no-op */ }
        }
    }
}
