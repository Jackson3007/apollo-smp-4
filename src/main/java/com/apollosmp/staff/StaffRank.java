package com.apollosmp.staff;

/** Server roles shown in chat and the tab list. */
public enum StaffRank {

    OWNER("Owner", "#ff4e50", true),
    MOD("Mod", "#5ad1e8", true),
    YOUTUBER("YouTuber", "#e94fd0", false);

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
        try {
            return StaffRank.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
