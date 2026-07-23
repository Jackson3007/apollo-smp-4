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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Manage a placed special business - laid out to match the normal business menu. */
public class SpecialBusinessMenu extends Gui {

    private static final int[] PRODUCT_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int COLLECT_ALL = 38;
    private static final int SELL_ALL = 40;
    private static final int DETAILS = 42;
    private static final int CLOSE = 44;

    private final SpecialBusiness business;
    private final Map<Integer, Material> slotToMaterial = new HashMap<>();

    public SpecialBusinessMenu(ApolloSMP plugin, Player viewer, SpecialBusiness business) {
        super(plugin, viewer, 5, business.name() + " <gray>[" + business.rarity() + "]</gray>");
        this.business = business;
    }

    @Override
    protected void build() {
        plugin.specialBusinesses().accrue(business);
        slotToMaterial.clear();

        double totalValue = 0;
        int totalItems = 0;
        int i = 0;
        for (Map.Entry<Material, Integer> e : business.storage().entrySet()) {
            int amount = e.getValue();
            if (amount <= 0 || i >= PRODUCT_SLOTS.length) continue;
            int slot = PRODUCT_SLOTS[i++];
            slotToMaterial.put(slot, e.getKey());
            double value = plugin.sell().priceOf(e.getKey()) * amount;
            totalValue += value;
            totalItems += amount;
            inventory.setItem(slot, Items.of(e.getKey(), Math.max(1, Math.min(64, amount)))
                    .name("<white>" + Items.pretty(e.getKey()))
                    .lore("<gray>Stored: <white>" + amount + "</white>",
                            "<gray>Worth: <#f9d423>" + plugin.msg().money(value) + "</#f9d423>",
                            "", "<yellow>Click to collect this")
                    .hideAttributes().build());
        }
        if (totalItems == 0) {
            inventory.setItem(22, Items.of(Material.STRUCTURE_VOID)
                    .name("<gray>Empty")
                    .lore("<gray>Come back later - your business",
                            "<gray>is still producing.").build());
        }

        inventory.setItem(4, buildInfo(totalValue));

        for (int s = 36; s < 45; s++) {
            inventory.setItem(s, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
        }
        inventory.setItem(COLLECT_ALL, Items.of(Material.HOPPER)
                .name("<#f9d423><bold>Collect All</bold>")
                .lore("<gray>Move all goods to your inventory", "", "<yellow>Click to collect")
                .hideAttributes().build());
        inventory.setItem(SELL_ALL, Items.of(totalValue > 0 ? Material.EMERALD_BLOCK : Material.GRAY_DYE)
                .name("<green><bold>Sell All</bold>")
                .lore("<gray>Sell everything for <#f9d423>" + plugin.msg().money(totalValue) + "</#f9d423>",
                        "", totalValue > 0 ? "<yellow>Click to sell" : "<dark_gray>Nothing to sell yet")
                .glow(totalValue > 0).hideAttributes().build());
        inventory.setItem(DETAILS, buildDetails());
        inventory.setItem(CLOSE, Items.of(Material.BARRIER).name("<red>Close").build());

        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    private ItemStack buildInfo(double totalValue) {
        List<String> info = new ArrayList<>();
        info.add("<gray>Owner: <white>" + business.ownerName() + "</white>");
        info.add("<gray>Rarity: <#e94fd0>" + business.rarity() + "</#e94fd0>");
        info.add("<gray>Industry: <white>" + business.industry() + "</white>");
        info.add("<gray>Income: <green>" + plugin.msg().money(business.exactProfit()) + "/day</green>");
        info.add("<dark_gray>―――――――――――");
        info.add("<gray>Produces per cycle:");
        info.add("  <white>" + Items.pretty(business.knownItem()) + "</white> <dark_gray>-</dark_gray> <green>"
                + business.effectiveKnownAmount() + "</green>");
        info.add("  <white>" + Items.pretty(business.hiddenItem()) + "</white> <dark_gray>-</dark_gray> <green>"
                + business.effectiveHiddenAmount() + "</green>");
        info.add("<dark_gray>―――――――――――");

        int stored = plugin.specialBusinesses().stored(business);
        int cap = business.effectiveStorage();
        if (stored >= cap) {
            info.add("<red><bold>Storage full!</bold></red> <gray>Collect to resume");
        } else {
            info.add("<gray>Next batch in: <#f9d423>"
                    + formatTime(plugin.specialBusinesses().secondsUntilNext(business) * 1000L) + "</#f9d423>");
        }
        info.add("<gray>Storage: <white>" + stored + "</white><gray>/</gray><white>" + cap + "</white>");
        info.add("<gray>Stored value: <#f9d423>" + plugin.msg().money(totalValue) + "</#f9d423>");

        return Items.of(business.block())
                .name("<gradient:#f9d423:#ff4e50><bold>" + business.name() + "</bold></gradient>")
                .lore(info).glow(true).hideAttributes().build();
    }

    private ItemStack buildDetails() {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + business.description());
        lore.add("<dark_gray>―――――――――――");
        lore.add("<gray>Trait: <#e94fd0>" + business.trait().display() + "</#e94fd0>");
        lore.add("<dark_gray>" + business.trait().description());
        lore.add("<gray>Cycle: <white>"
                + SpecialAuctionManager.formatSeconds(business.effectiveInterval()) + "</white>");
        lore.add("<gray>Capacity: <white>" + business.effectiveStorage() + "</white> items");
        lore.add("");
        lore.add("<dark_gray>One of a kind - no upgrades needed.");
        return Items.of(Material.NETHER_STAR)
                .name("<#e94fd0><bold>Business Details</bold>")
                .lore(lore).glow(true).hideAttributes().build();
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (!player.getUniqueId().equals(business.owner()) && slot != CLOSE) {
            plugin.msg().send(player, "<red>This isn't your business.");
            return;
        }
        switch (slot) {
            case CLOSE -> player.closeInventory();
            case COLLECT_ALL -> { collectAll(player); redraw(); }
            case SELL_ALL -> { sellAll(player); redraw(); }
            case DETAILS -> { /* display only */ }
            default -> {
                Material mat = slotToMaterial.get(slot);
                if (mat != null) { collectOne(player, mat); redraw(); }
            }
        }
    }

    private void collectOne(Player player, Material mat) {
        int amount = business.storage().getOrDefault(mat, 0);
        if (amount <= 0) return;
        int leftover = giveUpTo(player, mat, amount);
        if (leftover <= 0) business.storage().remove(mat);
        else business.storage().put(mat, leftover);
        plugin.specialBusinesses().save();
        if (leftover >= amount) plugin.msg().send(player, "<red>Your inventory is full.");
    }

    private void collectAll(Player player) {
        boolean full = false;
        for (Material mat : new ArrayList<>(business.storage().keySet())) {
            int amount = business.storage().getOrDefault(mat, 0);
            if (amount <= 0) continue;
            int leftover = giveUpTo(player, mat, amount);
            if (leftover <= 0) business.storage().remove(mat);
            else { business.storage().put(mat, leftover); full = true; }
        }
        plugin.specialBusinesses().save();
        plugin.msg().send(player, full ? "<yellow>Collected what fit - your inventory is full."
                : "<green>Collected all goods!");
    }

    private void sellAll(Player player) {
        double total = 0;
        int sold = 0;
        for (Map.Entry<Material, Integer> e : business.storage().entrySet()) {
            total += plugin.sell().priceOf(e.getKey()) * e.getValue();
            sold += e.getValue();
        }
        if (sold == 0) {
            plugin.msg().send(player, "<gray>There's nothing to sell yet.");
            return;
        }
        business.storage().clear();
        plugin.economy().deposit(player.getUniqueId(), total);
        plugin.specialBusinesses().save();
        plugin.msg().send(player, "<green>Sold <white>" + sold + "</white> goods for <#f9d423>"
                + plugin.msg().money(total) + "</#f9d423>.");
    }

    private static String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    private int giveUpTo(Player player, Material mat, int amount) {
        int remaining = amount;
        int maxStack = mat.getMaxStackSize();
        while (remaining > 0) {
            int chunk = Math.min(maxStack, remaining);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack(mat, chunk));
            int notAdded = 0;
            for (ItemStack leftover : overflow.values()) notAdded += leftover.getAmount();
            remaining -= (chunk - notAdded);
            if (notAdded > 0) break;
        }
        return remaining;
    }
}
