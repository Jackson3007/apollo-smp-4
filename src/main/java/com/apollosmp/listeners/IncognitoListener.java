package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;

/** Enforces character rules: normal commands work, admin commands don't. */
public class IncognitoListener implements Listener {

    /** Vanilla/operator commands to block while playing a character. */
    private static final Set<String> ADMIN_VANILLA = Set.of(
            "op", "deop", "gamemode", "gm", "gmc", "gms", "gma", "gmsp", "give", "tp", "teleport",
            "kill", "ban", "ban-ip", "banip", "pardon", "kick", "stop", "restart", "reload", "rl",
            "whitelist", "effect", "enchant", "setblock", "fill", "clone", "summon", "execute",
            "data", "gamerule", "difficulty", "defaultgamemode", "time", "weather", "xp",
            "experience", "spawnpoint", "setworldspawn", "save-all", "save-off", "save-on",
            "forceload", "spreadplayers", "particle", "playsound", "title", "team", "scoreboard",
            "bossbar", "attribute", "advancement", "loot", "recipe", "worldborder", "seed");

    private final ApolloSMP plugin;

    public IncognitoListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.incognito().isDisguised(player.getUniqueId())) return;

        String msg = event.getMessage().trim();
        String root = msg.split(" ")[0].toLowerCase();
        if (root.startsWith("/")) root = root.substring(1);
        int colon = root.indexOf(':');
        if (colon >= 0) root = root.substring(colon + 1);

        // Always allow the switch command.
        if (root.equals("adminmode") || root.equals("incognito") || root.equals("disguise")) return;

        if (isAdminCommand(root)) {
            event.setCancelled(true);
            plugin.msg().send(player, "<red>Not while you're playing a character. "
                    + "Use <white>/adminmode</white> to switch back first.");
        }
        // Everything else (normal player commands) is allowed through.
    }

    private boolean isAdminCommand(String root) {
        if (ADMIN_VANILLA.contains(root)) return true;
        PluginCommand cmd = plugin.getServer().getPluginCommand(root);
        if (cmd != null) {
            String perm = cmd.getPermission();
            if (perm != null && perm.toLowerCase().contains("admin")) return true;
        }
        return false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Save the character's state so it's there when you come back.
        plugin.incognito().handleQuit(event.getPlayer());
    }
}
