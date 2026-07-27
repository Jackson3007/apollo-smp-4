package com.apollosmp.town;

/** An agreed war between two towns, with a fixed end time. */
public class TownWar {

    private final String townA;
    private final String townB;
    private final long startedAt;
    private long endsAt;

    public TownWar(String townA, String townB, long startedAt, long endsAt) {
        this.townA = townA;
        this.townB = townB;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
    }

    public String townA() { return townA; }
    public String townB() { return townB; }
    public long startedAt() { return startedAt; }
    public long endsAt() { return endsAt; }
    public void setEndsAt(long endsAt) { this.endsAt = endsAt; }

    public boolean involves(String town) {
        return town != null && (town.equalsIgnoreCase(townA) || town.equalsIgnoreCase(townB));
    }

    public String other(String town) {
        if (townA.equalsIgnoreCase(town)) return townB;
        if (townB.equalsIgnoreCase(town)) return townA;
        return null;
    }

    public long millisLeft() {
        return Math.max(0, endsAt - System.currentTimeMillis());
    }

    /** Stable key regardless of which town is listed first. */
    public String key() { return key(townA, townB); }

    public static String key(String a, String b) {
        String first = a.toLowerCase();
        String second = b.toLowerCase();
        return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first;
    }
}
