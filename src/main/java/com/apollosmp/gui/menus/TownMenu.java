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

import java.util.ArrayList;
import java.util.List;

/** The /town hub. Shows create/join options with no town, or full management inside one. */
public class TownMenu extends Gui {

    private final List<String> inviteNames = new ArrayList<>();

    public TownMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 6, "<gradient:#f9d423:#ff4e50><bold>Town</bold></gradient>");
    }

    @Override
    protected void build() {
        Town town = plugin.towns().getTownOf(viewer.getUniqueId());
        if (town == null) buildNoTown();
        else buildTown(town);
        inventory.setItem(48, Items.of(Material.FILLED_MAP)
                .name("<#5ad1e8><bold>Browse Towns</bold>")
                .lore("<gray>See every town and teleport",
                        "<gray>to one of them.").build());
        inventory.setItem(49, Items.of(Material.BARRIER).name("<red>Close").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    private void buildNoTown() {
        inventory.setItem(4, Items.of(Material.OAK_SAPLING)
                .name("<#f9d423><bold>Found a Town</bold>")
                .lore("<gray>You're not in a town yet.",
                        "<gray>Found one for <#f9d423>"
                                + plugin.msg().money(plugin.getConfig().getDouble("towns.create-cost", 1000.0))
                                + "</#f9d423> to claim this chunk.")
                .glow(true).hideAttributes().build());

        inventory.setItem(20, Items.of(Material.EMERALD)
                .name("<green><bold>Create Town</bold>")
                .lore("<gray>Click, then type a name in chat.",
                        "<gray>3-16 letters/numbers.")
                .build());

        inviteNames.clear();
        inviteNames.addAll(plugin.towns().pendingInvites(viewer.getUniqueId()));
        if (inviteNames.isEmpty()) {
            inventory.setItem(24, Items.of(Material.PAPER)
                    .name("<gray>No pending invites")
                    .lore("<gray>Ask a mayor to invite you.").build());
        } else {
            int slot = 23;
            for (String name : inviteNames) {
                if (slot > 25) break;
                inventory.setItem(slot++, Items.of(Material.LIME_WOOL)
                        .name("<green><bold>Join " + name + "</bold>")
                        .lore("<gray>You've been invited.", "<yellow>Click to accept").build());
            }
        }
    }

    private void buildTown(Town town) {
        String mayorName = plugin.getServer().getOfflinePlayer(town.mayor()).getName();
        if (mayorName == null) mayorName = "Unknown";
        String founded = new java.text.SimpleDateFormat("MMM d, yyyy").format(new java.util.Date(town.founded()));
        TownRank myRank = town.rankOf(viewer.getUniqueId());

        inventory.setItem(4, Items.of(Material.WHITE_BANNER)
                .name("<gradient:#f9d423:#ff4e50><bold>" + town.name() + "</bold></gradient>")
                .lore("<gray>Mayor: <white>" + mayorName + "</white>",
                        "<gray>Founded: <white>" + founded + "</white>",
                        "<gray>Residents: <white>" + town.memberCount() + "</white>",
                        "<gray>Land claimed: <white>" + town.claims().size() + "</white> chunks",
                        "<gray>Bank: <#f9d423>" + plugin.msg().money(town.bank()) + "</#f9d423>",
                        "<gray>Daily tax: <white>" + plugin.msg().money(town.tax()) + "</white>",
                        "<gray>Your rank: <#5ad1e8>" + (myRank != null ? myRank.display() : "?") + "</#5ad1e8>")
                .glow(true).hideAttributes().build());

        inventory.setItem(19, Items.of(Material.GRASS_BLOCK)
                .name("<green>Claim This Chunk")
                .lore("<gray>Cost: <#f9d423>"
                        + plugin.msg().money(plugin.getConfig().getDouble("towns.claim-cost", 500.0)) + "</#f9d423>",
                        "<gray>Must touch existing town land.").build());
        inventory.setItem(20, Items.of(Material.DIRT).name("<yellow>Unclaim This Chunk").build());
        inventory.setItem(21, Items.of(Material.PLAYER_HEAD)
                .name("<#5ad1e8>Members & Ranks").lore("<gray>Invite, rank, or remove residents.").build());
        inventory.setItem(22, Items.of(Material.GOLD_INGOT)
                .name("<#f9d423>Town Bank").lore("<gray>Deposit or withdraw funds.").build());
        inventory.setItem(23, Items.of(Material.COMPARATOR)
                .name("<#5ad1e8>Rank Permissions").lore("<gray>Choose what each rank can do.").build());
        inventory.setItem(24, Items.of(Material.EMERALD)
                .name("<#f9d423>Set Daily Tax").lore("<gray>Per-resident tax into the bank.",
                        "<gray>Click, then type an amount.").build());

        boolean openToVisitors = town.publicSpawn();
        inventory.setItem(27, Items.of(openToVisitors ? Material.LIME_DYE : Material.RED_DYE)
                .name(openToVisitors ? "<green><bold>Visitors: Allowed</bold>" : "<red><bold>Visitors: Blocked</bold>")
                .lore("<gray>Whether players outside your town",
                        "<gray>can <white>/town tp</white> to your spawn.",
                        "",
                        openToVisitors ? "<green>Anyone can teleport here." : "<red>Residents only.",
                        "<yellow>Click to toggle")
                .glow(openToVisitors).hideAttributes().build());
        inventory.setItem(28, Items.of(Material.RED_BED)
                .name("<#5ad1e8>Set Spawn Here").lore("<gray>Move the town spawn to you.").build());
        inventory.setItem(29, Items.of(Material.ENDER_PEARL)
                .name("<#5ad1e8>Go to Town Spawn").build());
        inventory.setItem(30, Items.of(Material.OAK_SIGN)
                .name("<#f9d423>Sell This Plot")
                .lore("<gray>Put the chunk you're standing in",
                        "<gray>up for sale to a resident.",
                        "<gray>Click, then type a price.").build());
        inventory.setItem(31, Items.of(Material.LIME_DYE)
                .name("<green>Buy This Plot")
                .lore("<gray>Buy the plot you're standing in,",
                        "<gray>if it's for sale.").build());

        if (town.mayor().equals(viewer.getUniqueId())) {
            inventory.setItem(33, Items.of(Material.TNT)
                    .name("<red><bold>Disband Town</bold>").lore("<gray>Deletes the town for everyone.").build());
        } else {
            inventory.setItem(33, Items.of(Material.IRON_DOOR)
                    .name("<yellow>Leave Town").build());
        }
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == 49) { player.closeInventory(); return; }
        if (slot == 48) { new TownListMenu(plugin, player, 0).open(); return; }
        Town town = plugin.towns().getTownOf(player.getUniqueId());

        if (town == null) {
            if (slot == 20) {
                player.closeInventory();
                plugin.msg().send(player, "<#f9d423>Type a name for your town</#f9d423> <gray>(or 'cancel').");
                plugin.msg().send(player, "<dark_gray>Or use <white>/town create <name></white>.");
                plugin.prompts().await(player, name -> {
                    if (plugin.towns().createTown(player, name)) new TownMenu(plugin, player).open();
                });
                return;
            }
            if (slot >= 23 && slot <= 25) {
                int idx = slot - 23;
                if (idx < inviteNames.size()) {
                    if (plugin.towns().acceptInvite(player, inviteNames.get(idx))) new TownMenu(plugin, player).open();
                }
            }
            return;
        }

        switch (slot) {
            case 19 -> { plugin.towns().claimHere(player); redraw(); }
            case 20 -> { plugin.towns().unclaimHere(player); redraw(); }
            case 21 -> new TownMembersMenu(plugin, player).open();
            case 22 -> new TownBankMenu(plugin, player).open();
            case 23 -> {
                if (town.hasPerm(player.getUniqueId(), TownPerm.MANAGE_PERMS)) {
                    new TownPermsMenu(plugin, player, TownRank.RESIDENT).open();
                } else {
                    plugin.msg().send(player, "<red>Only ranks with Manage Permissions can edit this.");
                }
            }
            case 24 -> {
                player.closeInventory();
                plugin.msg().send(player, "<#f9d423>Type the daily tax amount</#f9d423> <gray>(or 'cancel').");
                plugin.msg().send(player, "<dark_gray>Or use <white>/town tax <amount></white>.");
                plugin.prompts().await(player, s -> {
                    try { plugin.towns().setTax(player, Double.parseDouble(s)); }
                    catch (NumberFormatException e) { plugin.msg().send(player, "<red>That's not a number."); }
                    new TownMenu(plugin, player).open();
                });
            }
            case 27 -> {
                plugin.towns().setPublicSpawn(player, !town.publicSpawn());
                redraw();
            }
            case 28 -> { plugin.towns().setSpawnHere(player); redraw(); }
            case 29 -> { plugin.towns().teleportSpawn(player); player.closeInventory(); }
            case 30 -> {
                player.closeInventory();
                plugin.msg().send(player, "<#f9d423>Type the plot price</#f9d423> <gray>(or 'cancel').");
                plugin.msg().send(player, "<dark_gray>Or use <white>/town sellplot <price></white>.");
                plugin.prompts().await(player, s -> {
                    try { plugin.towns().sellPlotHere(player, Double.parseDouble(s)); }
                    catch (NumberFormatException e) { plugin.msg().send(player, "<red>That's not a number."); }
                });
            }
            case 31 -> { plugin.towns().buyPlotHere(player); redraw(); }
            case 33 -> {
                if (town.mayor().equals(player.getUniqueId())) {
                    if (plugin.towns().disband(player)) player.closeInventory();
                } else {
                    if (plugin.towns().leave(player)) new TownMenu(plugin, player).open();
                }
            }
            default -> { /* no-op */ }
        }
    }
}
