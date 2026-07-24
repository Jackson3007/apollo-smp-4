package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.bank.BankManager;
import com.apollosmp.bank.Loan;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.town.TownPerm;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The screen you get from right-clicking a town bank block. */
public class TownBankBlockMenu extends Gui {

    private static final int BORROW = 20;
    private static final int MY_DEBT = 22;
    private static final int REQUESTS = 24;
    private static final int CLOSE = 31;

    private final String townName;

    public TownBankBlockMenu(ApolloSMP plugin, Player viewer, String townName) {
        super(plugin, viewer, 4, "<gradient:#f9d423:#ff4e50><bold>Town Bank</bold></gradient>");
        this.townName = townName;
    }

    @Override
    protected void build() {
        Town town = plugin.towns().townByName(townName);
        if (town == null) { viewer.closeInventory(); return; }

        BankManager bank = plugin.bank();
        BankManager.Reputation rep = bank.reputationOf(viewer.getUniqueId());
        List<Loan> mine = bank.loansOf(viewer.getUniqueId());
        boolean resident = town.isMember(viewer.getUniqueId());
        boolean official = town.hasPerm(viewer.getUniqueId(), TownPerm.WITHDRAW);

        inventory.setItem(4, Items.of(Material.GOLD_BLOCK)
                .name("<#f9d423><bold>" + town.name() + " Bank</bold>")
                .lore("<gray>Holding: <#f9d423>" + plugin.msg().money(town.bank()) + "</#f9d423>",
                        "<gray>Your standing: " + rep.coloured(),
                        "<gray>You can borrow up to <white>"
                                + plugin.msg().money(bank.borrowLimit(viewer.getUniqueId())) + "</white>",
                        "",
                        "<dark_gray>Interest is " + (int) bank.interestPercent() + "% on every loan.")
                .glow(true).hideAttributes().build());

        // ---- borrowing ----
        List<String> borrowLore = new ArrayList<>();
        if (!resident) {
            borrowLore.add("<red>Only residents can borrow here.");
        } else if (!mine.isEmpty()) {
            borrowLore.add("<gray>Settle your current loan first.");
        } else if (bank.borrowLimit(viewer.getUniqueId()) <= 0) {
            borrowLore.add("<red>You defaulted before.");
            borrowLore.add("<gray>Clear your debt to borrow again.");
        } else {
            borrowLore.add("<gray>Ask the town for a loan.");
            borrowLore.add("<gray>A town official has to approve it.");
            borrowLore.add("");
            borrowLore.add("<yellow>Click to choose an amount");
        }
        inventory.setItem(BORROW, Items.of(Material.EMERALD)
                .name("<green><bold>Request a Loan</bold>")
                .lore(borrowLore).build());

        // ---- what you owe ----
        List<String> debtLore = new ArrayList<>();
        if (mine.isEmpty()) {
            debtLore.add("<green>You're debt free.");
        } else {
            Loan loan = mine.get(0);
            debtLore.add("<gray>Borrowed: <white>" + plugin.msg().money(loan.principal()) + "</white>");
            debtLore.add("<gray>Still owing: <#f9d423>" + plugin.msg().money(loan.owed()) + "</#f9d423>");
            debtLore.add(loan.overdue()
                    ? "<red>OVERDUE - your reputation is suffering</red>"
                    : "<gray>Due in <white>" + loan.timeLeft() + "</white>");
            debtLore.add("");
            debtLore.add("<yellow>Left-click:</yellow> <gray>pay it all</gray>");
            debtLore.add("<yellow>Right-click:</yellow> <gray>pay part of it</gray>");
        }
        inventory.setItem(MY_DEBT, Items.of(mine.isEmpty() ? Material.PAPER : Material.BOOK)
                .name("<#f9d423><bold>Your Debt</bold>")
                .lore(debtLore).glow(!mine.isEmpty()).hideAttributes().build());

        // ---- approvals ----
        if (official) {
            int waiting = bank.requestsFor(town.name()).size();
            int lent = bank.loansOwedTo(town.name()).size();
            inventory.setItem(REQUESTS, Items.of(waiting > 0 ? Material.WRITABLE_BOOK : Material.BOOKSHELF)
                    .name("<#5ad1e8><bold>Loan Requests</bold>")
                    .lore("<gray>Waiting: <white>" + waiting + "</white>",
                            "<gray>Out on loan: <white>" + lent + "</white>",
                            "", "<yellow>Click to review")
                    .glow(waiting > 0).hideAttributes().build());
        }

        inventory.setItem(CLOSE, Items.of(Material.BARRIER).name("<red>Close").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        Town town = plugin.towns().townByName(townName);
        if (town == null) { player.closeInventory(); return; }
        BankManager bank = plugin.bank();

        switch (slot) {
            case CLOSE -> player.closeInventory();

            case BORROW -> {
                if (!town.isMember(player.getUniqueId())) {
                    plugin.msg().send(player, "<red>Only residents can borrow here.");
                    return;
                }
                if (!bank.loansOf(player.getUniqueId()).isEmpty()) {
                    plugin.msg().send(player, "<red>Settle your current loan first.");
                    return;
                }
                new LoanRequestMenu(plugin, player, townName).open();
            }

            case MY_DEBT -> {
                List<Loan> mine = bank.loansOf(player.getUniqueId());
                if (mine.isEmpty()) return;
                Loan loan = mine.get(0);
                if (click.isRightClick()) {
                    player.closeInventory();
                    plugin.msg().send(player, "<#f9d423>Type how much to pay</#f9d423> <gray>(or 'cancel').");
                    plugin.prompts().await(player, s -> {
                        try { bank.repay(player, Double.parseDouble(s)); }
                        catch (NumberFormatException e) { plugin.msg().send(player, "<red>That's not a number."); }
                        new TownBankBlockMenu(plugin, player, townName).open();
                    });
                } else {
                    bank.repay(player, loan.owed());
                    redraw();
                }
            }

            case REQUESTS -> {
                if (!town.hasPerm(player.getUniqueId(), TownPerm.WITHDRAW)) return;
                new LoanApprovalMenu(plugin, player, townName).open();
            }
            default -> { /* display only */ }
        }
    }
}
