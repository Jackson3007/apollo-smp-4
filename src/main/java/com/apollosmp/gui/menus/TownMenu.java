package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.town.TownRank;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The /town hub: a small landing screen with the town's stats and three ways in. */
public class TownMenu extends Gui {

    private static final int BANNER = 4;
    private static final int YOUR_TOWN = 11;
    private static final int VISIT = 13;
    private static final int MAP = 15;
    private static final int CLOSE = 22;

    // no-town state
    private static final int CREATE = 10;
    private static final int INVITE_START = 12;
    private static final int VISIT_ALT = 16;

    private final List<String> inviteNames = new ArrayList<>();

    public TownMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 3, "<gradient:#f9d423:#ff4e50><bold>Town</bold></gradient>");
    }

    @Override
    protected void build() {
        Town town = plugin.towns().getTownOf(viewer.getUniqueId());
        if (town == null) buildNoTown();
        else buildTown(town);
        inventory.setItem(CLOSE, Items.of(Material.BARRIER).name("<red>Close").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    private void buildNoTown() {
        inventory.setItem(BANNER, Items.of(Material.OAK_SAPLING)
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
                        "", "<yellow>Click, then type a name in chat")
                .build());

        inviteNames.clear();
        inviteNames.addAll(plugin.towns().pendingInvites(viewer.getUniqueId()));
        if (inviteNames.isEmpty()) {
            inventory.setItem(13, Items.of(Material.PAPER)
                    .name("<gray>No pending invites")
                    .lore("<gray>Ask a mayor to invite you.").build());
        } else {
            int slot = INVITE_START;
            for (String name : inviteNames) {
                if (slot > 14) break;
                inventory.setItem(slot++, Items.of(Material.LIME_WOOL)
                        .name("<green><bold>Join " + name + "</bold>")
                        .lore("<gray>You've been invited.", "", "<yellow>Click to accept").build());
            }
        }

        inventory.setItem(VISIT_ALT, Items.of(Material.COMPASS)
                .name("<#5ad1e8><bold>Visit Towns</bold>")
                .lore("<gray>Browse every town on the server",
                        "<gray>and teleport to one.",
                        "", "<yellow>Click to open")
                .build());
    }

    private void buildTown(Town town) {
        String mayorName = plugin.getServer().getOfflinePlayer(town.mayor()).getName();
        if (mayorName == null) mayorName = "Unknown";
        String founded = new java.text.SimpleDateFormat("MMM d, yyyy")
                .format(new java.util.Date(town.founded()));
        TownRank myRank = town.rankOf(viewer.getUniqueId());

        inventory.setItem(BANNER, Items.of(Material.WHITE_BANNER)
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

        inventory.setItem(YOUR_TOWN, Items.of(Material.WHITE_BANNER)
                .name("<#f9d423><bold>Your Town</bold>")
                .lore("<gray>Claim land, manage residents,",
                        "<gray>the bank, upgrades and settings.",
                        "", "<yellow>Click to manage")
                .hideAttributes().build());

        inventory.setItem(VISIT, Items.of(Material.COMPASS)
                .name("<#5ad1e8><bold>Visit Towns</bold>")
                .lore("<gray>Browse every town on the server",
                        "<gray>and teleport to one.",
                        "", "<yellow>Click to open")
                .build());

        inventory.setItem(MAP, Items.of(Material.FILLED_MAP)
                .name("<#5ad1e8><bold>Land Map</bold>")
                .lore("<gray>A text map of the claims",
                        "<gray>around where you're standing.",
                        "", "<yellow>Click to view")
                .build());
    }

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
            if (slot == VISIT_ALT) { new TownListMenu(plugin, player, 0).open(); return; }
            if (slot >= INVITE_START && slot <= 14) {
                int idx = slot - INVITE_START;
                if (idx < inviteNames.size()
                        && plugin.towns().acceptInvite(player, inviteNames.get(idx))) {
                    new TownMenu(plugin, player).open();
                }
            }
            return;
        }

        switch (slot) {
            case YOUR_TOWN -> new TownManageMenu(plugin, player).open();
            case VISIT -> new TownListMenu(plugin, player, 0).open();
            case MAP -> { player.closeInventory(); plugin.borders().sendMap(player); }
            default -> { /* no-op */ }
        }
    }
}
