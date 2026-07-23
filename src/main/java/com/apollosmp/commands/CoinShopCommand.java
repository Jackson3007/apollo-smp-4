package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.menus.CoinShopMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CoinShopCommand implements CommandExecutor {

    private final ApolloSMP plugin;

    public CoinShopCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can use the coin shop.");
            return true;
        }
        new CoinShopMenu(plugin, player).open();
        return true;
    }
}
