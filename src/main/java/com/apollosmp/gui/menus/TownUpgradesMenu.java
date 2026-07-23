package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.town.TownPerm;
import com.apollosmp.town.TownUpgrade;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Spend the town bank on permanent perks. */
public class TownUpgradesMenu extends Gui {

    private static final int[] SLOTS = {19, 20, 21, 22, 23, 24};

    public TownUpgradesMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 5, "<#f9d423><bold>Town Upgrades</bold>");
    }

    @Override
    protected void build() {
        Town town = plugin.towns().getTownOf(viewer.getUniqueId());
        if (town == null) { viewer.closeInventory(); return; }

        inventory.setItem(4, Items.of(Material.GOLD_BLOCK)
                .name("<#f9d423><bold>Town Bank: " + plugin.msg().money(town.bank()) + "</bold>")
                .lore("<gray>Upgrades are paid for by the town,",
                        "<gray>not out of your own pocket.",
                        "",
                        "<gray>Perks apply to residents standing",
                        "<gray>on your town's land.")
                .glow(true).hideAttributes().build());

        TownUpgrade[] all = TownUpgrade.values();
        for (int i = 0; i < all.length && i < SLOTS.length; i++) {
            TownUpgrade upgrade = all[i];
            int level = town.upgradeLevel(upgrade);
            boolean maxed = level >= upgrade.maxLevel();
            double cost = plugin.towns().upgradeCost(town, upgrade);

            List<String> lore = new ArrayList<>();
            for (String line : upgrade.description()) lore.add("<gray>" + line);
            lore.add("");
            lore.add("<gray>Level: <white>" + level + " / " + upgrade.maxLevel() + "</white>");
            lore.add(bar(level, upgrade.maxLevel()));
            lore.add("");
            if (upgrade == TownUpgrade.PRODUCTION && level > 0) {
                lore.add("<gray>Currently: <green>+" + (int) Math.round(level * 15) + "%</green> production");
            }
            if (upgrade == TownUpgrade.CLAIM_LIMIT) {
                lore.add("<gray>Claim limit: <white>" + town.claims().size()
                        + " / " + plugin.towns().claimLimit(town) + "</white>");
            }
            if (maxed) {
                lore.add("<green>Fully upgraded");
            } else {
                lore.add("<gray>Next level: <#f9d423>" + plugin.msg().money(cost) + "</#f9d423>");
                lore.add(town.bank() >= cost
                        ? "<yellow>Click to buy"
                        : "<red>The bank can't afford this yet");
            }

            inventory.setItem(SLOTS[i], Items.of(upgrade.icon())
                    .name((maxed ? "<green>" : "<#f9d423>") + "<bold>" + upgrade.display() + "</bold>")
                    .lore(lore.toArray(new String[0]))
                    .glow(level > 0).hideAttributes().build());
        }

        inventory.setItem(40, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    private String bar(int level, int max) {
        StringBuilder sb = new StringBuilder("<gray>");
        for (int i = 0; i < max; i++) {
            sb.append(i < level ? "<#f9d423>\u25a0</#f9d423>" : "<dark_gray>\u25a0</dark_gray>");
        }
        return sb.toString();
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == 40) { new TownMenu(plugin, player).open(); return; }

        Town town = plugin.towns().getTownOf(player.getUniqueId());
        if (town == null) return;
        TownUpgrade[] all = TownUpgrade.values();
        for (int i = 0; i < all.length && i < SLOTS.length; i++) {
            if (SLOTS[i] != slot) continue;
            if (!town.hasPerm(player.getUniqueId(), TownPerm.WITHDRAW)) {
                plugin.msg().send(player, "<red>You don't have permission to spend the town bank.");
                return;
            }
            plugin.towns().buyUpgrade(player, all[i]);
            redraw();
            return;
        }
    }
}
