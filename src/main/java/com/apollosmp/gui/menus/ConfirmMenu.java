package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** A reusable "are you sure?" screen for destructive actions. */
public class ConfirmMenu extends Gui {

    private static final int INFO = 4;
    private static final int CONFIRM = 11;
    private static final int CANCEL = 15;

    private final String question;
    private final List<String> warnings;
    private final Runnable onConfirm;
    private final Runnable onCancel;

    public ConfirmMenu(ApolloSMP plugin, Player viewer, String title, String question,
                       List<String> warnings, Runnable onConfirm, Runnable onCancel) {
        super(plugin, viewer, 3, title);
        this.question = question;
        this.warnings = warnings;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected void build() {
        String[] lore = new String[warnings.size()];
        for (int i = 0; i < warnings.size(); i++) lore[i] = warnings.get(i);

        inventory.setItem(INFO, Items.of(Material.PAPER)
                .name("<red><bold>" + question + "</bold>")
                .lore(lore).build());

        inventory.setItem(CONFIRM, Items.of(Material.LIME_WOOL)
                .name("<green><bold>Yes, I'm sure</bold>")
                .lore("<gray>This cannot be undone.").build());

        inventory.setItem(CANCEL, Items.of(Material.RED_WOOL)
                .name("<red><bold>No, go back</bold>")
                .lore("<gray>Nothing will change.")
                .glow(true).hideAttributes().build());

        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == CONFIRM) {
            if (onConfirm != null) onConfirm.run();
        } else if (slot == CANCEL) {
            if (onCancel != null) onCancel.run();
            else player.closeInventory();
        }
    }
}
