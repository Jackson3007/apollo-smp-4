package com.apollosmp.special;

/** Hidden perks a special business can roll. Revealed only to the winner. */
public enum SpecialTrait {

    EFFICIENT("Efficient", "Produces on a shorter cycle."),
    HIGH_YIELD("High Yield", "Produces more of its main item."),
    RARE_DEPOSIT("Rare Deposit", "Produces far more of its hidden item."),
    EXPANDED_STORAGE("Expanded Storage", "Holds much more before filling up."),
    UNSTABLE("Unstable", "Output swings wildly batch to batch."),
    RELIABLE("Reliable", "Output never varies."),
    AUTOMATED("Automated", "Keeps working harder while you're offline.");

    private final String display;
    private final String description;

    SpecialTrait(String display, String description) {
        this.display = display;
        this.description = description;
    }

    public String display() { return display; }
    public String description() { return description; }

    public static SpecialTrait fromString(String s) {
        try {
            return SpecialTrait.valueOf(s);
        } catch (Exception ex) {
            return RELIABLE;
        }
    }
}
