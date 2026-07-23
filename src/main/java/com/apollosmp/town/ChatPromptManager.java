package com.apollosmp.town;

import com.apollosmp.ApolloSMP;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Lets menus ask a player to type one line in chat, then runs a callback on the main thread. */
public class ChatPromptManager implements Listener {

    private record Pending(Consumer<String> callback, long since) {}

    private final ApolloSMP plugin;
    private final Map<UUID, Pending> waiting = new ConcurrentHashMap<>();

    public ChatPromptManager(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    public void await(Player player, Consumer<String> callback) {
        waiting.put(player.getUniqueId(), new Pending(callback, System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        Pending pending = waiting.get(player.getUniqueId());
        if (pending == null) return;
        if (System.currentTimeMillis() - pending.since() > 60_000L) {
            waiting.remove(player.getUniqueId());
            return;
        }
        waiting.remove(player.getUniqueId());
        event.setCancelled(true);

        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("cancel")) {
                plugin.msg().send(player, "<gray>Cancelled.");
                return;
            }
            pending.callback().accept(text);
        });
    }
}
