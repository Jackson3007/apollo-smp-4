package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Adds a few fake players to the count shown in the multiplayer server list, so
 * the server never looks empty to people browsing. This only affects the
 * server-list ping (the "X/Y" and hover in the multiplayer menu) - it does not
 * add players to the in-game tab list.
 */
public class FakePlayerPing implements Listener {

    private final ApolloSMP plugin;

    public FakePlayerPing(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPing(PaperServerListPingEvent event) {
        if (!plugin.getConfig().getBoolean("fake-players.enabled", true)) return;

        int min = Math.max(0, plugin.getConfig().getInt("fake-players.min", 2));
        int max = Math.max(min, plugin.getConfig().getInt("fake-players.max", 3));
        int add = (min == max) ? min : ThreadLocalRandom.current().nextInt(min, max + 1);
        if (add <= 0) return;

        event.setNumPlayers(event.getNumPlayers() + add);
        // Keep the shown maximum sensible so the count never looks broken.
        if (event.getMaxPlayers() < event.getNumPlayers()) {
            event.setMaxPlayers(event.getNumPlayers() + 1);
        }
    }
}
