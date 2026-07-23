package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Deposit to and withdraw from the town bank. */
public class TownBankMenu extends Gui {

    private static final double[] AMOUNTS = {100, 1000, 10000};
    private static final int[] DEPOSIT_SLOTS = {10, 11, 12};
    private static final int[] WITHDRAW_SLOTS = {19, 20, 21};
    private static final int DEPOSIT_CUSTOM = 13;
    private static final int WITHDRAW_CUSTOM = 22;

    public TownBankMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 4, "<#f9d423><bold>Town Bank</bold>");
    }

    @Override
    protected void build() {
        Town town = plugin.towns().getTownOf(viewer.getUniqueId());
        if (town == null) { viewer.closeInventory(); return; }

        inventory.setItem(4, Items.of(Material.GOLD_BLOCK)
                .name("<#f9d423><bold>Balance: " + plugin.msg().money(town.bank()) + "</bold>")
                .glow(true).hideAttributes().build());

        for (int i = 0; i < AMOUNTS.length; i++) {
            inventory.setItem(DEPOSIT_SLOTS[i], Items.of(Material.EMERALD)
                    .name("<green>Deposit " + plugin.msg().money(AMOUNTS[i])).build());
            inventory.setItem(WITHDRAW_SLOTS[i], Items.of(Material.REDSTONE)
                    .name("<yellow>Withdraw " + plugin.msg().money(AMOUNTS[i])).build());
        }
        inventory.setItem(DEPOSIT_CUSTOM, Items.of(Material.EMERALD_BLOCK)
                .name("<green>Deposit Custom").lore("<gray>Click to enter an amount.").build());
        inventory.setItem(WITHDRAW_CUSTOM, Items.of(Material.REDSTONE_BLOCK)
                .name("<yellow>Withdraw Custom").lore("<gray>Click to enter an amount.").build());

        inventory.setItem(31, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == 31) { new TownMenu(plugin, player).open(); return; }

        for (int i = 0; i < AMOUNTS.length; i++) {
            if (slot == DEPOSIT_SLOTS[i]) { plugin.towns().deposit(player, AMOUNTS[i]); redraw(); return; }
            if (slot == WITHDRAW_SLOTS[i]) { plugin.towns().withdraw(player, AMOUNTS[i]); redraw(); return; }
        }
        if (slot == DEPOSIT_CUSTOM) {
            new NumberPadMenu(plugin, player,
                    "<green><bold>Deposit</bold>",
                    "Amount to put into the town bank",
                    amount -> {
                        plugin.towns().deposit(player, amount);
                        new TownBankMenu(plugin, player).open();
                    },
                    () -> new TownBankMenu(plugin, player).open()
            ).open();
        } else if (slot == WITHDRAW_CUSTOM) {
            new NumberPadMenu(plugin, player,
                    "<yellow><bold>Withdraw</bold>",
                    "Amount to take from the town bank",
                    amount -> {
                        plugin.towns().withdraw(player, amount);
                        new TownBankMenu(plugin, player).open();
                    },
                    () -> new TownBankMenu(plugin, player).open()
            ).open();
        }
    }
}
