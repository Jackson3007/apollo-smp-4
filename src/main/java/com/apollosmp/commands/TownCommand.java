package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.menus.TownMenu;
import com.apollosmp.town.Town;
import com.apollosmp.town.TownRank;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /town — opens the GUI, but every GUI prompt also has a command form here so
 * players whose chat is blocked can still use the whole system.
 */
public class TownCommand implements CommandExecutor, TabCompleter {

    private final ApolloSMP plugin;

    public TownCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can use towns.");
            return true;
        }
        if (args.length == 0) {
            new TownMenu(plugin, player).open();
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "claim" -> plugin.towns().claimHere(player);
            case "unclaim" -> plugin.towns().unclaimHere(player);
            case "spawn" -> plugin.towns().teleportSpawn(player);
            case "setspawn" -> plugin.towns().setSpawnHere(player);
            case "leave" -> plugin.towns().leave(player);
            case "disband" -> plugin.towns().disband(player);
            case "buyplot" -> plugin.towns().buyPlotHere(player);

            case "create" -> {
                if (args.length < 2) plugin.msg().send(player, "<gray>Usage: <white>/town create <name></white>");
                else plugin.towns().createTown(player, args[1]);
            }
            case "invite" -> {
                if (args.length < 2) plugin.msg().send(player, "<gray>Usage: <white>/town invite <player></white>");
                else plugin.towns().invite(player, args[1]);
            }
            case "join", "accept" -> {
                if (args.length < 2) plugin.msg().send(player, "<gray>Usage: <white>/town join <town></white>");
                else plugin.towns().acceptInvite(player, args[1]);
            }
            case "kick" -> {
                if (args.length < 2) { plugin.msg().send(player, "<gray>Usage: <white>/town kick <player></white>"); return true; }
                UUID target = memberByName(player, args[1]);
                if (target == null) plugin.msg().send(player, "<red>No resident by that name.");
                else plugin.towns().kick(player, target);
            }
            case "rank" -> {
                if (args.length < 3) {
                    plugin.msg().send(player, "<gray>Usage: <white>/town rank <player> <assistant|commander|resident></white>");
                    return true;
                }
                UUID target = memberByName(player, args[1]);
                if (target == null) { plugin.msg().send(player, "<red>No resident by that name."); return true; }
                TownRank rank = TownRank.fromString(args[2].toUpperCase(), null);
                if (rank == null || rank == TownRank.MAYOR) {
                    plugin.msg().send(player, "<red>Pick assistant, commander, or resident.");
                    return true;
                }
                plugin.towns().setRank(player, target, rank);
            }
            case "deposit" -> {
                Double amount = parse(player, args, "/town deposit <amount>");
                if (amount != null) plugin.towns().deposit(player, amount);
            }
            case "withdraw" -> {
                Double amount = parse(player, args, "/town withdraw <amount>");
                if (amount != null) plugin.towns().withdraw(player, amount);
            }
            case "tax" -> {
                Double amount = parse(player, args, "/town tax <amount>");
                if (amount != null) plugin.towns().setTax(player, amount);
            }
            case "sellplot" -> {
                Double amount = parse(player, args, "/town sellplot <price>");
                if (amount != null) plugin.towns().sellPlotHere(player, amount);
            }

            case "tp", "teleport", "visit" -> {
                if (args.length < 2) plugin.msg().send(player, "<gray>Usage: <white>/town tp <town></white>");
                else plugin.towns().teleportToTown(player, args[1]);
            }
            case "list", "towns" -> new com.apollosmp.gui.menus.TownListMenu(plugin, player, 0).open();
            case "visitors", "publicspawn" -> {
                if (args.length < 2) {
                    plugin.msg().send(player, "<gray>Usage: <white>/town visitors <on|off></white>");
                    return true;
                }
                String v = args[1].toLowerCase();
                if (v.equals("on") || v.equals("true") || v.equals("allow")) {
                    plugin.towns().setPublicSpawn(player, true);
                } else if (v.equals("off") || v.equals("false") || v.equals("block")) {
                    plugin.towns().setPublicSpawn(player, false);
                } else {
                    plugin.msg().send(player, "<red>Use <white>on</white> or <white>off</white>.");
                }
            }
            case "help" -> sendHelp(player);
            default -> new TownMenu(plugin, player).open();
        }
        return true;
    }

    private void sendHelp(Player player) {
        plugin.msg().send(player, "<gradient:#f9d423:#ff4e50><bold>Town Commands</bold></gradient>");
        plugin.msg().send(player, "<gray>/town <white>- open the town menu");
        plugin.msg().send(player, "<gray>/town create <name> <white>- found a town");
        plugin.msg().send(player, "<gray>/town claim <white>| <gray>/town unclaim");
        plugin.msg().send(player, "<gray>/town invite <player> <white>| <gray>/town join <town>");
        plugin.msg().send(player, "<gray>/town kick <player> <white>| <gray>/town rank <player> <rank>");
        plugin.msg().send(player, "<gray>/town deposit <amt> <white>| <gray>/town withdraw <amt>");
        plugin.msg().send(player, "<gray>/town tax <amt> <white>| <gray>/town sellplot <price>");
        plugin.msg().send(player, "<gray>/town buyplot <white>| <gray>/town spawn <white>| <gray>/town setspawn");
        plugin.msg().send(player, "<gray>/town list <white>- browse every town");
        plugin.msg().send(player, "<gray>/town tp <town> <white>- teleport to a town");
        plugin.msg().send(player, "<gray>/town visitors <on|off> <white>- allow outside teleports");
        plugin.msg().send(player, "<gray>/town leave <white>| <gray>/town disband");
    }

    /** Parse a numeric argument, messaging the player on failure. */
    private Double parse(Player player, String[] args, String usage) {
        if (args.length < 2) {
            plugin.msg().send(player, "<gray>Usage: <white>" + usage + "</white>");
            return null;
        }
        try {
            return Double.parseDouble(args[1]);
        } catch (NumberFormatException ex) {
            plugin.msg().send(player, "<red>That's not a number.");
            return null;
        }
    }

    /** Find a resident of the sender's town by name. */
    private UUID memberByName(Player actor, String name) {
        Town town = plugin.towns().getTownOf(actor.getUniqueId());
        if (town == null) {
            plugin.msg().send(actor, "<red>You're not in a town.");
            return null;
        }
        for (UUID id : town.members().keySet()) {
            String n = plugin.getServer().getOfflinePlayer(id).getName();
            if (n != null && n.equalsIgnoreCase(name)) return id;
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("create", "claim", "unclaim", "invite", "join", "kick", "rank",
                    "deposit", "withdraw", "tax", "sellplot", "buyplot",
                    "spawn", "setspawn", "list", "tp", "visitors", "leave", "disband", "help");
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("kick") || args[0].equalsIgnoreCase("rank"))
                && sender instanceof Player p) {
            Town town = plugin.towns().getTownOf(p.getUniqueId());
            List<String> names = new ArrayList<>();
            if (town != null) {
                for (UUID id : town.members().keySet()) {
                    String n = plugin.getServer().getOfflinePlayer(id).getName();
                    if (n != null) names.add(n);
                }
            }
            return names;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("join"))) {
            List<String> names = new ArrayList<>();
            for (Town t : plugin.towns().allTowns()) names.add(t.name());
            return names;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("visitors")) {
            return List.of("on", "off");
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("rank")) {
            return List.of("assistant", "commander", "resident");
        }
        return List.of();
    }
}
