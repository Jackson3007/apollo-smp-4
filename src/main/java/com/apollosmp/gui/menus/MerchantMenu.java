package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.merchant.MerchantManager;
import com.apollosmp.merchant.MerchantOffer;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** The travelling merchant's stall - three offers, rotating daily. */
public class MerchantMenu extends Gui {

    private static final int[] SLOTS = {11, 13, 15};

    private List<MerchantOffer> offers = new ArrayList<>();

    public MerchantMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 3, "<gradient:#e94fd0:#5ad1e8><bold>Travelling Merchant</bold></gradient>");
    }

    @Override
    protected void build() {
        offers = plugin.merchant().offers();

        inventory.setItem(4, Items.of(Material.CLOCK)
                .name("<#e94fd0><bold>Today's Wares</bold>")
                .lore("<gray>Three goods, gone at midnight.",
                        "<gray>New stock in <white>"
                                + formatTime(plugin.merchant().millisUntilRotation()) + "</white>",
                        "",
                        "<gray>One of each per player, per day.")
                .glow(true).hideAttributes().build());

        for (int i = 0; i < SLOTS.length; i++) {
            if (i >= offers.size()) continue;
            MerchantOffer offer = offers.get(i);
            ItemStack preview = plugin.merchant().build(offer);
            if (preview == null) preview = new ItemStack(Material.BARRIER);

            boolean bought = plugin.merchant().hasBought(viewer, i);
            boolean afford = plugin.economy().has(viewer.getUniqueId(), offer.price());

            ItemMeta meta = preview.getItemMeta();
            if (meta != null) {
                List<net.kyori.adventure.text.Component> lore = meta.lore();
                if (lore == null) lore = new ArrayList<>();
                lore.add(com.apollosmp.util.Msg.lore("<dark_gray>―――――――――――"));
                lore.add(com.apollosmp.util.Msg.lore("<gray>Price: <#f9d423>"
                        + plugin.msg().money(offer.price()) + "</#f9d423>"));
                lore.add(com.apollosmp.util.Msg.lore(rarityLine(offer)));
                lore.add(com.apollosmp.util.Msg.lore(""));
                if (bought) {
                    lore.add(com.apollosmp.util.Msg.lore("<green>\u2714 Bought today"));
                } else if (!afford) {
                    lore.add(com.apollosmp.util.Msg.lore("<red>You can't afford this"));
                } else {
                    lore.add(com.apollosmp.util.Msg.lore("<yellow>Click to buy"));
                }
                meta.lore(lore);
                preview.setItemMeta(meta);
            }
            inventory.setItem(SLOTS[i], preview);
        }

        inventory.setItem(22, Items.of(Material.BARRIER).name("<red>Close").build());
        fillEmpty(Items.filler(Material.BLACK_STAINED_GLASS_PANE));
    }

    private String rarityLine(MerchantOffer offer) {
        return switch (offer.kind()) {
            case SPAWNER -> "<#e94fd0>Legendary</#e94fd0>";
            case BUSINESS, DRILL, TREE_AXE -> "<#f9d423>Rare</#f9d423>";
            case GOD_APPLE, TOTEM -> "<#5ad1e8>Uncommon</#5ad1e8>";
            case JUNK -> "<dark_gray>...the merchant swears it's valuable</dark_gray>";
        };
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == 22) { player.closeInventory(); return; }

        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] != slot) continue;
            MerchantManager.BuyResult result = plugin.merchant().buy(player, i);
            switch (result) {
                case SUCCESS -> {
                    plugin.msg().send(player, "<green>The merchant hands it over with a grin.");
                    player.playSound(player.getLocation(),
                            org.bukkit.Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
                }
                case NO_FUNDS -> plugin.msg().send(player, "<red>You can't afford that.");
                case ALREADY_BOUGHT -> plugin.msg().send(player,
                        "<yellow>The merchant only has one of those per customer today.");
                case GONE -> plugin.msg().send(player, "<red>That's already sold.");
            }
            redraw();
            return;
        }
    }
}
