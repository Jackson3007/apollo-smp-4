package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import com.apollosmp.town.ChatChannels;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /tc and /ac - talk to your town or to your alliance. */
public class ChannelCommand implements CommandExecutor {

    private final ApolloSMP plugin;
    private final ChatChannels.Channel channel;

    public ChannelCommand(ApolloSMP plugin, ChatChannels.Channel channel) {
        this.plugin = plugin;
        this.channel = channel;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can use chat channels.");
            return true;
        }
        if (plugin.towns().getTownOf(player.getUniqueId()) == null) {
            plugin.msg().send(player, "<red>You need to be in a town for that.");
            return true;
        }

        // With no message, toggle in and out of the channel.
        if (args.length == 0) {
            boolean already = plugin.channels().of(player) == channel;
            plugin.channels().set(player, already ? ChatChannels.Channel.PUBLIC : channel);
            if (already) {
                plugin.msg().send(player, "<gray>Back to public chat.");
            } else {
                plugin.msg().send(player, channel == ChatChannels.Channel.TOWN
                        ? "<#f9d423>Now talking to your town. <gray>Use the command again to leave."
                        : "<#5ad1e8>Now talking to your alliance. <gray>Use the command again to leave.");
            }
            return true;
        }

        StringBuilder message = new StringBuilder(args[0]);
        for (int i = 1; i < args.length; i++) message.append(" ").append(args[i]);
        plugin.channels().send(player, channel, message.toString());
        return true;
    }
}
