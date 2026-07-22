package com.apollosmp.orders;

import org.bukkit.Material;

import java.util.UUID;

public class Order {
    private final UUID id;
    private final UUID buyer;
    private final String buyerName;
    private final Material material;
    private final int quantity;
    private int filled;
    private final double pricePer;
    private final long createdAt;

    public Order(UUID id, UUID buyer, String buyerName, Material material,
                 int quantity, int filled, double pricePer, long createdAt) {
        this.id = id;
        this.buyer = buyer;
        this.buyerName = buyerName;
        this.material = material;
        this.quantity = quantity;
        this.filled = filled;
        this.pricePer = pricePer;
        this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public UUID buyer() { return buyer; }
    public String buyerName() { return buyerName; }
    public Material material() { return material; }
    public int quantity() { return quantity; }
    public int filled() { return filled; }
    public double pricePer() { return pricePer; }
    public long createdAt() { return createdAt; }

    public int remaining() { return quantity - filled; }
    public boolean isComplete() { return filled >= quantity; }
    public double totalValue() { return quantity * pricePer; }
    public double remainingValue() { return remaining() * pricePer; }

    void addFilled(int n) { this.filled += n; }
}
