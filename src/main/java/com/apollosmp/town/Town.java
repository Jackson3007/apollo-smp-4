package com.apollosmp.town;

import org.bukkit.Location;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A single town: its members, claimed land, bank, plots, and rank permissions. */
public class Town {

    private String name;
    private UUID mayor;
    private long founded;
    private double bank;
    private double tax;
    private Location spawn;

    private final Map<UUID, TownRank> members = new LinkedHashMap<>();
    private final Set<String> claims = new LinkedHashSet<>();
    private final Map<String, UUID> plotOwners = new HashMap<>();   // chunkKey -> owner
    private final Map<String, Double> plotSale = new HashMap<>();   // chunkKey -> asking price
    private final Map<TownRank, EnumSet<TownPerm>> rankPerms = new EnumMap<>(TownRank.class);

    public Town(String name, UUID mayor, long founded) {
        this.name = name;
        this.mayor = mayor;
        this.founded = founded;
        this.members.put(mayor, TownRank.MAYOR);
        for (TownRank r : TownRank.values()) rankPerms.put(r, r.defaultPerms());
    }

    // ---- basics ----
    public String name() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID mayor() { return mayor; }
    public void setMayor(UUID mayor) { this.mayor = mayor; }
    public long founded() { return founded; }
    public double bank() { return bank; }
    public void setBank(double bank) { this.bank = Math.max(0, bank); }
    public void depositBank(double amount) { this.bank += Math.max(0, amount); }
    public boolean withdrawBank(double amount) {
        if (amount <= 0 || bank < amount) return false;
        bank -= amount;
        return true;
    }
    public double tax() { return tax; }
    public void setTax(double tax) { this.tax = Math.max(0, tax); }
    public Location spawn() { return spawn; }
    public void setSpawn(Location spawn) { this.spawn = spawn; }

    // ---- members ----
    public Map<UUID, TownRank> members() { return members; }
    public boolean isMember(UUID id) { return members.containsKey(id); }
    public TownRank rankOf(UUID id) { return members.get(id); }
    public void addMember(UUID id, TownRank rank) { members.put(id, rank); }
    public void removeMember(UUID id) {
        members.remove(id);
        // Release any plots owned by the departing member back to the town.
        plotOwners.entrySet().removeIf(e -> e.getValue().equals(id));
    }
    public void setRank(UUID id, TownRank rank) { if (members.containsKey(id)) members.put(id, rank); }
    public int memberCount() { return members.size(); }

    // ---- claims & plots ----
    public Set<String> claims() { return claims; }
    public boolean hasClaim(String chunkKey) { return claims.contains(chunkKey); }
    public void addClaim(String chunkKey) { claims.add(chunkKey); }
    public void removeClaim(String chunkKey) {
        claims.remove(chunkKey);
        plotOwners.remove(chunkKey);
        plotSale.remove(chunkKey);
    }
    public Map<String, UUID> plotOwners() { return plotOwners; }
    public Map<String, Double> plotSale() { return plotSale; }
    public UUID plotOwner(String chunkKey) { return plotOwners.get(chunkKey); }
    public void setPlotOwner(String chunkKey, UUID owner) {
        if (owner == null) plotOwners.remove(chunkKey);
        else plotOwners.put(chunkKey, owner);
    }
    public Double plotPrice(String chunkKey) { return plotSale.get(chunkKey); }
    public void setForSale(String chunkKey, double price) { plotSale.put(chunkKey, price); }
    public void clearSale(String chunkKey) { plotSale.remove(chunkKey); }

    // ---- permissions ----
    public EnumSet<TownPerm> permsFor(TownRank rank) {
        return rankPerms.computeIfAbsent(rank, TownRank::defaultPerms);
    }
    public void togglePerm(TownRank rank, TownPerm perm) {
        EnumSet<TownPerm> set = permsFor(rank);
        if (!set.add(perm)) set.remove(perm);
    }
    public boolean hasPerm(UUID id, TownPerm perm) {
        if (id.equals(mayor)) return true;
        TownRank rank = members.get(id);
        if (rank == null) return false;
        if (rank == TownRank.MAYOR) return true;
        return permsFor(rank).contains(perm);
    }
}
