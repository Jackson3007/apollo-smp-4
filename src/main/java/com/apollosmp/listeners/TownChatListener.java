package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import com.apollosmp.town.Town;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Adds a [Town] tag in front of a resident's chat messages. */
public class TownChatListener implements Listener {

    private final ApolloSMP plugin;

    public TownChatListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Town town = plugin.towns().getTownOf(event.getPlayer().getUniqueId());
        if (town == null) return;
        final String tag = town.name();
        event.renderer((source, sourceName, message, viewer) ->
                Component.text("[", NamedTextColor.GRAY)
                        .append(Component.text(tag, TextColor.fromHexString("#f9d423")))
                        .append(Component.text("] ", NamedTextColor.GRAY))
                        .append(sourceName.colorIfAbsent(NamedTextColor.WHITE))
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(message.colorIfAbsent(NamedTextColor.WHITE)));
    }
}
