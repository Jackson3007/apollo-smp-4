package com.apollosmp.bank;

import java.util.UUID;

/** Money lent from a town bank, with a deadline. */
public class Loan {

    private final String id;
    private final UUID borrower;
    private final String borrowerName;
    private final String town;
    private final double principal;
    private double owed;
    private final long takenAt;
    private final long dueAt;
    private boolean defaulted;

    public Loan(String id, UUID borrower, String borrowerName, String town,
                double principal, double owed, long takenAt, long dueAt, boolean defaulted) {
        this.id = id;
        this.borrower = borrower;
        this.borrowerName = borrowerName;
        this.town = town;
        this.principal = principal;
        this.owed = owed;
        this.takenAt = takenAt;
        this.dueAt = dueAt;
        this.defaulted = defaulted;
    }

    public String id() { return id; }
    public UUID borrower() { return borrower; }
    public String borrowerName() { return borrowerName; }
    public String town() { return town; }
    public double principal() { return principal; }
    public double owed() { return owed; }
    public void setOwed(double owed) { this.owed = Math.max(0, owed); }
    public long takenAt() { return takenAt; }
    public long dueAt() { return dueAt; }
    public boolean defaulted() { return defaulted; }
    public void setDefaulted(boolean defaulted) { this.defaulted = defaulted; }

    public boolean settled() { return owed <= 0.009; }
    public long millisLeft() { return Math.max(0, dueAt - System.currentTimeMillis()); }
    public boolean overdue() { return !settled() && System.currentTimeMillis() > dueAt; }

    /** "2d 4h", or "overdue". */
    public String timeLeft() {
        if (overdue()) return "overdue";
        long minutes = millisLeft() / 60000;
        long days = minutes / 1440;
        long hours = (minutes % 1440) / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + (minutes % 60) + "m";
        return minutes + "m";
    }
}
