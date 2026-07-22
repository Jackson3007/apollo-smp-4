package com.apollosmp.util;

import java.util.Locale;

public final class Numbers {

    private Numbers() {}

    /** Parse an amount like "1000", "1.5k", "2m", "1b". Returns null if invalid. */
    public static Double parseAmount(String input) {
        if (input == null || input.isBlank()) return null;
        String s = input.trim().toLowerCase(Locale.ROOT).replace(",", "");
        double multiplier = 1;
        char last = s.charAt(s.length() - 1);
        switch (last) {
            case 'k' -> { multiplier = 1_000d; s = s.substring(0, s.length() - 1); }
            case 'm' -> { multiplier = 1_000_000d; s = s.substring(0, s.length() - 1); }
            case 'b' -> { multiplier = 1_000_000_000d; s = s.substring(0, s.length() - 1); }
            default -> { /* no suffix */ }
        }
        try {
            double value = Double.parseDouble(s) * multiplier;
            if (Double.isNaN(value) || Double.isInfinite(value)) return null;
            return value;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static Integer parseInt(String input) {
        if (input == null) return null;
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
