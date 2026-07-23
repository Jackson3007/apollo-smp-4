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
        inventory.setItem(25, Items.of(Material.EMERALD_BLOCK)
                .name("<gradient:#e94fd0:#5ad1e8><bold>Travelling Merchant</bold></gradient>")
                .lore("<gray>Three rare goods, new every day.",
                        "<gray>Very expensive, sometimes junk.",
                        "<dark_gray>/merchant", "", "<yellow>Click to open")
                .glow(true).hideAttributes().build());

        inventory.setItem(39, Items.of(Material.PAINTING)
                .name("<gradient:#5ad1e8:#e94fd0><bold>Discord</bold></gradient>")
                .lore("<gray>Updates, giveaways and chat.",
                        "<dark_gray>/discord", "", "<yellow>Click for the link")
                .hideAttributes().build());

        inventory.setItem(41, Items.of(Material.BOOK)
                .name("<#f9d423><bold>Getting Started</bold>")
                .lore("<gray>New here? A short guide to how",
                        "<gray>the server works and what to do first.",
                        "", "<yellow>Click to read")
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
            case 25 -> new MerchantMenu(plugin, player).open();
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
        var msg = plugin.msg();
        boolean hasTown = plugin.towns().getTownOf(player.getUniqueId()) != null;

        msg.sendRaw(player, "<#f9d423>\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501</#f9d423>");
        msg.sendRaw(player, "<gradient:#f9d423:#ff4e50><bold>  Welcome to Apollo SMP</bold></gradient>");
        msg.sendRaw(player, "");
        msg.sendRaw(player, "<gray>Apollo is survival with a real economy. You earn");
        msg.sendRaw(player, "<gray>money from what you gather, grow it into businesses");
        msg.sendRaw(player, "<gray>that pay you even offline, and build towns with");
        msg.sendRaw(player, "<gray>other players on land nobody can grief.");
        msg.sendRaw(player, "");

        msg.sendRaw(player, "<#f9d423><bold>Your first hour</bold>");
        msg.sendRaw(player, " <white>1.</white> <gray>Head somewhere wild with</gray> "
                + clickable("/rtp", "and start a base"));
        msg.sendRaw(player, " <white>2.</white> <gray>Save it with</gray> "
                + clickable("/sethome", "you get three homes"));
        msg.sendRaw(player, " <white>3.</white> <gray>Gather anything, then</gray> "
                + clickable("/sell", "turn it into cash"));
        msg.sendRaw(player, " <white>4.</white> <gray>Spend your first savings in</gray> "
                + clickable("/invest", "businesses earn while you're away"));
        msg.sendRaw(player, " <white>5.</white> <gray>Team up and</gray> "
                + clickable("/town", hasTown ? "manage your town" : "found a town"));
        msg.sendRaw(player, "");

        msg.sendRaw(player, "<#f9d423><bold>Making money</bold>");
        msg.sendRaw(player, " <gray>Selling to the server is instant but pays least.");
        msg.sendRaw(player, " <gray>The <white>auction house</white> gets you far more from");
        msg.sendRaw(player, " <gray>other players, and <white>businesses</white> pay you hourly");
        msg.sendRaw(player, " <gray>whether you're online or not.");
        msg.sendRaw(player, "");

        msg.sendRaw(player, "<#5ad1e8><bold>Towns</bold>");
        msg.sendRaw(player, " <gray>Founding a town claims the chunk you're stood in");
        msg.sendRaw(player, " <gray>and protects it. Claim more land, invite friends,");
        msg.sendRaw(player, " <gray>give them ranks, and buy upgrades from the town");
        msg.sendRaw(player, " <gray>bank like haste, speed and faster businesses.");
        msg.sendRaw(player, " <gray>Mayors can sell or rent plots to residents.");
        msg.sendRaw(player, "");

        msg.sendRaw(player, "<#e94fd0><bold>Worth knowing</bold>");
        msg.sendRaw(player, " <gray>A <white>travelling merchant</white> brings three rare goods");
        msg.sendRaw(player, " <gray>a day - sometimes treasure, sometimes junk.");
        msg.sendRaw(player, " <gray>Every day a <white>mystery business</white> goes to auction,");
        msg.sendRaw(player, " <gray>and you only learn what it really does after");
        msg.sendRaw(player, " <gray>you've won it.");
        msg.sendRaw(player, "");

        msg.sendRaw(player, "<gray>Everything has a button in "
                + clickable("/menu", "your hub") + "<gray>, so you never");
        msg.sendRaw(player, "<gray>need to memorise commands. Stuck? Ask in chat or");
        msg.sendRaw(player, "<gray>on " + clickable("/discord", "our Discord") + "<gray>.");
        msg.sendRaw(player, "<#f9d423>\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501</#f9d423>");
    }

    /** A command the player can just click instead of typing. */
    private String clickable(String command, String note) {
        return "<click:run_command:'" + command + "'><hover:show_text:'Click to run "
                + command + "'><#5ad1e8><u>" + command + "</u></#5ad1e8></hover></click>"
                + " <dark_gray>- " + note + "</dark_gray>";
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
