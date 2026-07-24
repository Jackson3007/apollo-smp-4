package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.bank.BankManager;
import com.apollosmp.gui.Gui;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Pick how much, then over how long. */
public class LoanRequestMenu extends Gui {

    private static final int[] TERM_SLOTS = {11, 13, 15};
    private static final int BACK = 22;

    private final String townName;
    private final double amount;

    public LoanRequestMenu(ApolloSMP plugin, Player viewer, String townName) {
        this(plugin, viewer, townName, 0);
    }

    public LoanRequestMenu(ApolloSMP plugin, Player viewer, String townName, double amount) {
        super(plugin, viewer, 3, amount <= 0
                ? "<green><bold>How Much?</bold>"
                : "<green><bold>Pay It Back When?</bold>");
        this.townName = townName;
        this.amount = amount;
    }

    @Override
    protected void build() {
        BankManager bank = plugin.bank();
        double limit = bank.borrowLimit(viewer.getUniqueId());

        if (amount <= 0) {
            inventory.setItem(4, Items.of(Material.GOLD_INGOT)
                    .name("<#f9d423><bold>Borrow up to " + plugin.msg().money(limit) + "</bold>")
                    .lore("<gray>Your standing: " + bank.reputationOf(viewer.getUniqueId()).coloured(),
                            "<gray>Interest: <white>" + (int) bank.interestPercent() + "%</white>")
                    .glow(true).hideAttributes().build());

            double[] options = {limit * 0.25, limit * 0.5, limit};
            int[] slots = {11, 13, 15};
            for (int i = 0; i < options.length; i++) {
                double value = Math.floor(options[i] / 100.0) * 100.0;
                if (value <= 0) continue;
                inventory.setItem(slots[i], Items.of(Material.EMERALD)
                        .name("<green><bold>" + plugin.msg().money(value) + "</bold>")
                        .lore("<gray>Repay <#f9d423>"
                                        + plugin.msg().money(value * (1 + bank.interestPercent() / 100.0))
                                        + "</#f9d423> in total",
                                "", "<yellow>Click to choose a term")
                        .build());
            }
            inventory.setItem(16, Items.of(Material.NAME_TAG)
                    .name("<#5ad1e8><bold>Another Amount</bold>")
                    .lore("<gray>Type your own figure.",
                            "", "<yellow>Click to enter it").build());
        } else {
            inventory.setItem(4, Items.of(Material.GOLD_INGOT)
                    .name("<#f9d423><bold>Borrowing " + plugin.msg().money(amount) + "</bold>")
                    .lore("<gray>Repay <#f9d423>"
                                    + plugin.msg().money(amount * (1 + bank.interestPercent() / 100.0))
                                    + "</#f9d423> <gray>in total</gray>",
                            "<gray>Miss the deadline and your",
                            "<gray>reputation takes the hit.")
                    .glow(true).hideAttributes().build());

            int[] terms = bank.terms();
            for (int i = 0; i < terms.length && i < TERM_SLOTS.length; i++) {
                inventory.setItem(TERM_SLOTS[i], Items.of(Material.CLOCK)
                        .name("<#f9d423><bold>" + terms[i] + " days</bold>")
                        .lore("<gray>Same interest either way -",
                                "<gray>pick what you can actually meet.",
                                "", "<yellow>Click to send the request")
                        .build());
            }
        }

        inventory.setItem(BACK, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        BankManager bank = plugin.bank();
        if (slot == BACK) {
            if (amount <= 0) new TownBankBlockMenu(plugin, player, townName).open();
            else new LoanRequestMenu(plugin, player, townName).open();
            return;
        }

        if (amount <= 0) {
            double limit = bank.borrowLimit(player.getUniqueId());
            double[] options = {limit * 0.25, limit * 0.5, limit};
            int[] slots = {11, 13, 15};
            for (int i = 0; i < slots.length; i++) {
                if (slots[i] != slot) continue;
                double value = Math.floor(options[i] / 100.0) * 100.0;
                if (value <= 0) return;
                new LoanRequestMenu(plugin, player, townName, value).open();
                return;
            }
            if (slot == 16) {
                player.closeInventory();
                plugin.msg().send(player, "<#f9d423>Type how much you'd like to borrow</#f9d423> "
                        + "<gray>(up to " + plugin.msg().money(limit) + ", or 'cancel').");
                plugin.prompts().await(player, s -> {
                    try {
                        double value = Double.parseDouble(s);
                        if (value <= 0 || value > limit) {
                            plugin.msg().send(player, "<red>That's outside your limit.");
                            new LoanRequestMenu(plugin, player, townName).open();
                            return;
                        }
                        new LoanRequestMenu(plugin, player, townName, value).open();
                    } catch (NumberFormatException e) {
                        plugin.msg().send(player, "<red>That's not a number.");
                        new LoanRequestMenu(plugin, player, townName).open();
                    }
                });
            }
            return;
        }

        int[] terms = bank.terms();
        for (int i = 0; i < terms.length && i < TERM_SLOTS.length; i++) {
            if (TERM_SLOTS[i] != slot) continue;
            bank.request(player, amount, terms[i]);
            player.closeInventory();
            return;
        }
    }
}
