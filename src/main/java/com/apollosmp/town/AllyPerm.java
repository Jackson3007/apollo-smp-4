package com.apollosmp.town;

/** What one town lets its ally do on its land. Granted per direction. */
public enum AllyPerm {

    BUILD("Build & Break", "Place and break blocks on your land."),
    CONTAINERS("Open Containers", "Use your chests, barrels and furnaces."),
    DOORS("Doors & Buttons", "Open doors, gates and pull levers."),
    SHOP_FREE("Free at the Barrel", "Take from your auction barrel at no cost.");

    private final String display;
    private final String description;

    AllyPerm(String display, String description) {
        this.display = display;
        this.description = description;
    }

    public String display() { return display; }
    public String description() { return description; }

    public static AllyPerm fromString(String s) {
        try {
            return AllyPerm.valueOf(s);
        } catch (Exception ex) {
            return null;
        }
    }
}
