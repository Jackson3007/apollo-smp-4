package com.apollosmp.merchant;

/** One thing the merchant is selling today. */
public class MerchantOffer {

    public enum Kind { SPAWNER, DRILL, TREE_AXE, GOD_APPLE, BUSINESS, TOTEM, JUNK }

    private final Kind kind;
    private final double price;
    /** Spawner mob, business id, or junk material - depends on the kind. */
    private final String data;

    public MerchantOffer(Kind kind, double price, String data) {
        this.kind = kind;
        this.price = price;
        this.data = data;
    }

    public Kind kind() { return kind; }
    public double price() { return price; }
    public String data() { return data; }

    public String serialize() {
        return kind.name() + ";" + price + ";" + (data == null ? "" : data);
    }

    public static MerchantOffer deserialize(String raw) {
        try {
            String[] parts = raw.split(";", 3);
            Kind kind = Kind.valueOf(parts[0]);
            double price = Double.parseDouble(parts[1]);
            String data = parts.length > 2 && !parts[2].isEmpty() ? parts[2] : null;
            return new MerchantOffer(kind, price, data);
        } catch (Exception ex) {
            return null;
        }
    }
}
