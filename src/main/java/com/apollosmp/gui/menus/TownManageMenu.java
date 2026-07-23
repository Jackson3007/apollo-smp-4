package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Everything you actually do with your town, one level in from the hub. */
public class TownManageMenu extends Gui {

    // land row (centred)
    private static final int CLAIM = 19;
    private static final int UNCLAIM = 20;
    private static final int SELL_PLOT = 21;
    private static final int RENT_PLOT = 22;
    private static final int TAKE_PLOT = 23;
    private static final int ALL_PLOTS = 24;

    // town row (centred)
    private static final int MEMBERS = 29;
    private static final int BANK = 30;
    private static final int UPGRADES = 31;
    private static final int HOME = 32;
    private static final int SETTINGS = 33;

    private static final int BACK = 40;

    public TownManageMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 5, "<#f9d423><bold>Your Town</bold>");
    }

    @Override
    protected void build() {
        Town town = plugin.towns().getTownOf(viewer.getUniqueId());
        if (town == null) { viewer.closeInventory(); return; }

        inventory.setItem(4, Items.of(Material.WHITE_BANNER)
                .name("<gradient:#f9d423:#ff4e50><bold>" + town.name() + "</bold></gradient>")
                .lore("<gray>Land: <white>" + town.claims().size() + " / "
                                + plugin.towns().claimLimit(town) + "</white> chunks",
                        "<gray>Residents: <white>" + town.memberCount() + "</white>",
                        "<gray>Bank: <#f9d423>" + plugin.msg().money(town.bank()) + "</#f9d423>")
                .glow(true).hideAttributes().build());

        // ---- land ----
        inventory.setItem(CLAIM, Items.of(Material.GRASS_BLOCK)
                .name("<green><bold>Claim Chunk</bold>")
                .lore("<gray>Cost: <#f9d423>"
                                + plugin.msg().money(plugin.getConfig().getDouble("towns.claim-cost", 500.0))
                                + "</#f9d423>",
                        "<gray>Must touch your existing land.",
                        "", "<yellow>Click to claim where you stand")
                .build());

        inventory.setItem(UNCLAIM, Items.of(Material.DIRT)
                .name("<yellow><bold>Unclaim Chunk</bold>")
                .lore("<gray>Release the chunk you're in.",
                        "", "<yellow>Click to unclaim")
                .build());

        inventory.setItem(SELL_PLOT, Items.of(Material.OAK_SIGN)
                .name("<#f9d423><bold>Sell This Plot</bold>")
                .lore("<gray>Offer the chunk you're in",
                        "<gray>to one of your residents.",
                        "", "<yellow>Click, then type a price")
                .build());

        inventory.setItem(RENT_PLOT, Items.of(Material.PAPER)
                .name("<#5ad1e8><bold>Rent Out This Plot</bold>")
                .lore("<gray>Charge a resident rent every",
                        "<gray>" + plugin.towns().rentPeriodLabel() + " instead of selling it.",
                        "<gray>Rent goes to the town bank.",
                        "", "<yellow>Click, then type a price")
                .build());

        inventory.setItem(TAKE_PLOT, Items.of(Material.LIME_DYE)
                .name("<green><bold>Take This Plot</bold>")
                .lore("<gray>Buy or rent the chunk you're in,",
                        "<gray>whichever the town listed it as.",
                        "", "<yellow>Click to take it")
                .build());

        inventory.setItem(ALL_PLOTS, Items.of(Material.FILLED_MAP)
                .name("<#e94fd0><bold>All Plots</bold>")
                .lore("<gray>Every plot in town and who owns it.",
                        "", "<yellow>Click to browse")
                .build());

        // ---- town ----
        inventory.setItem(MEMBERS, Items.of(Material.PLAYER_HEAD)
                .name("<#5ad1e8><bold>Members & Ranks</bold>")
                .lore("<gray>Residents: <white>" + town.memberCount() + "</white>",
                        "<gray>Invite, promote or remove people.",
                        "", "<yellow>Click to open")
                .build());

        inventory.setItem(BANK, Items.of(Material.GOLD_INGOT)
                .name("<#f9d423><bold>Town Bank</bold>")
                .lore("<gray>Balance: <#f9d423>" + plugin.msg().money(town.bank()) + "</#f9d423>",
                        "<gray>Deposit or withdraw funds.",
                        "", "<yellow>Click to open")
                .build());

        inventory.setItem(UPGRADES, Items.of(Material.BEACON)
                .name("<#f9d423><bold>Upgrades</bold>")
                .lore("<gray>Spend the bank on haste, speed,",
                        "<gray>healing and faster businesses.",
                        "", "<yellow>Click to open")
                .glow(true).hideAttributes().build());

        inventory.setItem(HOME, Items.of(Material.ENDER_PEARL)
                .name("<#5ad1e8><bold>Town Home</bold>")
                .lore("<gray>Teleport to your town spawn.",
                        "<dark_gray>/town home",
                        "", "<yellow>Click to teleport")
                .build());

        inventory.setItem(SETTINGS, Items.of(Material.COMPARATOR)
                .name("<#5ad1e8><bold>Settings</bold>")
                .lore("<gray>Tax, spawn, permissions, borders",
                        "<gray>and the risky options.",
                        "", "<yellow>Click to open")
                .build());

        inventory.setItem(BACK, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        Town town = plugin.towns().getTownOf(player.getUniqueId());
        if (town == null) { player.closeInventory(); return; }

        switch (slot) {
            case CLAIM -> { plugin.towns().claimHere(player); redraw(); }
            case UNCLAIM -> { plugin.towns().unclaimHere(player); redraw(); }
            case SELL_PLOT -> {
                player.closeInventory();
                plugin.msg().send(player, "<#f9d423>Type the plot price</#f9d423> <gray>(or 'cancel').");
                plugin.msg().send(player, "<dark_gray>Or use <white>/town sellplot <price></white>.");
                plugin.prompts().await(player, s -> {
                    try { plugin.towns().sellPlotHere(player, Double.parseDouble(s)); }
                    catch (NumberFormatException e) { plugin.msg().send(player, "<red>That's not a number."); }
                });
            }
            case RENT_PLOT -> {
                player.closeInventory();
                plugin.msg().send(player, "<#f9d423>Type the rent per "
                        + plugin.towns().rentPeriodLabel() + "</#f9d423> <gray>(or 'cancel').");
                plugin.msg().send(player, "<dark_gray>Or use <white>/town rentoutplot <price></white>.");
                plugin.prompts().await(player, s -> {
                    try { plugin.towns().rentOutPlotHere(player, Double.parseDouble(s)); }
                    catch (NumberFormatException e) { plugin.msg().send(player, "<red>That's not a number."); }
                });
            }
            case TAKE_PLOT -> {
                String here = com.apollosmp.town.TownManager.chunkKey(player.getLocation());
                com.apollosmp.town.Town at = plugin.towns().getTownAtLoc(player.getLocation());
                if (at != null && at.rentPrice(here) != null) plugin.towns().rentPlotHere(player);
                else plugin.towns().buyPlotHere(player);
                redraw();
            }
            case ALL_PLOTS -> new TownPlotsMenu(plugin, player, 0).open();

            case MEMBERS -> new TownMembersMenu(plugin, player).open();
            case BANK -> new TownBankMenu(plugin, player).open();
            case UPGRADES -> new TownUpgradesMenu(plugin, player).open();
            case HOME -> { plugin.towns().teleportSpawn(player); player.closeInventory(); }
            case SETTINGS -> new TownSettingsMenu(plugin, player).open();

            case BACK -> new TownMenu(plugin, player).open();
            default -> { /* no-op */ }
        }
    }
}
