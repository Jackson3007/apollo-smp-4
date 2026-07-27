package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Small Apollo+ convenience commands: /craft, /ec and /trail. */
public class PerkCommands implements CommandExecutor {

    private final ApolloSMP plugin;

    public PerkCommands(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can use that.");
            return true;
        }
        if (!player.hasPermission("apollo.plus") && !player.hasPermission("apollo.admin")) {
            plugin.msg().send(player, "<red>That's an <#ffd54a>Apollo+</#ffd54a> <red>perk.");
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "craft" -> {
                player.openWorkbench(null, true);
            }
            case "ec", "enderchest" -> {
                player.openInventory(player.getEnderChest());
            }
            case "trail" -> {
                boolean on = plugin.trails().toggle(player);
                plugin.msg().send(player, on
                        ? "<green>Your particle trail is now <#f9d423>on</#f9d423>."
                        : "<yellow>Your particle trail is now off.");
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
