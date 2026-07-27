package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.town.TownManager;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Pick the colour your town's name shows in chat. */
public class TownColourMenu extends Gui {

    private static final int[] SLOTS = {
            10, 11, 12, 13, 14, 15,
            19, 20, 21, 22, 23, 24
    };
    /** Wool that roughly matches each palette entry. */
    private static final Material[] WOOL = {
            Material.YELLOW_WOOL, Material.RED_WOOL, Material.LIGHT_BLUE_WOOL,
            Material.PINK_WOOL, Material.GREEN_WOOL, Material.PURPLE_WOOL,
            Material.ORANGE_WOOL, Material.WHITE_WOOL, Material.LIME_WOOL,
            Material.BLUE_WOOL, Material.LIGHT_GRAY_WOOL, Material.RED_TERRACOTTA
    };

    private static final int CUSTOM = 31;
    private static final int BACK = 40;

    public TownColourMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 5, "<#f9d423><bold>Town Colour</bold>");
    }

    @Override
    protected void build() {
        Town town = plugin.towns().getTownOf(viewer.getUniqueId());
        if (town == null) { viewer.closeInventory(); return; }
        String current = town.tagColour();

        inventory.setItem(4, Items.of(Material.NAME_TAG)
                .name("<" + current + "><bold>" + town.name() + "</bold></" + current + ">")
                .lore("<gray>This is how your town shows in chat.",
                        "<gray>Current: <" + current + ">" + current + "</" + current + ">",
                        "",
                        "<dark_gray>Only the mayor can change it.")
                .glow(true).hideAttributes().build());

        String[][] palette = TownManager.TAG_COLOURS;
        for (int i = 0; i < palette.length && i < SLOTS.length; i++) {
            String name = palette[i][0];
            String hex = palette[i][1];
            boolean active = hex.equalsIgnoreCase(current);
            inventory.setItem(SLOTS[i], Items.of(WOOL[i])
                    .name("<" + hex + "><bold>" + name + "</bold></" + hex + ">")
                    .lore("<gray>Your tag would read:",
                            "<dark_gray>[</dark_gray><" + hex + ">" + town.name()
                                    + "</" + hex + "><dark_gray> | Mayor]</dark_gray>",
                            "",
                            active ? "<green>In use" : "<yellow>Click to use this")
                    .glow(active).hideAttributes().build());
        }

        inventory.setItem(CUSTOM, Items.of(Material.PAPER)
                .name("<#5ad1e8><bold>Custom Hex</bold>")
                .lore("<gray>Type your own, like <white>#5ad1e8</white>.",
                        "", "<yellow>Click to enter one").build());

        inventory.setItem(BACK, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == BACK) { new TownSettingsMenu(plugin, player).open(); return; }

        Town town = plugin.towns().getTownOf(player.getUniqueId());
        if (town == null) { player.closeInventory(); return; }
        if (!town.mayor().equals(player.getUniqueId())) {
            plugin.msg().send(player, "<red>Only the mayor can change the town colour.");
            return;
        }

        if (slot == CUSTOM) {
            player.closeInventory();
            plugin.msg().send(player, "<#5ad1e8>Type a hex colour</#5ad1e8> "
                    + "<gray>like <white>#5ad1e8</white> (or 'cancel').");
            plugin.prompts().await(player, s -> {
                plugin.towns().setTagColour(player, s);
                new TownColourMenu(plugin, player).open();
            });
            return;
        }

        String[][] palette = TownManager.TAG_COLOURS;
        for (int i = 0; i < palette.length && i < SLOTS.length; i++) {
            if (SLOTS[i] != slot) continue;
            plugin.towns().setTagColour(player, palette[i][1]);
            redraw();
            return;
        }
    }
}
