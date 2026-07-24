package com.apollosmp.town;

import com.apollosmp.ApolloSMP;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Alliances between towns. One agreement covers everything: a shared chat
 * channel, free passage to each other's spawns, and mutual defence if either
 * side ends up in a war.
 */
public class DiplomacyManager {

    public record Offer(String from, long expires) {}

    private final ApolloSMP plugin;
    private final File file;

    /** pair key -> when it was signed */
    private final Map<String, Long> alliances = new ConcurrentHashMap<>();
    /** target town (lowercase) -> pending offer */
    private final Map<String, Offer> offers = new ConcurrentHashMap<>();

    public DiplomacyManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "alliances.yml");
        load();
    }

    private long offerTimeout() {
        return Math.max(1, plugin.getConfig().getLong("towns.diplomacy.offer-minutes", 10)) * 60_000L;
    }

    private int maxAllies() {
        return Math.max(1, plugin.getConfig().getInt("towns.diplomacy.max-allies", 3));
    }

    public static String key(String a, String b) {
        String first = a.toLowerCase();
        String second = b.toLowerCase();
        return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
    }

    // ---- state ----
    public boolean allied(String a, String b) {
        return a != null && b != null && alliances.containsKey(key(a, b));
    }

    public List<String> alliesOf(String town) {
        List<String> out = new ArrayList<>();
        if (town == null) return out;
        String lower = town.toLowerCase();
        for (String pair : alliances.keySet()) {
            String[] parts = pair.split("\\|");
            if (parts.length != 2) continue;
            if (parts[0].equals(lower)) out.add(properName(parts[1]));
            else if (parts[1].equals(lower)) out.add(properName(parts[0]));
        }
        return out;
    }

    private String properName(String lower) {
        Town town = plugin.towns().townByName(lower);
        return town == null ? lower : town.name();
    }

    public Offer offerFor(String town) {
        return town == null ? null : offers.get(town.toLowerCase());
    }

    // ---- proposing ----
    public boolean propose(Player player, String targetName) {
        Town own = plugin.towns().getTownOf(player.getUniqueId());
        if (own == null) { plugin.msg().send(player, "<red>You're not in a town."); return false; }
        if (!own.mayor().equals(player.getUniqueId())) {
            plugin.msg().send(player, "<red>Only the mayor can make alliances."); return false;
        }
        Town target = plugin.towns().townByName(targetName);
        if (target == null) {
            plugin.msg().send(player, "<red>There's no town called <white>" + targetName + "</white>.");
            return false;
        }
        if (target.name().equalsIgnoreCase(own.name())) {
            plugin.msg().send(player, "<red>You're already on your own side."); return false;
        }
        if (allied(own.name(), target.name())) {
            plugin.msg().send(player, "<yellow>You're already allied with them."); return false;
        }
        if (plugin.wars().atWar(own.name(), target.name())) {
            plugin.msg().send(player, "<red>Sign a peace treaty before you make friends."); return false;
        }
        if (alliesOf(own.name()).size() >= maxAllies()) {
            plugin.msg().send(player, "<red>Your town already has " + maxAllies() + " allies.");
            return false;
        }
        if (alliesOf(target.name()).size() >= maxAllies()) {
            plugin.msg().send(player, "<red>They've already got as many allies as they can hold.");
            return false;
        }

        offers.put(target.name().toLowerCase(),
                new Offer(own.name(), System.currentTimeMillis() + offerTimeout()));
        save();

        plugin.msg().send(player, "<green>Alliance offered to <white>" + target.name() + "</white>.");
        Player theirMayor = plugin.getServer().getPlayer(target.mayor());
        if (theirMayor != null) {
            plugin.msg().sendRaw(theirMayor, "");
            plugin.msg().sendRaw(theirMayor, "<#5ad1e8><bold>" + own.name()
                    + " proposes an alliance.</bold></#5ad1e8>");
            plugin.msg().sendRaw(theirMayor, "<gray>Shared chat, free passage, and they'll be");
            plugin.msg().sendRaw(theirMayor, "<gray>dragged into any war you fight.");
            plugin.msg().sendRaw(theirMayor, "<click:run_command:'/town ally accept " + own.name() + "'>"
                    + "<hover:show_text:'Sign the alliance'><green><u>Accept</u></green></hover></click>"
                    + " <dark_gray>|</dark_gray> "
                    + "<click:run_command:'/town ally decline'>"
                    + "<hover:show_text:'Refuse'><red><u>Decline</u></red></hover></click>");
            plugin.msg().sendRaw(theirMayor, "");
        }
        return true;
    }

    public boolean accept(Player player, String fromName) {
        Town own = plugin.towns().getTownOf(player.getUniqueId());
        if (own == null) { plugin.msg().send(player, "<red>You're not in a town."); return false; }
        if (!own.mayor().equals(player.getUniqueId())) {
            plugin.msg().send(player, "<red>Only the mayor can answer that."); return false;
        }
        Offer offer = offers.get(own.name().toLowerCase());
        if (offer == null || System.currentTimeMillis() > offer.expires()) {
            offers.remove(own.name().toLowerCase());
            plugin.msg().send(player, "<red>There's no offer waiting."); return false;
        }
        if (fromName != null && !fromName.equalsIgnoreCase(offer.from())) {
            plugin.msg().send(player, "<red>The offer is from <white>" + offer.from() + "</white>.");
            return false;
        }
        Town other = plugin.towns().townByName(offer.from());
        if (other == null) {
            offers.remove(own.name().toLowerCase());
            plugin.msg().send(player, "<red>That town no longer exists."); return false;
        }

        offers.remove(own.name().toLowerCase());
        alliances.put(key(own.name(), other.name()), System.currentTimeMillis());
        save();

        broadcast("<#5ad1e8><bold>ALLIANCE</bold></#5ad1e8> <white>" + other.name()
                + "</white> <gray>and</gray> <white>" + own.name()
                + "</white> <gray>have signed an alliance.</gray>");
        return true;
    }

    public boolean decline(Player player) {
        Town own = plugin.towns().getTownOf(player.getUniqueId());
        if (own == null) return false;
        Offer offer = offers.remove(own.name().toLowerCase());
        save();
        if (offer == null) {
            plugin.msg().send(player, "<gray>Nothing to decline.");
            return false;
        }
        plugin.msg().send(player, "<yellow>You turned down <white>" + offer.from() + "</white>.");
        return true;
    }

    public boolean breakAlliance(Player player, String otherName) {
        Town own = plugin.towns().getTownOf(player.getUniqueId());
        if (own == null) { plugin.msg().send(player, "<red>You're not in a town."); return false; }
        if (!own.mayor().equals(player.getUniqueId())) {
            plugin.msg().send(player, "<red>Only the mayor can break an alliance."); return false;
        }
        if (!allied(own.name(), otherName)) {
            plugin.msg().send(player, "<red>You aren't allied with them."); return false;
        }
        alliances.remove(key(own.name(), otherName));
        save();
        broadcast("<gray>The alliance between <white>" + own.name() + "</white> and <white>"
                + properName(otherName.toLowerCase()) + "</white> is over.</gray>");
        return true;
    }

    /** Clear everything involving a town that's been disbanded. */
    public void forgetTown(String town) {
        String lower = town.toLowerCase();
        alliances.keySet().removeIf(k -> {
            String[] parts = k.split("\\|");
            return parts.length == 2 && (parts[0].equals(lower) || parts[1].equals(lower));
        });
        offers.remove(lower);
        offers.entrySet().removeIf(e -> e.getValue().from().equalsIgnoreCase(town));
        save();
    }

    public void tick() {
        long now = System.currentTimeMillis();
        offers.entrySet().removeIf(e -> now > e.getValue().expires());
    }

    private void broadcast(String mini) {
        for (Player p : plugin.getServer().getOnlinePlayers()) plugin.msg().sendRaw(p, mini);
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, Long> e : alliances.entrySet()) {
            cfg.set("alliances." + e.getKey().replace("|", "__"), e.getValue());
        }
        for (Map.Entry<String, Offer> e : offers.entrySet()) {
            cfg.set("offers." + e.getKey() + ".from", e.getValue().from());
            cfg.set("offers." + e.getKey() + ".expires", e.getValue().expires());
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save alliances.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection allied = cfg.getConfigurationSection("alliances");
        if (allied != null) {
            for (String k : allied.getKeys(false)) {
                alliances.put(k.replace("__", "|"), cfg.getLong("alliances." + k));
            }
        }
        ConfigurationSection offerSection = cfg.getConfigurationSection("offers");
        if (offerSection != null) {
            for (String k : offerSection.getKeys(false)) {
                offers.put(k, new Offer(cfg.getString("offers." + k + ".from"),
                        cfg.getLong("offers." + k + ".expires")));
            }
        }
    }

    /** Everyone online in this town and its allies. */
    public Set<Player> allyAudience(Town town) {
        Set<Player> out = new LinkedHashSet<>();
        if (town == null) return out;
        List<String> names = new ArrayList<>(alliesOf(town.name()));
        names.add(town.name());
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            Town theirs = plugin.towns().getTownOf(p.getUniqueId());
            if (theirs == null) continue;
            for (String n : names) {
                if (theirs.name().equalsIgnoreCase(n)) { out.add(p); break; }
            }
        }
        return out;
    }
}
