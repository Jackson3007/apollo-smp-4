package com.apollosmp.auction;

import com.apollosmp.ApolloSMP;
import com.apollosmp.util.Items;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuctionManager {

    public enum ListResult { SUCCESS, EMPTY_HAND, TOO_MANY, PRICE_LOW, PRICE_HIGH, NO_FUNDS_FOR_TAX }
    public enum BuyResult { SUCCESS, NOT_FOUND, OWN_LISTING, NO_FUNDS }

    private final ApolloSMP plugin;
    private final File file;
    private final Map<UUID, Listing> listings = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> pendingNotifs = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public AuctionManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auctions.yml");
        load();
    }

    // ---- config helpers ----
    private long durationMillis() {
        return plugin.getConfig().getLong("auction-house.listing-duration-hours", 48) * 3600_000L;
    }
    private double listingTaxPercent() { return plugin.getConfig().getDouble("auction-house.listing-tax-percent", 3.0); }
    private double minPrice() { return plugin.getConfig().getDouble("auction-house.min-price", 1.0); }
    private double maxPrice() { return plugin.getConfig().getDouble("auction-house.max-price", 1.0E8); }

    // ---- queries ----
    public List<Listing> active() {
        List<Listing> list = new ArrayList<>(listings.values());
        list.sort(Comparator.comparingLong(Listing::createdAt).reversed());
        return list;
    }

    public List<Listing> bySeller(UUID seller) {
        List<Listing> list = new ArrayList<>();
        for (Listing l : listings.values()) if (l.seller().equals(seller)) list.add(l);
        list.sort(Comparator.comparingLong(Listing::createdAt).reversed());
        return list;
    }

    public int countBySeller(UUID seller) {
        int n = 0;
        for (Listing l : listings.values()) if (l.seller().equals(seller)) n++;
        return n;
    }

    public Listing get(UUID id) { return listings.get(id); }

    // ---- operations ----

    /** List the item in the seller's main hand for {@code price}. */
    public ListResult list(Player seller, double price) {
        ItemStack hand = seller.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) return ListResult.EMPTY_HAND;
        if (price < minPrice()) return ListResult.PRICE_LOW;
        if (price > maxPrice()) return ListResult.PRICE_HIGH;

        double tax = price * (listingTaxPercent() / 100.0);
        if (tax > 0 && !plugin.economy().withdraw(seller.getUniqueId(), tax)) {
            return ListResult.NO_FUNDS_FOR_TAX;
        }

        ItemStack listed = hand.clone();
        // Drop the inventory price tag so listings show only the asking price.
        if (plugin.worthTags() != null) plugin.worthTags().strip(listed);
        seller.getInventory().setItemInMainHand(null);

        long now = System.currentTimeMillis();
        Listing listing = new Listing(UUID.randomUUID(), seller.getUniqueId(), seller.getName(),
                listed, price, now, now + durationMillis());
        listings.put(listing.id(), listing);
        dirty = true;
        save();
        return ListResult.SUCCESS;
    }

    /** Buy a listing. Proceeds go to the seller's balance; item goes to the buyer. */
    public BuyResult buy(Player buyer, UUID listingId) {
        Listing listing = listings.get(listingId);
        if (listing == null) return BuyResult.NOT_FOUND;
        if (listing.seller().equals(buyer.getUniqueId())) return BuyResult.OWN_LISTING;
        if (!plugin.economy().has(buyer.getUniqueId(), listing.price())) return BuyResult.NO_FUNDS;

        plugin.economy().withdraw(buyer.getUniqueId(), listing.price());
        plugin.economy().deposit(listing.seller(), listing.price());
        listings.remove(listingId);
        dirty = true;
        Items.give(buyer, listing.item());
        notifySale(listing, buyer);
        save();
        return BuyResult.SUCCESS;
    }

    /** Tell the seller their item sold — right away if online, otherwise on their next join. */
    private void notifySale(Listing listing, Player buyer) {
        String item = listing.item().getAmount() + "x " + Items.pretty(listing.item().getType());
        String message = "<green>Your <white>" + item + "</white> sold for <#f9d423>"
                + plugin.msg().money(listing.price()) + "</#f9d423> to <white>" + buyer.getName() + "</white>!";
        Player seller = plugin.getServer().getPlayer(listing.seller());
        if (seller != null && seller.isOnline()) {
            plugin.msg().send(seller, message);
        } else {
            pendingNotifs.computeIfAbsent(listing.seller(), k -> new ArrayList<>()).add(message);
        }
    }

    /** Deliver any "your item sold" messages saved while the player was offline. */
    public void flushNotifications(Player player) {
        List<String> msgs = pendingNotifs.remove(player.getUniqueId());
        if (msgs == null || msgs.isEmpty()) return;
        plugin.msg().send(player, "<#f9d423>While you were away:</#f9d423>");
        for (String m : msgs) plugin.msg().send(player, m);
        dirty = true;
        save();
    }

    /** Cancel your own listing; the item is returned to your mailbox. */
    public boolean cancel(UUID seller, UUID listingId) {
        Listing listing = listings.get(listingId);
        if (listing == null || !listing.seller().equals(seller)) return false;
        listings.remove(listingId);
        plugin.mailbox().add(seller, listing.item());
        dirty = true;
        save();
        return true;
    }

    /** Move expired listings into their owner's mailbox. Called on a timer. */
    public void expireTick() {
        boolean changed = false;
        for (Listing listing : new ArrayList<>(listings.values())) {
            if (listing.isExpired()) {
                listings.remove(listing.id());
                plugin.mailbox().add(listing.seller(), listing.item());
                changed = true;
            }
        }
        if (changed) {
            dirty = true;
            save();
        }
    }

    // ---- persistence ----
    public void save() {
        if (!dirty) return;
        FileConfiguration cfg = new YamlConfiguration();
        for (Listing l : listings.values()) {
            String p = "listings." + l.id();
            cfg.set(p + ".seller", l.seller().toString());
            cfg.set(p + ".sellerName", l.sellerName());
            cfg.set(p + ".item", Items.toBase64(l.item()));
            cfg.set(p + ".price", l.price());
            cfg.set(p + ".createdAt", l.createdAt());
            cfg.set(p + ".expiresAt", l.expiresAt());
        }
        for (Map.Entry<UUID, List<String>> e : pendingNotifs.entrySet()) {
            cfg.set("notifications." + e.getKey(), e.getValue());
        }
        try {
            cfg.save(file);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save auctions.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection ls = cfg.getConfigurationSection("listings");
        if (ls != null) {
            for (String key : ls.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    String base = "listings." + key;
                    Listing listing = new Listing(
                            id,
                            UUID.fromString(cfg.getString(base + ".seller")),
                            cfg.getString(base + ".sellerName", "Unknown"),
                            Items.fromBase64(cfg.getString(base + ".item")),
                            cfg.getDouble(base + ".price"),
                            cfg.getLong(base + ".createdAt"),
                            cfg.getLong(base + ".expiresAt"));
                    listings.put(id, listing);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Skipped bad auction entry: " + key);
                }
            }
        }
        ConfigurationSection ns = cfg.getConfigurationSection("notifications");
        if (ns != null) {
            for (String key : ns.getKeys(false)) {
                try {
                    pendingNotifs.put(UUID.fromString(key),
                            new ArrayList<>(cfg.getStringList("notifications." + key)));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }
}
