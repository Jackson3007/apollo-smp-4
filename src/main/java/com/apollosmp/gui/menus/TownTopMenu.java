package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Which towns are doing best, by whichever measure you pick. */
public class TownTopMenu extends Gui {

    private static final int[] PODIUM = {12, 13, 14};
    private static final int LIST_START = 19;
    private static final int SORT = 40;
    private static final int BACK = 38;
    private static final int CLOSE = 42;

    private final String metric;
    private final List<String> shown = new ArrayList<>();

    public TownTopMenu(ApolloSMP plugin, Player viewer, String metric) {
        super(plugin, viewer, 5, "<#f9d423><bold>Top Towns</bold>");
        this.metric = metric == null ? "bank" : metric;
    }

    private String label() {
        return switch (metric) {
            case "land" -> "Most Land";
            case "residents" -> "Most Residents";
            default -> "Richest";
        };
    }

    private String valueOf(Town town) {
        return switch (metric) {
            case "land" -> town.claims().size() + " chunks";
            case "residents" -> town.memberCount() + " resident" + (town.memberCount() == 1 ? "" : "s");
            default -> plugin.msg().money(town.bank());
        };
    }

    @Override
    protected void build() {
        shown.clear();
        List<Town> top = plugin.towns().topTowns(metric, 24);
        Town mine = plugin.towns().getTownOf(viewer.getUniqueId());

        inventory.setItem(4, Items.of(Material.GOLD_BLOCK)
                .name("<#f9d423><bold>" + label() + "</bold>")
                .lore("<gray>Towns on the server: <white>"
                                + plugin.towns().allTowns().size() + "</white>",
                        "",
                        "<gray>Click the button below to rank by",
                        "<gray>money, land, or population.")
                .glow(true).hideAttributes().build());

        if (top.isEmpty()) {
            inventory.setItem(22, Items.of(Material.BARRIER)
                    .name("<gray>No towns yet")
                    .lore("<gray>Found one with <white>/town</white>.").build());
        }

        // Top three get the podium.
        Material[] medals = {Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.COPPER_BLOCK};
        String[] places = {"<#f9d423>1st", "<white>2nd", "<#c8873c>3rd"};
        for (int i = 0; i < 3 && i < top.size(); i++) {
            Town town = top.get(i);
            inventory.setItem(PODIUM[i], Items.of(medals[i])
                    .name(places[i] + " <bold>" + town.name() + "</bold>")
                    .lore("<gray>" + label() + ": <white>" + valueOf(town) + "</white>",
                            "<gray>Residents: <white>" + town.memberCount() + "</white>",
                            "<gray>Land: <white>" + town.claims().size() + "</white> chunks",
                            mine != null && mine.name().equalsIgnoreCase(town.name())
                                    ? "<green>That's your town" : "",
                            "", "<yellow>Click to visit")
                    .glow(true).hideAttributes().build());
            shown.add(town.name());
        }

        // The rest in a plain list.
        for (int i = 3; i < top.size() && LIST_START + (i - 3) < 36; i++) {
            Town town = top.get(i);
            inventory.setItem(LIST_START + (i - 3), Items.of(Material.WHITE_BANNER)
                    .name("<gray>" + (i + 1) + ". <white>" + town.name() + "</white>")
                    .lore("<gray>" + label() + ": <white>" + valueOf(town) + "</white>",
                            "<gray>Residents: <white>" + town.memberCount() + "</white>",
                            "", "<yellow>Click to visit")
                    .hideAttributes().build());
            shown.add(town.name());
        }

        inventory.setItem(SORT, Items.of(Material.HOPPER)
                .name("<#5ad1e8><bold>Ranking by: " + label() + "</bold>")
                .lore("<gray>Click to switch between money,",
                        "<gray>land and population.")
                .hideAttributes().build());
        inventory.setItem(BACK, Items.of(Material.ARROW).name("<gray>Back").build());
        inventory.setItem(CLOSE, Items.of(Material.BARRIER).name("<red>Close").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    private String nextMetric() {
        return switch (metric) {
            case "bank" -> "land";
            case "land" -> "residents";
            default -> "bank";
        };
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        switch (slot) {
            case SORT -> new TownTopMenu(plugin, player, nextMetric()).open();
            case BACK -> new TownMenu(plugin, player).open();
            case CLOSE -> player.closeInventory();
            default -> {
                int index = -1;
                for (int i = 0; i < 3; i++) if (PODIUM[i] == slot) index = i;
                if (index < 0 && slot >= LIST_START && slot < 36) index = 3 + (slot - LIST_START);
                if (index < 0 || index >= shown.size()) return;
                player.closeInventory();
                plugin.towns().teleportToTown(player, shown.get(index));
            }
        }
    }
}
