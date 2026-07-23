package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.rtp.RtpManager;
import com.apollosmp.town.Town;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** The server hub: every feature in one place. */
public class MainMenu extends Gui {

    public MainMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 5, "<gradient:#f9d423:#ff4e50><bold>Apollo SMP</bold></gradient>");
    }

    @Override
    protected void build() {
        double bal = plugin.economy().getBalance(viewer.getUniqueId());
        int mail = plugin.mailbox().size(viewer.getUniqueId());
        Town town = plugin.towns().getTownOf(viewer.getUniqueId());

        inventory.setItem(4, Items.of(Material.SUNFLOWER)
                .name("<#f9d423><bold>" + viewer.getName() + "</bold>")
                .lore("<gray>Balance: <white>" + plugin.msg().money(bal) + "</white>",
                        "<gray>Town: <white>" + (town == null ? "None" : town.name()) + "</white>",
                        "<dark_gray>Use /pay to send money")
                .glow(true).hideAttributes().build());

        // ---- Row 2: economy ----
        inventory.setItem(10, Items.of(Material.HOPPER)
                .name("<#ff4e50><bold>Sell to Server</bold>")
                .lore("<gray>Instantly sell your loot for cash.",
                        "<dark_gray>/sell", "", "<yellow>Click to open")
                .hideAttributes().build());

        inventory.setItem(11, Items.of(Material.CHEST)
                .name("<#f9d423><bold>Auction House</bold>")
                .lore("<gray>Buy & sell with other players.",
                        "<dark_gray>/ah", "", "<yellow>Click to open")
                .hideAttributes().build());

        inventory.setItem(12, Items.of(Material.WRITABLE_BOOK)
                .name("<#f9d423><bold>Buy Orders</bold>")
                .lore("<gray>Request items at your own price.",
                        "<dark_gray>/orders", "", "<yellow>Click to open")
                .hideAttributes().build());

        inventory.setItem(13, Items.of(Material.GOLD_INGOT)
                .name("<#f9d423><bold>Invest</bold>")
                .lore("<gray>Buy businesses that earn while",
                        "<gray>you're away.",
                        "<dark_gray>/invest", "", "<yellow>Click to open")
                .hideAttributes().build());

        inventory.setItem(14, Items.of(Material.CHEST_MINECART)
                .name("<#f9d423><bold>Collection Box</bold>")
                .lore("<gray>Waiting items: <white>" + mail + "</white>",
                        "<dark_gray>Expired auctions & filled orders", "",
                        "<yellow>Click to collect")
                .glow(mail > 0).hideAttributes().build());

        inventory.setItem(15, Items.of(Material.GOLD_BLOCK)
                .name("<#f9d423><bold>Baltop</bold>")
                .lore("<gray>See the richest players.",
                        "<dark_gray>/baltop", "", "<yellow>Click to view")
                .hideAttributes().build());

        inventory.setItem(16, Items.of(Material.WHITE_BANNER)
                .name("<#5ad1e8><bold>Towns</bold>")
                .lore(town == null
                                ? "<gray>Found a town or join a friend's."
                                : "<gray>Manage <white>" + town.name() + "</white>.",
                        "<dark_gray>/town", "", "<yellow>Click to open")
                .glow(town != null).hideAttributes().build());

        // ---- Row 3: getting around ----
        inventory.setItem(20, Items.of(Material.RED_BED)
                .name("<#ff4e50><bold>Homes</bold>")
                .lore("<gray>Set and teleport to your homes.",
                        "<dark_gray>/sethome, /home", "", "<yellow>Click to open")
                .hideAttributes().build());

        inventory.setItem(21, Items.of(Material.ENDER_PEARL)
                .name("<#f9d423><bold>Random Teleport</bold>")
                .lore("<gray>Warp out into the wilderness.",
                        "<dark_gray>/rtp", "", "<yellow>Click to teleport")
                .hideAttributes().build());

        inventory.setItem(22, Items.of(Material.COMPASS)
                .name("<#5ad1e8><bold>Visit a Town</bold>")
                .lore("<gray>Browse every town and teleport.",
                        "<dark_gray>/town list", "", "<yellow>Click to open")
                .hideAttributes().build());

        inventory.setItem(23, Items.of(Material.PLAYER_HEAD)
                .name("<#f9d423><bold>Teleport to a Player</bold>")
                .lore("<gray>Ask someone to teleport to them.",
                        "<dark_gray>/tpa <player>", "", "<gray>Use the command in chat")
                .hideAttributes().build());

        inventory.setItem(24, Items.of(Material.NETHER_STAR)
                .name("<#f9d423><bold>Vote</bold>")
                .lore("<gray>Support the server on the",
                        "<gray>server lists.",
                        "<dark_gray>/vote", "", "<yellow>Click to open")
                .hideAttributes().build());

        // ---- Row 5: community ----
        inventory.setItem(39, Items.of(Material.PAINTING)
                .name("<gradient:#5ad1e8:#e94fd0><bold>Discord</bold></gradient>")
                .lore("<gray>Updates, giveaways and chat.",
                        "<dark_gray>/discord", "", "<yellow>Click for the link")
                .hideAttributes().build());

        inventory.setItem(41, Items.of(Material.BOOK)
                .name("<#f9d423><bold>Help</bold>")
                .lore("<gray>A quick list of every command.", "", "<yellow>Click to view")
                .hideAttributes().build());

        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        switch (slot) {
            case 10 -> new SellMenu(plugin, player).open();
            case 11 -> new AuctionMenu(plugin, player, false, 0).open();
            case 12 -> new OrdersMenu(plugin, player, false, 0).open();
            case 13 -> new InvestMenu(plugin, player).open();
            case 14 -> {
                int collected = plugin.mailbox().collect(player);
                if (collected == 0) plugin.msg().send(player, "<gray>Your collection box is empty.");
                else plugin.msg().send(player, "<green>Collected <white>" + collected + "</white> item stack(s).");
                redraw();
            }
            case 15 -> {
                player.closeInventory();
                showBaltop(player);
            }
            case 16 -> new TownMenu(plugin, player).open();
            case 20 -> new HomesMenu(plugin, player).open();
            case 21 -> {
                player.closeInventory();
                RtpManager.Result r = plugin.rtp().attempt(player, false);
                switch (r) {
                    case SUCCESS -> plugin.msg().send(player, "<green>Teleported to the wild!");
                    case COOLDOWN -> plugin.msg().send(player, "<red>Please wait "
                            + plugin.rtp().cooldownLeft(player.getUniqueId()) + "s before using RTP again.");
                    case NO_FUNDS -> plugin.msg().send(player, "<red>You can't afford a random teleport.");
                    case NO_WORLD -> plugin.msg().send(player, "<red>RTP world is not loaded.");
                    case FAILED -> plugin.msg().send(player, "<red>Couldn't find a safe spot. Try again.");
                }
            }
            case 22 -> new TownListMenu(plugin, player, 0).open();
            case 24 -> new VoteMenu(plugin, player).open();
            case 39 -> {
                player.closeInventory();
                player.performCommand("discord");
            }
            case 41 -> {
                player.closeInventory();
                showHelp(player);
            }
            default -> { /* ignore */ }
        }
    }

    private void showHelp(Player player) {
        plugin.msg().sendRaw(player, "<gradient:#f9d423:#ff4e50><bold>Apollo SMP Commands</bold></gradient>");
        plugin.msg().sendRaw(player, " <#f9d423>/menu</#f9d423> <gray>- this menu</gray>");
        plugin.msg().sendRaw(player, " <#f9d423>/sell</#f9d423> <gray>- sell loot to the server</gray>");
        plugin.msg().sendRaw(player, " <#f9d423>/ah</#f9d423> <gray>- auction house</gray>");
        plugin.msg().sendRaw(player, " <#f9d423>/orders</#f9d423> <gray>- buy orders</gray>");
        plugin.msg().sendRaw(player, " <#f9d423>/invest</#f9d423> <gray>- businesses</gray>");
        plugin.msg().sendRaw(player, " <#f9d423>/town</#f9d423> <gray>- create & manage a town</gray>");
        plugin.msg().sendRaw(player, " <#f9d423>/sethome /home</#f9d423> <gray>- your homes</gray>");
        plugin.msg().sendRaw(player, " <#f9d423>/rtp</#f9d423> <gray>- random teleport</gray>");
        plugin.msg().sendRaw(player, " <#f9d423>/tpa</#f9d423> <gray>- teleport to a player</gray>");
        plugin.msg().sendRaw(player, " <#f9d423>/pay /bal /baltop</#f9d423> <gray>- money</gray>");
        plugin.msg().sendRaw(player, " <#f9d423>/vote /discord</#f9d423> <gray>- support & community</gray>");
    }

    private void showBaltop(Player player) {
        List<java.util.Map.Entry<java.util.UUID, Double>> top = plugin.economy().top(10);
        plugin.msg().sendRaw(player, "<gradient:#f9d423:#ff4e50><bold>Top Balances</bold></gradient>");
        int rank = 1;
        for (java.util.Map.Entry<java.util.UUID, Double> e : top) {
            plugin.msg().sendRaw(player, " <#f9d423>" + rank + ".</#f9d423> <white>"
                    + plugin.economy().nameOf(e.getKey()) + "</white> <gray>-</gray> "
                    + plugin.msg().money(e.getValue()));
            rank++;
        }
    }
}
