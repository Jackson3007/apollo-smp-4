package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.town.TownManager;
import com.apollosmp.town.TownRank;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The /town hub. Grouped into Land and Town sections, with settings tucked away. */
public class TownMenu extends Gui {

    // no-town state
    private static final int CREATE = 20;
    private static final int INVITE_START = 23;

    // land row
    private static final int LAND_LABEL = 9;
    private static final int CLAIM = 10;
    private static final int UNCLAIM = 11;
    private static final int THIS_PLOT = 12;
    private static final int SELL_PLOT = 13;
    private static final int BUY_PLOT = 14;
    private static final int ALL_PLOTS = 15;
    private static final int MAP = 16;

    // town row
    private static final int TOWN_LABEL = 18;
    private static final int MEMBERS = 19;
    private static final int BANK = 20;
    private static final int UPGRADES = 21;
    private static final int SPAWN = 22;
    private static final int VISIT = 23;

    // footer
    private static final int SETTINGS = 45;
    private static final int CLOSE = 49;
    private static final int LEAVE = 53;

    private final List<String> inviteNames = new ArrayList<>();

    public TownMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 6, "<gradient:#f9d423:#ff4e50><bold>Town</bold></gradient>");
    }

    @Override
    protected void build() {
        Town town = plugin.towns().getTownOf(viewer.getUniqueId());
        if (town == null) buildNoTown();
        else buildTown(town);
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    // ------------------------------------------------ no town yet
    private void buildNoTown() {
        inventory.setItem(4, Items.of(Material.OAK_SAPLING)
                .name("<#f9d423><bold>You're not in a town</bold>")
                .lore("<gray>Found your own, or accept an",
                        "<gray>invite from a friend.")
                .glow(true).hideAttributes().build());

        inventory.setItem(CREATE, Items.of(Material.EMERALD)
                .name("<green><bold>Create a Town</bold>")
                .lore("<gray>Cost: <#f9d423>"
                                + plugin.msg().money(plugin.getConfig().getDouble("towns.create-cost", 1000.0))
                                + "</#f9d423>",
                        "<gray>Claims the chunk you're standing in.",
                        "",
                        "<gray>Click, then type a name in chat.")
                .build());

        inviteNames.clear();
        inviteNames.addAll(plugin.towns().pendingInvites(viewer.getUniqueId()));
        if (inviteNames.isEmpty()) {
            inventory.setItem(24, Items.of(Material.PAPER)
                    .name("<gray>No pending invites")
                    .lore("<gray>Ask a mayor to invite you.").build());
        } else {
            int slot = INVITE_START;
            for (String name : inviteNames) {
                if (slot > 25) break;
                inventory.setItem(slot++, Items.of(Material.LIME_WOOL)
                        .name("<green><bold>Join " + name + "</bold>")
                        .lore("<gray>You've been invited.", "", "<yellow>Click to accept").build());
            }
        }

        inventory.setItem(SETTINGS, Items.of(Material.COMPASS)
                .name("<#5ad1e8><bold>Browse Towns</bold>")
                .lore("<gray>See every town on the server.").build());
        inventory.setItem(CLOSE, Items.of(Material.BARRIER).name("<red>Close").build());
    }

    // ------------------------------------------------ in a town
    private void buildTown(Town town) {
        String mayorName = plugin.getServer().getOfflinePlayer(town.mayor()).getName();
        if (mayorName == null) mayorName = "Unknown";
        String founded = new java.text.SimpleDateFormat("MMM d, yyyy")
                .format(new java.util.Date(town.founded()));
        TownRank myRank = town.rankOf(viewer.getUniqueId());

        inventory.setItem(4, Items.of(Material.WHITE_BANNER)
                .name("<gradient:#f9d423:#ff4e50><bold>" + town.name() + "</bold></gradient>")
                .lore("<gray>Mayor: <white>" + mayorName + "</white>",
                        "<gray>Founded: <white>" + founded + "</white>",
                        "<gray>Residents: <white>" + town.memberCount() + "</white>",
                        "<gray>Land: <white>" + town.claims().size() + " / "
                                + plugin.towns().claimLimit(town) + "</white> chunks",
                        "<gray>Bank: <#f9d423>" + plugin.msg().money(town.bank()) + "</#f9d423>",
                        "<gray>Daily tax: <white>" + plugin.msg().money(town.tax()) + "</white>",
                        "<gray>Your rank: <#5ad1e8>"
                                + (myRank != null ? myRank.display() : "?") + "</#5ad1e8>")
                .glow(true).hideAttributes().build());

        // ---- Land ----
        inventory.setItem(LAND_LABEL, Items.of(Material.LIME_STAINED_GLASS_PANE)
                .name("<green><bold>LAND</bold>").build());

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

        String hereKey = TownManager.chunkKey(viewer.getLocation());
        Town hereTown = plugin.towns().getTownAtLoc(viewer.getLocation());
        List<String> plotLore = new ArrayList<>();
        if (hereTown == null) {
            plotLore.add("<gray>Land: <white>Wilderness</white>");
            plotLore.add("<gray>Nobody owns this chunk.");
        } else {
            plotLore.add("<gray>Land: <#f9d423>" + hereTown.name() + "</#f9d423>");
            UUID plotOwner = hereTown.plotOwner(hereKey);
            if (plotOwner == null) {
                plotLore.add("<gray>Owner: <white>Town-owned</white>");
            } else {
                String on = plugin.getServer().getOfflinePlayer(plotOwner).getName();
                plotLore.add("<gray>Owner: <#e94fd0>" + (on == null ? "Unknown" : on) + "</#e94fd0>");
                if (plotOwner.equals(viewer.getUniqueId())) plotLore.add("<green>This plot is yours.");
            }
            Double ask = hereTown.plotPrice(hereKey);
            if (ask != null) plotLore.add("<gray>For sale: <#f9d423>" + plugin.msg().money(ask) + "</#f9d423>");
        }
        inventory.setItem(THIS_PLOT, Items.of(Material.MAP)
                .name("<#e94fd0><bold>Where You're Standing</bold>")
                .lore(plotLore.toArray(new String[0])).hideAttributes().build());

        inventory.setItem(SELL_PLOT, Items.of(Material.OAK_SIGN)
                .name("<#f9d423><bold>Sell This Plot</bold>")
                .lore("<gray>Offer this chunk to a resident.",
                        "", "<yellow>Click, then type a price")
                .build());

        inventory.setItem(BUY_PLOT, Items.of(Material.LIME_DYE)
                .name("<green><bold>Buy This Plot</bold>")
                .lore("<gray>Buy the chunk you're standing in,",
                        "<gray>if the town has it for sale.",
                        "", "<yellow>Click to buy")
                .build());

        inventory.setItem(ALL_PLOTS, Items.of(Material.FILLED_MAP)
                .name("<#e94fd0><bold>All Plots</bold>")
                .lore("<gray>Every plot in town and who owns it.",
                        "", "<yellow>Click to browse")
                .build());

        inventory.setItem(MAP, Items.of(Material.PAPER)
                .name("<#5ad1e8><bold>Land Map</bold>")
                .lore("<gray>A quick text map of nearby claims.",
                        "", "<yellow>Click to view")
                .build());

        // ---- Town ----
        inventory.setItem(TOWN_LABEL, Items.of(Material.YELLOW_STAINED_GLASS_PANE)
                .name("<#f9d423><bold>TOWN</bold>").build());

        inventory.setItem(MEMBERS, Items.of(Material.PLAYER_HEAD)
                .name("<#5ad1e8><bold>Members & Ranks</bold>")
                .lore("<gray>Invite, promote or remove residents.",
                        "<gray>Residents: <white>" + town.memberCount() + "</white>",
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

        inventory.setItem(SPAWN, Items.of(Material.ENDER_PEARL)
                .name("<#5ad1e8><bold>Town Home</bold>")
                .lore("<gray>Teleport to your town spawn.",
                        "<dark_gray>/town home",
                        "", "<yellow>Click to teleport")
                .build());

        inventory.setItem(VISIT, Items.of(Material.COMPASS)
                .name("<#5ad1e8><bold>Visit Towns</bold>")
                .lore("<gray>Browse every town and teleport.",
                        "", "<yellow>Click to open")
                .build());

        // ---- Footer ----
        inventory.setItem(SETTINGS, Items.of(Material.COMPARATOR)
                .name("<#5ad1e8><bold>Settings</bold>")
                .lore("<gray>Tax, spawn, permissions, borders",
                        "<gray>and the risky options.",
                        "", "<yellow>Click to open")
                .build());

        inventory.setItem(CLOSE, Items.of(Material.BARRIER).name("<red>Close").build());

        if (!town.mayor().equals(viewer.getUniqueId())) {
            inventory.setItem(LEAVE, Items.of(Material.IRON_DOOR)
                    .name("<yellow><bold>Leave Town</bold>")
                    .lore("<gray>You'd need a new invite to return.").build());
        }
    }

    // ------------------------------------------------ clicks
    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == CLOSE) { player.closeInventory(); return; }

        Town town = plugin.towns().getTownOf(player.getUniqueId());

        if (town == null) {
            if (slot == CREATE) {
                player.closeInventory();
                plugin.msg().send(player, "<#f9d423>Type a name for your town</#f9d423> <gray>(or 'cancel').");
                plugin.msg().send(player, "<dark_gray>Or use <white>/town create <name></white>.");
                plugin.prompts().await(player, name -> {
                    if (plugin.towns().createTown(player, name)) new TownMenu(plugin, player).open();
                });
                return;
            }
            if (slot == SETTINGS) { new TownListMenu(plugin, player, 0).open(); return; }
            if (slot >= INVITE_START && slot <= 25) {
                int idx = slot - INVITE_START;
                if (idx < inviteNames.size()
                        && plugin.towns().acceptInvite(player, inviteNames.get(idx))) {
                    new TownMenu(plugin, player).open();
                }
            }
            return;
        }

        switch (slot) {
            case CLAIM -> { plugin.towns().claimHere(player); redraw(); }
            case UNCLAIM -> { plugin.towns().unclaimHere(player); redraw(); }
            case THIS_PLOT -> redraw();
            case SELL_PLOT -> {
                player.closeInventory();
                plugin.msg().send(player, "<#f9d423>Type the plot price</#f9d423> <gray>(or 'cancel').");
                plugin.msg().send(player, "<dark_gray>Or use <white>/town sellplot <price></white>.");
                plugin.prompts().await(player, s -> {
                    try { plugin.towns().sellPlotHere(player, Double.parseDouble(s)); }
                    catch (NumberFormatException e) { plugin.msg().send(player, "<red>That's not a number."); }
                });
            }
            case BUY_PLOT -> { plugin.towns().buyPlotHere(player); redraw(); }
            case ALL_PLOTS -> new TownPlotsMenu(plugin, player, 0).open();
            case MAP -> { player.closeInventory(); plugin.borders().sendMap(player); }

            case MEMBERS -> new TownMembersMenu(plugin, player).open();
            case BANK -> new TownBankMenu(plugin, player).open();
            case UPGRADES -> new TownUpgradesMenu(plugin, player).open();
            case SPAWN -> { plugin.towns().teleportSpawn(player); player.closeInventory(); }
            case VISIT -> new TownListMenu(plugin, player, 0).open();

            case SETTINGS -> new TownSettingsMenu(plugin, player).open();

            case LEAVE -> {
                String name = town.name();
                new ConfirmMenu(plugin, player,
                        "<yellow><bold>Leave " + name + "?</bold>",
                        "Leave " + name + "?",
                        List.of("<gray>You'll lose access to town land",
                                "<gray>and any plots you own here.",
                                "",
                                "<gray>You'd need a new invite to return."),
                        () -> { plugin.towns().leave(player); new TownMenu(plugin, player).open(); },
                        () -> new TownMenu(plugin, player).open()
                ).open();
            }
            default -> { /* no-op */ }
        }
    }
}
