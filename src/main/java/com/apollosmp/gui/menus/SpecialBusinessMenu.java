package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.special.SpecialAuctionManager;
import com.apollosmp.special.SpecialBusiness;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Manage a placed special business: see output and collect it. */
public class SpecialBusinessMenu extends Gui {

    private static final int COLLECT = 15;
    private static final int CLOSE = 22;

    private final SpecialBusiness business;

    public SpecialBusinessMenu(ApolloSMP plugin, Player viewer, SpecialBusiness business) {
        super(plugin, viewer, 3, "<gradient:#f9d423:#ff4e50><bold>Special Business</bold></gradient>");
        this.business = business;
    }

    @Override
    protected void build() {
        plugin.specialBusinesses().accrue(business);

        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + business.description());
        lore.add("<dark_gray>―――――――――――");
        lore.add("<gray>Owner: <white>" + business.ownerName() + "</white>");
        lore.add("<gray>Rarity: <white>" + business.rarity() + "</white>");
        lore.add("<gray>Trait: <#e94fd0>" + business.trait().display() + "</#e94fd0>");
        lore.add("<dark_gray>" + business.trait().description());
        lore.add("<gray>Cycle: <white>"
                + SpecialAuctionManager.formatSeconds(business.effectiveInterval()) + "</white>");
        lore.add("<gray>Est. profit: <#f9d423>"
                + plugin.msg().money(business.exactProfit()) + "</#f9d423>/day");

        inventory.setItem(11, Items.of(business.block())
                .name("<gradient:#f9d423:#ff4e50><bold>" + business.name() + "</bold></gradient>")
                .lore(lore.toArray(new String[0]))
                .glow(true).hideAttributes().build());

        int stored = plugin.specialBusinesses().stored(business);
        int cap = business.effectiveStorage();
        List<String> collectLore = new ArrayList<>();
        collectLore.add("<gray>Stored: <white>" + stored + " / " + cap + "</white>");
        for (Map.Entry<Material, Integer> e : business.storage().entrySet()) {
            collectLore.add("  <dark_gray>+</dark_gray> <white>" + e.getValue() + "x "
                    + SpecialAuctionManager.pretty(e.getKey()) + "</white>");
        }
        if (stored >= cap) {
            collectLore.add("<red>Storage full - production stopped");
        } else {
            collectLore.add("<gray>Next batch in <white>"
                    + SpecialAuctionManager.formatSeconds(
                            (int) plugin.specialBusinesses().secondsUntilNext(business)) + "</white>");
        }
        collectLore.add("");
        collectLore.add(stored > 0 ? "<yellow>Click to collect" : "<dark_gray>Nothing to collect yet");

        inventory.setItem(COLLECT, Items.of(Material.CHEST)
                .name("<#f9d423><bold>Collect Output</bold>")
                .lore(collectLore.toArray(new String[0]))
                .glow(stored > 0).hideAttributes().build());

        inventory.setItem(CLOSE, Items.of(Material.BARRIER).name("<red>Close").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == CLOSE) { player.closeInventory(); return; }
        if (slot != COLLECT) return;

        if (!player.getUniqueId().equals(business.owner())) {
            plugin.msg().send(player, "<red>This isn't your business.");
            return;
        }
        int collected = plugin.specialBusinesses().collect(player, business);
        plugin.msg().send(player, collected == 0
                ? "<gray>Nothing stored yet."
                : "<green>Collected <white>" + collected + "</white> items.");
        redraw();
    }
}
