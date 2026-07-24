package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import com.apollosmp.util.Items;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class WorthCommand implements CommandExecutor {

    private final ApolloSMP plugin;

    public WorthCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can check item values.");
            return true;
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            plugin.msg().send(player, "<gray>You're not holding anything.");
            return true;
        }
        if (!plugin.sell().isSellable(held)) {
            plugin.msg().send(player, "<red>The server won't buy that.");
            return true;
        }
        double total = plugin.sell().valueOf(held);
        int amount = Math.max(1, held.getAmount());
        double each = total / amount;

        plugin.msg().sendRaw(player, "<white>" + amount + "x "
                + Items.pretty(held.getType()) + "</white>");
        plugin.msg().sendRaw(player, "  <gray>Whole stack:</gray> <#f9d423><bold>"
                + plugin.msg().money(total) + "</bold></#f9d423>");
        if (amount > 1) {
            plugin.msg().sendRaw(player, "  <gray>Each:</gray> <white>"
                    + plugin.msg().money(each) + "</white>");
        }
        return true;
    }
}
