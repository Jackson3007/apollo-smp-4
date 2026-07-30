package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Pick which character (or your real self) to play as. */
public class PersonaMenu extends Gui {

    private static final int[] SLOTS = {11, 13, 15};
    private static final int SELF = 22;

    public PersonaMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 3, "<#ff4e50><bold>Choose a Character</bold>");
    }

    @Override
    protected void build() {
        String cur = plugin.incognito().currentSlot(viewer.getUniqueId());
        int count = plugin.incognito().personaCount();

        for (int i = 0; i < count && i < SLOTS.length; i++) {
            String name = plugin.incognito().personaName(i);
            boolean active = cur.equals(String.valueOf(i + 1));
            inventory.setItem(SLOTS[i], Items.of(Material.PLAYER_HEAD)
                    .name((active ? "<green>" : "<#f9d423>") + "<bold>" + name + "</bold>")
                    .lore("<gray>Play as this character with their own",
                            "<gray>saved inventory and progress.",
                            active ? "<green>Currently active" : "",
                            "", active ? "<dark_gray>You're already this character" : "<yellow>Click to become " + name)
                    .glow(active).hideAttributes().build());
        }

        boolean disguised = plugin.incognito().isDisguised(viewer.getUniqueId());
        inventory.setItem(SELF, Items.of(disguised ? Material.LIME_DYE : Material.BARRIER)
                .name(disguised ? "<green><bold>Return to Admin (you)</bold>" : "<gray>You (admin)")
                .lore(disguised
                                ? new String[]{"<gray>Drop the disguise and get your", "<gray>real self back.", "", "<yellow>Click to return"}
                                : new String[]{"<gray>You're currently yourself."})
                .glow(disguised).hideAttributes().build());

        inventory.setItem(26, Items.of(Material.BARRIER).name("<red>Close").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (!player.hasPermission("apollo.admin") && !plugin.incognito().isDisguised(player.getUniqueId())) {
            player.closeInventory();
            return;
        }
        if (slot == 26) { player.closeInventory(); return; }
        if (slot == SELF) {
            player.closeInventory();
            plugin.incognito().returnToSelf(player);
            return;
        }
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == slot && i < plugin.incognito().personaCount()) {
                player.closeInventory();
                plugin.incognito().switchTo(player, String.valueOf(i + 1));
                return;
            }
        }
    }
}
