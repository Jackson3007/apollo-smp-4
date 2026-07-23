package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.invest.Business;
import com.apollosmp.invest.BusinessBlock;
import com.apollosmp.invest.BusinessManager;
import com.apollosmp.invest.Businesses;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Manage a placed business: view, collect, sell, and upgrade its level. */
public class BusinessMenu extends Gui {

    private static final int[] PRODUCT_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int TOWN_TOGGLE = 36;
    private static final int COLLECT_ALL = 38;
    private static final int SELL_ALL = 40;
    private static final int UPGRADE = 42;
    private static final int CLOSE = 44;

    private final BusinessBlock block;
    private final Map<Integer, Material> slotToMaterial = new HashMap<>();

    public BusinessMenu(ApolloSMP plugin, Player viewer, BusinessBlock block) {
        super(plugin, viewer, 5, title(block));
        this.block = block;
    }

    private static String title(BusinessBlock block) {
        Business def = Businesses.get(block.businessId());
        String base = def != null ? def.displayName() : "<#f9d423>Business";
        return base + " <gray>[L" + block.level() + "]</gray>";
    }

    @Override
    protected void build() {
        plugin.businesses().updateProduction(block);
        slotToMaterial.clear();
        Business def = Businesses.get(block.businessId());

        double totalValue = 0;
        int totalItems = 0;
        int i = 0;
        for (Map.Entry<Material, Integer> e : block.storage().entrySet()) {
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
                    .lore("<gray>Come back later - your business", "<gray>is still producing.").build());
        }

        inventory.setItem(4, buildInfo(def, totalValue));

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
        inventory.setItem(TOWN_TOGGLE, buildTownToggle());
        inventory.setItem(UPGRADE, buildUpgrade(def));
        inventory.setItem(CLOSE, Items.of(Material.BARRIER).name("<red>Close").build());

        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    private ItemStack buildInfo(Business def, double totalValue) {
        double hourly = def != null ? def.hourlyValueAtLevel(plugin.sell(), block.level()) : 0;
        List<String> info = new ArrayList<>();
        info.add("<gray>Owner: <white>" + block.ownerName() + "</white>");
        info.add("<gray>Level: <#f9d423>L" + block.level() + "</#f9d423>");
        info.add("<gray>Income: <green>" + plugin.msg().money(hourly) + "/hr</green>");
        if (def != null) {
            info.add("<dark_gray>―――――――――――");
            info.add("<gray>Produces per hour:");
            for (Business.Product p : def.products()) {
                info.add("  <white>" + Items.pretty(p.material()) + "</white> <dark_gray>-</dark_gray> <green>"
                        + def.perHourAtLevel(p, block.level()) + "/hr</green>");
            }
            info.add("<dark_gray>―――――――――――");
            boolean full = true;
            for (Business.Product p : def.products()) {
                if (block.storage().getOrDefault(p.material(), 0) < def.capacityForAtLevel(p, block.level())) {
                    full = false;
                    break;
                }
            }
            if (full) {
                info.add("<red><bold>Storage full!</bold></red> <gray>Collect to resume");
            } else {
                long nextInMs = Math.max(0, def.intervalMillis() - (System.currentTimeMillis() - block.lastGen()));
                info.add("<gray>Next batch in: <#f9d423>" + formatTime(nextInMs) + "</#f9d423>");
            }
        }
        info.add("<gray>Stored value: <#f9d423>" + plugin.msg().money(totalValue) + "</#f9d423>");
        return Items.of(def != null ? def.block() : Material.CHEST)
                .name(def != null ? def.displayName() + " <gray>[L" + block.level() + "]</gray>" : "<#f9d423>Business")
                .lore(info).glow(true).hideAttributes().build();
    }

    /** Pay the owner, or pay their town. */
    private ItemStack buildTownToggle() {
        com.apollosmp.town.Town myTown = plugin.towns().getTownOf(block.owner());
        String assigned = block.town();

        List<String> lore = new ArrayList<>();
        if (assigned != null) {
            lore.add("<gray>Earnings go to <#f9d423>" + assigned + "</#f9d423>");
            lore.add("<gray>instead of the owner's pocket.");
            lore.add("");
            lore.add("<yellow>Click to pay the owner again");
        } else if (myTown == null) {
            lore.add("<gray>Earnings go to <white>" + block.ownerName() + "</white>.");
            lore.add("");
            lore.add("<dark_gray>Join a town to donate this");
            lore.add("<dark_gray>business's income to it.");
        } else {
            lore.add("<gray>Earnings go to <white>" + block.ownerName() + "</white>.");
            lore.add("");
            lore.add("<gray>Assign it to <#f9d423>" + myTown.name() + "</#f9d423> and every");
            lore.add("<gray>sale pays the town bank instead.");
            lore.add("");
            lore.add("<yellow>Click to assign to your town");
        }

        return Items.of(assigned != null ? Material.WHITE_BANNER : Material.PLAYER_HEAD)
                .name(assigned != null
                        ? "<#f9d423><bold>Paying: " + assigned + "</bold>"
                        : "<gray><bold>Paying: Owner</bold>")
                .lore(lore).glow(assigned != null).hideAttributes().build();
    }

