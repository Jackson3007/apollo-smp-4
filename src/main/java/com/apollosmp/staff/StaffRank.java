package com.apollosmp.staff;

/** Server roles shown in chat and the tab list. */
public enum StaffRank {

    OWNER("Owner", "#ff4e50", true),
    MOD("Mod", "#5ad1e8", true),
    YOUTUBER("YouTuber", "#e94fd0", false),
    // Purchasable donor rank ($4.99/mo). Not staff - carries perks, not admin powers.
    APOLLO_PLUS("Apollo+", "#ffd54a", false);

    private final String display;
    private final String colour;
    private final boolean staff;

    StaffRank(String display, String colour, boolean staff) {
        this.display = display;
        this.colour = colour;
        this.staff = staff;
    }

    public String display() { return display; }
    public String colour() { return colour; }

    /** True when the role should carry admin powers. */
    public boolean isStaff() { return staff; }

    /** "<#ff4e50>Owner</#ff4e50>" */
    public String tag() {
        return "<" + colour + ">" + display + "</" + colour + ">";
    }

    public static StaffRank fromString(String s) {
        if (s == null) return null;
        String norm = s.trim().toLowerCase();
        // Friendly aliases for the donor rank ("/rank set X apollo+").
        switch (norm) {
            case "apollo+", "apollo_plus", "apolloplus", "plus", "apollo plus" -> {
                return APOLLO_PLUS;
            }
            default -> { /* fall through to enum lookup */ }
        }
        try {
            return StaffRank.valueOf(norm.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
