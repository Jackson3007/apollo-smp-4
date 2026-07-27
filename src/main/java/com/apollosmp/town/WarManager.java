package com.apollosmp.town;

import com.apollosmp.ApolloSMP;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wars between towns. Both mayors have to agree, and every war has a fixed
 * length so nobody is stuck under siege indefinitely.
 */
public class WarManager {

    /** A declaration waiting on the other town's answer. */
    public record Declaration(String from, int minutes, long expires) {}

    /** How long a war may run for. */
    public static final int[] DURATIONS = {10, 30, 60, 120};

    private final ApolloSMP plugin;
    private final File file;

    /** war key -> war */
    private final Map<String, TownWar> wars = new ConcurrentHashMap<>();
    /** target town (lowercase) -> pending declaration */
    private final Map<String, Declaration> declarations = new ConcurrentHashMap<>();
    /** war key -> town that asked for peace */
    private final Map<String, String> peaceOffers = new ConcurrentHashMap<>();

    public WarManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "wars.yml");
        load();
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("towns.war.enabled", true);
    }

    private long declarationTimeout() {
        return Math.max(1, plugin.getConfig().getLong("towns.war.offer-minutes", 5)) * 60_000L;
    }

    // ---- state ----
    public boolean atWar(String townA, String townB) {
        if (townA == null || townB == null) return false;
        TownWar direct = wars.get(TownWar.key(townA, townB));
        if (direct != null && direct.millisLeft() > 0) return true;

        // Mutual defence: an ally of a combatant is a combatant.
        if (plugin.diplomacy() == null) return false;
        for (TownWar war : wars.values()) {
            if (war.millisLeft() <= 0) continue;
            boolean aLeft = onSide(townA, war.townA());
            boolean bRight = onSide(townB, war.townB());
            boolean aRight = onSide(townA, war.townB());
            boolean bLeft = onSide(townB, war.townA());
            if ((aLeft && bRight) || (aRight && bLeft)) return true;
        }
        return false;
    }

    /** Is this town the combatant, or one of its allies? */
    private boolean onSide(String town, String combatant) {
        if (town.equalsIgnoreCase(combatant)) return true;
        for (String ally : plugin.diplomacy().alliesOf(combatant)) {
            if (ally.equalsIgnoreCase(town)) return true;
        }
        return false;
    }

    /** Are these two players on opposite sides of a war? */
    public boolean atWar(Player a, Player b) {
        Town ta = plugin.towns().getTownOf(a.getUniqueId());
        Town tb = plugin.towns().getTownOf(b.getUniqueId());
        if (ta == null || tb == null) return false;
        return atWar(ta.name(), tb.name());
    }

    public List<TownWar> warsFor(String town) {
        List<TownWar> out = new ArrayList<>();
        for (TownWar war : wars.values()) {
            if (war.involves(town) && war.millisLeft() > 0) out.add(war);
        }
        return out;
    }

    public Declaration declarationFor(String town) {
        return town == null ? null : declarations.get(town.toLowerCase());
    }

    public String peaceOfferFor(String townA, String townB) {
        return peaceOffers.get(TownWar.key(townA, townB));
    }

    // ---- declaring ----
    public boolean declare(Player player, String targetName, int minutes) {
        if (!enabled()) { plugin.msg().send(player, "<red>Wars are disabled on this server."); return false; }

        Town own = plugin.towns().getTownOf(player.getUniqueId());
        if (own == null) { plugin.msg().send(player, "<red>You're not in a town."); return false; }
        if (!own.mayor().equals(player.getUniqueId())) {
            plugin.msg().send(player, "<red>Only the mayor can declare war."); return false;
        }
        Town target = plugin.towns().townByName(targetName);
        if (target == null) {
            plugin.msg().send(player, "<red>There's no town called <white>" + targetName + "</white>."); return false;
        }
        if (target.name().equalsIgnoreCase(own.name())) {
            plugin.msg().send(player, "<red>You can't declare war on yourself."); return false;
        }
        if (atWar(own.name(), target.name())) {
            plugin.msg().send(player, "<yellow>You're already at war with them."); return false;
        }
        if (plugin.diplomacy() != null && plugin.diplomacy().allied(own.name(), target.name())) {
            plugin.msg().send(player, "<red>They're your ally. Break the alliance first.");
            return false;
        }

        boolean valid = false;
        for (int d : DURATIONS) if (d == minutes) valid = true;
        if (!valid) {
            plugin.msg().send(player, "<red>Pick 10, 30, 60 or 120 minutes."); return false;
        }

        declarations.put(target.name().toLowerCase(),
                new Declaration(own.name(), minutes, System.currentTimeMillis() + declarationTimeout()));
        save();

        plugin.msg().send(player, "<yellow>War declared on <white>" + target.name()
                + "</white> for <white>" + minutes + " minutes</white>. Waiting for them to accept.");

        Player theirMayor = plugin.getServer().getPlayer(target.mayor());
        if (theirMayor != null) {
            plugin.msg().sendRaw(theirMayor, "");
            plugin.msg().sendRaw(theirMayor, "<red><bold>\u2694 " + own.name()
                    + " has declared war on " + target.name() + "!</bold></red>");
            plugin.msg().sendRaw(theirMayor, "<gray>Proposed length: <white>" + minutes + " minutes</white>");
            plugin.msg().sendRaw(theirMayor, "<click:run_command:'/town war accept " + own.name() + "'>"
                    + "<hover:show_text:'Accept the war'><green><u>Accept</u></green></hover></click>"
                    + " <dark_gray>|</dark_gray> "
                    + "<click:run_command:'/town war decline " + own.name() + "'>"
                    + "<hover:show_text:'Refuse'><red><u>Decline</u></red></hover></click>");
            plugin.msg().sendRaw(theirMayor, "");
        }
        return true;
    }

    public boolean accept(Player player, String attackerName) {
        Town own = plugin.towns().getTownOf(player.getUniqueId());
        if (own == null) { plugin.msg().send(player, "<red>You're not in a town."); return false; }
        if (!own.mayor().equals(player.getUniqueId())) {
            plugin.msg().send(player, "<red>Only the mayor can answer a declaration."); return false;
        }
        Declaration pending = declarations.get(own.name().toLowerCase());
        if (pending == null || System.currentTimeMillis() > pending.expires()) {
            declarations.remove(own.name().toLowerCase());
            plugin.msg().send(player, "<red>There's no declaration waiting."); return false;
        }
        if (attackerName != null && !attackerName.equalsIgnoreCase(pending.from())) {
            plugin.msg().send(player, "<red>The declaration is from <white>" + pending.from() + "</white>.");
            return false;
        }
        Town attacker = plugin.towns().townByName(pending.from());
        if (attacker == null) {
            declarations.remove(own.name().toLowerCase());
            plugin.msg().send(player, "<red>That town no longer exists."); return false;
        }

        declarations.remove(own.name().toLowerCase());
        long now = System.currentTimeMillis();
        TownWar war = new TownWar(attacker.name(), own.name(), now, now + pending.minutes() * 60_000L);
        wars.put(war.key(), war);
        save();

        broadcast("<red><bold>\u2694 WAR</bold></red> <white>" + attacker.name()
                + "</white> <gray>and</gray> <white>" + own.name()
                + "</white> <gray>are at war for <white>" + pending.minutes() + " minutes</white>!");
        broadcast("<dark_gray>Chests and businesses on enemy land are fair game. Watch your back.</dark_gray>");
        callAllies(attacker.name(), own.name());
        return true;
    }

    /** Tell everyone's allies they've been pulled in. */
    private void callAllies(String a, String b) {
        if (plugin.diplomacy() == null) return;
        announceAllies(a, b);
        announceAllies(b, a);
    }

    private void announceAllies(String town, String enemy) {
        List<String> allies = plugin.diplomacy().alliesOf(town);
        if (allies.isEmpty()) return;
        for (String ally : allies) {
            Town allyTown = plugin.towns().townByName(ally);
            if (allyTown == null) continue;
            for (UUID member : allyTown.members().keySet()) {
                Player p = plugin.getServer().getPlayer(member);
                if (p == null) continue;
                plugin.msg().send(p, "<red>Your ally <white>" + town
                        + "</white> is at war with <white>" + enemy
                        + "</white>. You're in it too.");
            }
        }
    }

    public boolean decline(Player player) {
        Town own = plugin.towns().getTownOf(player.getUniqueId());
        if (own == null) return false;
        Declaration pending = declarations.remove(own.name().toLowerCase());
        save();
        if (pending == null) {
            plugin.msg().send(player, "<gray>Nothing to decline.");
            return false;
        }
        plugin.msg().send(player, "<green>You refused the war with <white>" + pending.from() + "</white>.");
        Town attacker = plugin.towns().townByName(pending.from());
        if (attacker != null) {
            Player theirMayor = plugin.getServer().getPlayer(attacker.mayor());
            if (theirMayor != null) {
                plugin.msg().send(theirMayor, "<yellow>" + own.name() + " refused your declaration.");
            }
        }
        return true;
    }

    // ---- peace ----
    public boolean offerPeace(Player player, String enemyName) {
        Town own = plugin.towns().getTownOf(player.getUniqueId());
        if (own == null) { plugin.msg().send(player, "<red>You're not in a town."); return false; }
        if (!own.mayor().equals(player.getUniqueId())) {
            plugin.msg().send(player, "<red>Only the mayor can sue for peace."); return false;
        }
        List<TownWar> active = warsFor(own.name());
        if (active.isEmpty()) { plugin.msg().send(player, "<gray>You aren't at war."); return false; }

        TownWar war = null;
        if (enemyName == null && active.size() == 1) war = active.get(0);
        else {
            for (TownWar w : active) {
                if (enemyName != null && enemyName.equalsIgnoreCase(w.other(own.name()))) war = w;
            }
        }
        if (war == null) {
            plugin.msg().send(player, "<red>Name which town you want peace with."); return false;
        }

        String enemy = war.other(own.name());
        String existing = peaceOffers.get(war.key());
        if (existing != null && !existing.equalsIgnoreCase(own.name())) {
            // They already offered - this seals it.
            endWar(war, "<green><bold>PEACE</bold></green> <white>" + war.townA()
                    + "</white> <gray>and</gray> <white>" + war.townB()
                    + "</white> <gray>have signed a treaty.</gray>");
            return true;
        }

        peaceOffers.put(war.key(), own.name());
        save();
        plugin.msg().send(player, "<gray>Peace offered to <white>" + enemy
                + "</white>. They need to agree.");

        Town enemyTown = plugin.towns().townByName(enemy);
        if (enemyTown != null) {
            Player theirMayor = plugin.getServer().getPlayer(enemyTown.mayor());
            if (theirMayor != null) {
                plugin.msg().sendRaw(theirMayor, "<white>" + own.name()
                        + "</white> <gray>is offering peace. </gray>"
                        + "<click:run_command:'/town peace " + own.name() + "'>"
                        + "<hover:show_text:'Sign the treaty'><green><u>Accept</u></green></hover></click>");
            }
        }
        return true;
    }

    /** Called on a timer to expire wars and stale declarations. */
    public void tick() {
        long now = System.currentTimeMillis();
        for (TownWar war : new ArrayList<>(wars.values())) {
            if (war.millisLeft() > 0) continue;
            endWar(war, "<gray>The war between <white>" + war.townA() + "</white> and <white>"
                    + war.townB() + "</white> has run its course.</gray>");
        }
        for (Map.Entry<String, Declaration> e : new ArrayList<>(declarations.entrySet())) {
            if (now > e.getValue().expires()) {
                declarations.remove(e.getKey());
                save();
            }
        }
    }

    private void endWar(TownWar war, String message) {
        wars.remove(war.key());
        peaceOffers.remove(war.key());
        save();
        broadcast(message);
    }

    /** Wipe any wars involving a town that's just been disbanded. */
    public void forgetTown(String town) {
        for (TownWar war : new ArrayList<>(wars.values())) {
            if (war.involves(town)) {
                wars.remove(war.key());
                peaceOffers.remove(war.key());
            }
        }
        declarations.remove(town.toLowerCase());
        declarations.entrySet().removeIf(e -> e.getValue().from().equalsIgnoreCase(town));
        save();
    }

    private void broadcast(String mini) {
        for (Player p : plugin.getServer().getOnlinePlayers()) plugin.msg().sendRaw(p, mini);
    }

    public static String formatLeft(long millis) {
        long seconds = millis / 1000;
        if (seconds >= 3600) return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        if (seconds >= 60) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        int i = 0;
        for (TownWar war : wars.values()) {
            cfg.set("wars." + i + ".a", war.townA());
            cfg.set("wars." + i + ".b", war.townB());
            cfg.set("wars." + i + ".started", war.startedAt());
            cfg.set("wars." + i + ".ends", war.endsAt());
            i++;
        }
        for (Map.Entry<String, Declaration> e : declarations.entrySet()) {
            cfg.set("declarations." + e.getKey() + ".from", e.getValue().from());
            cfg.set("declarations." + e.getKey() + ".minutes", e.getValue().minutes());
            cfg.set("declarations." + e.getKey() + ".expires", e.getValue().expires());
        }
        for (Map.Entry<String, String> e : peaceOffers.entrySet()) {
            cfg.set("peace." + e.getKey().replace("|", "__"), e.getValue());
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save wars.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection warSection = cfg.getConfigurationSection("wars");
        if (warSection != null) {
            for (String key : warSection.getKeys(false)) {
                String base = "wars." + key;
                String a = cfg.getString(base + ".a");
                String b = cfg.getString(base + ".b");
                if (a == null || b == null) continue;
                TownWar war = new TownWar(a, b, cfg.getLong(base + ".started"), cfg.getLong(base + ".ends"));
                if (war.millisLeft() > 0) wars.put(war.key(), war);
            }
        }
        ConfigurationSection decl = cfg.getConfigurationSection("declarations");
        if (decl != null) {
            for (String key : decl.getKeys(false)) {
                String base = "declarations." + key;
                declarations.put(key, new Declaration(cfg.getString(base + ".from"),
                        cfg.getInt(base + ".minutes"), cfg.getLong(base + ".expires")));
            }
        }
        ConfigurationSection peace = cfg.getConfigurationSection("peace");
        if (peace != null) {
            for (String key : peace.getKeys(false)) {
                peaceOffers.put(key.replace("__", "|"), cfg.getString("peace." + key));
            }
        }
    }
}
