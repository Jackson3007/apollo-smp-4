package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.DiplomacyManager;
import com.apollosmp.town.Town;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Alliances: who you're friendly with, and who's asking. */
public class TownDiplomacyMenu extends Gui {

    private static final int[] ALLY_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int PROPOSE = 30;
    private static final int ACCEPT = 29;
    private static final int DECLINE = 33;
    private static final int BACK = 40;

    private final List<String> allies = new ArrayList<>();

    public TownDiplomacyMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 5, "<#5ad1e8><bold>Alliances</bold>");
    }

    @Override
    protected void build() {
        allies.clear();
        Town town = plugin.towns().getTownOf(viewer.getUniqueId());
        if (town == null) { viewer.closeInventory(); return; }

        boolean isMayor = town.mayor().equals(viewer.getUniqueId());
        List<String> current = plugin.diplomacy().alliesOf(town.name());
        DiplomacyManager.Offer offer = plugin.diplomacy().offerFor(town.name());

        inventory.setItem(4, Items.of(Material.WHITE_BANNER)
                .name("<#5ad1e8><bold>" + town.name() + "'s Alliances</bold>")
                .lore("<gray>Allies: <white>" + current.size() + "</white>",
                        "",
                        "<gray>An alliance gives you:",
                        "<#5ad1e8>- shared chat with /ac</#5ad1e8>",
                        "<#5ad1e8>- free passage to their spawn</#5ad1e8>",
                        "<#5ad1e8>- they join your wars, and you join theirs</#5ad1e8>",
                        "",
                        "<dark_gray>You can't declare war on an ally.")
                .glow(true).hideAttributes().build());

        if (current.isEmpty()) {
            inventory.setItem(22, Items.of(Material.GRAY_DYE)
                    .name("<gray>No allies yet")
                    .lore("<gray>Propose one below.").build());
        }
        for (int i = 0; i < current.size() && i < ALLY_SLOTS.length; i++) {
            String name = current.get(i);
            Town ally = plugin.towns().townByName(name);
            inventory.setItem(ALLY_SLOTS[i], Items.of(Material.LIME_BANNER)
                    .name("<#5ad1e8><bold>" + name + "</bold>")
                    .lore("<gray>Residents: <white>"
                                    + (ally == null ? "?" : ally.memberCount()) + "</white>",
                            "<gray>Land: <white>"
                                    + (ally == null ? "?" : ally.claims().size()) + "</white> chunks",
                            "",
                            isMayor ? "<red>Shift-click to break the alliance"
                                    : "<dark_gray>Mayor only")
                    .hideAttributes().build());
            allies.add(name);
        }

        if (offer != null && System.currentTimeMillis() < offer.expires()) {
            inventory.setItem(ACCEPT, Items.of(Material.LIME_WOOL)
                    .name("<green><bold>Accept " + offer.from() + "</bold>")
                    .lore("<gray>They've offered an alliance.",
                            "", isMayor ? "<yellow>Click to sign" : "<dark_gray>Mayor only")
                    .glow(true).hideAttributes().build());
            inventory.setItem(DECLINE, Items.of(Material.RED_WOOL)
                    .name("<red><bold>Decline</bold>")
                    .lore("<gray>Turn down <white>" + offer.from() + "</white>.",
                            "", isMayor ? "<yellow>Click to refuse" : "<dark_gray>Mayor only")
                    .build());
        } else {
            inventory.setItem(PROPOSE, Items.of(Material.WRITABLE_BOOK)
                    .name("<#5ad1e8><bold>Propose an Alliance</bold>")
                    .lore("<gray>Pick a town to approach.",
                            "<dark_gray>/town ally <town>",
                            "", isMayor ? "<yellow>Click to choose" : "<dark_gray>Mayor only")
                    .build());
        }

        inventory.setItem(BACK, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        Town town = plugin.towns().getTownOf(player.getUniqueId());
        if (town == null) { player.closeInventory(); return; }
        if (slot == BACK) { new TownManageMenu(plugin, player).open(); return; }

        boolean isMayor = town.mayor().equals(player.getUniqueId());
        if (!isMayor) {
            plugin.msg().send(player, "<red>Only the mayor handles alliances.");
            return;
        }

        for (int i = 0; i < ALLY_SLOTS.length; i++) {
            if (ALLY_SLOTS[i] == slot && i < allies.size()) {
                if (click.isShiftClick()) {
                    plugin.diplomacy().breakAlliance(player, allies.get(i));
                    redraw();
                } else {
                    plugin.msg().send(player, "<gray>Shift-click to break this alliance.");
                }
                return;
            }
        }

        switch (slot) {
            case ACCEPT -> { plugin.diplomacy().accept(player, null); redraw(); }
            case DECLINE -> { plugin.diplomacy().decline(player); redraw(); }
            case PROPOSE -> new TownAllyTargetMenu(plugin, player).open();
            default -> { /* display only */ }
        }
    }
}
