package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Enforces incognito rules: only /adminmode works, and gear is saved on quit. */
public class IncognitoListener implements Listener {

    private final ApolloSMP plugin;

    public IncognitoListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.incognito().isIncognito(player.getUniqueId())) return;

        String msg = event.getMessage().trim();
        String root = msg.split(" ")[0].toLowerCase();
        if (root.startsWith("/")) root = root.substring(1);
        // Strip any namespace like "minecraft:".
        int colon = root.indexOf(':');
        if (colon >= 0) root = root.substring(colon + 1);

        // Still allowed while disguised: return to normal, and the admin panel.
        switch (root) {
            case "adminmode", "incognito", "disguise", "admin", "apanel", "adminpanel" -> {
                return;
            }
            default -> { /* fall through and block */ }
        }

        event.setCancelled(true);
        plugin.msg().send(player, "<red>You're incognito - only <white>/adminmode</white> "
                + "<red>and <white>/admin</white> <red>work. Run <white>/adminmode</white> to return.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Put real gear back on the live player so it saves; clears the flag.
        plugin.incognito().handleQuit(event.getPlayer());
    }
}
