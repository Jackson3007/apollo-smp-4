package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Toggle incognito spy mode. While incognito this is the only command that works. */
public class AdminModeCommand implements CommandExecutor {

    private final ApolloSMP plugin;

    public AdminModeCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can use admin mode.");
            return true;
        }
        // Must work even while incognito (that's how they get back), so allow it if
        // they're already incognito even though the permission is stripped-feeling.
        if (!plugin.incognito().isIncognito(player.getUniqueId())
                && !player.hasPermission("apollo.admin")) {
            plugin.msg().send(player, "<red>You don't have permission to do that.");
            return true;
        }

        if (plugin.incognito().isIncognito(player.getUniqueId())) {
            plugin.incognito().exit(player);
        } else {
            plugin.incognito().enter(player);
        }
        return true;
    }
}
