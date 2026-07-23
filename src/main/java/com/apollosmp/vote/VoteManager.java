package com.apollosmp.vote;

import com.apollosmp.ApolloSMP;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vote links and the periodic reminder. Purely informational for now — no
 * rewards, coins or keys are handed out for voting.
 */
public class VoteManager {

    public record Service(String name, String link) {}

    /** Used when config.yml has no properly configured sites yet. */
    private static final List<Service> DEFAULT_SITES = List.of(
            new Service("TopG", "http://topg.org/minecraft-servers/server-684435#vote"),
            new Service("PlanetMinecraft",
                    "https://www.planetminecraft.com/server/apollo-smp-apollo-noob-club/vote/"));

    private final ApolloSMP plugin;
    private final File file;

    /** Money owed to players who voted while offline. */
    private final Map<String, Double> pending = new ConcurrentHashMap<>();
    /** Sites each player has already been paid for today, so a retry can't double-pay. */
    private final Map<String, Set<String>> paidToday = new ConcurrentHashMap<>();
    private volatile String paidDate = "";
    private volatile boolean votifierActive = false;

    public VoteManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "votes.yml");
        load();
    }

    public double reward() {
        return plugin.getConfig().getDouble("voting.reward", 500.0);
    }

    public void setVotifierActive(boolean active) { this.votifierActive = active; }
    public boolean votifierActive() { return votifierActive; }

    private String today() { return LocalDate.now().toString(); }

    private void rollDay() {
        String now = today();
        if (!now.equals(paidDate)) {
            paidDate = now;
            paidToday.clear();
        }
    }

    /** Called on the main thread when a vote site confirms a vote. */
    public void handleVerifiedVote(String username, String service) {
        rollDay();
        String key = username.toLowerCase();
        String site = service == null || service.isBlank() ? "unknown" : service.toLowerCase();

        Set<String> already = paidToday.computeIfAbsent(key, k -> new LinkedHashSet<>());
        if (!already.add(site)) {
            plugin.getLogger().info("[Vote] Ignored a repeat vote from " + username + " on " + site + ".");
            return;
        }

        double amount = reward();
        Player online = plugin.getServer().getPlayerExact(username);
        if (online != null) {
            plugin.economy().deposit(online.getUniqueId(), amount);
            plugin.msg().send(online, "");
            plugin.msg().send(online, "<gradient:#f9d423:#ff4e50><bold>Thanks for voting!</bold></gradient>");
            plugin.msg().send(online, "<gray>Your vote was confirmed - here's <#f9d423>"
                    + plugin.msg().money(amount) + "</#f9d423>.");
            plugin.msg().send(online, "");
            announce(online.getName());
        } else {
            pending.merge(key, amount, Double::sum);
            plugin.getLogger().info("[Vote] " + username + " voted while offline - "
                    + amount + " saved for their return.");
        }
        save();
    }

    private void announce(String name) {
        if (!plugin.getConfig().getBoolean("voting.announce", true)) return;
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            plugin.msg().sendRaw(p, "<gray>" + name
                    + " voted for the server. <dark_gray>(/vote)</dark_gray>");
        }
    }

    /** Pay out anything earned while they were away. */
    public void deliverPending(Player player) {
        Double owed = pending.remove(player.getName().toLowerCase());
        if (owed == null || owed <= 0) return;
        plugin.economy().deposit(player.getUniqueId(), owed);
        save();
        plugin.msg().send(player, "<green>Welcome back! Your votes earned you <#f9d423>"
                + plugin.msg().money(owed) + "</#f9d423> while you were offline.");
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, Double> e : pending.entrySet()) {
            cfg.set("pending." + e.getKey(), e.getValue());
        }
        cfg.set("paid.date", paidDate);
        for (Map.Entry<String, Set<String>> e : paidToday.entrySet()) {
            cfg.set("paid.players." + e.getKey(), new ArrayList<>(e.getValue()));
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save votes.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection pend = cfg.getConfigurationSection("pending");
        if (pend != null) {
            for (String name : pend.getKeys(false)) pending.put(name, cfg.getDouble("pending." + name));
        }
        paidDate = cfg.getString("paid.date", "");
        ConfigurationSection paid = cfg.getConfigurationSection("paid.players");
        if (paid != null) {
            for (String name : paid.getKeys(false)) {
                paidToday.put(name, new LinkedHashSet<>(cfg.getStringList("paid.players." + name)));
            }
        }
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
        if (out.isEmpty()) out.addAll(DEFAULT_SITES);
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
            plugin.msg().send(player, "<gray>Each vote pays you <#f9d423>"
                    + plugin.msg().money(reward()) + "</#f9d423> and helps more players find us.");
            plugin.msg().send(player, "<click:run_command:'/vote'><hover:show_text:'Click to open the vote menu'>"
                    + "<#5ad1e8><u>Click here for the vote links</u></#5ad1e8></hover></click>");
            plugin.msg().send(player, bar);
        }
    }
}
