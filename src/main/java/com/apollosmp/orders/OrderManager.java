package com.apollosmp.orders;

import com.apollosmp.ApolloSMP;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Buy orders: a player requests N of an item at a fixed price each. The full
 * cost (plus tax) is escrowed up front, so any seller can fulfil the order even
 * while the buyer is offline. Items are delivered to the buyer's mailbox.
 */
public class OrderManager {

    public enum CreateResult { SUCCESS, TOO_MANY, PRICE_LOW, PRICE_HIGH, BAD_QUANTITY, NO_FUNDS }

    private final ApolloSMP plugin;
    private final File file;
    private final java.util.Map<UUID, Order> orders = new ConcurrentHashMap<>();
    private volatile boolean dirty = false;

    public OrderManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "orders.yml");
        load();
    }

    private int maxOrders() { return plugin.getConfig().getInt("orders.max-orders-per-player", 5); }
    private double taxPercent() { return plugin.getConfig().getDouble("orders.fulfil-tax-percent", 3.0); }
    private double minPrice() { return plugin.getConfig().getDouble("orders.min-price", 1.0); }
    private double maxPrice() { return plugin.getConfig().getDouble("orders.max-price", 1.0E8); }

    public List<Order> active() {
        List<Order> list = new ArrayList<>(orders.values());
        list.sort(Comparator.comparingLong(Order::createdAt).reversed());
        return list;
    }

    public List<Order> byBuyer(UUID buyer) {
        List<Order> list = new ArrayList<>();
        for (Order o : orders.values()) if (o.buyer().equals(buyer)) list.add(o);
        list.sort(Comparator.comparingLong(Order::createdAt).reversed());
        return list;
    }

    public int countByBuyer(UUID buyer) {
        int n = 0;
        for (Order o : orders.values()) if (o.buyer().equals(buyer)) n++;
        return n;
    }

    public Order get(UUID id) { return orders.get(id); }

    /** Create a buy order using the material in the buyer's main hand as the template. */
    public CreateResult create(Player buyer, double pricePer, int quantity) {
        ItemStack hand = buyer.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) return CreateResult.BAD_QUANTITY;
        Material material = hand.getType();
        if (quantity <= 0 || quantity > 3456) return CreateResult.BAD_QUANTITY; // 54 stacks cap
        if (pricePer < minPrice()) return CreateResult.PRICE_LOW;
        if (pricePer > maxPrice()) return CreateResult.PRICE_HIGH;
        if (countByBuyer(buyer.getUniqueId()) >= maxOrders()) return CreateResult.TOO_MANY;

        double escrow = pricePer * quantity;
        double tax = escrow * (taxPercent() / 100.0);
        if (!plugin.economy().withdraw(buyer.getUniqueId(), escrow + tax)) {
            return CreateResult.NO_FUNDS;
        }

        Order order = new Order(UUID.randomUUID(), buyer.getUniqueId(), buyer.getName(),
                material, quantity, 0, pricePer, System.currentTimeMillis());
        orders.put(order.id(), order);
        dirty = true;
        save();
        return CreateResult.SUCCESS;
    }

    public record FulfilResult(int filled, double earned) {
        public boolean any() { return filled > 0; }
    }

    /**
     * A seller fulfils an order from their inventory. Pays the seller pricePer
     * for each item delivered; items go to the buyer's mailbox.
     */
    public FulfilResult fulfil(Player seller, UUID orderId, int maxToSell) {
        Order order = orders.get(orderId);
        if (order == null || order.isComplete()) return new FulfilResult(0, 0);
        if (order.buyer().equals(seller.getUniqueId())) return new FulfilResult(0, 0);

        ItemStack model = new ItemStack(order.material());
        int have = Items.countMatching(seller, model);
        int canFill = Math.min(order.remaining(), Math.min(have, Math.max(0, maxToSell)));
        if (canFill <= 0) return new FulfilResult(0, 0);

        int removed = Items.removeMatching(seller, model, canFill);
        if (removed <= 0) return new FulfilResult(0, 0);

        double earned = removed * order.pricePer();
        plugin.economy().deposit(seller.getUniqueId(), earned);

        // Deliver items to the buyer's mailbox in stack-sized chunks.
        int toDeliver = removed;
        int maxStack = order.material().getMaxStackSize();
        while (toDeliver > 0) {
            int chunk = Math.min(maxStack, toDeliver);
            plugin.mailbox().add(order.buyer(), new ItemStack(order.material(), chunk));
            toDeliver -= chunk;
        }

        order.addFilled(removed);
        if (order.isComplete()) orders.remove(order.id());
        dirty = true;
        save();
        return new FulfilResult(removed, earned);
    }

    /** Buyer cancels their order; unfilled escrow is refunded. */
    public boolean cancel(UUID buyer, UUID orderId) {
        Order order = orders.get(orderId);
        if (order == null || !order.buyer().equals(buyer)) return false;
        double refund = order.remainingValue();
        orders.remove(orderId);
        if (refund > 0) plugin.economy().deposit(buyer, refund);
        dirty = true;
        save();
        return true;
    }

    public String buyerName(Order order) {
        OfflinePlayer op = org.bukkit.Bukkit.getOfflinePlayer(order.buyer());
        return op.getName() != null ? op.getName() : order.buyerName();
    }

    // ---- persistence ----
    public void save() {
        if (!dirty) return;
        FileConfiguration cfg = new YamlConfiguration();
        for (Order o : orders.values()) {
            String p = "orders." + o.id();
            cfg.set(p + ".buyer", o.buyer().toString());
            cfg.set(p + ".buyerName", o.buyerName());
            cfg.set(p + ".material", o.material().name());
            cfg.set(p + ".quantity", o.quantity());
            cfg.set(p + ".filled", o.filled());
            cfg.set(p + ".pricePer", o.pricePer());
            cfg.set(p + ".createdAt", o.createdAt());
        }
        try {
            cfg.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save orders.yml: " + e.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("orders");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String base = "orders." + key;
                Material mat = Material.matchMaterial(cfg.getString(base + ".material", "STONE"));
                if (mat == null) continue;
                Order order = new Order(id,
                        UUID.fromString(cfg.getString(base + ".buyer")),
                        cfg.getString(base + ".buyerName", "Unknown"),
                        mat,
                        cfg.getInt(base + ".quantity"),
                        cfg.getInt(base + ".filled"),
                        cfg.getDouble(base + ".pricePer"),
                        cfg.getLong(base + ".createdAt"));
                orders.put(id, order);
            } catch (Exception ex) {
                plugin.getLogger().warning("Skipped bad order entry: " + key);
            }
        }
    }
}
