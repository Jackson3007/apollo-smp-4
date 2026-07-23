package com.apollosmp.vote;

import com.apollosmp.ApolloSMP;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Vote links and the periodic reminder. Purely informational for now — no
 * rewards, coins or keys are handed out for voting.
 */
public class VoteManager {

    public record Service(String name, String link) {}

    /** Used when config.yml has no properly configured sites yet. */
    private static final Service DEFAULT_SITE =
            new Service("TopG", "https://topg.org/minecraft-servers/server-684435");

    private final ApolloSMP plugin;

    public VoteManager(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    public boolean reminderEnabled() {
        return plugin.getConfig().getBoolean("voting.reminder-enabled", true);
    }

    public int reminderMinutes() {
        return Math.max(1, plugin.getConfig().getInt("voting.reminder-minutes", 60));
    }

    /** Configured vote sites, skipping any that still hold placeholder links. */
    public List<Service> services() {
        List<Service> out = new ArrayList<>();
        for (Map<?, ?> m : plugin.getConfig().getMapList("voting.services")) {
            Object name = m.get("name");
            Object link = m.get("link");
            if (name == null || link == null) continue;
            String url = link.toString().trim();
            // Unconfigured placeholders shipped in the default config.
            if (url.isEmpty() || url.contains("000000")) continue;
            out.add(new Service(name.toString(), url));
        }
        if (out.isEmpty()) out.add(DEFAULT_SITE);
        return out;
    }

    /** Nudge everyone online to join the Discord. Alternates with the vote reminder. */
    public void sendDiscordReminder() {
        if (!plugin.getConfig().getBoolean("discord.enabled", true)) return;
        String invite = discordInvite();
        if (invite == null || invite.isBlank()) return;

        String bar = "<dark_gray>\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa";
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            plugin.msg().send(player, bar);
            plugin.msg().send(player, "<gradient:#5ad1e8:#e94fd0><bold>Join our Discord!</bold></gradient>");
            plugin.msg().send(player, "<gray>Updates, giveaways and a place to chat.");
            plugin.msg().send(player, "<click:open_url:'" + invite + "'><hover:show_text:'Click to open Discord'>"
                    + "<#5ad1e8><u>" + invite + "</u></#5ad1e8></hover></click>");
            plugin.msg().send(player, bar);
        }
    }

    public String discordInvite() {
        return plugin.getConfig().getString("discord.invite", "https://discord.gg/ztg4bkvdpN");
    }

    /** Nudge everyone online to vote. */
    public void sendReminders() {
        if (!reminderEnabled()) return;
        if (services().isEmpty()) return;

        String bar = "<dark_gray>\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa\u25aa";
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            plugin.msg().send(player, bar);
            plugin.msg().send(player, "<gradient:#f9d423:#ff4e50><bold>\u2600 Vote for Apollo!</bold></gradient>");
            plugin.msg().send(player, "<gray>Voting helps more players find the server.");
            plugin.msg().send(player, "<click:run_command:'/vote'><hover:show_text:'Click to open the vote menu'>"
                    + "<#5ad1e8><u>Click here for the vote links</u></#5ad1e8></hover></click>");
            plugin.msg().send(player, bar);
        }
    }
}
