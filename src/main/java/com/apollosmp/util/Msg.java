package com.apollosmp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.text.DecimalFormat;

/**
 * Central place for turning config strings into Adventure Components and for
 * formatting money consistently everywhere in the plugin.
 */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final String prefix;
    private final String symbol;
    private final DecimalFormat moneyFormat;

    public Msg(FileConfiguration cfg) {
        this.prefix = cfg.getString("branding.prefix", "<gold>Apollo</gold> » ");
        this.symbol = cfg.getString("economy.symbol", "$");
        int decimals = cfg.getInt("economy.decimals", 2);
        StringBuilder pattern = new StringBuilder("#,##0");
        if (decimals > 0) {
            pattern.append('.');
            for (int i = 0; i < decimals; i++) pattern.append('0');
        }
        this.moneyFormat = new DecimalFormat(pattern.toString());
    }

    /** Parse a MiniMessage string into a Component (no forced italics). */
    public static Component mm(String miniMessage) {
        return MM.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    /** Parse with the italic default explicitly stripped, useful for item lore. */
    public static Component lore(String miniMessage) {
        return MM.deserialize(miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    public Component prefixed(String miniMessage) {
        return MM.deserialize(prefix + miniMessage).decoration(TextDecoration.ITALIC, false);
    }

    public void send(CommandSender to, String miniMessage) {
        to.sendMessage(prefixed(miniMessage));
    }

    public void sendRaw(CommandSender to, String miniMessage) {
        to.sendMessage(mm(miniMessage));
    }

    /** "$1,234.56" style string (MiniMessage-safe, no tags). */
    public String money(double amount) {
        return symbol + moneyFormat.format(amount);
    }

    public String moneyRaw(double amount) {
        return moneyFormat.format(amount);
    }

    public String symbol() {
        return symbol;
    }
}
