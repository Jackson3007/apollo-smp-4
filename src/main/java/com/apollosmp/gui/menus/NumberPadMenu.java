package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/** A click-to-type number pad, used anywhere an amount is needed. */
public class NumberPadMenu extends Gui {

    private static final int DISPLAY = 4;
    private static final int[] DIGIT_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30}; // 1..9
    private static final int ZERO = 37;
    private static final int DOT = 38;
    private static final int BACKSPACE = 14;
    private static final int CLEAR = 23;
    private static final int CONFIRM = 32;
    private static final int CANCEL = 41;

    private final String prompt;
    private final Consumer<Double> onConfirm;
    private final Runnable onCancel;
    private final StringBuilder input = new StringBuilder();

    public NumberPadMenu(ApolloSMP plugin, Player viewer, String title, String prompt,
                         Consumer<Double> onConfirm, Runnable onCancel) {
        super(plugin, viewer, 5, title);
        this.prompt = prompt;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    @Override
    protected void build() {
        String current = input.length() == 0 ? "0" : input.toString();
        inventory.setItem(DISPLAY, Items.of(Material.GOLD_INGOT)
                .name("<#f9d423><bold>" + current + "</bold>")
                .lore("<gray>" + prompt, "", "<gray>Click the numbers to enter an amount.")
                .glow(true).hideAttributes().build());

        for (int i = 0; i < DIGIT_SLOTS.length; i++) {
            char c = (char) ('1' + i);
            inventory.setItem(DIGIT_SLOTS[i], Items.of(Material.PAPER)
                    .name("<white><bold>" + c + "</bold>").build());
        }
        inventory.setItem(ZERO, Items.of(Material.PAPER).name("<white><bold>0</bold>").build());
        inventory.setItem(DOT, Items.of(Material.PAPER).name("<white><bold>.</bold>").build());

        inventory.setItem(BACKSPACE, Items.of(Material.ARROW).name("<yellow>Backspace").build());
        inventory.setItem(CLEAR, Items.of(Material.WATER_BUCKET).name("<yellow>Clear").build());
        inventory.setItem(CONFIRM, Items.of(Material.LIME_DYE)
                .name("<green><bold>Confirm</bold>").glow(true).hideAttributes().build());
        inventory.setItem(CANCEL, Items.of(Material.BARRIER).name("<red>Cancel").build());

        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        for (int i = 0; i < DIGIT_SLOTS.length; i++) {
            if (DIGIT_SLOTS[i] == slot) {
                append((char) ('1' + i));
                return;
            }
        }
        switch (slot) {
            case ZERO -> append('0');
            case DOT -> {
                if (input.indexOf(".") < 0 && input.length() > 0) append('.');
            }
            case BACKSPACE -> {
                if (input.length() > 0) input.deleteCharAt(input.length() - 1);
                redraw();
            }
            case CLEAR -> {
                input.setLength(0);
                redraw();
            }
            case CANCEL -> {
                if (onCancel != null) onCancel.run();
                else player.closeInventory();
            }
            case CONFIRM -> {
                double value;
                try {
                    value = input.length() == 0 ? 0 : Double.parseDouble(input.toString());
                } catch (NumberFormatException ex) {
                    plugin.msg().send(player, "<red>That isn't a valid amount.");
                    return;
                }
                onConfirm.accept(value);
            }
            default -> { /* no-op */ }
        }
    }

    private void append(char c) {
        if (input.length() >= 12) return;
        input.append(c);
        redraw();
    }
}
