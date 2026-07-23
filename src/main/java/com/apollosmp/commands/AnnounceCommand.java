package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Broadcast a message to everyone, styled as the server speaking. */
public class AnnounceCommand implements CommandExecutor {

    private final ApolloSMP plugin;

    public AnnounceCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("apollo.admin")) {
            plugin.msg().send(sender, "<red>You don't have permission to do that.");
            return true;
        }
        if (args.length == 0) {
            plugin.msg().send(sender, "<gray>Usage: <white>/announce <message></white>");
            return true;
        }

        StringBuilder text = new StringBuilder(args[0]);
        for (int i = 1; i < args.length; i++) text.append(" ").append(args[i]);
        // Strip MiniMessage tags so a message can't inject formatting or click actions.
        String message = text.toString().replace("<", "").replace(">", "");

        String bar = "<#f9d423>\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501</#f9d423>";
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            plugin.msg().sendRaw(player, bar);
            plugin.msg().sendRaw(player, "<gradient:#f9d423:#ff4e50><bold>  ANNOUNCEMENT</bold></gradient>");
            plugin.msg().sendRaw(player, "<white>  " + message + "</white>");
            plugin.msg().sendRaw(player, bar);
            player.playSound(player.getLocation(),
                    org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.2f);
        }
        plugin.getLogger().info("[Announce] " + message);
        if (sender instanceof Player) plugin.msg().send(sender, "<green>Announcement sent.");
        return true;
    }
}
