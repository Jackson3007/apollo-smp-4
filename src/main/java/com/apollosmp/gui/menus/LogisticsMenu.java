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
import java.util.Map;

/** Screen for a placed distribution or wholesale block. */
public class LogisticsMenu extends Gui {

    private static final int[] SOURCE_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final int[] PRODUCT_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int COLLECT_ALL = 37;
    private static final int SELL_NOW = 39;
    private static final int AUTO_SELL = 41;
    private static final int NOTIFY = 43;
    private static final int CLOSE = 44;
    private static final int DIST_CLOSE = 33;

    private final LogisticsManager.Node node;
    private final boolean wholesale;

    public LogisticsMenu(ApolloSMP plugin, Player viewer, LogisticsManager.Node node, boolean wholesale) {
        super(plugin, viewer, wholesale ? 5 : 4, wholesale
                ? "<#f9d423><bold>Wholesale Block</bold>"
                : "<#5ad1e8><bold>Distribution Block</bold>");
        this.node = node;
        this.wholesale = wholesale;
    }

    @Override
    protected void build() {
        LogisticsManager logistics = plugin.logistics();

        if (wholesale) {
            buildWholesale(logistics);
            inventory.setItem(CLOSE, Items.of(Material.BARRIER).name("<red>Close").build());
        } else {
            buildDistribution(logistics);
            inventory.setItem(DIST_CLOSE, Items.of(Material.BARRIER).name("<red>Close").build());
        }
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
        LogisticsManager.Node hub = logistics.wholesalerFor(node);
        if (hub == null) {
            lore.add("<red>Not attached to a Wholesale Block.");
            lore.add("<gray>Place one touching this block.");
        } else {
            lore.add("<green>Attached to a Wholesale Block");
            lore.add("<gray>at <white>" + hub.x + ", " + hub.y + ", " + hub.z + "</white>");
        }

        inventory.setItem(13, Items.of(LogisticsManager.DISTRIBUTION_BLOCK)
                .name("<#5ad1e8><bold>Distribution Block</bold>")
                .lore(lore.toArray(new String[0]))
                .glow(true).hideAttributes().build());
    }

