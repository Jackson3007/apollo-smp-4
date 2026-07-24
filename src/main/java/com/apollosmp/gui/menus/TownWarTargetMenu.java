package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.town.WarManager;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Pick who to fight, then how long for. */
public class TownWarTargetMenu extends Gui {

    private static final int PAGE_SIZE = 27;
    private final List<String> shown = new ArrayList<>();
    private final String chosen;

    public TownWarTargetMenu(ApolloSMP plugin, Player viewer) {
        this(plugin, viewer, null);
    }

    public TownWarTargetMenu(ApolloSMP plugin, Player viewer, String chosen) {
        super(plugin, viewer, 5, chosen == null
                ? "<red><bold>Declare War On...</bold>"
                : "<red><bold>How Long?</bold>");
        this.chosen = chosen;
    }

    @Override
    protected void build() {
        shown.clear();
        Town own = plugin.towns().getTownOf(viewer.getUniqueId());
        if (own == null) { viewer.closeInventory(); return; }

        if (chosen == null) {
            List<Town> targets = new ArrayList<>();
            for (Town t : plugin.towns().allTowns()) {
                if (t.name().equalsIgnoreCase(own.name())) continue;
                if (plugin.wars().atWar(own.name(), t.name())) continue;
                targets.add(t);
            }
            if (targets.isEmpty()) {
                inventory.setItem(22, Items.of(Material.BARRIER)
                        .name("<gray>Nobody to fight")
                        .lore("<gray>There are no other towns yet.").build());
            }
            for (int i = 0; i < targets.size() && i < PAGE_SIZE; i++) {
                Town t = targets.get(i);
                inventory.setItem(i, Items.of(Material.WHITE_BANNER)
                        .name("<#ff4e50><bold>" + t.name() + "</bold>")
                        .lore("<gray>Residents: <white>" + t.memberCount() + "</white>",
                                "<gray>Land: <white>" + t.claims().size() + "</white> chunks",
                                "", "<yellow>Click to choose a length")
                        .hideAttributes().build());
                shown.add(t.name());
            }
        } else {
            inventory.setItem(4, Items.of(Material.RED_BANNER)
                    .name("<red><bold>War on " + chosen + "</bold>")
                    .lore("<gray>How long should it last?",
                            "<gray>They still have to agree.")
                    .glow(true).hideAttributes().build());
            int[] slots = {20, 21, 23, 24};
            for (int i = 0; i < WarManager.DURATIONS.length && i < slots.length; i++) {
                int minutes = WarManager.DURATIONS[i];
                inventory.setItem(slots[i], Items.of(Material.CLOCK)
                        .name("<#f9d423><bold>" + label(minutes) + "</bold>")
                        .lore("<gray>War ends automatically after this.",
                                "", "<yellow>Click to declare").build());
            }
        }

        inventory.setItem(40, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    private String label(int minutes) {
        if (minutes < 60) return minutes + " minutes";
        int hours = minutes / 60;
        return hours == 1 ? "1 hour" : hours + " hours";
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == 40) {
            if (chosen == null) new TownWarMenu(plugin, player).open();
            else new TownWarTargetMenu(plugin, player).open();
            return;
        }

        if (chosen == null) {
            if (slot >= 0 && slot < shown.size()) {
                new TownWarTargetMenu(plugin, player, shown.get(slot)).open();
            }
            return;
        }

        int[] slots = {20, 21, 23, 24};
        for (int i = 0; i < WarManager.DURATIONS.length && i < slots.length; i++) {
            if (slots[i] != slot) continue;
            plugin.wars().declare(player, chosen, WarManager.DURATIONS[i]);
            player.closeInventory();
            return;
        }
    }
}
