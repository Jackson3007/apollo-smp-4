package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import com.apollosmp.staff.StaffRank;
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

public class RankCommand implements CommandExecutor, TabCompleter {

    private final ApolloSMP plugin;

    public RankCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("apollo.admin")) {
            plugin.msg().send(sender, "<red>You don't have permission to do that.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            Map<UUID, StaffRank> all = plugin.ranks().all();
            if (all.isEmpty()) {
                plugin.msg().send(sender, "<gray>Nobody has a role yet. Try <white>/rank set <player> apollo+</white>.");
                return true;
            }
            plugin.msg().sendRaw(sender, "<#f9d423><bold>Server roles</bold>");
            for (Map.Entry<UUID, StaffRank> e : all.entrySet()) {
                String name = plugin.economy().nameOf(e.getKey());
                plugin.msg().sendRaw(sender, "  <white>" + (name == null ? e.getKey() : name)
                        + "</white> <dark_gray>-</dark_gray> " + e.getValue().tag());
            }
            return true;
        }

        if (!args[0].equalsIgnoreCase("set") || args.length < 3) {
            plugin.msg().send(sender, "<gray>Usage: <white>/rank set <player> <owner|mod|youtuber|apollo+|none></white>");
            plugin.msg().send(sender, "<gray>Or <white>/rank list</white>.");
            return true;
        }

        OfflinePlayer target = plugin.getServer().getOfflinePlayer(args[1]);
        if (target.getName() == null || (!target.hasPlayedBefore() && !target.isOnline())) {
            plugin.msg().send(sender, "<red>Never seen a player called <white>" + args[1] + "</white>.");
            return true;
        }

        String wanted = args[2].toLowerCase();
        if (wanted.equals("none") || wanted.equals("clear") || wanted.equals("remove")) {
            plugin.ranks().set(target.getUniqueId(), null);
            plugin.msg().send(sender, "<yellow>Cleared <white>" + target.getName() + "</white>'s role.");
            return true;
        }

        StaffRank rank = StaffRank.fromString(wanted);
        if (rank == null) {
            plugin.msg().send(sender, "<red>Pick owner, mod, youtuber, apollo+ or none.");
            return true;
        }

        plugin.ranks().set(target.getUniqueId(), rank);
        plugin.msg().send(sender, "<green><white>" + target.getName() + "</white> is now "
                + rank.tag() + (rank.isStaff() ? " <gray>(admin powers granted)</gray>" : ""));

        Player online = target.getPlayer();
        if (online != null) {
            plugin.msg().send(online, "<green>You've been given the " + rank.tag() + " <green>role.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("apollo.admin")) return List.of();
        if (args.length == 1) return List.of("set", "list");
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            List<String> names = new ArrayList<>();
            for (Player p : plugin.getServer().getOnlinePlayers()) names.add(p.getName());
            return names;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return List.of("owner", "mod", "youtuber", "apollo+", "none");
        }
        return List.of();
    }
}
