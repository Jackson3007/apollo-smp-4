package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.menus.SellMenu;
import com.apollosmp.sell.SellManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SellCommand implements CommandExecutor, TabCompleter {

    private final ApolloSMP plugin;

    public SellCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can sell.");
            return true;
        }
        if (args.length == 0) {
            new SellMenu(plugin, player).open();
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "hand" -> {
                SellManager.Result r = plugin.sell().sellHand(player);
                if (!r.soldAnything()) plugin.msg().send(player, "<red>That item can't be sold here.");
                else plugin.msg().send(player, "<green>Sold <white>" + r.quantity()
                        + "</white> for <#f9d423>" + plugin.msg().money(r.earned()) + "</#f9d423>.");
            }
            case "all" -> {
                SellManager.Result r = plugin.sell().sellAll(player);
                if (!r.soldAnything()) plugin.msg().send(player, "<red>Nothing sellable in your inventory.");
                else plugin.msg().send(player, "<green>Sold <white>" + r.quantity()
                        + "</white> items for <#f9d423>" + plugin.msg().money(r.earned()) + "</#f9d423>.");
            }
            default -> plugin.msg().send(player, "<red>Usage: /sell [hand|all]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("hand", "all")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        }
        return out;
    }
}
