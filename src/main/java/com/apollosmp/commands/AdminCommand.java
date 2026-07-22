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
            plugin.msg().send(sender, "<gray>Use <white>/apollo reload</white> or <white>/apollo version</white>.");
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
            default -> plugin.msg().send(sender, "<red>Usage: /apollo <reload|version>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("reload", "version")) {
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
            }
        }
        return out;
    }
}
