package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import com.apollosmp.auction.AuctionManager;
import com.apollosmp.gui.menus.AuctionMenu;
import com.apollosmp.util.Numbers;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class AuctionCommand implements CommandExecutor, TabCompleter {

    private final ApolloSMP plugin;

    public AuctionCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can use the auction house.");
            return true;
        }
        if (args.length == 0) {
            new AuctionMenu(plugin, player, false, 0).open();
            return true;
        }
        if (args[0].equalsIgnoreCase("sell") || args[0].equalsIgnoreCase("list")) {
            if (args.length < 2) {
                plugin.msg().send(player, "<red>Usage: /ah sell <price>");
                return true;
            }
            Double price = Numbers.parseAmount(args[1]);
            if (price == null || price <= 0) {
                plugin.msg().send(player, "<red>Enter a valid price.");
                return true;
            }
            AuctionManager.ListResult result = plugin.auctions().list(player, price);
            switch (result) {
                case SUCCESS -> plugin.msg().send(player, "<green>Listed for <#f9d423>"
                        + plugin.msg().money(price) + "</#f9d423>! View it with <white>/ah</white>.");
                case EMPTY_HAND -> plugin.msg().send(player, "<red>Hold the item you want to sell.");
                case TOO_MANY -> plugin.msg().send(player, "<red>You've reached your listing limit.");
                case PRICE_LOW -> plugin.msg().send(player, "<red>That price is too low.");
                case PRICE_HIGH -> plugin.msg().send(player, "<red>That price is too high.");
                case NO_FUNDS_FOR_TAX -> plugin.msg().send(player, "<red>You can't afford the listing fee.");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("mine")) {
            new AuctionMenu(plugin, player, true, 0).open();
            return true;
        }
        plugin.msg().send(player, "<red>Usage: /ah [sell <price>|mine]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("sell", "mine")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        }
        return out;
    }
}
