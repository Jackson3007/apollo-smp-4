package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Toggle "disappear like you logged off" (fake leave/join, off tab & locator). */
public class VanishCommand implements CommandExecutor {

    private final ApolloSMP plugin;

    public VanishCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can vanish.");
            return true;
        }
        if (!player.hasPermission("apollo.admin")) {
            plugin.msg().send(player, "<red>You don't have permission to do that.");
            return true;
        }
        plugin.staffMode().toggleVanish(player);
        return true;
    }
}
