package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NickCommand implements CommandExecutor {

    private final ApolloSMP plugin;

    public NickCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can set a nickname.");
            return true;
        }
        if (!player.hasPermission("apollo.plus") && !player.hasPermission("apollo.admin")) {
            plugin.msg().send(player, "<red>Nicknames are an <#ffd54a>Apollo+</#ffd54a> <red>perk.");
            return true;
        }
        if (args.length == 0) {
            plugin.msg().send(player, "<gray>Usage: <white>/nick <name></white> <gray>or <white>/nick off</white>.");
            plugin.msg().send(player, "<gray>You can use colours, e.g. <white>/nick <#5ad1e8>Star</white>.");
            return true;
        }

        String first = args[0].toLowerCase();
        if (first.equals("off") || first.equals("clear") || first.equals("none") || first.equals("reset")) {
            plugin.nicks().set(player, null);
            plugin.msg().send(player, "<green>Your nickname has been cleared.");
            return true;
        }

        String nick = String.join(" ", args);
        // Guard against silly lengths (the visible text, once colours are stripped).
        Component preview = com.apollosmp.util.Msg.mm(nick);
        String plain = PlainTextComponentSerializer.plainText().serialize(preview);
        if (plain.length() > 20) {
            plugin.msg().send(player, "<red>That nickname is too long (20 characters max).");
            return true;
        }
        if (plain.isBlank()) {
            plugin.msg().send(player, "<red>That nickname is empty once colours are removed.");
            return true;
        }

        plugin.nicks().set(player, nick);
        plugin.msg().send(player, "<green>Your nickname is now " + nick + "<green>.");
        return true;
    }
}
