package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.admin.InventorySnapshots;
import com.apollosmp.gui.Gui;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

/** Read-only view of an offline player's last known inventory. */
public class AdminInventoryMenu extends Gui {

    private final UUID target;

    public AdminInventoryMenu(ApolloSMP plugin, Player viewer, UUID target) {
        super(plugin, viewer, 6, "<#ff4e50><bold>Inventory Snapshot</bold>");
        this.target = target;
    }

    @Override
    protected void build() {
        String name = plugin.economy().nameOf(target);
        if (name == null) name = "Unknown";
        InventorySnapshots.Snapshot snap = plugin.snapshots().get(target);

        if (snap == null) {
            inventory.setItem(22, Items.of(Material.BARRIER)
                    .name("<gray>No snapshot yet")
                    .lore("<gray>Snapshots are taken when a player",
                            "<gray>logs out. <white>" + name + "</white> hasn't",
                            "<gray>logged out since this was added.").build());
            inventory.setItem(49, Items.of(Material.ARROW).name("<gray>Back").build());
            fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
            return;
        }

        // Main inventory: slots 9-35 are the backpack, 0-8 the hotbar.
        ItemStack[] contents = snap.contents();
        for (int i = 0; i < 36 && i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) continue;
            // Show the hotbar along the bottom of the grid, like the real inventory.
            int slot = (i < 9) ? 27 + i : i - 9;
            inventory.setItem(slot, item.clone());
        }

        // Armour and off-hand on the row below.
        ItemStack[] armor = snap.armor();
        int[] armorSlots = {36, 37, 38, 39};
        for (int i = 0; i < armor.length && i < 4; i++) {
            if (armor[i] == null || armor[i].getType().isAir()) continue;
            inventory.setItem(armorSlots[3 - i], armor[i].clone());
        }
        if (snap.offHand() != null && !snap.offHand().getType().isAir()) {
            inventory.setItem(41, snap.offHand().clone());
        }

        inventory.setItem(45, Items.of(Material.PAPER)
                .name("<#f9d423><bold>" + name + "</bold>")
                .lore("<gray>Taken: <white>"
                                + new SimpleDateFormat("MMM d, yyyy HH:mm").format(new Date(snap.taken()))
                                + "</white>",
                        "<gray>This is their inventory as it was",
                        "<gray>when they last logged out.",
                        "",
                        "<dark_gray>Read-only - changes here do nothing.")
                .build());

        inventory.setItem(49, Items.of(Material.ARROW).name("<gray>Back").build());
        inventory.setItem(53, Items.of(Material.BARRIER).name("<red>Close").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == 49) new AdminPlayerMenu(plugin, player, target).open();
        else if (slot == 53) player.closeInventory();
    }
}
