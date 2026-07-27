package com.apollosmp.special;

import com.apollosmp.ApolloSMP;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs the daily mystery-business auction: one at a time, ending at noon,
 * with bids held in escrow and refunded the moment someone is outbid.
 */
public class SpecialAuctionManager {

    public enum BidResult { SUCCESS, TOO_LOW, NO_FUNDS, ALREADY_LEADING, NO_AUCTION, ENDED }

    private final ApolloSMP plugin;
    private final File file;
    private final SpecialGenerator generator = new SpecialGenerator();

    private SpecialBusiness lot;
    private long endsAt;
    private double currentBid;
    private UUID bidder;
    private String bidderName;
    /** What we actually took from the leader, so refunds can never drift. */
    private double escrow;
    private boolean warned5;
    private boolean warned1;

    /** Businesses won but not yet collected. */
    private final Map<UUID, List<SpecialBusiness>> claims = new LinkedHashMap<>();

    public SpecialAuctionManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "specialauction.yml");
        load();
    }

    // ---- config ----
    public double startingBid() {
        return plugin.getConfig().getDouble("special-auction.opening-bid", 20000.0);
    }

    public double minIncrement() {
        return plugin.getConfig().getDouble("special-auction.min-increment", 2500.0);
    }

    private int durationHours() {
        return plugin.getConfig().getInt("special-auction.duration-hours", 24);
    }

    private int endHour() {
        return plugin.getConfig().getInt("special-auction.end-hour", 12);
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(plugin.getConfig().getString("special-auction.timezone", "America/Los_Angeles"));
        } catch (Exception ex) {
            return ZoneId.systemDefault();
        }
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("special-auction.enabled", true);
    }

    // ---- state ----
    public SpecialBusiness lot() { return lot; }
    public long endsAt() { return endsAt; }
    public double currentBid() { return currentBid; }
    public UUID bidder() { return bidder; }
    public String bidderName() { return bidderName; }
    public boolean active() { return lot != null && endsAt > System.currentTimeMillis(); }
    public boolean hasBid() { return bidder != null; }

    public double nextMinimumBid() {
        return hasBid() ? currentBid + minIncrement() : startingBid();
    }

    public long millisLeft() {
        return Math.max(0, endsAt - System.currentTimeMillis());
    }

    /** The next occurrence of the configured hour, in the configured zone. */
    private long nextEndTime() {
        ZonedDateTime now = ZonedDateTime.now(zone());
        ZonedDateTime target = now.withHour(endHour()).withMinute(0).withSecond(0).withNano(0);
        if (!target.isAfter(now)) target = target.plusDays(1);
        // Honour a non-24h duration by capping how far out we schedule.
        long millis = target.toInstant().toEpochMilli();
        long maxOut = System.currentTimeMillis() + durationHours() * 3600_000L;
        return Math.min(millis, maxOut);
    }

    /** Admin testing: move the closing time. */
    public void setEndsAt(long millis) {
        this.endsAt = millis;
        this.warned5 = false;
        this.warned1 = false;
        save();
    }

    /** Admin testing: everything normally hidden during bidding. */
    public void revealAll(Player player) {
        if (lot == null) {
            plugin.msg().send(player, "<red>No auction running.");
            return;
        }
        plugin.msg().sendRaw(player, "<#ff4e50><bold>[ADMIN PEEK]</bold></#ff4e50>");
        revealTo(player, lot);
        plugin.msg().sendRaw(player, "<gray>Ends at: <white>" + timeLeftText() + "</white> from now");
        plugin.msg().sendRaw(player, "<gray>Escrow held: <white>"
                + plugin.msg().money(escrow) + "</white>");
    }

    // ---- lifecycle ----
    /** Called on a timer. Ends finished auctions and starts the next one. */
    public void tick() {
        if (!enabled()) return;
        long now = System.currentTimeMillis();

        if (lot == null) {
            startNew();
            return;
        }
        if (now >= endsAt) {
            settle();
            startNew();
            return;
        }

        long left = endsAt - now;
        if (!warned5 && left <= 5 * 60_000L) {
            warned5 = true;
            broadcast("<#f9d423><bold>5 minutes left</bold></#f9d423> <gray>on <white>"
                    + lot.name() + "</white>. Current bid: <#f9d423>"
                    + plugin.msg().money(currentBid) + "</#f9d423>");
            save();
        }
        if (!warned1 && left <= 60_000L) {
            warned1 = true;
            broadcast("<#ff4e50><bold>1 minute left</bold></#ff4e50> <gray>on <white>"
                    + lot.name() + "</white>! <white>/specialauction</white>");
            save();
        }
    }

    public void startNew() {
        lot = generator.generate();
        endsAt = nextEndTime();
        currentBid = 0;
        bidder = null;
        bidderName = null;
        escrow = 0;
        warned5 = false;
        warned1 = false;
        save();

        plugin.getLogger().info("[SpecialAuction] New lot: " + lot.name()
                + " (" + lot.rarity() + " " + lot.industry() + "), ends " + endsAt);

        broadcast("");
        broadcast("<gradient:#f9d423:#ff4e50><bold>MYSTERY BUSINESS AUCTION</bold></gradient>");
        broadcast("<gray>Today's lot: <white>" + lot.name() + "</white> <dark_gray>("
                + lot.rarity() + " " + lot.industry() + ")</dark_gray>");
        broadcast("<gray>Estimated profit: <#f9d423>" + plugin.msg().money(lot.profitMin())
                + " - " + plugin.msg().money(lot.profitMax()) + "</#f9d423> per day");
        broadcast("<gray>Opening bid: <#f9d423>" + plugin.msg().money(startingBid())
                + "</#f9d423>  <dark_gray>|</dark_gray>  <white>/specialauction</white>");
        broadcast("");
    }

    /** Place a bid, moving the money into escrow. */
    public BidResult bid(Player player, double amount) {
        if (lot == null) return BidResult.NO_AUCTION;
        if (System.currentTimeMillis() >= endsAt) return BidResult.ENDED;
        if (player.getUniqueId().equals(bidder)) return BidResult.ALREADY_LEADING;
        if (amount < nextMinimumBid()) return BidResult.TOO_LOW;
        if (!plugin.economy().has(player.getUniqueId(), amount)) return BidResult.NO_FUNDS;

        // Take the new bid first, so a failure can't lose the old one.
        plugin.economy().withdraw(player.getUniqueId(), amount);
        plugin.getLogger().info("[SpecialAuction] " + player.getName() + " bid "
                + amount + " on " + lot.name());

        refundLeader("outbid");

        bidder = player.getUniqueId();
        bidderName = player.getName();
        currentBid = amount;
        escrow = amount;

        // Anti-snipe: a late bid buys everyone another five minutes.
        long left = endsAt - System.currentTimeMillis();
        if (left <= 5 * 60_000L) {
            endsAt += 5 * 60_000L;
            warned5 = false;
            warned1 = false;
            broadcast("<gray>Late bid - <white>" + lot.name()
                    + "</white> extended by <white>5 minutes</white>.");
        }
        save();

        broadcast("<#f9d423>" + player.getName() + "</#f9d423> <gray>bids <#f9d423>"
                + plugin.msg().money(amount) + "</#f9d423> on <white>" + lot.name() + "</white>.");
        return BidResult.SUCCESS;
    }

    /** Give the current leader their escrow back exactly once. */
    private void refundLeader(String reason) {
        if (bidder == null || escrow <= 0) return;
        UUID previous = bidder;
        double amount = escrow;
        escrow = 0;
        bidder = null;

        plugin.economy().deposit(previous, amount);
        plugin.getLogger().info("[SpecialAuction] Refunded " + amount + " to " + previous + " (" + reason + ")");
        Player online = plugin.getServer().getPlayer(previous);
        if (online != null) {
            plugin.msg().send(online, "<yellow>You were outbid on <white>"
                    + (lot == null ? "the auction" : lot.name())
                    + "</white>. <green>" + plugin.msg().money(amount) + " refunded.");
        }
    }

    /** Close the auction and hand over the business. */
    public void settle() {
        if (lot == null) return;

        if (bidder == null) {
            broadcast("<gray>No bids on <white>" + lot.name() + "</white>. The lot is withdrawn.");
            plugin.getLogger().info("[SpecialAuction] " + lot.name() + " expired with no bids.");
            lot = null;
            save();
            return;
        }

        UUID winner = bidder;
        String winnerName = bidderName;
        double paid = escrow;
        SpecialBusiness won = lot;

        // The winning bid stays withdrawn - no refund.
        escrow = 0;
        bidder = null;
        lot = null;

        won.setOwner(winner);
        won.setOwnerName(winnerName);
        plugin.getLogger().info("[SpecialAuction] " + winnerName + " won " + won.name()
                + " for " + paid);

        broadcast("");
        broadcast("<gradient:#f9d423:#ff4e50><bold>AUCTION WON</bold></gradient>");
        broadcast("<white>" + winnerName + "</white> <gray>takes <white>" + won.name()
                + "</white> for <#f9d423>" + plugin.msg().money(paid) + "</#f9d423>.");
        broadcast("");

        Player online = plugin.getServer().getPlayer(winner);
        ItemStack item = plugin.specialBusinesses().createItem(won);
        if (online != null && online.getInventory().firstEmpty() != -1) {
            online.getInventory().addItem(item);
            revealTo(online, won);
        } else {
            claims.computeIfAbsent(winner, k -> new ArrayList<>()).add(won);
            if (online != null) {
                revealTo(online, won);
                plugin.msg().send(online,
                        "<yellow>Your inventory was full - collect it with <white>/specialauction claim</white>.");
            }
        }
        save();
    }

    /** Tell the winner everything that was hidden during bidding. */
    public void revealTo(Player player, SpecialBusiness b) {
        var msg = plugin.msg();
        msg.sendRaw(player, "<gradient:#f9d423:#ff4e50><bold>" + b.name() + "</bold></gradient>");
        msg.sendRaw(player, "<gray>" + b.description());
        msg.sendRaw(player, "<gray>Rarity: <white>" + b.rarity() + "</white>   Industry: <white>"
                + b.industry() + "</white>");
        msg.sendRaw(player, "<gray>Produces <white>" + b.effectiveKnownAmount() + "x "
                + pretty(b.knownItem()) + "</white> and <white>" + b.effectiveHiddenAmount() + "x "
                + pretty(b.hiddenItem()) + "</white>");
        msg.sendRaw(player, "<gray>Every <white>" + formatSeconds(b.effectiveInterval())
                + "</white>, storing up to <white>" + b.effectiveStorage() + "</white> items");
        msg.sendRaw(player, "<gray>Trait: <#e94fd0>" + b.trait().display() + "</#e94fd0> <dark_gray>- "
                + b.trait().description() + "</dark_gray>");
        msg.sendRaw(player, "<gray>Estimated profit: <#f9d423>"
                + plugin.msg().money(b.exactProfit()) + "</#f9d423> per day");
    }

    // ---- claims ----
    public List<SpecialBusiness> claimsFor(UUID id) {
        return new ArrayList<>(claims.getOrDefault(id, new ArrayList<>()));
    }

    public int claim(Player player) {
        List<SpecialBusiness> pending = claims.get(player.getUniqueId());
        if (pending == null || pending.isEmpty()) return 0;
        int given = 0;
        List<SpecialBusiness> left = new ArrayList<>();
        for (SpecialBusiness b : pending) {
            if (player.getInventory().firstEmpty() == -1) {
                left.add(b);
                continue;
            }
            player.getInventory().addItem(plugin.specialBusinesses().createItem(b));
            given++;
        }
        if (left.isEmpty()) claims.remove(player.getUniqueId());
        else claims.put(player.getUniqueId(), left);
        save();
        return given;
    }

    // ---- helpers ----
    private void broadcast(String mini) {
        for (Player p : plugin.getServer().getOnlinePlayers()) plugin.msg().sendRaw(p, mini);
    }

    public static String pretty(Material material) {
        String[] words = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    public static String formatSeconds(int seconds) {
        if (seconds >= 60) {
            int m = seconds / 60;
            int s = seconds % 60;
            return s == 0 ? m + "m" : m + "m " + s + "s";
        }
        return seconds + "s";
    }

    public String timeLeftText() {
        long millis = millisLeft();
        long total = millis / 1000;
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long seconds = total % 60;
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        if (lot != null) {
            writeBusiness(cfg, "current.business", lot);
            cfg.set("current.endsAt", endsAt);
            cfg.set("current.bid", currentBid);
            cfg.set("current.escrow", escrow);
            cfg.set("current.bidder", bidder == null ? null : bidder.toString());
            cfg.set("current.bidderName", bidderName);
            cfg.set("current.warned5", warned5);
            cfg.set("current.warned1", warned1);
        }
        int i = 0;
        for (Map.Entry<UUID, List<SpecialBusiness>> e : claims.entrySet()) {
            for (SpecialBusiness b : e.getValue()) {
                writeBusiness(cfg, "claims." + i + ".business", b);
                cfg.set("claims." + i + ".owner", e.getKey().toString());
                i++;
            }
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save specialauction.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        if (cfg.contains("current.business")) {
            lot = readBusiness(cfg, "current.business");
            endsAt = cfg.getLong("current.endsAt");
            currentBid = cfg.getDouble("current.bid");
            escrow = cfg.getDouble("current.escrow");
            String raw = cfg.getString("current.bidder");
            bidder = raw == null ? null : UUID.fromString(raw);
            bidderName = cfg.getString("current.bidderName");
            warned5 = cfg.getBoolean("current.warned5");
            warned1 = cfg.getBoolean("current.warned1");
        }

        ConfigurationSection claimSection = cfg.getConfigurationSection("claims");
        if (claimSection != null) {
            for (String key : claimSection.getKeys(false)) {
                try {
                    SpecialBusiness b = readBusiness(cfg, "claims." + key + ".business");
                    UUID owner = UUID.fromString(cfg.getString("claims." + key + ".owner"));
                    if (b != null) claims.computeIfAbsent(owner, k -> new ArrayList<>()).add(b);
                } catch (Exception ignored) {
                    // skip malformed entries
                }
            }
        }
    }

    void writeBusiness(FileConfiguration cfg, String path, SpecialBusiness b) {
        cfg.set(path + ".id", b.id());
        cfg.set(path + ".name", b.name());
        cfg.set(path + ".description", b.description());
        cfg.set(path + ".industry", b.industry());
        cfg.set(path + ".rarity", b.rarity());
        cfg.set(path + ".block", b.block().name());
        cfg.set(path + ".knownItem", b.knownItem().name());
        cfg.set(path + ".knownAmount", b.knownAmount());
        cfg.set(path + ".hiddenItem", b.hiddenItem().name());
        cfg.set(path + ".hiddenAmount", b.hiddenAmount());
        cfg.set(path + ".interval", b.intervalSeconds());
        cfg.set(path + ".storage", b.maxStorage());
        cfg.set(path + ".profitMin", b.profitMin());
        cfg.set(path + ".profitMax", b.profitMax());
        cfg.set(path + ".exactProfit", b.exactProfit());
        cfg.set(path + ".trait", b.trait().name());
        if (b.owner() != null) {
            cfg.set(path + ".owner", b.owner().toString());
            cfg.set(path + ".ownerName", b.ownerName());
        }
        if (b.town() != null) cfg.set(path + ".town", b.town());
    }

    SpecialBusiness readBusiness(FileConfiguration cfg, String path) {
        if (!cfg.contains(path + ".name")) return null;
        SpecialBusiness b = new SpecialBusiness();
        b.setId(cfg.getString(path + ".id"));
        b.setName(cfg.getString(path + ".name"));
        b.setDescription(cfg.getString(path + ".description"));
        b.setIndustry(cfg.getString(path + ".industry"));
        b.setRarity(cfg.getString(path + ".rarity"));
        b.setBlock(material(cfg.getString(path + ".block"), Material.DEEPSLATE));
        b.setKnownItem(material(cfg.getString(path + ".knownItem"), Material.STONE));
        b.setKnownAmount(cfg.getInt(path + ".knownAmount", 1));
        b.setHiddenItem(material(cfg.getString(path + ".hiddenItem"), Material.DIAMOND));
        b.setHiddenAmount(cfg.getInt(path + ".hiddenAmount", 1));
        b.setIntervalSeconds(cfg.getInt(path + ".interval", 300));
        b.setMaxStorage(cfg.getInt(path + ".storage", 1024));
        b.setProfitMin(cfg.getDouble(path + ".profitMin"));
        b.setProfitMax(cfg.getDouble(path + ".profitMax"));
        b.setExactProfit(cfg.getDouble(path + ".exactProfit"));
        b.setTrait(SpecialTrait.fromString(cfg.getString(path + ".trait", "RELIABLE")));
        String owner = cfg.getString(path + ".owner");
        if (owner != null) {
            b.setOwner(UUID.fromString(owner));
            b.setOwnerName(cfg.getString(path + ".ownerName"));
        }
        b.setTown(cfg.getString(path + ".town"));
        b.setLastGen(System.currentTimeMillis());
        return b;
    }

    static Material material(String name, Material fallback) {
        if (name == null) return fallback;
        Material m = Material.matchMaterial(name);
        return m == null ? fallback : m;
    }
}
