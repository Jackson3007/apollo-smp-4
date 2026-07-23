package com.apollosmp.town;

/** Individual permissions a town rank can hold. */
public enum TownPerm {
    BUILD,        // break/place/interact in town land
    CLAIM,        // claim or unclaim chunks
    INVITE,       // invite new residents
    KICK,         // remove residents
    SET_RANK,     // change a resident's rank
    WITHDRAW,     // take money from the town bank
    SET_TAX,      // change the tax rate
    SET_SPAWN,    // move the town spawn
    SELL_PLOT,    // put a chunk up for sale as a plot
    MANAGE_PERMS  // edit rank permissions
}
