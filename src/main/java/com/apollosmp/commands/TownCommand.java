package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.menus.TownMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class TownCommand implements CommandExecutor, TabCompleter {

    private final ApolloSMP plugin;

    public TownCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can use towns.");
            return true;
        }
        if (args.length == 0) {
            new TownMenu(plugin, player).open();
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "claim" -> plugin.towns().claimHere(player);
            case "unclaim" -> plugin.towns().unclaimHere(player);
            case "spawn" -> plugin.towns().teleportSpawn(player);
            case "create" -> {
                if (args.length < 2) plugin.msg().send(player, "<gray>Usage: <white>/town create <name></white>");
                else plugin.towns().createTown(player, args[1]);
            }
            case "leave" -> plugin.towns().leave(player);
            default -> new TownMenu(plugin, player).open();
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("claim", "unclaim", "spawn", "create", "leave");
        }
        return List.of();
    }
}
