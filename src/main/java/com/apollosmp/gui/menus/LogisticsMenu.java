package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.invest.BusinessBlock;
import com.apollosmp.invest.Business;
import com.apollosmp.invest.Businesses;
import com.apollosmp.logistics.LogisticsManager;
import com.apollosmp.special.SpecialBusiness;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Screen for a placed distribution or wholesale block. */
public class LogisticsMenu extends Gui {

    private static final int SELL_NOW = 29;
    private static final int NOTIFY = 31;
    private static final int CLOSE = 33;

    private final LogisticsManager.Node node;
    private final boolean wholesale;

    public LogisticsMenu(ApolloSMP plugin, Player viewer, LogisticsManager.Node node, boolean wholesale) {
        super(plugin, viewer, 4, wholesale
                ? "<#f9d423><bold>Wholesale Block</bold>"
                : "<#5ad1e8><bold>Distribution Block</bold>");
        this.node = node;
        this.wholesale = wholesale;
    }

    @Override
    protected void build() {
        LogisticsManager logistics = plugin.logistics();

        if (wholesale) buildWholesale(logistics);
        else buildDistribution(logistics);

        inventory.setItem(CLOSE, Items.of(Material.BARRIER).name("<red>Close").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    private void buildDistribution(LogisticsManager logistics) {
        List<BusinessBlock> businesses = logistics.linkedBusinesses(node);
        List<SpecialBusiness> specials = logistics.linkedSpecials(node);

        List<String> lore = new ArrayList<>();
        lore.add("<gray>Owner: <white>" + node.ownerName + "</white>");
        lore.add("<gray>Range: <white>" + logistics.businessRadius() + "</white> blocks");
        lore.add("<gray>Connected: <white>"
                + (businesses.size() + specials.size()) + "</white> business(es)");
        lore.add("");
        if (businesses.isEmpty() && specials.isEmpty()) {
            lore.add("<red>Nothing in range.");
            lore.add("<gray>Place it closer to your businesses.");
        } else {
            for (BusinessBlock b : businesses) {
                Business def = Businesses.get(b.businessId());
                lore.add("  <dark_gray>+</dark_gray> <white>"
                        + (def == null ? b.businessId() : def.displayName())
                        + "</white> <gray>[L" + b.level() + "]</gray>");
            }
            for (SpecialBusiness b : specials) {
                lore.add("  <dark_gray>+</dark_gray> <#e94fd0>" + b.name() + "</#e94fd0>");
            }
        }
        lore.add("");
        lore.add("<gray>Put a <white>Wholesale Block</white> within");
        lore.add("<gray><white>" + logistics.hubRadius() + "</white> blocks to start auto-selling.");

        inventory.setItem(13, Items.of(LogisticsManager.DISTRIBUTION_BLOCK)
                .name("<#5ad1e8><bold>Distribution Block</bold>")
                .lore(lore.toArray(new String[0]))
                .glow(true).hideAttributes().build());
    }

    private void buildWholesale(LogisticsManager logistics) {
        List<LogisticsManager.Node> hubs = logistics.linkedDistributors(node);
        int businesses = 0;
        for (LogisticsManager.Node d : hubs) {
            businesses += logistics.linkedBusinesses(d).size() + logistics.linkedSpecials(d).size();
        }
        double pending = logistics.pendingValue(node);
        double fee = pending * (logistics.feePercent() / 100.0);

        List<String> lore = new ArrayList<>();
        lore.add("<gray>Owner: <white>" + node.ownerName + "</white>");
        lore.add("<gray>Distribution blocks: <white>" + hubs.size() + "</white>");
        lore.add("<gray>Businesses covered: <white>" + businesses + "</white>");
        lore.add("<dark_gray>―――――――――――");
        lore.add("<gray>Waiting to sell: <#f9d423>" + plugin.msg().money(pending) + "</#f9d423>");
        lore.add("<gray>Handling fee: <red>-" + plugin.msg().money(fee) + "</red>");
        lore.add("<gray>You'd receive: <green>" + plugin.msg().money(pending - fee) + "</green>");
        lore.add("<dark_gray>―――――――――――");
        lore.add("<gray>Next sale in: <white>"
                + format(logistics.secondsUntilSale(node)) + "</white>");
        lore.add("<gray>Earned so far: <#f9d423>"
                + plugin.msg().money(node.lifetimeEarned) + "</#f9d423>");
        if (hubs.isEmpty()) {
            lore.add("");
            lore.add("<red>No distribution blocks in range.");
        }

        inventory.setItem(13, Items.of(LogisticsManager.WHOLESALE_BLOCK)
                .name("<#f9d423><bold>Wholesale Block</bold>")
                .lore(lore.toArray(new String[0]))
                .glow(true).hideAttributes().build());

        inventory.setItem(SELL_NOW, Items.of(pending > 0 ? Material.HOPPER : Material.GRAY_DYE)
                .name("<green><bold>Sell Now</bold>")
                .lore("<gray>Don't wait for the timer.",
                        "", pending > 0 ? "<yellow>Click to sell" : "<dark_gray>Nothing to sell yet")
                .glow(pending > 0).hideAttributes().build());

        inventory.setItem(NOTIFY, Items.of(node.notify ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(node.notify
                        ? "<green><bold>Chat Alerts: On</bold>"
                        : "<gray><bold>Chat Alerts: Off</bold>")
                .lore("<gray>Tell you in chat each time this",
                        "<gray>block makes a sale.",
                        "", "<yellow>Click to toggle")
                .glow(node.notify).hideAttributes().build());
    }

    private String format(long seconds) {
        if (seconds >= 60) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == CLOSE) { player.closeInventory(); return; }
        if (!wholesale) return;
        if (!player.getUniqueId().equals(node.owner)) {
            plugin.msg().send(player, "<red>This isn't yours.");
            return;
        }

        if (slot == SELL_NOW) {
            double earned = plugin.logistics().sell(node, false);
            plugin.msg().send(player, earned <= 0
                    ? "<gray>Nothing to sell right now."
                    : "<green>Sold for <#f9d423>" + plugin.msg().money(earned) + "</#f9d423>.");
            redraw();
        } else if (slot == NOTIFY) {
            node.notify = !node.notify;
            plugin.logistics().save();
            plugin.msg().send(player, node.notify
                    ? "<green>Chat alerts on."
                    : "<gray>Chat alerts off.");
            redraw();
        }
    }
}
