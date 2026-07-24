package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.bank.BankManager;
import com.apollosmp.bank.Loan;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Town officials review who's asking to borrow, and who already owes. */
public class LoanApprovalMenu extends Gui {

    private static final int PAGE_SIZE = 18;
    private static final int BACK = 40;

    private final String townName;
    private final List<String> shown = new ArrayList<>();

    public LoanApprovalMenu(ApolloSMP plugin, Player viewer, String townName) {
        super(plugin, viewer, 5, "<#5ad1e8><bold>Loan Requests</bold>");
        this.townName = townName;
    }

    @Override
    protected void build() {
        shown.clear();
        Town town = plugin.towns().townByName(townName);
        if (town == null) { viewer.closeInventory(); return; }
        BankManager bank = plugin.bank();

        List<BankManager.Request> waiting = bank.requestsFor(townName);
        if (waiting.isEmpty()) {
            inventory.setItem(13, Items.of(Material.BARRIER)
                    .name("<gray>No requests")
                    .lore("<gray>Nobody's asking to borrow right now.").build());
        }
        for (int i = 0; i < waiting.size() && i < PAGE_SIZE; i++) {
            BankManager.Request r = waiting.get(i);
            double owed = r.amount() * (1 + bank.interestPercent() / 100.0);
            inventory.setItem(i, Items.of(Material.PAPER)
                    .name("<#f9d423><bold>" + r.borrowerName() + "</bold>")
                    .lore("<gray>Wants: <white>" + plugin.msg().money(r.amount()) + "</white>",
                            "<gray>Over: <white>" + r.days() + " days</white>",
                            "<gray>Repaying: <#f9d423>" + plugin.msg().money(owed) + "</#f9d423>",
                            "<gray>Standing: " + bank.reputationOf(r.borrower()).coloured(),
                            "<gray>Bank holds: <white>" + plugin.msg().money(town.bank()) + "</white>",
                            "",
                            "<green>Left-click:</green> <gray>approve</gray>",
                            "<red>Right-click:</red> <gray>deny</gray>")
                    .build());
            shown.add(r.id());
        }

        // What's already out the door.
        List<Loan> outstanding = bank.loansOwedTo(townName);
        int slot = 27;
        for (Loan loan : outstanding) {
            if (slot > 35) break;
            inventory.setItem(slot++, Items.of(loan.overdue() ? Material.REDSTONE : Material.BOOK)
                    .name((loan.overdue() ? "<red>" : "<gray>") + loan.borrowerName())
                    .lore("<gray>Owes: <#f9d423>" + plugin.msg().money(loan.owed()) + "</#f9d423>",
                            loan.overdue() ? "<red>Overdue</red>"
                                    : "<gray>Due in <white>" + loan.timeLeft() + "</white>",
                            "<gray>Standing: " + bank.reputationOf(loan.borrower()).coloured())
                    .build());
        }

        inventory.setItem(BACK, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == BACK) { new TownBankBlockMenu(plugin, player, townName).open(); return; }
        if (slot < 0 || slot >= shown.size()) return;

        String id = shown.get(slot);
        if (click.isRightClick()) plugin.bank().deny(player, id);
        else plugin.bank().approve(player, id);
        redraw();
    }
}
