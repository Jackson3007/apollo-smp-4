package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class VaultCommand implements CommandExecutor, TabCompleter {

    private final ApolloSMP plugin;

    public VaultCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can open a vault.");
            return true;
        }
        if (!player.hasPermission("apollo.admin")) {
            plugin.msg().send(player, "<red>You don't have permission to do that.");
            return true;
        }

        int index = 1;
        if (args.length > 0) {
            try {
                index = Integer.parseInt(args[0]);
            } catch (NumberFormatException ex) {
                plugin.msg().send(player, "<red>Usage: /pv [1-" + plugin.vaults().vaultCount() + "]");
                return true;
            }
        }
        if (index < 1 || index > plugin.vaults().vaultCount()) {
            plugin.msg().send(player, "<red>Pick a vault between 1 and "
                    + plugin.vaults().vaultCount() + ".");
            return true;
        }
        plugin.vaults().open(player, index);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("apollo.admin")) {
            List<String> out = new ArrayList<>();
            for (int i = 1; i <= plugin.vaults().vaultCount(); i++) out.add(String.valueOf(i));
            return out;
        }
        return List.of();
    }
}
