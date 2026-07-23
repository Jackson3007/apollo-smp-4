package com.apollosmp.town;

import java.util.EnumSet;

/** Ranks within a town, from most to least authority. */
public enum TownRank {
    MAYOR,
    ASSISTANT,
    COMMANDER,
    RESIDENT;

    /** Default permission set for this rank when a town is created. */
    public EnumSet<TownPerm> defaultPerms() {
        return switch (this) {
            case MAYOR -> EnumSet.allOf(TownPerm.class);
            case ASSISTANT -> EnumSet.of(TownPerm.BUILD, TownPerm.CLAIM, TownPerm.INVITE,
                    TownPerm.KICK, TownPerm.WITHDRAW, TownPerm.SELL_PLOT, TownPerm.SET_SPAWN);
            case COMMANDER -> EnumSet.of(TownPerm.BUILD, TownPerm.INVITE, TownPerm.KICK);
            case RESIDENT -> EnumSet.of(TownPerm.BUILD);
        };
    }

    public String display() {
        return switch (this) {
            case MAYOR -> "Mayor";
            case ASSISTANT -> "Assistant";
            case COMMANDER -> "Commander";
            case RESIDENT -> "Resident";
        };
    }

    public static TownRank fromString(String s, TownRank fallback) {
        try {
            return TownRank.valueOf(s);
        } catch (Exception e) {
            return fallback;
        }
    }
}
