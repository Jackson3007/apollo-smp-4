package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final ApolloSMP plugin;

    public AdminCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("apollo.admin")) {
            plugin.msg().send(sender, "<red>You don't have permission.");
            return true;
        }
        if (args.length == 0) {
            plugin.msg().sendRaw(sender, "<gradient:#f9d423:#ff4e50><bold>Apollo SMP</bold></gradient> "
                    + "<gray>v" + plugin.getPluginMeta().getVersion() + "</gray>");
            plugin.msg().send(sender, "<gray>Use <white>/apollo reload</white>, <white>/apollo version</white> "
                    + "<gray>or <white>/apollo fakeah <refresh|clear></white>.");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadAll();
                plugin.msg().send(sender, "<green>Configuration reloaded.");
            }
            case "version" -> plugin.msg().sendRaw(sender,
                    "<gradient:#f9d423:#ff4e50><bold>Apollo SMP</bold></gradient> <gray>v"
                            + plugin.getPluginMeta().getVersion() + "</gray>");
            case "fakeah" -> {
                String sub = args.length > 1 ? args[1].toLowerCase() : "refresh";
                if (sub.equals("clear")) {
                    plugin.auctions().clearFakes();
                    plugin.msg().send(sender, "<yellow>Cleared all seeded auction listings.");
                } else {
                    plugin.fakeAuctions().seed();
                    plugin.msg().send(sender, "<green>Refreshed seeded auction listings "
                            + "<gray>(now <#f9d423>" + plugin.auctions().fakeCount() + "</#f9d423> <gray>up).");
                }
            }
            default -> plugin.msg().send(sender, "<red>Usage: /apollo <reload|version|fakeah>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("reload", "version", "fakeah")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("fakeah")) {
            for (String s : List.of("refresh", "clear")) {
                if (s.startsWith(args[1].toLowerCase())) out.add(s);
            }
        }
        return out;
    }
}
