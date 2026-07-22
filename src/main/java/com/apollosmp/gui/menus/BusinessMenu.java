package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.invest.Business;
import com.apollosmp.invest.BusinessBlock;
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

/** Manage a placed business block: view, collect, or sell its product. */
public class BusinessMenu extends Gui {

    private static final int[] PRODUCT_SLOTS = {19, 20, 21, 22, 23, 24, 25};
    private static final int COLLECT_ALL = 39;
    private static final int SELL_ALL = 41;
    private static final int CLOSE = 44;

    private final BusinessBlock block;
    private final Map<Integer, Material> slotToMaterial = new HashMap<>();

    public BusinessMenu(ApolloSMP plugin, Player viewer, BusinessBlock block) {
        super(plugin, viewer, 5, businessTitle(block));
        this.block = block;
    }

    private static String businessTitle(BusinessBlock block) {
        Business def = Businesses.get(block.businessId());
        return def != null ? def.displayName() : "<#f9d423>Business";
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
                            "",
                            "<yellow>Click to collect this")
                    .hideAttributes().build());
        }

        if (totalItems == 0) {
            inventory.setItem(22, Items.of(Material.STRUCTURE_VOID)
                    .name("<gray>Empty")
                    .lore("<gray>Come back later - your business",
                            "<gray>is still producing.").build());
        }

        double hourly = def != null ? def.hourlyValue(plugin.sell()) : 0;
        inventory.setItem(4, Items.of(def != null ? def.block() : Material.CHEST)
                .name(def != null ? def.displayName() : "<#f9d423>Business")
                .lore("<gray>Owner: <white>" + block.ownerName() + "</white>",
                        "<gray>Income: <green>" + plugin.msg().money(hourly) + "/hr</green>",
                        "<gray>Stored value: <#f9d423>" + plugin.msg().money(totalValue) + "</#f9d423>")
                .glow(true).hideAttributes().build());

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
        inventory.setItem(CLOSE, Items.of(Material.BARRIER).name("<red>Close").build());

        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == CLOSE) {
            player.closeInventory();
            return;
        }
        if (slot == COLLECT_ALL) {
            collectAll(player);
            redraw();
            return;
        }
        if (slot == SELL_ALL) {
            sellAll(player);
            redraw();
            return;
        }
        Material mat = slotToMaterial.get(slot);
        if (mat != null) {
            collectOne(player, mat);
            redraw();
        }
    }

    private void collectOne(Player player, Material mat) {
        int amount = block.storage().getOrDefault(mat, 0);
        if (amount <= 0) return;
        int leftover = giveUpTo(player, mat, amount);
        if (leftover <= 0) block.storage().remove(mat);
        else block.storage().put(mat, leftover);
        plugin.businesses().save();
        if (leftover >= amount) {
            plugin.msg().send(player, "<red>Your inventory is full.");
        }
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
        plugin.economy().deposit(player.getUniqueId(), total);
        plugin.businesses().save();
        plugin.msg().send(player, "<green>Sold <white>" + sold + "</white> goods for <#f9d423>"
                + plugin.msg().money(total) + "</#f9d423>.");
    }

    /** Give up to {@code amount} of a material; returns the leftover that didn't fit. */
    private int giveUpTo(Player player, Material mat, int amount) {
        int remaining = amount;
        int maxStack = mat.getMaxStackSize();
        while (remaining > 0) {
            int chunk = Math.min(maxStack, remaining);
            ItemStack stack = new ItemStack(mat, chunk);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            int notAdded = 0;
            for (ItemStack leftover : overflow.values()) notAdded += leftover.getAmount();
            remaining -= (chunk - notAdded);
            if (notAdded > 0) break; // inventory full
        }
        return remaining;
    }
}
