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
                .name("<green>Deposit Custom").lore("<gray>Click, then type an amount.").build());
        inventory.setItem(WITHDRAW_CUSTOM, Items.of(Material.REDSTONE_BLOCK)
                .name("<yellow>Withdraw Custom").lore("<gray>Click, then type an amount.").build());

        inventory.setItem(33, Items.of(com.apollosmp.bank.BankManager.BANK_BLOCK)
                .name("<#f9d423><bold>Buy a Bank Block</bold>")
                .lore("<gray>Place it in town so residents can",
                        "<gray>borrow against the town's money.",
                        "<gray>Cost: <#f9d423>" + plugin.msg().money(plugin.bank().bankPrice()) + "</#f9d423>",
                        "<dark_gray>Paid from the town bank",
                        "", "<yellow>Click to buy")
                .hideAttributes().build());

        inventory.setItem(34, Items.of(com.apollosmp.shop.ShopManager.STALL_BLOCK)
                .name("<#5ad1e8><bold>Buy a Market Stall</bold>")
                .lore("<gray>Sell goods to travellers. Takings",
                        "<gray>go straight into this bank.",
                        "<gray>Cost: <#f9d423>" + plugin.msg().money(plugin.shops().stallPrice()) + "</#f9d423>",
                        "<dark_gray>Paid from the town bank",
                        "", "<yellow>Click to buy")
                .hideAttributes().build());

        inventory.setItem(29, Items.of(Material.EMERALD)
                .name("<#f9d423><bold>Daily Tax</bold>")
                .lore("<gray>Currently <white>" + plugin.msg().money(town.tax()) + "</white> per resident",
                        "<gray>Collected into this bank each day.",
                        "", "<yellow>Click to change")
                .build());

        inventory.setItem(31, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == 31) { new TownManageMenu(plugin, player).open(); return; }
        if (slot == 33 || slot == 34) {
            com.apollosmp.town.Town town = plugin.towns().getTownOf(player.getUniqueId());
            if (town == null) { player.closeInventory(); return; }
            boolean isBank = slot == 33;
            double cost = isBank ? plugin.bank().bankPrice() : plugin.shops().stallPrice();
            if (!town.hasPerm(player.getUniqueId(), com.apollosmp.town.TownPerm.WITHDRAW)) {
                plugin.msg().send(player, "<red>You can't spend the town bank.");
                return;
            }
            if (!town.withdrawBank(cost)) {
                plugin.msg().send(player, "<red>The town bank needs "
                        + plugin.msg().money(cost) + ".");
                return;
            }
            plugin.towns().markDirty();
            com.apollosmp.util.Items.give(player, isBank
                    ? plugin.bank().createBlock()
                    : plugin.shops().createBlock());
            plugin.msg().send(player, "<green>Bought for <#f9d423>"
                    + plugin.msg().money(cost) + "</#f9d423>. Place it on your town's land.");
            redraw();
            return;
        }
        if (slot == 29) {
            player.closeInventory();
            plugin.msg().send(player, "<#f9d423>Type the daily tax per resident</#f9d423> <gray>(or 'cancel').");
            plugin.prompts().await(player, s -> {
                try { plugin.towns().setTax(player, Double.parseDouble(s)); }
                catch (NumberFormatException e) { plugin.msg().send(player, "<red>That's not a number."); }
                new TownBankMenu(plugin, player).open();
            });
            return;
        }

        for (int i = 0; i < AMOUNTS.length; i++) {
            if (slot == DEPOSIT_SLOTS[i]) { plugin.towns().deposit(player, AMOUNTS[i]); redraw(); return; }
            if (slot == WITHDRAW_SLOTS[i]) { plugin.towns().withdraw(player, AMOUNTS[i]); redraw(); return; }
        }
        if (slot == DEPOSIT_CUSTOM) {
            player.closeInventory();
            plugin.msg().send(player, "<#f9d423>Type the amount to deposit</#f9d423> <gray>(or 'cancel').");
            plugin.msg().send(player, "<dark_gray>Or use <white>/town deposit <amount></white>.");
            plugin.prompts().await(player, s -> {
                try { plugin.towns().deposit(player, Double.parseDouble(s)); }
                catch (NumberFormatException e) { plugin.msg().send(player, "<red>That's not a number."); }
                new TownBankMenu(plugin, player).open();
            });
        } else if (slot == WITHDRAW_CUSTOM) {
            player.closeInventory();
            plugin.msg().send(player, "<#f9d423>Type the amount to withdraw</#f9d423> <gray>(or 'cancel').");
            plugin.msg().send(player, "<dark_gray>Or use <white>/town withdraw <amount></white>.");
            plugin.prompts().await(player, s -> {
                try { plugin.towns().withdraw(player, Double.parseDouble(s)); }
                catch (NumberFormatException e) { plugin.msg().send(player, "<red>That's not a number."); }
                new TownBankMenu(plugin, player).open();
            });
        }
    }
}
