package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.town.TownPerm;
import com.apollosmp.town.TownRank;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Toggle what each editable rank is allowed to do. */
public class TownPermsMenu extends Gui {

    // Ranks that can be edited (Mayor always has everything).
    private static final TownRank[] EDITABLE = {TownRank.ASSISTANT, TownRank.COMMANDER, TownRank.RESIDENT};
    private static final int PERM_START = 18;

    private TownRank rank;

    public TownPermsMenu(ApolloSMP plugin, Player viewer, TownRank rank) {
        super(plugin, viewer, 5, "<#5ad1e8><bold>Rank Permissions</bold>");
        this.rank = (rank == null || rank == TownRank.MAYOR) ? TownRank.RESIDENT : rank;
    }

    @Override
    protected void build() {
        Town town = plugin.towns().getTownOf(viewer.getUniqueId());
        if (town == null) { viewer.closeInventory(); return; }

        inventory.setItem(4, Items.of(Material.NAME_TAG)
                .name("<#f9d423><bold>Editing: " + rank.display() + "</bold>")
                .lore("<gray>Click to switch rank.",
                        "<gray>Green = allowed, red = blocked.")
                .glow(true).hideAttributes().build());

        TownPerm[] perms = TownPerm.values();
        for (int i = 0; i < perms.length; i++) {
            TownPerm perm = perms[i];
            boolean on = town.permsFor(rank).contains(perm);
            inventory.setItem(PERM_START + i, Items.of(on ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name((on ? "<green>" : "<red>") + prettyPerm(perm))
                    .lore(on ? "<green>Allowed" : "<red>Blocked", "<gray>Click to toggle.").build());
        }

        inventory.setItem(40, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == 40) { new TownSettingsMenu(plugin, player).open(); return; }
        if (slot == 4) {
            int idx = 0;
            for (int i = 0; i < EDITABLE.length; i++) if (EDITABLE[i] == rank) idx = i;
            rank = EDITABLE[(idx + 1) % EDITABLE.length];
            redraw();
            return;
        }
        int index = slot - PERM_START;
        TownPerm[] perms = TownPerm.values();
        if (index >= 0 && index < perms.length) {
            plugin.towns().togglePerm(player, rank, perms[index]);
            redraw();
        }
    }

    private String prettyPerm(TownPerm perm) {
        String[] words = perm.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }
}
