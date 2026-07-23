package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.town.TownManager;
import com.apollosmp.util.Items;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Every sold or for-sale plot in your town, and who owns it. */
public class TownPlotsMenu extends Gui {

    private static final int PAGE_SIZE = 45;

    private final int page;
    private final List<String> shown = new ArrayList<>();

    public TownPlotsMenu(ApolloSMP plugin, Player viewer, int page) {
        super(plugin, viewer, 6, "<#e94fd0><bold>Town Plots</bold>");
        this.page = Math.max(0, page);
    }

    @Override
    protected void build() {
        shown.clear();
        Town town = plugin.towns().getTownOf(viewer.getUniqueId());
        if (town == null) { viewer.closeInventory(); return; }

        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(town.plotOwners().keySet());
        keys.addAll(town.plotSale().keySet());
        keys.addAll(town.plotRent().keySet());
        List<String> all = new ArrayList<>(keys);

        int from = page * PAGE_SIZE;
        int to = Math.min(all.size(), from + PAGE_SIZE);
        List<String> pageItems = from >= all.size() ? new ArrayList<>() : all.subList(from, to);

        if (pageItems.isEmpty()) {
            inventory.setItem(22, Items.of(Material.BARRIER)
                    .name("<gray>No plots yet")
                    .lore("<gray>Stand on a chunk and use",
                            "<gray><white>Sell This Plot</white> to create one.").build());
        }

        String standingOn = TownManager.chunkKey(viewer.getLocation());
        for (int i = 0; i < pageItems.size(); i++) {
            String key = pageItems.get(i);
            UUID owner = town.plotOwner(key);
            Double price = town.plotPrice(key);
            String ownerName = owner == null ? null : plugin.getServer().getOfflinePlayer(owner).getName();
            if (owner != null && ownerName == null) ownerName = "Unknown";

            int[] coords = parse(key);
            List<String> lore = new ArrayList<>();
            lore.add(owner == null
                    ? "<gray>Owner: <white>Town-owned</white>"
                    : "<gray>Owner: <white>" + ownerName + "</white>");
            if (price != null) lore.add("<gray>For sale: <#f9d423>" + plugin.msg().money(price) + "</#f9d423>");
            Double rent = town.rentPrice(key);
            if (rent != null) {
                lore.add("<gray>Rent: <#f9d423>" + plugin.msg().money(rent) + "</#f9d423> <gray>per "
                        + plugin.towns().rentPeriodLabel() + "</gray>");
                Long due = town.rentDueAt(key);
                if (owner != null && due != null) {
                    long mins = Math.max(0, (due - System.currentTimeMillis()) / 60000);
                    lore.add("<gray>Next payment in <white>" + (mins / 60) + "h " + (mins % 60) + "m</white>");
                }
            }
            if (coords != null) {
                lore.add("<gray>Chunk: <white>" + coords[0] + ", " + coords[1] + "</white>");
                lore.add("<gray>Around x <white>" + (coords[0] * 16 + 8)
                        + "</white>, z <white>" + (coords[1] * 16 + 8) + "</white>");
            }
            if (key.equals(standingOn)) lore.add("<#5ad1e8>You're standing here</#5ad1e8>");
            lore.add("");
            lore.add("<yellow>Click to teleport");

            ItemStack icon;
            Player onlineOwner = owner == null ? null : plugin.getServer().getPlayer(owner);
            if (onlineOwner != null) {
                icon = Items.playerHead(onlineOwner, "<#e94fd0>" + ownerName + "'s Plot</#e94fd0>", lore);
            } else {
                icon = Items.of(price != null ? Material.OAK_SIGN : Material.GRASS_BLOCK)
                        .name(owner == null ? "<#f9d423>Unclaimed Plot" : "<#e94fd0>" + ownerName + "'s Plot")
                        .lore(lore.toArray(new String[0])).build();
            }
            inventory.setItem(i, icon);
            shown.add(key);
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
            int[] coords = parse(shown.get(slot));
            if (coords == null) return;
            World world = worldOf(shown.get(slot));
            if (world == null) { plugin.msg().send(player, "<red>That world isn't loaded."); return; }
            Location target = world.getHighestBlockAt(coords[0] * 16 + 8, coords[1] * 16 + 8)
                    .getLocation().add(0.5, 1.0, 0.5);
            player.teleport(target);
            plugin.msg().send(player, "<green>Teleported to the plot.");
            player.closeInventory();
            return;
        }
        switch (slot) {
            case 45 -> { if (page > 0) new TownPlotsMenu(plugin, player, page - 1).open(); }
            case 49 -> new TownManageMenu(plugin, player).open();
            case 53 -> new TownPlotsMenu(plugin, player, page + 1).open();
            default -> { /* no-op */ }
        }
    }

    /** chunkKey is "world,cx,cz". */
    private int[] parse(String key) {
        String[] parts = key.split(",");
        if (parts.length != 3) return null;
        try {
            return new int[]{Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private World worldOf(String key) {
        String[] parts = key.split(",");
        return parts.length == 3 ? plugin.getServer().getWorld(parts[0]) : null;
    }
}
