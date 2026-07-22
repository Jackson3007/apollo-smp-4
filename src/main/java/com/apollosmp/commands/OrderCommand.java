package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.menus.OrdersMenu;
import com.apollosmp.orders.OrderManager;
import com.apollosmp.util.Items;
import com.apollosmp.util.Numbers;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class OrderCommand implements CommandExecutor, TabCompleter {

    private final ApolloSMP plugin;

    public OrderCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can use buy orders.");
            return true;
        }
        if (args.length == 0) {
            new OrdersMenu(plugin, player, false, 0).open();
            return true;
        }
        if (args[0].equalsIgnoreCase("create") || args[0].equalsIgnoreCase("add")) {
            if (args.length < 2) {
                plugin.msg().send(player, "<red>Usage: /orders create <price-per-item> [amount]");
                return true;
            }
            Double price = Numbers.parseAmount(args[1]);
            if (price == null || price <= 0) {
                plugin.msg().send(player, "<red>Enter a valid price per item.");
                return true;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getType().isAir()) {
                plugin.msg().send(player, "<red>Hold an example of the item you want to buy.");
                return true;
            }
            int quantity = hand.getAmount();
            if (args.length >= 3) {
                Integer q = Numbers.parseInt(args[2]);
                if (q == null || q <= 0) {
                    plugin.msg().send(player, "<red>Enter a valid amount.");
                    return true;
                }
                quantity = q;
            }
            OrderManager.CreateResult result = plugin.orders().create(player, price, quantity);
            switch (result) {
                case SUCCESS -> plugin.msg().send(player, "<green>Buy order placed: <white>" + quantity
                        + "x " + Items.pretty(hand.getType()) + "</white> at <#f9d423>"
                        + plugin.msg().money(price) + "</#f9d423> each.");
                case TOO_MANY -> plugin.msg().send(player, "<red>You've reached your buy-order limit.");
                case PRICE_LOW -> plugin.msg().send(player, "<red>That price is too low.");
                case PRICE_HIGH -> plugin.msg().send(player, "<red>That price is too high.");
                case BAD_QUANTITY -> plugin.msg().send(player, "<red>Invalid amount (max 3456).");
                case NO_FUNDS -> plugin.msg().send(player, "<red>You must pre-pay the order. You can't afford it.");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("mine")) {
            new OrdersMenu(plugin, player, true, 0).open();
            return true;
        }
        plugin.msg().send(player, "<red>Usage: /orders [create <price> [amount]|mine]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("create", "mine")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        }
        return out;
    }
}
