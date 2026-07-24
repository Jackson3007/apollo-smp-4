package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.AllyPerm;
import com.apollosmp.town.Town;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

/** What one ally is allowed to do on your land. Each town sets its own. */
public class TownAllyPermsMenu extends Gui {

    private static final int[] SLOTS = {19, 20, 21, 22};
    private static final int BREAK = 31;
    private static final int BACK = 40;

    private final String allyName;

    public TownAllyPermsMenu(ApolloSMP plugin, Player viewer, String allyName) {
        super(plugin, viewer, 5, "<#5ad1e8><bold>Trust: " + allyName + "</bold>");
        this.allyName = allyName;
    }

    @Override
    protected void build() {
        Town own = plugin.towns().getTownOf(viewer.getUniqueId());
        if (own == null) { viewer.closeInventory(); return; }
        Town ally = plugin.towns().townByName(allyName);
        boolean isMayor = own.mayor().equals(viewer.getUniqueId());

        Set<AllyPerm> granted = plugin.diplomacy().grantsFrom(own.name(), allyName);
        Set<AllyPerm> theirs = plugin.diplomacy().grantsFrom(allyName, own.name());

        inventory.setItem(4, Items.of(Material.LIME_BANNER)
                .name("<#5ad1e8><bold>" + allyName + "</bold>")
                .lore("<gray>Residents: <white>"
                                + (ally == null ? "?" : ally.memberCount()) + "</white>",
                        "",
                        "<gray>These settings control what <white>" + allyName + "</white>",
                        "<gray>may do on <white>" + own.name() + "</white>'s land.",
                        "<dark_gray>They set their own separately.",
                        "",
                        "<gray>They currently allow you: <white>"
                                + (theirs.isEmpty() ? "nothing" : theirs.size() + " thing(s)") + "</white>")
                .glow(true).hideAttributes().build());

        AllyPerm[] all = AllyPerm.values();
        for (int i = 0; i < all.length && i < SLOTS.length; i++) {
            AllyPerm perm = all[i];
            boolean on = granted.contains(perm);
            inventory.setItem(SLOTS[i], Items.of(on ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name((on ? "<green>" : "<gray>") + "<bold>" + perm.display() + "</bold>")
                    .lore("<gray>" + perm.description(),
                            "",
                            on ? "<green>Allowed" : "<red>Not allowed",
                            theirs.contains(perm)
                                    ? "<dark_gray>They allow you this too" : "",
                            "", isMayor ? "<yellow>Click to toggle" : "<dark_gray>Mayor only")
                    .glow(on).hideAttributes().build());
        }

        inventory.setItem(BREAK, Items.of(Material.BARRIER)
                .name("<red><bold>Break Alliance</bold>")
                .lore("<gray>End the alliance with <white>" + allyName + "</white>.",
                        "<gray>All trust is revoked both ways.",
                        "", isMayor ? "<yellow>Click to break it" : "<dark_gray>Mayor only")
                .build());

        inventory.setItem(BACK, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == BACK) { new TownDiplomacyMenu(plugin, player).open(); return; }

        Town own = plugin.towns().getTownOf(player.getUniqueId());
        if (own == null) { player.closeInventory(); return; }
        if (!own.mayor().equals(player.getUniqueId())) {
            plugin.msg().send(player, "<red>Only the mayor sets what allies may do.");
            return;
        }

        if (slot == BREAK) {
            String name = allyName;
            new ConfirmMenu(plugin, player,
                    "<red><bold>Break with " + name + "?</bold>",
                    "End the alliance with " + name + "?",
                    List.of("<gray>You'd lose shared chat, free passage",
                            "<gray>and mutual defence.",
                            "<red>All trust settings are wiped both ways.",
                            "",
                            "<gray>You could always ask again later."),
                    () -> {
                        plugin.diplomacy().breakAlliance(player, name);
                        new TownDiplomacyMenu(plugin, player).open();
                    },
                    () -> new TownAllyPermsMenu(plugin, player, name).open()
            ).open();
            return;
        }

        AllyPerm[] all = AllyPerm.values();
        for (int i = 0; i < all.length && i < SLOTS.length; i++) {
            if (SLOTS[i] != slot) continue;
            boolean now = plugin.diplomacy().toggleGrant(own.name(), allyName, all[i]);
            plugin.msg().send(player, now
                    ? "<green>" + allyName + " may now: <white>" + all[i].display() + "</white>"
                    : "<yellow>" + allyName + " may no longer: <white>" + all[i].display() + "</white>");
            redraw();
            return;
        }
    }
}
