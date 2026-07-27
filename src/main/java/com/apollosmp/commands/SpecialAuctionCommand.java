package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.menus.SpecialAuctionMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class SpecialAuctionCommand implements CommandExecutor, TabCompleter {

    private final ApolloSMP plugin;

    public SpecialAuctionCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can use the auction.");
            return true;
        }
        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "claim" -> {
                    int given = plugin.specialAuction().claim(player);
                    plugin.msg().send(player, given == 0
                            ? "<gray>You have nothing waiting."
                            : "<green>Collected <white>" + given + "</white> business(es).");
                    return true;
                }
                case "reroll" -> {
                    if (!player.hasPermission("apollo.admin")) {
                        plugin.msg().send(player, "<red>You don't have permission to do that.");
                        return true;
                    }
                    plugin.specialAuction().settle();
                    plugin.specialAuction().startNew();
                    plugin.msg().send(player, "<green>New lot rolled.");
                    return true;
                }
                default -> { /* fall through to the menu */ }
            }
        }
        new SpecialAuctionMenu(plugin, player).open();
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if (sender.hasPermission("apollo.admin")) return List.of("claim", "reroll");
            return List.of("claim");
        }
        return List.of();
    }
}
