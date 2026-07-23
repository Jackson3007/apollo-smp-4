package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class DiscordCommand implements CommandExecutor {

    private final ApolloSMP plugin;

    public DiscordCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String invite = plugin.voting().discordInvite();
        if (invite == null || invite.isBlank()) {
            plugin.msg().send(sender, "<red>No Discord link is set up yet.");
            return true;
        }
        plugin.msg().send(sender, "<gradient:#5ad1e8:#e94fd0><bold>Apollo SMP Discord</bold></gradient>");
        plugin.msg().send(sender, "<click:open_url:'" + invite + "'><hover:show_text:'Click to open Discord'>"
                + "<#5ad1e8><u>" + invite + "</u></#5ad1e8></hover></click>");
        return true;
    }
}
