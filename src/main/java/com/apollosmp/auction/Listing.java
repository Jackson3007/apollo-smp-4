package com.apollosmp.auction;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class Listing {
    private final UUID id;
    private final UUID seller;
    private final String sellerName;
    private final ItemStack item;
    private final double price;
    private final long createdAt;
    private final long expiresAt;
    /** Set when a town is selling this, so the money goes to its bank. */
    private String town;

    public Listing(UUID id, UUID seller, String sellerName, ItemStack item,
                   double price, long createdAt, long expiresAt) {
        this.id = id;
        this.seller = seller;
        this.sellerName = sellerName;
        this.item = item;
        this.price = price;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public UUID id() { return id; }
    public UUID seller() { return seller; }
    public String sellerName() { return sellerName; }
    public String town() { return town; }
    public void setTown(String town) { this.town = (town == null || town.isBlank()) ? null : town; }
    public ItemStack item() { return item.clone(); }
    public double price() { return price; }
    public long createdAt() { return createdAt; }
    public long expiresAt() { return expiresAt; }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }

    public long millisLeft() {
        return Math.max(0, expiresAt - System.currentTimeMillis());
    }
}
