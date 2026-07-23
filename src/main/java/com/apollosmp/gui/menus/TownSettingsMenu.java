package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.town.TownPerm;
import com.apollosmp.town.TownRank;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Everything you set once and forget: tax, spawn, permissions, and the risky stuff. */
public class TownSettingsMenu extends Gui {

    private static final int TAX = 10;
    private static final int SET_SPAWN = 11;
    private static final int VISITORS = 12;
    private static final int PERMS = 13;
    private static final int BORDERS = 14;
    private static final int MOVE = 15;
    private static final int DISBAND = 16;
    private static final int BACK = 31;

    public TownSettingsMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 4, "<#5ad1e8><bold>Town Settings</bold>");
    }

    @Override
    protected void build() {
        Town town = plugin.towns().getTownOf(viewer.getUniqueId());
        if (town == null) { viewer.closeInventory(); return; }
        boolean isMayor = town.mayor().equals(viewer.getUniqueId());

        inventory.setItem(4, Items.of(Material.COMPARATOR)
                .name("<#5ad1e8><bold>" + town.name() + " Settings</bold>")
                .lore("<gray>Options only leaders usually touch.")
                .glow(true).hideAttributes().build());

        inventory.setItem(TAX, Items.of(Material.EMERALD)
                .name("<#f9d423><bold>Daily Tax</bold>")
                .lore("<gray>Currently: <white>" + plugin.msg().money(town.tax()) + "</white> per resident",
                        "<gray>Collected into the town bank.",
                        "", "<yellow>Click to change")
                .build());

        inventory.setItem(SET_SPAWN, Items.of(Material.RED_BED)
                .name("<#f9d423><bold>Set Town Spawn</bold>")
                .lore("<gray>Move the spawn to where you stand.",
                        "<gray>You must be on your own land.",
                        "", "<yellow>Click to set")
                .build());

        boolean open = town.publicSpawn();
        inventory.setItem(VISITORS, Items.of(open ? Material.LIME_DYE : Material.RED_DYE)
                .name(open ? "<green><bold>Visitors: Allowed</bold>" : "<red><bold>Visitors: Blocked</bold>")
                .lore("<gray>Can outsiders <white>/town tp</white> to",
                        "<gray>your spawn?",
                        "", "<yellow>Click to toggle")
                .glow(open).hideAttributes().build());

        inventory.setItem(PERMS, Items.of(Material.WRITABLE_BOOK)
                .name("<#5ad1e8><bold>Rank Permissions</bold>")
                .lore("<gray>Choose what each rank can do.",
                        "", "<yellow>Click to open")
                .build());

        boolean bordersOn = plugin.borders().isOn(viewer);
        inventory.setItem(BORDERS, Items.of(bordersOn ? Material.GLOWSTONE_DUST : Material.REDSTONE)
                .name(bordersOn ? "<green><bold>Borders: On</bold>" : "<gray><bold>Borders: Off</bold>")
                .lore("<gray>Particle outline around claims.",
                        "<gray><#5ad1e8>Cyan</#5ad1e8> your town, <#ff4e50>red</#ff4e50> others,",
                        "<gray><#e94fd0>purple</#e94fd0> owned plots.",
                        "",
                        "<dark_gray>This is your own setting, not the town's.",
                        "<yellow>Click to toggle")
                .glow(bordersOn).hideAttributes().build());

        if (isMayor) {
            inventory.setItem(MOVE, Items.of(Material.MINECART)
                    .name("<#ff4e50><bold>Move Town</bold>")
                    .lore("<gray>Restart the town where you stand.",
                            "<gray>Residents, ranks and bank are kept.",
                            "<red>All land and plots are released.",
                            "", "<yellow>Click to move")
                    .hideAttributes().build());

            inventory.setItem(DISBAND, Items.of(Material.TNT)
                    .name("<red><bold>Disband Town</bold>")
                    .lore("<gray>Delete the town for everyone.",
                            "<red>This cannot be undone.",
                            "", "<yellow>Click to disband")
                    .hideAttributes().build());
        }

        inventory.setItem(BACK, Items.of(Material.ARROW).name("<gray>Back to Town").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        Town town = plugin.towns().getTownOf(player.getUniqueId());
        if (town == null) { player.closeInventory(); return; }

        switch (slot) {
            case BACK -> new TownMenu(plugin, player).open();

            case TAX -> {
                player.closeInventory();
                plugin.msg().send(player, "<#f9d423>Type the daily tax amount</#f9d423> <gray>(or 'cancel').");
                plugin.msg().send(player, "<dark_gray>Or use <white>/town tax <amount></white>.");
                plugin.prompts().await(player, s -> {
                    try { plugin.towns().setTax(player, Double.parseDouble(s)); }
                    catch (NumberFormatException e) { plugin.msg().send(player, "<red>That's not a number."); }
                    new TownSettingsMenu(plugin, player).open();
                });
            }

            case SET_SPAWN -> { plugin.towns().setSpawnHere(player); redraw(); }

            case VISITORS -> {
                plugin.towns().setPublicSpawn(player, !town.publicSpawn());
                redraw();
            }

            case PERMS -> {
                if (town.hasPerm(player.getUniqueId(), TownPerm.MANAGE_PERMS)) {
                    new TownPermsMenu(plugin, player, TownRank.RESIDENT).open();
                } else {
                    plugin.msg().send(player, "<red>Only ranks with Manage Permissions can edit this.");
                }
            }

            case BORDERS -> {
                boolean on = plugin.borders().toggle(player);
                plugin.msg().send(player, on
                        ? "<green>Claim borders shown."
                        : "<yellow>Claim borders hidden.");
                redraw();
            }

            case MOVE -> {
                if (!town.mayor().equals(player.getUniqueId())) return;
                String name = town.name();
                new ConfirmMenu(plugin, player,
                        "<red><bold>Move " + name + "?</bold>",
                        "Move " + name + " here?",
                        List.of("<gray>Your town restarts in this chunk.",
                                "",
                                "<green>Kept:</green> <gray>residents, ranks, bank</gray>",
                                "<red>Lost:</red> <gray>all <white>" + town.claims().size()
                                        + "</white> chunks and every plot</gray>",
                                "",
                                "<gray>Builds stay, but stop being protected."),
                        () -> { plugin.towns().moveTown(player); player.closeInventory(); },
                        () -> new TownSettingsMenu(plugin, player).open()
                ).open();
            }

            case DISBAND -> {
                if (!town.mayor().equals(player.getUniqueId())) return;
                String name = town.name();
                new ConfirmMenu(plugin, player,
                        "<red><bold>Disband " + name + "?</bold>",
                        "Disband " + name + "?",
                        List.of("<gray>This will permanently:",
                                "<red>- release all <white>" + town.claims().size() + "</white> chunks",
                                "<red>- remove all <white>" + town.memberCount() + "</white> residents",
                                "<red>- delete every plot owner",
                                "<red>- destroy <white>" + plugin.msg().money(town.bank()) + "</white> in the bank",
                                "",
                                "<gray>There is no way to get it back."),
                        () -> { plugin.towns().disband(player); player.closeInventory(); },
                        () -> new TownSettingsMenu(plugin, player).open()
                ).open();
            }

            default -> { /* no-op */ }
        }
    }
}
