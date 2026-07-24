package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.shop.ShopManager;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Pick a price for what you're holding, without typing numbers. */
public class ShopPriceMenu extends Gui {

    private static final int MATCH = 10;
    private static final int PLUS_HALF = 12;
    private static final int DOUBLE = 14;
    private static final int CUSTOM = 16;
    private static final int BACK = 22;

    private final ShopManager.Stall stall;

    public ShopPriceMenu(ApolloSMP plugin, Player viewer, ShopManager.Stall stall) {
        super(plugin, viewer, 3, "<#f9d423><bold>Set a Price</bold>");
        this.stall = stall;
    }

    /** The server's own buy price, used as the anchor for suggestions. */
    private double base() {
        ItemStack held = viewer.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) return 0;
        double unit = plugin.sell().priceOf(held.getType());
        return unit > 0 ? unit : 1.0;
    }

    @Override
    protected void build() {
        ItemStack held = viewer.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            inventory.setItem(13, Items.of(Material.BARRIER)
                    .name("<red>You're not holding anything")
                    .lore("<gray>Hold what you want to sell, then",
                            "<gray>open this again.").build());
            inventory.setItem(BACK, Items.of(Material.ARROW).name("<gray>Back").build());
            fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
            return;
        }

        double unit = base();
        inventory.setItem(4, Items.of(held.getType(), Math.max(1, Math.min(64, held.getAmount())))
                .name("<#f9d423><bold>" + Items.pretty(held.getType()) + "</bold>")
                .lore("<gray>You're stocking <white>" + held.getAmount() + "</white>",
                        "<gray>The server pays <white>" + plugin.msg().money(unit) + "</white> each",
                        "",
                        "<gray>Pick what travellers pay per item.")
                .glow(true).hideAttributes().build());

        offer(MATCH, Material.IRON_INGOT, "Match the server", unit,
                "<gray>Same as /sell. Sells fast.");
        offer(PLUS_HALF, Material.GOLD_INGOT, "Half again", round(unit * 1.5),
                "<gray>A fair margin for the town.");
        offer(DOUBLE, Material.DIAMOND, "Double", round(unit * 2),
                "<gray>More profit, slower to shift.");

        inventory.setItem(CUSTOM, Items.of(Material.NAME_TAG)
                .name("<#5ad1e8><bold>Your Own Price</bold>")
                .lore("<gray>Type a figure in chat.",
                        "", "<yellow>Click to enter it").build());

        inventory.setItem(BACK, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    private void offer(int slot, Material icon, String label, double price, String note) {
        ItemStack held = viewer.getInventory().getItemInMainHand();
        int amount = held == null ? 1 : held.getAmount();
        inventory.setItem(slot, Items.of(icon)
                .name("<#f9d423><bold>" + label + "</bold>")
                .lore("<gray>Price: <#f9d423>" + plugin.msg().money(price) + "</#f9d423> each",
                        "<gray>Whole stack: <white>" + plugin.msg().money(price * amount) + "</white>",
                        note,
                        "", "<yellow>Click to stock at this price")
                .hideAttributes().build());
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == BACK) { new TownShopMenu(plugin, player, stall).open(); return; }

        double unit = base();
        Double chosen = switch (slot) {
            case MATCH -> unit;
            case PLUS_HALF -> round(unit * 1.5);
            case DOUBLE -> round(unit * 2);
            default -> null;
        };

        if (chosen != null) {
            plugin.shops().stock(player, stall, chosen);
            new TownShopMenu(plugin, player, stall).open();
            return;
        }

        if (slot == CUSTOM) {
            player.closeInventory();
            plugin.msg().send(player, "<#f9d423>Type the price per item</#f9d423> <gray>(or 'cancel').");
            plugin.prompts().await(player, s -> {
                try {
                    plugin.shops().stock(player, stall, Double.parseDouble(s));
                } catch (NumberFormatException e) {
                    plugin.msg().send(player, "<red>That's not a number.");
                }
                new TownShopMenu(plugin, player, stall).open();
            });
        }
    }
}
