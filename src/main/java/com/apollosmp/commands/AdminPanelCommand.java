package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.menus.AdminPlayersMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AdminPanelCommand implements CommandExecutor {

    private final ApolloSMP plugin;

    public AdminPanelCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can open the admin panel.");
            return true;
        }
        if (!player.hasPermission("apollo.admin")) {
            plugin.msg().send(player, "<red>You don't have permission to do that.");
            return true;
        }
        new AdminPlayersMenu(plugin, player, 0, false).open();
        return true;
    }
}
