package com.apollosmp.invest;

import com.apollosmp.sell.SellManager;
import org.bukkit.Material;

import java.util.List;

/**
 * A type of business the player can buy. Placing its block generates product
 * over time, which the owner can collect or sell.
 */
public class Business {

    /** One product line: {@code amountPerInterval} items produced each interval. */
    public record Product(Material material, int amountPerInterval) {}

    private final String id;
    private final String displayName;   // MiniMessage
    private final String tagline;       // MiniMessage, short
    private final String particleName;  // Bukkit Particle enum name (resolved safely)
    private final Material block;
    private final double price;
    private final int intervalSeconds;
    private final int capacityHours;
    private final List<Product> products;

    public Business(String id, String displayName, String tagline, String particleName,
                    Material block, double price, int intervalSeconds, int capacityHours,
                    List<Product> products) {
        this.id = id;
        this.displayName = displayName;
        this.tagline = tagline;
        this.particleName = particleName;
        this.block = block;
        this.price = price;
        this.intervalSeconds = intervalSeconds;
        this.capacityHours = capacityHours;
        this.products = products;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String tagline() { return tagline; }
    public String particleName() { return particleName; }
    public Material block() { return block; }
    public double price() { return price; }
    public int intervalSeconds() { return intervalSeconds; }
    public List<Product> products() { return products; }

    public long intervalMillis() { return intervalSeconds * 1000L; }

    /** How many of a product are produced per hour. */
    public int perHour(Product product) {
        return product.amountPerInterval() * 3600 / intervalSeconds;
    }

    /** Max amount of a given product this business can hold before it stops producing. */
    public int capacityFor(Product product) {
        int intervals = Math.max(1, (capacityHours * 3600) / intervalSeconds);
        return product.amountPerInterval() * intervals;
    }

    /** Estimated income per hour, valued at current server sell prices. */
    public double hourlyValue(SellManager sell) {
        double perInterval = 0;
        for (Product p : products) {
            perInterval += sell.priceOf(p.material()) * p.amountPerInterval();
        }
        double intervalsPerHour = 3600.0 / intervalSeconds;
        return perInterval * intervalsPerHour;
    }
}
