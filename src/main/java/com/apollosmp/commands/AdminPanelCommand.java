package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.menus.AdminPlayersMenu;
import com.apollosmp.merchant.MerchantOffer;
import com.apollosmp.special.SpecialAuctionManager;
import com.apollosmp.special.SpecialBusiness;
import com.apollosmp.util.Items;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** /admin - the player panel, plus testing tools for the merchant and auction. */
public class AdminPanelCommand implements CommandExecutor, TabCompleter {

    private final ApolloSMP plugin;

    public AdminPanelCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can use the admin panel.");
            return true;
        }
        if (!player.hasPermission("apollo.admin")) {
            plugin.msg().send(player, "<red>You don't have permission to do that.");
            return true;
        }
        if (args.length == 0) {
            new AdminPlayersMenu(plugin, player, 0, false).open();
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "merchant" -> merchant(player, args);
            case "auction" -> auction(player, args);
            case "vote" -> vote(player, args);
            case "help" -> help(player);
            default -> help(player);
        }
        return true;
    }

    // ------------------------------------------------ merchant
    private void merchant(Player player, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase() : "peek";
        switch (sub) {
            case "reroll" -> {
                plugin.merchant().forceReroll();
                plugin.msg().send(player, "<green>Merchant stock rerolled.");
                showStock(player);
            }
            case "reset" -> {
                plugin.merchant().clearPurchases();
                plugin.msg().send(player, "<green>Purchase limits cleared - everyone can buy again.");
            }
            case "peek" -> showStock(player);
            case "give" -> {
                if (args.length < 3) {
                    plugin.msg().send(player, "<gray>Usage: <white>/admin merchant give "
                            + "<spawner|drill|feller|godapple|totem|business|junk></white>");
                    return;
                }
                giveMerchantItem(player, args[2].toLowerCase());
            }
            case "expire" -> {
                int changed = expireTools(player);
                plugin.msg().send(player, changed == 0
                        ? "<gray>No merchant tools in your inventory."
                        : "<green>Set <white>" + changed + "</white> tool(s) to crumble in 30 seconds.");
            }
            default -> plugin.msg().send(player,
                    "<gray>Usage: <white>/admin merchant <peek|reroll|reset|give|expire></white>");
        }
    }

    private void showStock(Player player) {
        plugin.msg().sendRaw(player, "<#e94fd0><bold>Merchant stock (" + plugin.merchant().stockDate() + ")</bold>");
        List<MerchantOffer> offers = plugin.merchant().offers();
        for (int i = 0; i < offers.size(); i++) {
            MerchantOffer o = offers.get(i);
            plugin.msg().sendRaw(player, " <#f9d423>" + (i + 1) + ".</#f9d423> <white>"
                    + o.kind().name() + "</white>"
                    + (o.data() == null ? "" : " <gray>(" + o.data() + ")</gray>")
                    + " <gray>-</gray> <#f9d423>" + plugin.msg().money(o.price()) + "</#f9d423>");
        }
    }

    private void giveMerchantItem(Player player, String kind) {
        MerchantOffer.Kind resolved;
        String data = null;
        switch (kind) {
            case "spawner" -> { resolved = MerchantOffer.Kind.SPAWNER; data = "BLAZE"; }
            case "drill" -> resolved = MerchantOffer.Kind.DRILL;
            case "feller", "axe", "treeaxe" -> resolved = MerchantOffer.Kind.TREE_AXE;
            case "godapple", "apple" -> resolved = MerchantOffer.Kind.GOD_APPLE;
            case "totem" -> resolved = MerchantOffer.Kind.TOTEM;
            case "business" -> { resolved = MerchantOffer.Kind.BUSINESS; data = "quarry"; }
            case "junk" -> { resolved = MerchantOffer.Kind.JUNK; data = "DIRT"; }
            default -> {
                plugin.msg().send(player, "<red>Unknown item. Try drill, feller, spawner, "
                        + "godapple, totem, business or junk.");
                return;
            }
        }
        ItemStack item = plugin.merchant().build(new MerchantOffer(resolved, 0, data));
        if (item == null) {
            plugin.msg().send(player, "<red>Couldn't build that item.");
            return;
        }
        Items.give(player, item);
        plugin.msg().send(player, "<green>Given: <white>" + resolved.name() + "</white>.");
    }

    /** Rewrite the expiry on any merchant tools the admin is carrying. */
    private int expireTools(Player player) {
        long soon = System.currentTimeMillis() + 30_000L;
        int changed = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (plugin.merchant().toolType(item) == null) continue;
            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            meta.getPersistentDataContainer().set(
                    plugin.merchant().expiryKey(), PersistentDataType.LONG, soon);
            item.setItemMeta(meta);
            changed++;
        }
        if (changed > 0) player.updateInventory();
        return changed;
    }

    // ------------------------------------------------ auction
    private void auction(Player player, String[] args) {
        SpecialAuctionManager auction = plugin.specialAuction();
        String sub = args.length > 1 ? args[1].toLowerCase() : "info";

        switch (sub) {
            case "info", "peek" -> auction.revealAll(player);

            case "reroll", "new" -> {
                auction.settle();
                auction.startNew();
                plugin.msg().send(player, "<green>Settled the old lot and rolled a new one.");
            }

            case "end", "settle" -> {
                auction.setEndsAt(System.currentTimeMillis());
                plugin.msg().send(player, "<green>Auction will close within a second.");
            }

            case "time" -> {
                if (args.length < 3) {
                    plugin.msg().send(player, "<gray>Usage: <white>/admin auction time <seconds></white>");
                    return;
                }
                try {
                    long seconds = Long.parseLong(args[2]);
                    auction.setEndsAt(System.currentTimeMillis() + seconds * 1000L);
                    plugin.msg().send(player, "<green>Auction now ends in <white>"
                            + seconds + "s</white>. <gray>Warnings will fire again.");
                } catch (NumberFormatException ex) {
                    plugin.msg().send(player, "<red>That's not a number.");
                }
            }

            case "bid" -> {
                if (args.length < 4) {
                    plugin.msg().send(player,
                            "<gray>Usage: <white>/admin auction bid <player> <amount></white>");
                    return;
                }
                Player target = plugin.getServer().getPlayerExact(args[2]);
                if (target == null) {
                    plugin.msg().send(player, "<red>That player isn't online.");
                    return;
                }
                try {
                    double amount = Double.parseDouble(args[3]);
                    SpecialAuctionManager.BidResult result = auction.bid(target, amount);
                    plugin.msg().send(player, "<gray>Result: <white>" + result.name() + "</white>");
                } catch (NumberFormatException ex) {
                    plugin.msg().send(player, "<red>That's not a number.");
                }
            }

            case "give" -> {
                SpecialBusiness lot = auction.lot();
                if (lot == null) {
                    plugin.msg().send(player, "<red>No auction running.");
                    return;
                }
                Items.give(player, plugin.specialBusinesses().createItem(lot));
                plugin.msg().send(player, "<green>Given a copy of <white>" + lot.name()
                        + "</white> for testing. <gray>The live auction is untouched.");
                auction.revealTo(player, lot);
            }

            case "fill" -> {
                int updated = 0;
                for (SpecialBusiness b : plugin.specialBusinesses().all()) {
                    if (!player.getUniqueId().equals(b.owner())) continue;
                    b.setLastGen(System.currentTimeMillis() - 86_400_000L);
                    plugin.specialBusinesses().accrue(b);
                    updated++;
                }
                plugin.msg().send(player, updated == 0
                        ? "<gray>You have no placed special businesses."
                        : "<green>Ran a day of production on <white>" + updated + "</white> business(es).");
            }

            default -> plugin.msg().send(player,
                    "<gray>Usage: <white>/admin auction <info|reroll|end|time|bid|give|fill></white>");
        }
    }

    // ------------------------------------------------ voting
    private void vote(Player player, String[] args) {
        String sub = args.length > 1 ? args[1].toLowerCase() : "status";
        switch (sub) {
            case "status" -> {
                boolean hooked = plugin.voting().votifierActive();
                plugin.msg().sendRaw(player, "<#f9d423><bold>Vote status</bold>");
                plugin.msg().sendRaw(player, " <gray>Votifier hooked: "
                        + (hooked ? "<green>yes</green>" : "<red>no - install NuVotifier</red>"));
                plugin.msg().sendRaw(player, " <gray>Reward per vote: <white>"
                        + plugin.msg().money(plugin.voting().reward()) + "</white>");
                plugin.msg().sendRaw(player, " <gray>Sites configured: <white>"
                        + plugin.voting().services().size() + "</white>");
                for (var s : plugin.voting().services()) {
                    plugin.msg().sendRaw(player, "   <dark_gray>- " + s.name() + "</dark_gray>");
                }
            }
            case "pending" -> {
                var owed = plugin.voting().pendingPayouts();
                if (owed.isEmpty()) {
                    plugin.msg().send(player, "<gray>Nothing queued - every vote has been paid.");
                    return;
                }
                plugin.msg().sendRaw(player, "<#f9d423><bold>Waiting to be paid</bold>");
                for (var e : owed.entrySet()) {
                    plugin.msg().sendRaw(player, " <white>" + e.getKey() + "</white> <gray>-</gray> "
                            + plugin.msg().money(e.getValue()));
                }
                plugin.msg().sendRaw(player, "<dark_gray>Paid automatically when they next join.");
            }
            case "test" -> {
                String target = args.length > 2 ? args[2] : player.getName();
                plugin.msg().send(player, "<gray>Simulating a confirmed vote for <white>"
                        + target + "</white>...");
                plugin.voting().handleVerifiedVote(target, "admin-test");
            }
            default -> plugin.msg().send(player,
                    "<gray>Usage: <white>/admin vote <status|test [player]></white>");
        }
    }

    // ------------------------------------------------ help
    private void help(Player player) {
        var msg = plugin.msg();
        msg.sendRaw(player, "<gradient:#f9d423:#ff4e50><bold>Admin Testing</bold></gradient>");
        msg.sendRaw(player, " <#f9d423>/admin</#f9d423> <gray>- player panel</gray>");
        msg.sendRaw(player, "<#5ad1e8>Merchant</#5ad1e8>");
        msg.sendRaw(player, " <white>/admin merchant peek</white> <gray>- today's stock</gray>");
        msg.sendRaw(player, " <white>/admin merchant reroll</white> <gray>- new stock now</gray>");
        msg.sendRaw(player, " <white>/admin merchant reset</white> <gray>- clear buy limits</gray>");
        msg.sendRaw(player, " <white>/admin merchant give <item></white> <gray>- free sample</gray>");
        msg.sendRaw(player, " <white>/admin merchant expire</white> <gray>- tools crumble in 30s</gray>");
        msg.sendRaw(player, "<#5ad1e8>Auction</#5ad1e8>");
        msg.sendRaw(player, " <white>/admin auction info</white> <gray>- reveal hidden details</gray>");
        msg.sendRaw(player, " <white>/admin auction time 60</white> <gray>- end in 60s</gray>");
        msg.sendRaw(player, " <white>/admin auction bid <player> <amt></white> <gray>- bid as them</gray>");
        msg.sendRaw(player, " <white>/admin auction end</white> <gray>- close it now</gray>");
        msg.sendRaw(player, " <white>/admin auction reroll</white> <gray>- settle & start fresh</gray>");
        msg.sendRaw(player, " <white>/admin auction give</white> <gray>- copy of the lot</gray>");
        msg.sendRaw(player, " <white>/admin auction fill</white> <gray>- fast-forward a day</gray>");
        msg.sendRaw(player, "<#5ad1e8>Voting</#5ad1e8>");
        msg.sendRaw(player, " <white>/admin vote status</white> <gray>- is Votifier hooked?</gray>");
        msg.sendRaw(player, " <white>/admin vote test</white> <gray>- fake a confirmed vote</gray>");
        msg.sendRaw(player, " <white>/admin vote pending</white> <gray>- unpaid vote rewards</gray>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("apollo.admin")) return List.of();
        if (args.length == 1) return List.of("merchant", "auction", "vote", "help");
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("merchant")) {
                return List.of("peek", "reroll", "reset", "give", "expire");
            }
            if (args[0].equalsIgnoreCase("auction")) {
                return List.of("info", "reroll", "end", "time", "bid", "give", "fill");
            }
            if (args[0].equalsIgnoreCase("vote")) {
                return List.of("status", "test", "pending");
            }
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("merchant") && args[1].equalsIgnoreCase("give")) {
                return List.of("drill", "feller", "spawner", "godapple", "totem", "business", "junk");
            }
            if (args[0].equalsIgnoreCase("auction") && args[1].equalsIgnoreCase("time")) {
                return List.of("30", "60", "300");
            }
            if (args[0].equalsIgnoreCase("auction") && args[1].equalsIgnoreCase("bid")) {
                List<String> names = new ArrayList<>();
                for (Player p : plugin.getServer().getOnlinePlayers()) names.add(p.getName());
                return names;
            }
        }
        return List.of();
    }
}