    private ItemStack buildUpgrade(Business def) {
        if (def == null) {
            return Items.filler(Material.GRAY_STAINED_GLASS_PANE);
        }
        if (block.level() >= Business.MAX_LEVEL) {
            return Items.of(Material.NETHER_STAR)
                    .name("<#f9d423><bold>Max Level</bold>")
                    .lore("<gray>This business is fully upgraded", "<gray>at <#f9d423>L" + Business.MAX_LEVEL + "</#f9d423>.")
                    .glow(true).hideAttributes().build();
        }
        int next = block.level() + 1;
        double cost = def.upgradeCost(block.level());
        long required = def.unitsToUpgrade(block.level());
        long progress = block.producedSinceUpgrade();
        boolean ready = progress >= required;
        boolean affordable = plugin.economy().has(viewer.getUniqueId(), cost);

        List<String> lore = new ArrayList<>();
        lore.add("<gray>Upgrade <#f9d423>L" + block.level() + "</#f9d423> <gray>-> <#f9d423>L" + next + "</#f9d423>");
        lore.add("<gray>Boost: <green>+60% production</green>");
        lore.add("<gray>Cost: <#f9d423>" + plugin.msg().money(cost) + "</#f9d423>");
        lore.add("<gray>Progress: <white>" + Math.min(progress, required) + "</white><gray>/</gray><white>"
                + required + "</white> <gray>units");
        lore.add("");
        if (!ready) {
            lore.add("<red>Produce " + (required - progress) + " more units to unlock");
        } else if (!affordable) {
            lore.add("<red>You need " + plugin.msg().money(cost) + " to upgrade");
        } else {
            lore.add("<green><bold>Click to upgrade!</bold>");
        }
        return Items.of(ready && affordable ? Material.EXPERIENCE_BOTTLE : Material.BOOK)
                .name("<#b7f542><bold>Upgrade Business</bold>")
                .lore(lore).glow(ready && affordable).hideAttributes().build();
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        switch (slot) {
            case CLOSE -> player.closeInventory();
            case COLLECT_ALL -> { collectAll(player); redraw(); }
            case SELL_ALL -> { sellAll(player); redraw(); }
            case TOWN_TOGGLE -> { toggleTown(player); redraw(); }
            case UPGRADE -> { doUpgrade(player); redraw(); }
            default -> {
                Material mat = slotToMaterial.get(slot);
                if (mat != null) { collectOne(player, mat); redraw(); }
            }
        }
    }

    private void toggleTown(Player player) {
        if (!player.getUniqueId().equals(block.owner())) {
            plugin.msg().send(player, "<red>Only the owner can change where the money goes.");
            return;
        }
        if (block.town() != null) {
            String was = block.town();
            block.setTown(null);
            plugin.businesses().save();
            plugin.msg().send(player, "<yellow>This business now pays you again "
                    + "<gray>(was paying " + was + ").");
            return;
        }
        com.apollosmp.town.Town town = plugin.towns().getTownOf(player.getUniqueId());
        if (town == null) {
            plugin.msg().send(player, "<red>You're not in a town.");
            return;
        }
        block.setTown(town.name());
        plugin.businesses().save();
        plugin.msg().send(player, "<green>Every sale from this business now pays <#f9d423>"
                + town.name() + "</#f9d423>'s bank.");
    }

    private void doUpgrade(Player player) {
        BusinessManager.UpgradeResult result = plugin.businesses().tryUpgrade(player, block);
        switch (result) {
            case SUCCESS -> plugin.msg().send(player, "<green>Upgraded to <#f9d423>L" + block.level()
                    + "</#f9d423>! Production increased.");
            case MAXED -> plugin.msg().send(player, "<gray>This business is already max level.");
            case NOT_ENOUGH_PRODUCED -> plugin.msg().send(player, "<red>It hasn't produced enough yet.");
            case NO_FUNDS -> plugin.msg().send(player, "<red>You can't afford the upgrade.");
            case ERROR -> plugin.msg().send(player, "<red>Something went wrong.");
        }
    }

    private void collectOne(Player player, Material mat) {
        int amount = block.storage().getOrDefault(mat, 0);
        if (amount <= 0) return;
        int leftover = giveUpTo(player, mat, amount);
        if (leftover <= 0) block.storage().remove(mat);
        else block.storage().put(mat, leftover);
        plugin.businesses().save();
        if (leftover >= amount) plugin.msg().send(player, "<red>Your inventory is full.");
    }

    private void collectAll(Player player) {
        boolean full = false;
        for (Material mat : new ArrayList<>(block.storage().keySet())) {
            int amount = block.storage().getOrDefault(mat, 0);
            if (amount <= 0) continue;
            int leftover = giveUpTo(player, mat, amount);
            if (leftover <= 0) block.storage().remove(mat);
            else { block.storage().put(mat, leftover); full = true; }
        }
        plugin.businesses().save();
        plugin.msg().send(player, full ? "<yellow>Collected what fit - your inventory is full."
                : "<green>Collected all goods!");
    }

    private void sellAll(Player player) {
        double total = 0;
        int sold = 0;
        for (Map.Entry<Material, Integer> e : block.storage().entrySet()) {
            total += plugin.sell().priceOf(e.getKey()) * e.getValue();
            sold += e.getValue();
        }
        if (sold == 0) {
            plugin.msg().send(player, "<gray>There's nothing to sell yet.");
            return;
        }
        block.storage().clear();
        String paidTown = plugin.businesses().payOut(block, total);
        plugin.businesses().save();
        plugin.msg().send(player, paidTown == null
                ? "<green>Sold <white>" + sold + "</white> goods for <#f9d423>"
                        + plugin.msg().money(total) + "</#f9d423>."
                : "<green>Sold <white>" + sold + "</white> goods for <#f9d423>"
                        + plugin.msg().money(total) + "</#f9d423> <gray>into <white>"
                        + paidTown + "</white>'s bank.");
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
