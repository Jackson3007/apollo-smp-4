package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import com.apollosmp.rtp.RtpManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RtpCommand implements CommandExecutor {

    private final ApolloSMP plugin;

    public RtpCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can teleport.");
            return true;
        }
        plugin.msg().send(player, "<gray>Searching for a safe spot...");
        RtpManager.Result r = plugin.rtp().attempt(player, false);
        switch (r) {
            case SUCCESS -> plugin.msg().send(player, "<green>Teleported to the wild!");
            case COOLDOWN -> plugin.msg().send(player, "<red>Wait "
                    + plugin.rtp().cooldownLeft(player.getUniqueId()) + "s before using RTP again.");
            case NO_FUNDS -> plugin.msg().send(player, "<red>You can't afford a random teleport.");
            case NO_WORLD -> plugin.msg().send(player, "<red>The RTP world isn't loaded.");
            case FAILED -> plugin.msg().send(player, "<red>Couldn't find a safe spot. Try again.");
        }
        return true;
    }
}
