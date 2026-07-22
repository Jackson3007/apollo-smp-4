package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.menus.HomesMenu;
import com.apollosmp.homes.Home;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class HomeCommands implements CommandExecutor, TabCompleter {

    private final ApolloSMP plugin;

    public HomeCommands(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players have homes.");
            return true;
        }
        switch (command.getName().toLowerCase()) {
            case "sethome" -> setHome(player, args);
            case "delhome" -> delHome(player, args);
            case "home" -> home(player, args);
            case "homes" -> new HomesMenu(plugin, player).open();
            default -> { return false; }
        }
        return true;
    }

    private void setHome(Player player, String[] args) {
        String name = args.length >= 1 ? args[0] : "home";
        if (name.length() > 24) {
            plugin.msg().send(player, "<red>Home names must be 24 characters or fewer.");
            return;
        }
        boolean exists = plugin.homes().exists(player.getUniqueId(), name);
        int limit = plugin.homes().limitFor(player);
        if (!exists && plugin.homes().count(player.getUniqueId()) >= limit) {
            plugin.msg().send(player, "<red>You've hit your home limit (<white>" + limit
                    + "</white>). Delete one or rank up.");
            return;
        }
        plugin.homes().setHome(player.getUniqueId(), name, player.getLocation(), Material.RED_BED);
        plugin.msg().send(player, (exists ? "<green>Updated home <#f9d423>" : "<green>Home <#f9d423>")
                + name + "</#f9d423> set!");
    }

    private void delHome(Player player, String[] args) {
        if (args.length < 1) {
            plugin.msg().send(player, "<red>Usage: /delhome <name>");
            return;
        }
        if (plugin.homes().deleteHome(player.getUniqueId(), args[0])) {
            plugin.msg().send(player, "<green>Deleted home <#f9d423>" + args[0] + "</#f9d423>.");
        } else {
            plugin.msg().send(player, "<red>You don't have a home called <white>" + args[0] + "</white>.");
        }
    }

    private void home(Player player, String[] args) {
        List<Home> homes = plugin.homes().getHomes(player.getUniqueId());
        if (homes.isEmpty()) {
            plugin.msg().send(player, "<red>You have no homes. Set one with <white>/sethome</white>.");
            return;
        }
        Home target;
        if (args.length >= 1) {
            target = plugin.homes().getHome(player.getUniqueId(), args[0]);
            if (target == null) {
                plugin.msg().send(player, "<red>No home called <white>" + args[0] + "</white>.");
                return;
            }
        } else if (homes.size() == 1) {
            target = homes.get(0);
        } else {
            new HomesMenu(plugin, player).open();
            return;
        }
        double cost = plugin.getConfig().getDouble("homes.teleport-cost", 0.0);
        plugin.teleports().warmupTeleport(player, target.toLocation(), cost,
                "<green>Welcome to <#f9d423>" + target.name() + "</#f9d423>!");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player player)) return out;
        String name = command.getName().toLowerCase();
        if ((name.equals("home") || name.equals("delhome")) && args.length == 1) {
            for (Home h : plugin.homes().getHomes(player.getUniqueId())) {
                if (h.name().toLowerCase().startsWith(args[0].toLowerCase())) out.add(h.name());
            }
        }
        return out;
    }
}
