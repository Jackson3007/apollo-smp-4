package com.apollosmp.admin;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Optional bridge to LibsDisguises for changing a player's skin and the name above
 * their head. Called by reflection so the plugin builds and runs whether or not
 * LibsDisguises is installed - if it's missing, these quietly do nothing.
 */
public final class Disguises {

    private Disguises() {}

    public static boolean available() {
        try {
            Class.forName("me.libraryaddict.disguise.DisguiseAPI");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Disguise {@code player} as the Minecraft account {@code name} (skin + nameplate). */
    public static boolean disguiseAs(Player player, String name) {
        try {
            Class<?> playerDisguise = Class.forName("me.libraryaddict.disguise.disguisetypes.PlayerDisguise");
            Object disguise = playerDisguise.getConstructor(String.class).newInstance(name);
            Class<?> disguiseType = Class.forName("me.libraryaddict.disguise.disguisetypes.Disguise");
            Class<?> api = Class.forName("me.libraryaddict.disguise.DisguiseAPI");
            Method disguiseToAll = api.getMethod("disguiseToAll", Entity.class, disguiseType);
            disguiseToAll.invoke(null, player, disguise);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void undisguise(Player player) {
        try {
            Class<?> api = Class.forName("me.libraryaddict.disguise.DisguiseAPI");
            Method undisguiseToAll = api.getMethod("undisguiseToAll", Entity.class);
            undisguiseToAll.invoke(null, player);
        } catch (Throwable ignored) {
            // LibsDisguises not present or API differs - nothing to undo.
        }
    }
}
