package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import com.apollosmp.util.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EconomyCommand implements CommandExecutor, TabCompleter {

    private final ApolloSMP plugin;

    public EconomyCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "balance" -> handleBalance(sender, args);
            case "pay" -> handlePay(sender, args);
            case "baltop" -> handleBaltop(sender);
            case "eco" -> handleEco(sender, args);
            default -> { return false; }
        }
        return true;
    }

    private void handleBalance(CommandSender sender, String[] args) {
        if (args.length >= 1) {
            OfflinePlayer target = resolve(args[0]);
            if (target == null || !plugin.economy().hasAccount(target.getUniqueId())) {
                plugin.msg().send(sender, "<red>No account found for <white>" + args[0] + "</white>.");
                return;
            }
            plugin.msg().send(sender, "<white>" + target.getName() + "</white> <gray>has</gray> <#f9d423>"
                    + plugin.msg().money(plugin.economy().getBalance(target.getUniqueId())) + "</#f9d423>.");
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Console must specify a player: /balance <player>");
            return;
        }
        plugin.msg().send(player, "<gray>Your balance: <#f9d423>"
                + plugin.msg().money(plugin.economy().getBalance(player.getUniqueId())) + "</#f9d423>");
    }

    private void handlePay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can pay.");
            return;
        }
        if (!plugin.getConfig().getBoolean("economy.allow-pay", true)) {
            plugin.msg().send(player, "<red>Payments are disabled on this server.");
            return;
        }
        if (args.length < 2) {
            plugin.msg().send(player, "<red>Usage: /pay <player> <amount>");
            return;
        }
        OfflinePlayer target = resolve(args[0]);
        if (target == null || !plugin.economy().hasAccount(target.getUniqueId())) {
            plugin.msg().send(player, "<red>No account found for <white>" + args[0] + "</white>.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.msg().send(player, "<red>You can't pay yourself.");
            return;
        }
        Double amount = Numbers.parseAmount(args[1]);
        if (amount == null || amount <= 0) {
            plugin.msg().send(player, "<red>Enter a valid amount greater than zero.");
            return;
        }
        if (!plugin.economy().has(player.getUniqueId(), amount)) {
            plugin.msg().send(player, "<red>You don't have that much.");
            return;
        }
        double taxPercent = plugin.getConfig().getDouble("economy.pay-tax-percent", 0.0);
        double tax = amount * (taxPercent / 100.0);
        double received = amount - tax;

        plugin.economy().withdraw(player.getUniqueId(), amount);
        plugin.economy().deposit(target.getUniqueId(), received);

        plugin.msg().send(player, "<green>Sent <#f9d423>" + plugin.msg().money(received)
                + "</#f9d423> to <white>" + target.getName() + "</white>"
                + (tax > 0 ? " <dark_gray>(tax " + plugin.msg().money(tax) + ")" : "") + ".");

        Player online = target.getPlayer();
        if (online != null) {
            plugin.msg().send(online, "<green>You received <#f9d423>" + plugin.msg().money(received)
                    + "</#f9d423> from <white>" + player.getName() + "</white>.");
        }
    }

    private void handleBaltop(CommandSender sender) {
        List<Map.Entry<UUID, Double>> top = plugin.economy().top(10);
        plugin.msg().sendRaw(sender, "<gradient:#f9d423:#ff4e50><bold>Top Balances</bold></gradient>");
        int rank = 1;
        for (Map.Entry<UUID, Double> e : top) {
            plugin.msg().sendRaw(sender, " <#f9d423>" + rank + ".</#f9d423> <white>"
                    + plugin.economy().nameOf(e.getKey()) + "</white> <gray>-</gray> "
                    + plugin.msg().money(e.getValue()));
            rank++;
        }
        if (top.isEmpty()) plugin.msg().sendRaw(sender, "<gray>No accounts yet.");
    }

    private void handleEco(CommandSender sender, String[] args) {
        if (!sender.hasPermission("apollo.admin")) {
            plugin.msg().send(sender, "<red>You don't have permission.");
            return;
        }
        if (args.length < 3) {
            plugin.msg().send(sender, "<red>Usage: /eco <give|take|set> <player> <amount>");
            return;
        }
        OfflinePlayer target = resolve(args[1]);
        if (target == null || !plugin.economy().hasAccount(target.getUniqueId())) {
            plugin.msg().send(sender, "<red>No account found for <white>" + args[1] + "</white>.");
            return;
        }
        Double amount = Numbers.parseAmount(args[2]);
        if (amount == null || amount < 0) {
            plugin.msg().send(sender, "<red>Enter a valid amount.");
            return;
        }
        UUID id = target.getUniqueId();
        switch (args[0].toLowerCase()) {
            case "give" -> {
                plugin.economy().deposit(id, amount);
                plugin.msg().send(sender, "<green>Gave <#f9d423>" + plugin.msg().money(amount)
                        + "</#f9d423> to <white>" + target.getName() + "</white>.");
            }
            case "take" -> {
                plugin.economy().withdraw(id, amount);
                plugin.msg().send(sender, "<green>Took <#f9d423>" + plugin.msg().money(amount)
                        + "</#f9d423> from <white>" + target.getName() + "</white>.");
            }
            case "set" -> {
                plugin.economy().set(id, amount);
                plugin.msg().send(sender, "<green>Set <white>" + target.getName()
                        + "</white>'s balance to <#f9d423>" + plugin.msg().money(amount) + "</#f9d423>.");
            }
            default -> plugin.msg().send(sender, "<red>Unknown action. Use give, take or set.");
        }
    }

    @SuppressWarnings("deprecation")
    private OfflinePlayer resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online;
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);
        return off;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("eco") && args.length == 1) {
            for (String s : List.of("give", "take", "set")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
            return out;
        }
        int nameArg = command.getName().equalsIgnoreCase("eco") ? 2 : 1;
        if (args.length == nameArg) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[args.length - 1].toLowerCase())) {
                    out.add(p.getName());
                }
            }
        }
        return out;
    }
}
