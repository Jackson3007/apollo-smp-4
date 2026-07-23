package com.apollosmp.vote;

import com.apollosmp.ApolloSMP;
import com.apollosmp.util.Items;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Vote links, per-day claim tracking, and Sky Coin / key rewards. */
public class VoteManager {

    public record Service(String name, String link) {}

    public enum ClaimResult { GRANTED, GRANTED_ALL_BONUS, ALREADY_CLAIMED, NO_SERVICE }

    private static final class DayClaims {
        String date;
        final Set<String> claimed = new LinkedHashSet<>();
    }

    private final ApolloSMP plugin;
    private final File file;
    private final Map<UUID, DayClaims> claims = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public VoteManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "votes.yml");
        load();
    }

    public int coinsPerVote() { return plugin.getConfig().getInt("voting.coins-per-vote", 1); }
    public int allVotedBonus() { return plugin.getConfig().getInt("voting.all-voted-bonus", 5); }
    public boolean giveKey() { return plugin.getConfig().getBoolean("voting.give-key-per-vote", true); }

    public List<Service> services() {
        List<Service> out = new ArrayList<>();
        List<Map<?, ?>> raw = plugin.getConfig().getMapList("voting.services");
        for (Map<?, ?> m : raw) {
            Object name = m.get("name");
            Object link = m.get("link");
            if (name != null && link != null) out.add(new Service(name.toString(), link.toString()));
        }
        return out;
    }

    public Set<String> claimedToday(UUID id) {
        DayClaims c = claims.get(id);
        if (c == null || !today().equals(c.date)) return new LinkedHashSet<>();
        return new LinkedHashSet<>(c.claimed);
    }

    /** Record a vote/claim for a service and pay the reward. */
    public ClaimResult claim(Player player, String serviceName) {
        boolean valid = services().stream().anyMatch(s -> s.name().equalsIgnoreCase(serviceName));
        if (!valid) return ClaimResult.NO_SERVICE;

        DayClaims c = claims.computeIfAbsent(player.getUniqueId(), k -> new DayClaims());
        if (!today().equals(c.date)) {
            c.date = today();
            c.claimed.clear();
        }
        if (c.claimed.stream().anyMatch(s -> s.equalsIgnoreCase(serviceName))) {
            return ClaimResult.ALREADY_CLAIMED;
        }
        c.claimed.add(serviceName);

        plugin.skyCoins().add(player.getUniqueId(), coinsPerVote());
        if (giveKey()) Items.give(player, plugin.customItems().voteKey());

        dirty = true;
        save();

        if (c.claimed.size() >= services().size() && !services().isEmpty()) {
            plugin.skyCoins().add(player.getUniqueId(), allVotedBonus());
            return ClaimResult.GRANTED_ALL_BONUS;
        }
        return ClaimResult.GRANTED;
    }

    private String today() {
        return LocalDate.now().toString();
    }

    public void save() {
        if (!dirty) return;
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, DayClaims> e : claims.entrySet()) {
            DayClaims c = e.getValue();
            if (c.date == null) continue;
            cfg.set(e.getKey() + ".date", c.date);
            cfg.set(e.getKey() + ".claimed", new ArrayList<>(c.claimed));
        }
        try {
            cfg.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save votes.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String key : cfg.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                DayClaims c = new DayClaims();
                c.date = cfg.getString(key + ".date");
                c.claimed.addAll(cfg.getStringList(key + ".claimed"));
                claims.put(id, c);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
