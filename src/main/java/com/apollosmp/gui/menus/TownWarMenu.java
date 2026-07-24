package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.town.TownWar;
import com.apollosmp.town.WarManager;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Declare war, answer a declaration, or sue for peace. */
public class TownWarMenu extends Gui {

    private static final int[] WAR_SLOTS = {19, 20, 21, 22, 23};
    private static final int DECLARE = 30;
    private static final int ANSWER_YES = 29;
    private static final int ANSWER_NO = 33;
    private static final int BACK = 40;

    private final List<String> enemies = new ArrayList<>();

    public TownWarMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 5, "<red><bold>War & Peace</bold>");
    }

    @Override
    protected void build() {
        enemies.clear();
        Town town = plugin.towns().getTownOf(viewer.getUniqueId());
        if (town == null) { viewer.closeInventory(); return; }

        boolean isMayor = town.mayor().equals(viewer.getUniqueId());
        List<TownWar> active = plugin.wars().warsFor(town.name());
        WarManager.Declaration pending = plugin.wars().declarationFor(town.name());

        inventory.setItem(4, Items.of(active.isEmpty() ? Material.WHITE_BANNER : Material.RED_BANNER)
                .name(active.isEmpty()
                        ? "<green><bold>At Peace</bold>"
                        : "<red><bold>At War</bold>")
                .lore("<gray>Town: <white>" + town.name() + "</white>",
                        "<gray>Active wars: <white>" + active.size() + "</white>",
                        "",
                        "<gray>While at war with a town you can:",
                        "<red>- fight them on their land</red>",
                        "<red>- open their chests</red>",
                        "<red>- raid their businesses</red>",
                        "<dark_gray>Blocks stay protected either way.")
                .glow(!active.isEmpty()).hideAttributes().build());

        // ---- current wars ----
        for (int i = 0; i < active.size() && i < WAR_SLOTS.length; i++) {
            TownWar war = active.get(i);
            String enemy = war.other(town.name());
            String offer = plugin.wars().peaceOfferFor(town.name(), enemy);
            boolean theyOffered = offer != null && offer.equalsIgnoreCase(enemy);

            inventory.setItem(WAR_SLOTS[i], Items.of(Material.IRON_SWORD)
                    .name("<red><bold>vs " + enemy + "</bold>")
                    .lore("<gray>Ends in <white>" + WarManager.formatLeft(war.millisLeft()) + "</white>",
                            "",
                            theyOffered
                                    ? "<green>They've offered peace!</green>"
                                    : (offer != null ? "<gray>You've offered peace." : ""),
                            isMayor ? "<yellow>Click to offer or accept peace"
                                    : "<dark_gray>Only the mayor can make peace")
                    .glow(theyOffered).hideAttributes().build());
            enemies.add(enemy);
        }

        // ---- an incoming declaration ----
        if (pending != null && System.currentTimeMillis() < pending.expires()) {
            inventory.setItem(ANSWER_YES, Items.of(Material.LIME_WOOL)
                    .name("<green><bold>Accept War</bold>")
                    .lore("<gray><white>" + pending.from() + "</white> wants a war",
                            "<gray>lasting <white>" + pending.minutes() + " minutes</white>.",
                            "", isMayor ? "<yellow>Click to accept" : "<dark_gray>Mayor only")
                    .glow(true).hideAttributes().build());
            inventory.setItem(ANSWER_NO, Items.of(Material.RED_WOOL)
                    .name("<red><bold>Decline</bold>")
                    .lore("<gray>Refuse <white>" + pending.from() + "</white>.",
                            "", isMayor ? "<yellow>Click to refuse" : "<dark_gray>Mayor only")
                    .build());
        } else {
            inventory.setItem(DECLARE, Items.of(Material.IRON_SWORD)
                    .name("<red><bold>Declare War</bold>")
                    .lore("<gray>Pick a town and a length.",
                            "<gray>They have to agree before it starts.",
                            "<dark_gray>/town war <town> <10|30|60|120>",
                            "", isMayor ? "<yellow>Click to choose a town" : "<dark_gray>Mayor only")
                    .hideAttributes().build());
        }

        inventory.setItem(BACK, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        Town town = plugin.towns().getTownOf(player.getUniqueId());
        if (town == null) { player.closeInventory(); return; }
        boolean isMayor = town.mayor().equals(player.getUniqueId());

        if (slot == BACK) { new TownManageMenu(plugin, player).open(); return; }
        if (!isMayor) {
            plugin.msg().send(player, "<red>Only the mayor handles war and peace.");
            return;
        }

        for (int i = 0; i < WAR_SLOTS.length; i++) {
            if (WAR_SLOTS[i] == slot && i < enemies.size()) {
                plugin.wars().offerPeace(player, enemies.get(i));
                redraw();
                return;
            }
        }

        switch (slot) {
            case ANSWER_YES -> { plugin.wars().accept(player, null); redraw(); }
            case ANSWER_NO -> { plugin.wars().decline(player); redraw(); }
            case DECLARE -> new TownWarTargetMenu(plugin, player).open();
            default -> { /* display only */ }
        }
    }
}
