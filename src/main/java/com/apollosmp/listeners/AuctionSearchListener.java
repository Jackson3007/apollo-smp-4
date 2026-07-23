package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.menus.AuctionMenu;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Captures the next chat line from a player who pressed "Search" in the auction house. */
public class AuctionSearchListener implements Listener {

    private final ApolloSMP plugin;
    private final Map<UUID, Long> awaiting = new ConcurrentHashMap<>();

    public AuctionSearchListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    /** Mark a player as waiting to type a search query. */
    public void await(Player player) {
        awaiting.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Long since = awaiting.get(player.getUniqueId());
        if (since == null) return;
        // If they took too long, let the message pass through as normal chat.
        if (System.currentTimeMillis() - since > 60_000L) {
            awaiting.remove(player.getUniqueId());
            return;
        }
        awaiting.remove(player.getUniqueId());
        event.setCancelled(true);

        String query = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (query.isEmpty() || query.equalsIgnoreCase("cancel")) {
                plugin.msg().send(player, "<gray>Search cancelled.");
                new AuctionMenu(plugin, player, false, 0).open();
            } else {
                plugin.msg().send(player, "<gray>Showing results for <white>" + query + "</white>.");
                new AuctionMenu(plugin, player, false, 0, query).open();
            }
        });
    }
}
