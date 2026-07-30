package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/** Lets an admin pick which of a player's personal vaults to open. */
public class AdminVaultsMenu extends Gui {

    private final UUID target;

    public AdminVaultsMenu(ApolloSMP plugin, Player viewer, UUID target) {
        super(plugin, viewer, 3, "<#ff4e50><bold>Admin - Vaults</bold>");
        this.target = target;
    }

    @Override
    protected void build() {
        String name = plugin.economy().nameOf(target);
        if (name == null) name = "Unknown";

        inventory.setItem(4, Items.of(Material.ENDER_CHEST)
                .name("<#f9d423><bold>" + name + "'s Vaults</bold>")
                .lore("<gray>Click a vault to open it.",
                        "<yellow>Your edits save to their vault.").hideAttributes().build());

        int count = plugin.vaults().vaultCount();
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20};
        for (int i = 0; i < count && i < slots.length; i++) {
            int index = i + 1;
            int used = plugin.vaults().used(target, index);
            inventory.setItem(slots[i], Items.of(Material.CHEST)
                    .name("<#5ad1e8><bold>Vault " + index + "</bold>")
                    .lore("<gray>Items stored: <white>" + used + "</white>",
                            "", "<yellow>Click to open").build());
        }

        inventory.setItem(22, Items.of(Material.ARROW).name("<gray>Back").build());
        inventory.setItem(26, Items.of(Material.BARRIER).name("<red>Close").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (!player.hasPermission("apollo.admin")) { player.closeInventory(); return; }
        if (slot == 22) { new AdminPlayerMenu(plugin, player, target).open(); return; }
        if (slot == 26) { player.closeInventory(); return; }

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20};
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] == slot && i < plugin.vaults().vaultCount()) {
                plugin.vaults().openFor(player, target, i + 1);
                plugin.msg().send(player, "<gray>Opened <white>"
                        + plugin.economy().nameOf(target) + "</white>'s vault " + (i + 1) + ".");
                return;
            }
        }
    }
}
