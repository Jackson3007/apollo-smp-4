package com.apollosmp.vote;

import com.apollosmp.ApolloSMP;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

/**
 * Listens for confirmed votes from Votifier / NuVotifier.
 *
 * Done entirely by reflection so ApolloSMP never needs Votifier as a build
 * dependency - if it isn't installed the hook simply stays inactive.
 */
public class VotifierHook implements Listener {

    private final ApolloSMP plugin;
    private volatile boolean active = false;

    public VotifierHook(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() {
        return active;
    }

    public void tryRegister() {
        Class<?> raw;
        try {
            raw = Class.forName("com.vexsoftware.votifier.model.VotifierEvent");
        } catch (ClassNotFoundException notInstalled) {
            plugin.getLogger().warning("Votifier/NuVotifier not found - vote rewards can't be paid out.");
            plugin.getLogger().warning("Install NuVotifier and set it up on your vote sites to enable them.");
            return;
        }
        try {
            Class<? extends Event> eventClass = raw.asSubclass(Event.class);
            EventExecutor executor = (listener, event) -> handle(event);
            plugin.getServer().getPluginManager()
                    .registerEvent(eventClass, this, EventPriority.NORMAL, executor, plugin);
            active = true;
            plugin.getLogger().info("Votifier detected - confirmed votes will be rewarded.");
        } catch (Throwable ex) {
            plugin.getLogger().warning("Could not hook Votifier: " + ex.getMessage());
        }
    }

    /** Fires (async) when a vote site confirms a vote. */
    private void handle(Event event) {
        try {
            Object vote = event.getClass().getMethod("getVote").invoke(event);
            if (vote == null) return;
            Object usernameObj = vote.getClass().getMethod("getUsername").invoke(vote);
            Object serviceObj = vote.getClass().getMethod("getServiceName").invoke(vote);
            final String username = usernameObj == null ? "" : usernameObj.toString().trim();
            final String service = serviceObj == null ? "" : serviceObj.toString().trim();
            if (username.isEmpty()) return;

            // Vote events arrive off the main thread.
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.voting().handleVerifiedVote(username, service));
        } catch (Throwable ex) {
            plugin.getLogger().warning("Failed to read an incoming vote: " + ex.getMessage());
        }
    }
}
