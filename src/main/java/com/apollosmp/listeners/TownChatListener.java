package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import com.apollosmp.town.Town;
import com.apollosmp.town.TownRank;
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

        // If they're in a town or ally channel, this never reaches public chat.
        var channel = plugin.channels().of(event.getPlayer());
        if (town != null && channel != com.apollosmp.town.ChatChannels.Channel.PUBLIC) {
            String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(event.message());
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.channels().send(event.getPlayer(), channel, text));
            return;
        }

        com.apollosmp.staff.StaffRank staff = plugin.ranks().of(event.getPlayer());
        if (town == null && staff == null) return; // nothing to add

        final Component badge = staff == null
                ? Component.empty()
                : Component.text("[", NamedTextColor.DARK_GRAY)
                        .append(Component.text(staff.display(),
                                TextColor.fromHexString(staff.colour())))
                        .append(Component.text("] ", NamedTextColor.DARK_GRAY));

        if (town == null) {
            // Staff with no town still get their badge.
            event.renderer((source, sourceName, message, viewer) ->
                    badge.append(sourceName.colorIfAbsent(NamedTextColor.WHITE))
                            .append(Component.text(": ", NamedTextColor.GRAY))
                            .append(message.colorIfAbsent(NamedTextColor.WHITE)));
            return;
        }

        final String tag = town.name();
        TownRank townRank = town.rankOf(event.getPlayer().getUniqueId());
        final String rankName = townRank == null ? TownRank.RESIDENT.display() : townRank.display();

        event.renderer((source, sourceName, message, viewer) ->
                badge.append(Component.text("[", NamedTextColor.GRAY))
                        .append(Component.text(tag, TextColor.fromHexString("#f9d423")))
                        .append(Component.text(" | ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(rankName, TextColor.fromHexString("#5ad1e8")))
                        .append(Component.text("] ", NamedTextColor.GRAY))
                        .append(sourceName.colorIfAbsent(NamedTextColor.WHITE))
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(message.colorIfAbsent(NamedTextColor.WHITE)));
    }
}
