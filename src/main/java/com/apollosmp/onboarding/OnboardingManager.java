package com.apollosmp.onboarding;

import com.apollosmp.ApolloSMP;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A "Getting Started" checklist for new players. Each task teaches a core system
 * and pays a small reward; finishing them all pays a bonus. Completion is read from
 * live state (has a home, owns a business, in a town, ...) so nothing to track live.
 */
public class OnboardingManager {

    public record Task(String id, String title, String howTo, Material icon, double reward) {}

    private static final List<Task> TASKS = List.of(
            new Task("home", "Set your first home",
                    "Stand where you want and use /sethome.", Material.RED_BED, 250),
            new Task("ah", "List an item for sale",
                    "Open /ah and list an item on the auction house.", Material.GOLD_INGOT, 300),
            new Task("business", "Buy your first business",
                    "Use /invest to buy a passive-income business.", Material.EMERALD, 500),
            new Task("town", "Join or found a town",
                    "Use /town to create your own or join friends.", Material.OAK_DOOR, 500),
            new Task("rich", "Reach $5,000",
                    "Sell items at /sell and vote to grow your balance.", Material.GOLD_BLOCK, 1000),
            new Task("playtime", "Play for 30 minutes",
                    "Stick around and explore the server!", Material.CLOCK, 500)
    );

    private static final String BONUS_ID = "bonus";

    private final ApolloSMP plugin;
    private final File file;
    private final Map<UUID, Set<String>> claimed = new ConcurrentHashMap<>();

    public OnboardingManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "onboarding.yml");
        load();
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("onboarding.enabled", true);
    }

    public List<Task> tasks() {
        return TASKS;
    }

    public double bonusReward() {
        return plugin.getConfig().getDouble("onboarding.completion-bonus", 2000.0);
    }

    /** Whether the task's goal is currently met. */
    public boolean isComplete(String taskId, Player player) {
        UUID id = player.getUniqueId();
        return switch (taskId) {
            case "home" -> plugin.homes().count(id) >= 1;
            case "ah" -> plugin.auctions().countBySeller(id) >= 1;
            case "business" -> plugin.businesses().countOwnedBy(id) >= 1;
            case "town" -> plugin.towns().getTownOf(id) != null;
            case "rich" -> plugin.economy().getBalance(id) >= 5000;
            case "playtime" -> playtimeMinutes(player) >= 30;
            default -> false;
        };
    }

    private long playtimeMinutes(Player player) {
        try {
            return player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20L / 60L;
        } catch (Exception ex) {
            return 0;
        }
    }

    public boolean isClaimed(UUID id, String taskId) {
        Set<String> set = claimed.get(id);
        return set != null && set.contains(taskId);
    }

    /** Claim a finished task's reward. Returns true if a reward was paid. */
    public boolean claim(Player player, String taskId) {
        if (!enabled()) return false;
        Task task = byId(taskId);
        if (task == null) return false;
        UUID id = player.getUniqueId();
        if (isClaimed(id, taskId)) return false;
        if (!isComplete(taskId, player)) return false;

        mark(id, taskId);
        plugin.economy().deposit(id, task.reward());
        return true;
    }

    public boolean allTasksClaimed(UUID id) {
        for (Task t : TASKS) if (!isClaimed(id, t.id())) return false;
        return true;
    }

    public boolean bonusClaimed(UUID id) {
        return isClaimed(id, BONUS_ID);
    }

    /** Claim the all-done bonus. Returns true if paid. */
    public boolean claimBonus(Player player) {
        if (!enabled()) return false;
        UUID id = player.getUniqueId();
        if (bonusClaimed(id) || !allTasksClaimed(id)) return false;
        mark(id, BONUS_ID);
        plugin.economy().deposit(id, bonusReward());
        return true;
    }

    private Task byId(String taskId) {
        for (Task t : TASKS) if (t.id().equals(taskId)) return t;
        return null;
    }

    private void mark(UUID id, String taskId) {
        claimed.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet()).add(taskId);
        save();
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> e : claimed.entrySet()) {
            cfg.set("claimed." + e.getKey(), new java.util.ArrayList<>(e.getValue()));
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save onboarding.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("claimed");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                Set<String> set = ConcurrentHashMap.newKeySet();
                set.addAll(cfg.getStringList("claimed." + key));
                claimed.put(UUID.fromString(key), set);
            } catch (IllegalArgumentException ignored) {
                // skip malformed entry
            }
        }
    }
}