    private void buildWholesale(LogisticsManager logistics) {
        logistics.pull(node);

        List<LogisticsManager.Node> sources = logistics.linkedDistributors(node);
        int businesses = 0;
        for (LogisticsManager.Node d : sources) {
            businesses += logistics.linkedBusinesses(d).size() + logistics.linkedSpecials(d).size();
        }

        int stored = logistics.stored(node);
        int cap = logistics.storageCap();
        double value = logistics.storedValue(node);
        double fee = value * (logistics.feePercent() / 100.0);

        List<String> lore = new ArrayList<>();
        lore.add("<gray>Owner: <white>" + node.ownerName + "</white>");
        lore.add("<gray>Sources attached: <white>" + sources.size() + "</white>");
        lore.add("<gray>Businesses covered: <white>" + businesses + "</white>");
        lore.add("<dark_gray>―――――――――――");
        lore.add("<gray>Storage: <white>" + stored + "</white><dark_gray>/</dark_gray><white>"
                + cap + "</white>");
        lore.add(bar(cap <= 0 ? 0 : (double) stored / cap));
        lore.add("<gray>Worth: <#f9d423>" + plugin.msg().money(value) + "</#f9d423>");
        lore.add("<gray>After fee: <green>" + plugin.msg().money(value - fee) + "</green>");
        lore.add("<dark_gray>―――――――――――");
        lore.add(node.autoSell
                ? "<gray>Next auto-sell: <white>" + format(logistics.secondsUntilSale(node)) + "</white>"
                : "<gray>Auto-sell is <red>off</red> - collect by hand");
        lore.add("<gray>Earned so far: <#f9d423>"
                + plugin.msg().money(node.lifetimeEarned) + "</#f9d423>");
        if (stored >= cap) lore.add("<red>Full - collect or sell to resume</red>");

        inventory.setItem(4, Items.of(LogisticsManager.WHOLESALE_BLOCK)
                .name("<#f9d423><bold>Wholesale Block</bold>")
                .lore(lore.toArray(new String[0]))
                .glow(true).hideAttributes().build());

        // Attached distribution blocks.
        if (sources.isEmpty()) {
            inventory.setItem(13, Items.of(Material.BARRIER)
                    .name("<red><bold>Nothing attached</bold>")
                    .lore("<gray>Place a <white>Distribution Block</white>",
                            "<gray>touching this one. Chain more together",
                            "<gray>to reach further.").build());
        } else {
            for (int i = 0; i < sources.size() && i < SOURCE_SLOTS.length; i++) {
                LogisticsManager.Node d = sources.get(i);
                List<BusinessBlock> normal = logistics.linkedBusinesses(d);
                List<SpecialBusiness> special = logistics.linkedSpecials(d);

                List<String> dl = new ArrayList<>();
                dl.add("<gray>At <white>" + d.x + ", " + d.y + ", " + d.z + "</white>");
                dl.add("<gray>Feeding <white>" + (normal.size() + special.size())
                        + "</white> business(es)");
                if (normal.isEmpty() && special.isEmpty()) {
                    dl.add("<red>Nothing in range of this one.");
                } else {
                    for (BusinessBlock b : normal) {
                        Business bd = Businesses.get(b.businessId());
                        dl.add("  <dark_gray>+</dark_gray> <white>"
                                + (bd == null ? b.businessId() : stripTags(bd.displayName()))
                                + "</white> <gray>[L" + b.level() + "]</gray>");
                    }
                    for (SpecialBusiness b : special) {
                        dl.add("  <dark_gray>+</dark_gray> <#e94fd0>" + b.name() + "</#e94fd0>");
                    }
                }
                inventory.setItem(SOURCE_SLOTS[i], Items.of(LogisticsManager.DISTRIBUTION_BLOCK)
                        .name("<#5ad1e8><bold>Source " + (i + 1) + "</bold>")
                        .lore(dl.toArray(new String[0]))
                        .hideAttributes().build());
            }
        }

        // What's sitting in the block right now.
        int slot = 0;
        for (Map.Entry<Material, Integer> e : node.storage.entrySet()) {
            if (slot >= PRODUCT_SLOTS.length) break;
            int amount = e.getValue();
            if (amount <= 0) continue;
            double worth = plugin.sell().priceOf(e.getKey()) * amount;
            inventory.setItem(PRODUCT_SLOTS[slot++],
                    Items.of(e.getKey(), Math.max(1, Math.min(64, amount)))
                            .name("<white>" + Items.pretty(e.getKey()))
                            .lore("<gray>Stored: <white>" + amount + "</white>",
                                    "<gray>Worth: <#f9d423>" + plugin.msg().money(worth) + "</#f9d423>")
                            .hideAttributes().build());
        }
        if (slot == 0) {
            inventory.setItem(22, Items.of(Material.STRUCTURE_VOID)
                    .name("<gray>Nothing collected yet")
                    .lore("<gray>Goods arrive from your businesses",
                            "<gray>as they produce.").build());
        }

        inventory.setItem(COLLECT_ALL, Items.of(stored > 0 ? Material.HOPPER : Material.GRAY_DYE)
                .name("<#f9d423><bold>Collect All</bold>")
                .lore("<gray>Take everything into your inventory",
                        "<gray>instead of selling it.",
                        "", stored > 0 ? "<yellow>Click to collect" : "<dark_gray>Nothing stored")
                .glow(stored > 0).hideAttributes().build());

        inventory.setItem(SELL_NOW, Items.of(stored > 0 ? Material.EMERALD : Material.GRAY_DYE)
                .name("<green><bold>Sell Now</bold>")
                .lore("<gray>Sell everything stored for <#f9d423>"
                                + plugin.msg().money(value - fee) + "</#f9d423>",
                        "<dark_gray>after the " + (int) logistics.feePercent() + "% fee",
                        "", stored > 0 ? "<yellow>Click to sell" : "<dark_gray>Nothing to sell")
                .glow(stored > 0).hideAttributes().build());

        inventory.setItem(AUTO_SELL, Items.of(node.autoSell ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(node.autoSell
                        ? "<green><bold>Auto-Sell: On</bold>"
                        : "<gray><bold>Auto-Sell: Off</bold>")
                .lore("<gray>Sell everything automatically",
                        "<gray>every <white>" + logistics.intervalMinutes() + "</white> minutes.",
                        "<gray>With it off, goods just pile up",
                        "<gray>here for you to collect.",
                        "", "<yellow>Click to toggle")
                .glow(node.autoSell).hideAttributes().build());

        inventory.setItem(NOTIFY, Items.of(node.notify ? Material.BELL : Material.GRAY_DYE)
                .name(node.notify
                        ? "<green><bold>Chat Alerts: On</bold>"
                        : "<gray><bold>Chat Alerts: Off</bold>")
                .lore("<gray>Tell you in chat each time this",
                        "<gray>block makes a sale.",
                        "", "<yellow>Click to toggle")
                .glow(node.notify).hideAttributes().build());
    }

    /** Ten-segment fill bar, matching the business holograms. */
    private String bar(double ratio) {
        double clamped = Math.max(0, Math.min(1, ratio));
        int filled = (int) Math.round(clamped * 10);
        String colour = clamped >= 1.0 ? "red" : clamped >= 0.75 ? "#f9d423" : "green";
        StringBuilder sb = new StringBuilder("<" + colour + ">");
        for (int i = 0; i < filled; i++) sb.append("\u2588");
        sb.append("</").append(colour).append("><dark_gray>");
        for (int i = filled; i < 10; i++) sb.append("\u2591");
        sb.append("</dark_gray>");
        return sb.toString();
    }

    private String stripTags(String mini) {
        return mini.replaceAll("<[^>]*>", "").trim();
    }

    private String format(long seconds) {
        if (seconds >= 60) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == CLOSE || slot == DIST_CLOSE) { player.closeInventory(); return; }
        if (!wholesale) return;
        if (!player.getUniqueId().equals(node.owner)) {
            plugin.msg().send(player, "<red>This isn't yours.");
            return;
        }

        switch (slot) {
            case COLLECT_ALL -> {
                int given = plugin.logistics().collect(player, node);
                plugin.msg().send(player, given == 0
                        ? "<gray>Nothing stored yet."
                        : "<green>Collected <white>" + given + "</white> items.");
                redraw();
            }
            case SELL_NOW -> {
                double earned = plugin.logistics().sell(node, false);
                plugin.msg().send(player, earned <= 0
                        ? "<gray>Nothing to sell right now."
                        : "<green>Sold for <#f9d423>" + plugin.msg().money(earned) + "</#f9d423>.");
                redraw();
            }
            case AUTO_SELL -> {
                node.autoSell = !node.autoSell;
                plugin.logistics().save();
                plugin.msg().send(player, node.autoSell
                        ? "<green>Auto-sell on."
                        : "<gray>Auto-sell off - goods will pile up here.");
                redraw();
            }
            case NOTIFY -> {
                node.notify = !node.notify;
                plugin.logistics().save();
                plugin.msg().send(player, node.notify
                        ? "<green>Chat alerts on."
                        : "<gray>Chat alerts off.");
                redraw();
            }
            default -> { /* display only */ }
        }
    }
}
