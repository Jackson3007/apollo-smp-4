package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class StaffCommand implements CommandExecutor, TabCompleter {

    private final ApolloSMP plugin;

    public StaffCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can use staff mode.");
            return true;
        }
        if (!player.hasPermission("apollo.admin")) {
            plugin.msg().send(player, "<red>You don't have permission to do that.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("vanish")) {
            boolean hidden = !plugin.staffMode().isVanished(player.getUniqueId());
            plugin.staffMode().setVanished(player, hidden);
            plugin.msg().send(player, hidden
                    ? "<gray>You're now hidden from other players."
                    : "<gray>You're visible again.");
            return true;
        }

        plugin.staffMode().toggle(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("apollo.admin")) return List.of("vanish");
        return List.of();
    }
}
