package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.invest.Business;
import com.apollosmp.invest.Businesses;
import com.apollosmp.items.CustomItems;
import com.apollosmp.merchant.MerchantOffer;
import com.apollosmp.spawner.SpawnerManager;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Every unique item in the plugin, free, for admins. */
public class AdminItemsMenu extends Gui {

    private static final String[] GEAR = {
            CustomItems.DRILL, CustomItems.BLADE, CustomItems.CLEAVER,
            CustomItems.BOW, CustomItems.BOOTS, CustomItems.VOTE_KEY
    };
    private static final EntityType[] SPAWNERS = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
            EntityType.CAVE_SPIDER, EntityType.BLAZE, EntityType.ENDERMAN
    };

    /** slot -> what to hand over. */
    private final Map<Integer, Runnable> actions = new HashMap<>();
    private final Map<Integer, String> businessAtSlot = new HashMap<>();

    public AdminItemsMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 6, "<#ff4e50><bold>Admin - All Items</bold>");
    }

    @Override
    protected void build() {
        actions.clear();
        businessAtSlot.clear();

        inventory.setItem(4, Items.of(Material.NETHER_STAR)
                .name("<#ff4e50><bold>Item Catalogue</bold>")
                .lore("<gray>Everything unique in one place.",
                        "<gray>Click any item to receive it free.",
                        "",
                        "<gray>Businesses: <white>left</white> = L1,",
                        "<gray><white>right</white> = L5, <white>shift</white> = L10")
                .glow(true).hideAttributes().build());

        // ---- businesses ----
        label(9, Material.LIME_STAINED_GLASS_PANE, "<green><bold>BUSINESSES</bold>");
        int slot = 10;
        for (Business def : Businesses.all()) {
            if (slot > 16) break;
            ItemStack preview = plugin.businesses().createItem(def, 1);
            inventory.setItem(slot, preview);
            businessAtSlot.put(slot, def.id());
            slot++;
        }

        // ---- custom gear ----
        label(18, Material.YELLOW_STAINED_GLASS_PANE, "<#f9d423><bold>APOLLO GEAR</bold>");
        slot = 19;
        for (String id : GEAR) {
            if (slot > 25) break;
            ItemStack item = plugin.customItems().build(id);
            if (item != null) {
                inventory.setItem(slot, item);
                final String gearId = id;
                actions.put(slot, () -> give(plugin.customItems().build(gearId)));
            }
            slot++;
        }

        // ---- merchant stock ----
        label(27, Material.MAGENTA_STAINED_GLASS_PANE, "<#e94fd0><bold>MERCHANT</bold>");
        addMerchant(28, MerchantOffer.Kind.DRILL, null);
        addMerchant(29, MerchantOffer.Kind.TREE_AXE, null);
        addMerchant(30, MerchantOffer.Kind.GOD_APPLE, null);
        addMerchant(31, MerchantOffer.Kind.TOTEM, null);

        // ---- logistics ----
        inventory.setItem(32, plugin.logistics().createDistribution());
        actions.put(32, () -> give(plugin.logistics().createDistribution()));
        inventory.setItem(33, plugin.logistics().createWholesale());
        actions.put(33, () -> give(plugin.logistics().createWholesale()));

        // ---- spawners ----
        label(36, Material.LIGHT_BLUE_STAINED_GLASS_PANE, "<#5ad1e8><bold>SPAWNERS</bold>");
        slot = 37;
        for (EntityType type : SPAWNERS) {
            if (slot > 43) break;
            ItemStack item = plugin.spawners().createItem(type, 1);
            inventory.setItem(slot, item);
            final EntityType mob = type;
            actions.put(slot, () -> give(plugin.spawners().createItem(mob, 1)));
            slot++;
        }

        inventory.setItem(49, Items.of(Material.BARRIER).name("<red>Close").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    private void label(int slot, Material material, String name) {
        inventory.setItem(slot, Items.of(material).name(name).build());
    }

    private void addMerchant(int slot, MerchantOffer.Kind kind, String data) {
        ItemStack preview = plugin.merchant().build(new MerchantOffer(kind, 0, data));
        if (preview == null) return;
        inventory.setItem(slot, preview);
        actions.put(slot, () -> give(plugin.merchant().build(new MerchantOffer(kind, 0, data))));
    }

    private void give(ItemStack item) {
        if (item == null) return;
        Items.give(viewer, item);
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (!player.hasPermission("apollo.admin")) { player.closeInventory(); return; }
        if (slot == 49) { player.closeInventory(); return; }

        String businessId = businessAtSlot.get(slot);
        if (businessId != null) {
            Business def = Businesses.get(businessId);
            if (def == null) return;
            int level = click.isShiftClick() ? Business.MAX_LEVEL : (click.isRightClick() ? 5 : 1);
            Items.give(player, plugin.businesses().createItem(def, level));
            plugin.msg().send(player, "<green>Given <white>" + def.id()
                    + "</white> at level <#f9d423>L" + level + "</#f9d423>.");
            return;
        }

        Runnable action = actions.get(slot);
        if (action != null) {
            action.run();
            plugin.msg().send(player, "<green>Item added to your inventory.");
        }
    }
}
