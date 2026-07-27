package com.apollosmp.town;

import com.apollosmp.ApolloSMP;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Which channel each player is talking in. Defaults to public. */
public class ChatChannels {

    public enum Channel { PUBLIC, TOWN, ALLY }

    private final ApolloSMP plugin;
    private final Map<UUID, Channel> channels = new ConcurrentHashMap<>();

    public ChatChannels(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    public Channel of(Player player) {
        return channels.getOrDefault(player.getUniqueId(), Channel.PUBLIC);
    }

    public void set(Player player, Channel channel) {
        if (channel == Channel.PUBLIC) channels.remove(player.getUniqueId());
        else channels.put(player.getUniqueId(), channel);
    }

    public void forget(Player player) {
        channels.remove(player.getUniqueId());
    }

    /** Send one message to a channel without changing what the player is in. */
    public void send(Player player, Channel channel, String message) {
        Town town = plugin.towns().getTownOf(player.getUniqueId());
        if (town == null) {
            plugin.msg().send(player, "<red>You're not in a town.");
            return;
        }
        String clean = message.replace("<", "").replace(">", "");

        if (channel == Channel.TOWN) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                Town theirs = plugin.towns().getTownOf(p.getUniqueId());
                if (theirs == null || !theirs.name().equalsIgnoreCase(town.name())) continue;
                plugin.msg().sendRaw(p, "<dark_gray>[</dark_gray><#f9d423>Town</#f9d423><dark_gray>]</dark_gray> "
                        + "<white>" + player.getName() + "</white><gray>:</gray> <gray>" + clean + "</gray>");
            }
            return;
        }

        for (Player p : plugin.diplomacy().allyAudience(town)) {
            Town theirs = plugin.towns().getTownOf(p.getUniqueId());
            String tag = theirs == null ? "" : theirs.name();
            plugin.msg().sendRaw(p, "<dark_gray>[</dark_gray><#5ad1e8>Ally</#5ad1e8><dark_gray>]</dark_gray> "
                    + "<dark_gray>" + tag + "</dark_gray> <white>" + player.getName()
                    + "</white><gray>:</gray> <gray>" + clean + "</gray>");
        }
    }
}
