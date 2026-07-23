package com.apollosmp.town;

import com.apollosmp.ApolloSMP;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TownManager {

    private final ApolloSMP plugin;
    private final File file;

    private final Map<String, Town> towns = new ConcurrentHashMap<>();          // lower-name -> town
    private final Map<String, String> chunkIndex = new ConcurrentHashMap<>();   // chunkKey -> lower-name
    private final Map<UUID, String> playerTown = new ConcurrentHashMap<>();     // uuid -> lower-name
    private final Map<UUID, Set<String>> invites = new ConcurrentHashMap<>();   // uuid -> town names
    private volatile boolean dirty = false;

    public TownManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "towns.yml");
        load();
    }

    // ---- config ----
    private double createCost() { return plugin.getConfig().getDouble("towns.create-cost", 1000.0); }
    private double claimCost() { return plugin.getConfig().getDouble("towns.claim-cost", 500.0); }
    private int claimsBase() { return plugin.getConfig().getInt("towns.claims-base", 6); }
    private int claimsPerMember() { return plugin.getConfig().getInt("towns.claims-per-member", 4); }

    private int maxClaims(Town town) {
        return claimsBase() + town.memberCount() * claimsPerMember()
                + town.upgradeLevel(TownUpgrade.CLAIM_LIMIT) * 2;
    }

    /** Public so menus can show the same number. */
    public int claimLimit(Town town) { return maxClaims(town); }

    public double upgradeCost(Town town, TownUpgrade upgrade) {
        double multiplier = plugin.getConfig().getDouble("towns.upgrade-cost-multiplier", 1.0);
        return upgrade.costFor(town.upgradeLevel(upgrade)) * Math.max(0.1, multiplier);
    }

    /** Buy the next level of an upgrade using the town bank. */
    public boolean buyUpgrade(Player player, TownUpgrade upgrade) {
        Town town = getTownOf(player.getUniqueId());
        if (town == null) { plugin.msg().send(player, "<red>You're not in a town."); return false; }
        if (!town.hasPerm(player.getUniqueId(), TownPerm.WITHDRAW)) {
            plugin.msg().send(player, "<red>You don't have permission to spend the town bank.");
            return false;
        }
        int level = town.upgradeLevel(upgrade);
        if (level >= upgrade.maxLevel()) {
            plugin.msg().send(player, "<yellow>" + upgrade.display() + " is already maxed out.");
            return false;
        }
        double cost = upgradeCost(town, upgrade);
        if (!town.withdrawBank(cost)) {
            plugin.msg().send(player, "<red>The town bank needs " + plugin.msg().money(cost)
                    + " for that. It has " + plugin.msg().money(town.bank()) + ".");
            return false;
        }
        town.setUpgradeLevel(upgrade, level + 1);
        touch();

        plugin.msg().send(player, "<green>" + upgrade.display() + " upgraded to level <white>"
                + (level + 1) + "</white> for " + plugin.msg().money(cost) + ".");
        for (UUID member : town.members().keySet()) {
            if (member.equals(player.getUniqueId())) continue;
            Player other = plugin.getServer().getPlayer(member);
            if (other != null) {
                plugin.msg().send(other, "<#f9d423>" + town.name() + "</#f9d423> <gray>upgraded <white>"
                        + upgrade.display() + "</white> to level <white>" + (level + 1) + "</white>.");
            }
        }
        return true;
    }

    /** Give residents their town's perks while they stand on its land. */
    public void applyUpgradeEffects() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Town town = getTownAtLoc(player.getLocation());
            if (town == null || !town.isMember(player.getUniqueId())) continue;
            for (TownUpgrade upgrade : TownUpgrade.values()) {
                int level = town.upgradeLevel(upgrade);
                if (level <= 0) continue;
                org.bukkit.potion.PotionEffectType type = upgrade.effect();
                if (type == null) continue;
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        type, 120, level - 1, true, false, true));
            }
        }
    }

    // ---- keys ----
    public static String chunkKey(World world, int cx, int cz) { return world.getName() + "," + cx + "," + cz; }
    public static String chunkKey(Chunk c) { return chunkKey(c.getWorld(), c.getX(), c.getZ()); }
    public static String chunkKey(Location loc) {
        return loc.getWorld().getName() + "," + (loc.getBlockX() >> 4) + "," + (loc.getBlockZ() >> 4);
    }

    // ---- lookups ----
    public Town townByName(String name) { return name == null ? null : towns.get(name.toLowerCase()); }
    public Town getTownOf(UUID id) {
        String n = playerTown.get(id);
        return n == null ? null : towns.get(n);
    }
    public Town getTownAt(String chunkKey) {
        String n = chunkIndex.get(chunkKey);
        return n == null ? null : towns.get(n);
    }
    public Town getTownAtLoc(Location loc) { return getTownAt(chunkKey(loc)); }
    public List<Town> allTowns() { return new ArrayList<>(towns.values()); }
    public Set<String> pendingInvites(UUID id) { return invites.getOrDefault(id, new LinkedHashSet<>()); }

    // ---- creation ----
    public boolean createTown(Player player, String rawName) {
        if (getTownOf(player.getUniqueId()) != null) {
            plugin.msg().send(player, "<red>You're already in a town. Leave it first."); return false;
        }
        String name = rawName == null ? "" : rawName.trim();
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            plugin.msg().send(player, "<red>Town names must be 3-16 letters, numbers, or underscores."); return false;
        }
        if (towns.containsKey(name.toLowerCase())) {
            plugin.msg().send(player, "<red>A town with that name already exists."); return false;
        }
        String here = chunkKey(player.getLocation());
        if (chunkIndex.containsKey(here)) {
            plugin.msg().send(player, "<red>This chunk is already claimed by another town."); return false;
        }
        if (!plugin.economy().has(player.getUniqueId(), createCost())) {
            plugin.msg().send(player, "<red>You need " + plugin.msg().money(createCost()) + " to found a town."); return false;
        }
        plugin.economy().withdraw(player.getUniqueId(), createCost());

        Town town = new Town(name, player.getUniqueId(), System.currentTimeMillis());
        town.setSpawn(player.getLocation());
        town.addClaim(here);
        towns.put(name.toLowerCase(), town);
        chunkIndex.put(here, name.toLowerCase());
        playerTown.put(player.getUniqueId(), name.toLowerCase());
        touch();
        plugin.msg().send(player, "<green>Town <#f9d423>" + name + "</#f9d423> founded! This chunk is now claimed.");
        return true;
    }

    public boolean disband(Player player) {
        Town town = getTownOf(player.getUniqueId());
        if (town == null) { plugin.msg().send(player, "<red>You're not in a town."); return false; }
        if (!town.mayor().equals(player.getUniqueId())) {
            plugin.msg().send(player, "<red>Only the mayor can disband the town."); return false;
        }
        for (String ck : new ArrayList<>(town.claims())) chunkIndex.remove(ck);
        for (UUID m : new ArrayList<>(town.members().keySet())) playerTown.remove(m);
        towns.remove(town.name().toLowerCase());
        touch();
        plugin.msg().send(player, "<yellow>Town <white>" + town.name() + "</white> has been disbanded.");
        return true;
    }

    /**
     * Pick the whole town up and drop it where the mayor is standing.
     * Members, ranks and the bank survive; land and plots are released.
     */
    public boolean moveTown(Player player) {
        Town town = getTownOf(player.getUniqueId());
        if (town == null) { plugin.msg().send(player, "<red>You're not in a town."); return false; }
        if (!town.mayor().equals(player.getUniqueId())) {
            plugin.msg().send(player, "<red>Only the mayor can move the town."); return false;
        }
        String here = chunkKey(player.getLocation());
        Town at = getTownAt(here);
        if (at != null && !at.name().equalsIgnoreCase(town.name())) {
            plugin.msg().send(player, "<red>That chunk belongs to <white>" + at.name() + "</white>.");
            return false;
        }
        double cost = plugin.getConfig().getDouble("towns.move-cost", 0.0);
        if (cost > 0 && !plugin.economy().has(player.getUniqueId(), cost)) {
            plugin.msg().send(player, "<red>Moving the town costs " + plugin.msg().money(cost) + ".");
            return false;
        }
        if (cost > 0) plugin.economy().withdraw(player.getUniqueId(), cost);

        int released = town.claims().size();
        for (String ck : new ArrayList<>(town.claims())) chunkIndex.remove(ck);
        town.claims().clear();
        town.plotOwners().clear();
        town.plotSale().clear();

        town.addClaim(here);
        chunkIndex.put(here, town.name().toLowerCase());
        town.setSpawn(player.getLocation());
        touch();

        plugin.msg().send(player, "<green><white>" + town.name() + "</white> has moved here. <gray>"
                + released + " old chunk(s) released; this chunk is now your town centre.");
        for (UUID member : town.members().keySet()) {
            if (member.equals(player.getUniqueId())) continue;
            Player other = plugin.getServer().getPlayer(member);
            if (other != null) {
                plugin.msg().send(other, "<#f9d423>" + town.name()
                        + " has relocated. <gray>Use <white>/town spawn</white> to find the new centre.");
            }
        }
        return true;
    }

    // ---- claiming ----
    public boolean claimHere(Player player) {
        Town town = getTownOf(player.getUniqueId());
        if (town == null) { plugin.msg().send(player, "<red>You're not in a town."); return false; }
        if (!town.hasPerm(player.getUniqueId(), TownPerm.CLAIM)) {
            plugin.msg().send(player, "<red>You don't have permission to claim land."); return false;
        }
        String here = chunkKey(player.getLocation());
        if (town.hasClaim(here)) { plugin.msg().send(player, "<yellow>Your town already owns this chunk."); return false; }
        if (chunkIndex.containsKey(here)) { plugin.msg().send(player, "<red>Another town already owns this chunk."); return false; }
        if (town.claims().size() >= maxClaims(town)) {
            plugin.msg().send(player, "<red>Your town is at its claim limit (" + maxClaims(town)
                    + "). Invite more residents to expand."); return false;
        }
        if (!isAdjacentToTown(town, here)) {
            plugin.msg().send(player, "<red>New claims must touch your existing town land."); return false;
        }
        if (!plugin.economy().has(player.getUniqueId(), claimCost())) {
            plugin.msg().send(player, "<red>You need " + plugin.msg().money(claimCost()) + " to claim a chunk."); return false;
        }
        plugin.economy().withdraw(player.getUniqueId(), claimCost());
        town.addClaim(here);
        chunkIndex.put(here, town.name().toLowerCase());
        touch();
        plugin.msg().send(player, "<green>Chunk claimed for " + plugin.msg().money(claimCost())
                + ". Town land: <white>" + town.claims().size() + "/" + maxClaims(town) + "</white>.");
        return true;
    }

    public boolean unclaimHere(Player player) {
        Town town = getTownOf(player.getUniqueId());
        if (town == null) { plugin.msg().send(player, "<red>You're not in a town."); return false; }
        if (!town.hasPerm(player.getUniqueId(), TownPerm.CLAIM)) {
            plugin.msg().send(player, "<red>You don't have permission to unclaim land."); return false;
        }
        String here = chunkKey(player.getLocation());
        if (!town.hasClaim(here)) { plugin.msg().send(player, "<red>Your town doesn't own this chunk."); return false; }
        if (town.spawn() != null && here.equals(chunkKey(town.spawn()))) {
            plugin.msg().send(player, "<red>You can't unclaim the town's spawn chunk."); return false;
        }
        town.removeClaim(here);
        chunkIndex.remove(here);
        touch();
        plugin.msg().send(player, "<yellow>Chunk unclaimed.");
        return true;
    }

    private boolean isAdjacentToTown(Town town, String chunkKey) {
        String[] p = chunkKey.split(",");
        if (p.length != 3) return false;
        String w = p[0];
        int cx = Integer.parseInt(p[1]);
        int cz = Integer.parseInt(p[2]);
        return town.hasClaim(w + "," + (cx + 1) + "," + cz)
                || town.hasClaim(w + "," + (cx - 1) + "," + cz)
                || town.hasClaim(w + "," + cx + "," + (cz + 1))
                || town.hasClaim(w + "," + cx + "," + (cz - 1));
    }

    // ---- invites & membership ----
    public boolean invite(Player actor, String targetName) {
        Town town = getTownOf(actor.getUniqueId());
        if (town == null) { plugin.msg().send(actor, "<red>You're not in a town."); return false; }
        if (!town.hasPerm(actor.getUniqueId(), TownPerm.INVITE)) {
            plugin.msg().send(actor, "<red>You don't have permission to invite."); return false;
        }
        Player target = plugin.getServer().getPlayerExact(targetName);
        if (target == null) { plugin.msg().send(actor, "<red>That player isn't online."); return false; }
        if (getTownOf(target.getUniqueId()) != null) {
            plugin.msg().send(actor, "<red>That player is already in a town."); return false;
        }
        invites.computeIfAbsent(target.getUniqueId(), k -> new LinkedHashSet<>()).add(town.name());
        plugin.msg().send(actor, "<green>Invited <white>" + target.getName() + "</white> to " + town.name() + ".");
        plugin.msg().send(target, "<#f9d423>You've been invited to join <white>" + town.name()
                + "</white>! Open <white>/town</white> to accept.");
        return true;
    }

    public boolean acceptInvite(Player player, String townName) {
        if (getTownOf(player.getUniqueId()) != null) {
            plugin.msg().send(player, "<red>Leave your current town first."); return false;
        }
        Set<String> inv = invites.get(player.getUniqueId());
        Town town = townByName(townName);
        if (inv == null || town == null || inv.stream().noneMatch(n -> n.equalsIgnoreCase(townName))) {
            plugin.msg().send(player, "<red>You don't have an invite from that town."); return false;
        }
        inv.removeIf(n -> n.equalsIgnoreCase(townName));
        town.addMember(player.getUniqueId(), TownRank.RESIDENT);
        playerTown.put(player.getUniqueId(), town.name().toLowerCase());
        touch();
        plugin.msg().send(player, "<green>Welcome to <#f9d423>" + town.name() + "</#f9d423>!");
        return true;
    }

    public boolean leave(Player player) {
        Town town = getTownOf(player.getUniqueId());
        if (town == null) { plugin.msg().send(player, "<red>You're not in a town."); return false; }
        if (town.mayor().equals(player.getUniqueId())) {
            plugin.msg().send(player, "<red>The mayor can't leave — disband or transfer the town instead."); return false;
        }
        town.removeMember(player.getUniqueId());
        playerTown.remove(player.getUniqueId());
        touch();
        plugin.msg().send(player, "<yellow>You left " + town.name() + ".");
        return true;
    }

    public boolean kick(Player actor, UUID target) {
        Town town = getTownOf(actor.getUniqueId());
        if (town == null) return false;
        if (!town.hasPerm(actor.getUniqueId(), TownPerm.KICK)) {
            plugin.msg().send(actor, "<red>You don't have permission to kick."); return false;
        }
        if (target.equals(town.mayor())) { plugin.msg().send(actor, "<red>You can't kick the mayor."); return false; }
        TownRank actorRank = town.rankOf(actor.getUniqueId());
        TownRank targetRank = town.rankOf(target);
        if (targetRank == null) return false;
        if (actorRank != null && targetRank.ordinal() <= actorRank.ordinal() && !actor.getUniqueId().equals(town.mayor())) {
            plugin.msg().send(actor, "<red>You can only kick lower-ranked residents."); return false;
        }
        town.removeMember(target);
        playerTown.remove(target);
        touch();
        plugin.msg().send(actor, "<yellow>Resident removed from the town.");
        Player t = plugin.getServer().getPlayer(target);
        if (t != null) plugin.msg().send(t, "<red>You were removed from " + town.name() + ".");
        return true;
    }

    public boolean setRank(Player actor, UUID target, TownRank rank) {
        Town town = getTownOf(actor.getUniqueId());
        if (town == null) return false;
        if (!town.hasPerm(actor.getUniqueId(), TownPerm.SET_RANK)) {
            plugin.msg().send(actor, "<red>You don't have permission to set ranks."); return false;
        }
        if (rank == TownRank.MAYOR) { plugin.msg().send(actor, "<red>Use transfer to change the mayor."); return false; }
        if (target.equals(town.mayor())) { plugin.msg().send(actor, "<red>You can't change the mayor's rank."); return false; }
        if (!town.isMember(target)) return false;
        TownRank actorRank = town.rankOf(actor.getUniqueId());
        if (actorRank != null && rank.ordinal() < actorRank.ordinal() && !actor.getUniqueId().equals(town.mayor())) {
            plugin.msg().send(actor, "<red>You can't promote someone above your own rank."); return false;
        }
        town.setRank(target, rank);
        touch();
        plugin.msg().send(actor, "<green>Rank updated to <white>" + rank.display() + "</white>.");
        return true;
    }

    // ---- bank ----
    public boolean deposit(Player player, double amount) {
        Town town = getTownOf(player.getUniqueId());
        if (town == null || amount <= 0) return false;
        if (!plugin.economy().has(player.getUniqueId(), amount)) {
            plugin.msg().send(player, "<red>You don't have that much."); return false;
        }
        plugin.economy().withdraw(player.getUniqueId(), amount);
        town.depositBank(amount);
        touch();
        plugin.msg().send(player, "<green>Deposited " + plugin.msg().money(amount)
                + ". Town bank: <#f9d423>" + plugin.msg().money(town.bank()) + "</#f9d423>.");
        return true;
    }

    public boolean withdraw(Player player, double amount) {
        Town town = getTownOf(player.getUniqueId());
        if (town == null || amount <= 0) return false;
        if (!town.hasPerm(player.getUniqueId(), TownPerm.WITHDRAW)) {
            plugin.msg().send(player, "<red>You don't have permission to withdraw."); return false;
        }
        if (!town.withdrawBank(amount)) { plugin.msg().send(player, "<red>The town bank doesn't have that much."); return false; }
        plugin.economy().deposit(player.getUniqueId(), amount);
        touch();
        plugin.msg().send(player, "<green>Withdrew " + plugin.msg().money(amount)
                + ". Town bank: <#f9d423>" + plugin.msg().money(town.bank()) + "</#f9d423>.");
        return true;
    }

    public boolean setTax(Player player, double amount) {
        Town town = getTownOf(player.getUniqueId());
        if (town == null || amount < 0) return false;
        if (!town.hasPerm(player.getUniqueId(), TownPerm.SET_TAX)) {
            plugin.msg().send(player, "<red>You don't have permission to set taxes."); return false;
        }
        town.setTax(amount);
        touch();
        plugin.msg().send(player, "<green>Daily tax set to " + plugin.msg().money(amount) + " per resident.");
        return true;
    }

    public boolean togglePerm(Player actor, TownRank rank, TownPerm perm) {
        Town town = getTownOf(actor.getUniqueId());
        if (town == null) return false;
        if (!town.hasPerm(actor.getUniqueId(), TownPerm.MANAGE_PERMS)) {
            plugin.msg().send(actor, "<red>You don't have permission to manage permissions."); return false;
        }
        if (rank == TownRank.MAYOR) return false;
        town.togglePerm(rank, perm);
        touch();
        return true;
    }

    // ---- spawn ----
    public boolean setSpawnHere(Player player) {
        Town town = getTownOf(player.getUniqueId());
        if (town == null) return false;
        if (!town.hasPerm(player.getUniqueId(), TownPerm.SET_SPAWN)) {
            plugin.msg().send(player, "<red>You don't have permission to set spawn."); return false;
        }
        if (!town.hasClaim(chunkKey(player.getLocation()))) {
            plugin.msg().send(player, "<red>Stand on your own town land to set spawn."); return false;
        }
        town.setSpawn(player.getLocation());
        touch();
        plugin.msg().send(player, "<green>Town spawn set here.");
        return true;
    }

    /** Let the town decide whether outsiders may teleport to its spawn. */
    public boolean setPublicSpawn(Player actor, boolean allow) {
        Town town = getTownOf(actor.getUniqueId());
        if (town == null) { plugin.msg().send(actor, "<red>You're not in a town."); return false; }
        if (!town.hasPerm(actor.getUniqueId(), TownPerm.SET_SPAWN)) {
            plugin.msg().send(actor, "<red>You don't have permission to change this."); return false;
        }
        town.setPublicSpawn(allow);
        touch();
        plugin.msg().send(actor, allow
                ? "<green>Visitors can now teleport to your town spawn."
                : "<yellow>Only residents can teleport to your town spawn now.");
        return true;
    }

    /** Teleport to any town's spawn by name. */
    public boolean teleportToTown(Player player, String townName) {
        Town town = townByName(townName);
        if (town == null) {
            plugin.msg().send(player, "<red>There's no town called <white>" + townName + "</white>.");
            return false;
        }
        if (town.spawn() == null) {
            plugin.msg().send(player, "<red>That town hasn't set a spawn yet.");
            return false;
        }
        if (!town.isMember(player.getUniqueId())) {
            if (!plugin.getConfig().getBoolean("towns.public-spawn-teleport", true)) {
                plugin.msg().send(player, "<red>Teleporting to towns is disabled on this server.");
                return false;
            }
            if (!town.publicSpawn()) {
                plugin.msg().send(player, "<red><white>" + town.name()
                        + "</white> doesn't allow visitors to teleport in.");
                return false;
            }
        }
        player.teleport(town.spawn());
        plugin.msg().send(player, "<green>Teleported to <#f9d423>" + town.name() + "</#f9d423>.");
        return true;
    }

    public boolean teleportSpawn(Player player) {
        Town town = getTownOf(player.getUniqueId());
        if (town == null) { plugin.msg().send(player, "<red>You're not in a town."); return false; }
        if (town.spawn() == null) { plugin.msg().send(player, "<red>Your town has no spawn set."); return false; }
        player.teleport(town.spawn());
        plugin.msg().send(player, "<green>Teleported to town spawn.");
        return true;
    }

    // ---- plots ----
    public boolean sellPlotHere(Player player, double price) {
        Town town = getTownOf(player.getUniqueId());
        if (town == null) return false;
        if (!town.hasPerm(player.getUniqueId(), TownPerm.SELL_PLOT)) {
            plugin.msg().send(player, "<red>You don't have permission to sell plots."); return false;
        }
        String here = chunkKey(player.getLocation());
        if (!town.hasClaim(here)) { plugin.msg().send(player, "<red>Your town doesn't own this chunk."); return false; }
        if (price < 0) price = 0;
        town.setForSale(here, price);
        touch();
        plugin.msg().send(player, "<green>This plot is for sale at " + plugin.msg().money(price)
                + ". A resident can buy it from the town menu while standing here.");
        return true;
    }

    private long rentPeriodMillis() {
        return Math.max(1L, plugin.getConfig().getLong("towns.rent-period-hours", 24)) * 3600_000L;
    }

    public String rentPeriodLabel() {
        long hours = Math.max(1L, plugin.getConfig().getLong("towns.rent-period-hours", 24));
        if (hours % 24 == 0) {
            long days = hours / 24;
            return days == 1 ? "day" : days + " days";
        }
        return hours == 1 ? "hour" : hours + " hours";
    }

    /** List the chunk you're standing in as a rental. */
    public boolean rentOutPlotHere(Player player, double price) {
        Town town = getTownOf(player.getUniqueId());
        if (town == null) { plugin.msg().send(player, "<red>You're not in a town."); return false; }
        if (!town.hasPerm(player.getUniqueId(), TownPerm.SELL_PLOT)) {
            plugin.msg().send(player, "<red>You don't have permission to list plots."); return false;
        }
        String here = chunkKey(player.getLocation());
        if (!town.hasClaim(here)) { plugin.msg().send(player, "<red>Your town doesn't own this chunk."); return false; }
        if (price < 0) price = 0;
        town.setForRent(here, price);
        touch();
        plugin.msg().send(player, "<green>This plot is up for rent at " + plugin.msg().money(price)
                + " per " + rentPeriodLabel() + ".");
        return true;
    }

    /** Take out a tenancy on the plot you're standing in. */
    public boolean rentPlotHere(Player player) {
        String here = chunkKey(player.getLocation());
        Town town = getTownAt(here);
        if (town == null) { plugin.msg().send(player, "<red>This land isn't part of a town."); return false; }
        if (!town.isMember(player.getUniqueId())) {
            plugin.msg().send(player, "<red>Only residents can rent plots in this town."); return false;
        }
        Double rent = town.rentPrice(here);
        if (rent == null) { plugin.msg().send(player, "<red>This plot isn't for rent."); return false; }
        if (town.plotOwner(here) != null) {
            plugin.msg().send(player, "<red>Someone is already renting this plot."); return false;
        }
        if (!plugin.economy().has(player.getUniqueId(), rent)) {
            plugin.msg().send(player, "<red>You need " + plugin.msg().money(rent)
                    + " for the first " + rentPeriodLabel() + "."); return false;
        }
        plugin.economy().withdraw(player.getUniqueId(), rent);
        town.depositBank(rent);
        town.setPlotOwner(here, player.getUniqueId());
        town.setRentDue(here, System.currentTimeMillis() + rentPeriodMillis());
        touch();
        plugin.msg().send(player, "<green>Rented! You paid " + plugin.msg().money(rent)
                + ". Next payment in one " + rentPeriodLabel() + ".");
        return true;
    }

    /** Give up a rental you hold. */
    public boolean endRentHere(Player player) {
        String here = chunkKey(player.getLocation());
        Town town = getTownAt(here);
        if (town == null) return false;
        if (!player.getUniqueId().equals(town.plotOwner(here))) {
            plugin.msg().send(player, "<red>You aren't renting this plot."); return false;
        }
        if (town.rentPrice(here) == null) {
            plugin.msg().send(player, "<red>You own this plot outright - it isn't a rental."); return false;
        }
        town.setPlotOwner(here, null);
        town.rentDue().remove(here);
        touch();
        plugin.msg().send(player, "<yellow>You've given up the tenancy. No more rent is due.");
        return true;
    }

    /** Take a plot off the market. */
    public boolean unlistPlotHere(Player player) {
        Town town = getTownOf(player.getUniqueId());
        if (town == null) { plugin.msg().send(player, "<red>You're not in a town."); return false; }
        if (!town.hasPerm(player.getUniqueId(), TownPerm.SELL_PLOT)) {
            plugin.msg().send(player, "<red>You don't have permission to change listings."); return false;
        }
        String here = chunkKey(player.getLocation());
        if (!town.isListed(here)) { plugin.msg().send(player, "<gray>This plot isn't listed."); return false; }
        town.clearListing(here);
        touch();
        plugin.msg().send(player, "<yellow>Listing removed.");
        return true;
    }

    /** Charge tenants when their period is up. Evicts anyone who can't pay. */
    public void collectRent() {
        boolean changed = false;
        long now = System.currentTimeMillis();

        for (Town town : towns.values()) {
            for (Map.Entry<String, Long> entry : new ArrayList<>(town.rentDue().entrySet())) {
                String plot = entry.getKey();
                if (entry.getValue() > now) continue;

                Double rent = town.rentPrice(plot);
                UUID tenant = town.plotOwner(plot);
                if (rent == null || tenant == null) {
                    town.rentDue().remove(plot);
                    changed = true;
                    continue;
                }

                Player online = plugin.getServer().getPlayer(tenant);
                if (plugin.economy().has(tenant, rent)) {
                    plugin.economy().withdraw(tenant, rent);
                    town.depositBank(rent);
                    town.setRentDue(plot, now + rentPeriodMillis());
                    if (online != null) {
                        plugin.msg().send(online, "<gray>Rent of " + plugin.msg().money(rent)
                                + " paid to <white>" + town.name() + "</white>.");
                    }
                } else {
                    town.setPlotOwner(plot, null);
                    town.rentDue().remove(plot);
                    if (online != null) {
                        plugin.msg().send(online, "<red>You couldn't cover the rent on your plot in <white>"
                                + town.name() + "</white>. The tenancy has ended.");
                    }
                    Player mayor = plugin.getServer().getPlayer(town.mayor());
                    if (mayor != null) {
                        plugin.msg().send(mayor, "<yellow>A tenant missed rent - a plot in <white>"
                                + town.name() + "</white> is available again.");
                    }
                }
                changed = true;
            }
        }
        if (changed) touch();
    }

    public boolean buyPlotHere(Player player) {
        String here = chunkKey(player.getLocation());
        Town town = getTownAt(here);
        if (town == null) { plugin.msg().send(player, "<red>This land isn't part of a town."); return false; }
        if (!town.isMember(player.getUniqueId())) {
            plugin.msg().send(player, "<red>Only residents can buy plots in this town."); return false;
        }
        Double price = town.plotPrice(here);
        if (price == null) {
            if (town.rentPrice(here) != null) {
                plugin.msg().send(player, "<gray>This plot is for rent, not sale. Use <white>/town rentplot</white>.");
            } else {
                plugin.msg().send(player, "<red>This plot isn't for sale.");
            }
            return false;
        }
        if (town.plotOwner(here) != null) {
            plugin.msg().send(player, "<red>Someone already owns this plot."); return false;
        }
        if (!plugin.economy().has(player.getUniqueId(), price)) {
            plugin.msg().send(player, "<red>You can't afford this plot."); return false;
        }
        plugin.economy().withdraw(player.getUniqueId(), price);
        town.depositBank(price);
        town.setPlotOwner(here, player.getUniqueId());
        town.clearSale(here);
        touch();
        plugin.msg().send(player, "<green>Plot purchased! Only you (and the mayor) can build here now.");
        return true;
    }

    // ---- protection ----
    public boolean canBuild(Player player, Location loc) {
        if (player.hasPermission("apollo.town.bypass")) return true;
        Town town = getTownAtLoc(loc);
        if (town == null) return true; // wilderness
        String key = chunkKey(loc);
        UUID plotOwner = town.plotOwner(key);
        if (plotOwner != null) {
            return plotOwner.equals(player.getUniqueId()) || player.getUniqueId().equals(town.mayor());
        }
        return town.hasPerm(player.getUniqueId(), TownPerm.BUILD);
    }

    // ---- taxes (called on a timer) ----
    public void collectTaxes() {
        boolean changed = false;
        for (Town town : towns.values()) {
            if (town.tax() <= 0) continue;
            for (UUID member : new ArrayList<>(town.members().keySet())) {
                if (member.equals(town.mayor())) continue;
                if (plugin.economy().has(member, town.tax())) {
                    plugin.economy().withdraw(member, town.tax());
                    town.depositBank(town.tax());
                    changed = true;
                    Player p = plugin.getServer().getPlayer(member);
                    if (p != null) plugin.msg().send(p, "<gray>Town tax of "
                            + plugin.msg().money(town.tax()) + " paid to " + town.name() + ".");
                }
            }
        }
        if (changed) touch();
    }

    // ---- persistence ----
    private void touch() { dirty = true; save(); }

    public void save() {
        if (!dirty) return;
        FileConfiguration cfg = new YamlConfiguration();
        for (Town town : towns.values()) {
            String base = "towns." + town.name();
            cfg.set(base + ".mayor", town.mayor().toString());
            cfg.set(base + ".founded", town.founded());
            cfg.set(base + ".bank", town.bank());
            cfg.set(base + ".tax", town.tax());
            cfg.set(base + ".public-spawn", town.publicSpawn());
            for (Map.Entry<TownUpgrade, Integer> e : town.upgrades().entrySet()) {
                cfg.set(base + ".upgrades." + e.getKey().name(), e.getValue());
            }
            if (town.spawn() != null) cfg.set(base + ".spawn", serLoc(town.spawn()));
            for (Map.Entry<UUID, TownRank> e : town.members().entrySet()) {
                cfg.set(base + ".members." + e.getKey(), e.getValue().name());
            }
            cfg.set(base + ".claims", new ArrayList<>(town.claims()));
            List<String> plots = new ArrayList<>();
            for (Map.Entry<String, UUID> e : town.plotOwners().entrySet()) plots.add(e.getKey() + "=" + e.getValue());
            cfg.set(base + ".plots", plots);
            List<String> sales = new ArrayList<>();
            for (Map.Entry<String, Double> e : town.plotSale().entrySet()) sales.add(e.getKey() + "=" + e.getValue());
            cfg.set(base + ".sales", sales);
            List<String> rents = new ArrayList<>();
            for (Map.Entry<String, Double> e : town.plotRent().entrySet()) rents.add(e.getKey() + "=" + e.getValue());
            cfg.set(base + ".rents", rents);
            List<String> due = new ArrayList<>();
            for (Map.Entry<String, Long> e : town.rentDue().entrySet()) due.add(e.getKey() + "=" + e.getValue());
            cfg.set(base + ".rentDue", due);
            for (TownRank r : TownRank.values()) {
                List<String> perms = new ArrayList<>();
                for (TownPerm p : town.permsFor(r)) perms.add(p.name());
                cfg.set(base + ".perms." + r.name(), perms);
            }
        }
        try {
            cfg.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save towns.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("towns");
        if (root == null) return;
        for (String name : root.getKeys(false)) {
            try {
                String base = "towns." + name;
                UUID mayor = UUID.fromString(cfg.getString(base + ".mayor"));
                long founded = cfg.getLong(base + ".founded");
                Town town = new Town(name, mayor, founded);
                town.setBank(cfg.getDouble(base + ".bank"));
                town.setTax(cfg.getDouble(base + ".tax"));
                town.setPublicSpawn(cfg.getBoolean(base + ".public-spawn", true));
                ConfigurationSection ups = cfg.getConfigurationSection(base + ".upgrades");
                if (ups != null) {
                    for (String upName : ups.getKeys(false)) {
                        TownUpgrade up = TownUpgrade.fromString(upName);
                        if (up != null) town.setUpgradeLevel(up, ups.getInt(upName));
                    }
                }
                String spawn = cfg.getString(base + ".spawn");
                if (spawn != null) town.setSpawn(deserLoc(spawn));

                ConfigurationSection ms = cfg.getConfigurationSection(base + ".members");
                if (ms != null) {
                    for (String uid : ms.getKeys(false)) {
                        town.addMember(UUID.fromString(uid), TownRank.fromString(ms.getString(uid), TownRank.RESIDENT));
                    }
                }
                for (String ck : cfg.getStringList(base + ".claims")) town.addClaim(ck);
                for (String entry : cfg.getStringList(base + ".plots")) {
                    String[] pr = entry.split("=");
                    if (pr.length == 2) town.setPlotOwner(pr[0], UUID.fromString(pr[1]));
                }
                for (String entry : cfg.getStringList(base + ".sales")) {
                    String[] pr = entry.split("=");
                    if (pr.length == 2) town.setForSale(pr[0], Double.parseDouble(pr[1]));
                }
                for (String entry : cfg.getStringList(base + ".rents")) {
                    String[] pr = entry.split("=");
                    if (pr.length == 2) town.setForRent(pr[0], Double.parseDouble(pr[1]));
                }
                for (String entry : cfg.getStringList(base + ".rentDue")) {
                    String[] pr = entry.split("=");
                    if (pr.length == 2) town.setRentDue(pr[0], Long.parseLong(pr[1]));
                }
                ConfigurationSection ps = cfg.getConfigurationSection(base + ".perms");
                if (ps != null) {
                    for (String rankName : ps.getKeys(false)) {
                        TownRank r = TownRank.fromString(rankName, null);
                        if (r == null) continue;
                        java.util.EnumSet<TownPerm> set = java.util.EnumSet.noneOf(TownPerm.class);
                        for (String pn : ps.getStringList(rankName)) {
                            try { set.add(TownPerm.valueOf(pn)); } catch (Exception ignored) {}
                        }
                        town.permsFor(r).clear();
                        town.permsFor(r).addAll(set);
                    }
                }

                towns.put(name.toLowerCase(), town);
                for (String ck : town.claims()) chunkIndex.put(ck, name.toLowerCase());
                for (UUID m : town.members().keySet()) playerTown.put(m, name.toLowerCase());
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipped bad town entry: " + name + " (" + ex.getMessage() + ")");
            }
        }
    }

    private String serLoc(Location l) {
        return l.getWorld().getName() + "," + l.getX() + "," + l.getY() + "," + l.getZ()
                + "," + l.getYaw() + "," + l.getPitch();
    }

    private Location deserLoc(String s) {
        String[] p = s.split(",");
        World w = plugin.getServer().getWorld(p[0]);
        if (w == null) return null;
        return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                Float.parseFloat(p[4]), Float.parseFloat(p[5]));
    }
}
