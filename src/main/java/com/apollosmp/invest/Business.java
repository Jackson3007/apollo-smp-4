package com.apollosmp.invest;

import com.apollosmp.sell.SellManager;
import org.bukkit.Material;

import java.util.List;

/**
 * A type of business. Placing its block generates product over time. Each block
 * carries a level (1..MAX_LEVEL); higher levels produce exponentially more.
 */
public class Business {

    public record Product(Material material, int amountPerInterval) {}

    /** Production multiplier per level. L2 = 1.6x, L3 = 2.56x, ... */
    public static final double GROWTH = 1.6;
    public static final int MAX_LEVEL = 10;

    private final String id;
    private final String displayName;
    private final String tagline;
    private final String particleName;
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

    // ---- level-scaled production ----

    public int amountAtLevel(Product product, int level) {
        double mult = Math.pow(GROWTH, Math.max(0, level - 1));
        return Math.max(product.amountPerInterval(),
                (int) Math.round(product.amountPerInterval() * mult));
    }

    public int perHourAtLevel(Product product, int level) {
        return amountAtLevel(product, level) * 3600 / intervalSeconds;
    }

    /** Convenience: base (level 1) per-hour rate. */
    public int perHour(Product product) {
        return perHourAtLevel(product, 1);
    }

    public int capacityForAtLevel(Product product, int level) {
        int intervals = Math.max(1, (capacityHours * 3600) / intervalSeconds);
        return amountAtLevel(product, level) * intervals;
    }

    public double hourlyValueAtLevel(SellManager sell, int level) {
        double perInterval = 0;
        for (Product p : products) {
            perInterval += sell.priceOf(p.material()) * amountAtLevel(p, level);
        }
        return perInterval * (3600.0 / intervalSeconds);
    }

    /** Convenience: base (level 1) income per hour. */
    public double hourlyValue(SellManager sell) {
        return hourlyValueAtLevel(sell, 1);
    }

    // ---- upgrades ----

    /** Cost to upgrade FROM the given level to the next (exponential). */
    public double upgradeCost(int currentLevel) {
        return price * 0.6 * Math.pow(1.8, Math.max(0, currentLevel - 1));
    }

    /** Units that must be produced at the current level before upgrading. */
    public long unitsToUpgrade(int currentLevel) {
        return (long) (256 * Math.pow(2, Math.max(0, currentLevel - 1)));
    }
}
