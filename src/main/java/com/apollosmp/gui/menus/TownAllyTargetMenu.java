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

/** Pick a town to offer an alliance to. */
public class TownAllyTargetMenu extends Gui {

    private static final int PAGE_SIZE = 27;
    private final List<String> shown = new ArrayList<>();

    public TownAllyTargetMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 4, "<#5ad1e8><bold>Approach Which Town?</bold>");
    }

    @Override
    protected void build() {
        shown.clear();
        Town own = plugin.towns().getTownOf(viewer.getUniqueId());
        if (own == null) { viewer.closeInventory(); return; }

        List<Town> options = new ArrayList<>();
        for (Town t : plugin.towns().allTowns()) {
            if (t.name().equalsIgnoreCase(own.name())) continue;
            if (plugin.diplomacy().allied(own.name(), t.name())) continue;
            options.add(t);
        }

        if (options.isEmpty()) {
            inventory.setItem(13, Items.of(Material.BARRIER)
                    .name("<gray>Nobody to approach")
                    .lore("<gray>Every other town is already your ally,",
                            "<gray>or there aren't any yet.").build());
        }
        for (int i = 0; i < options.size() && i < PAGE_SIZE; i++) {
            Town t = options.get(i);
            boolean atWar = plugin.wars().atWar(own.name(), t.name());
            inventory.setItem(i, Items.of(Material.WHITE_BANNER)
                    .name("<white><bold>" + t.name() + "</bold>")
                    .lore("<gray>Residents: <white>" + t.memberCount() + "</white>",
                            "<gray>Land: <white>" + t.claims().size() + "</white> chunks",
                            "",
                            atWar ? "<red>You're at war - make peace first"
                                  : "<yellow>Click to offer an alliance")
                    .hideAttributes().build());
            shown.add(t.name());
        }

        inventory.setItem(31, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == 31) { new TownDiplomacyMenu(plugin, player).open(); return; }
        if (slot < 0 || slot >= shown.size()) return;
        plugin.diplomacy().propose(player, shown.get(slot));
        player.closeInventory();
    }
}
