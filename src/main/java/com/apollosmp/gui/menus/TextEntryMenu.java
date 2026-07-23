package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

/**
 * An on-screen keyboard. Lets players enter text by clicking letters, so nothing
 * here depends on chat being available.
 */
public class TextEntryMenu extends Gui {

    private static final int DISPLAY = 4;
    private static final int LETTER_START = 9;   // 9..34 = A..Z
    private static final int LETTER_END = 34;
    private static final int UNDERSCORE = 35;
    private static final int DIGIT_START = 36;   // 36..44 = 1..9
    private static final int DIGIT_END = 44;
    private static final int ZERO = 45;
    private static final int BACKSPACE = 47;
    private static final int CLEAR = 49;
    private static final int CANCEL = 51;
    private static final int CONFIRM = 53;

    private final String prompt;
    private final int maxLength;
    private final Consumer<String> onConfirm;
    private final Runnable onCancel;
    private final StringBuilder text = new StringBuilder();

    public TextEntryMenu(ApolloSMP plugin, Player viewer, String title, String prompt,
                         String initial, int maxLength,
                         Consumer<String> onConfirm, Runnable onCancel) {
        super(plugin, viewer, 6, title);
        this.prompt = prompt;
        this.maxLength = Math.max(1, maxLength);
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        if (initial != null) text.append(initial);
    }

    @Override
    protected void build() {
        String current = text.length() == 0 ? "(empty)" : text.toString();
        inventory.setItem(DISPLAY, Items.of(Material.NAME_TAG)
                .name("<#f9d423><bold>" + current + "</bold>")
                .lore("<gray>" + prompt,
                        "<gray>Length: <white>" + text.length() + "/" + maxLength + "</white>",
                        "",
                        "<gray>Click letters below to type.")
                .glow(true).hideAttributes().build());

        for (int slot = LETTER_START; slot <= LETTER_END; slot++) {
            char c = (char) ('A' + (slot - LETTER_START));
            inventory.setItem(slot, Items.of(Material.PAPER)
                    .name("<white><bold>" + c + "</bold>").build());
        }
        inventory.setItem(UNDERSCORE, Items.of(Material.PAPER)
                .name("<white><bold>_</bold>").build());

        for (int slot = DIGIT_START; slot <= DIGIT_END; slot++) {
            char c = (char) ('1' + (slot - DIGIT_START));
            inventory.setItem(slot, Items.of(Material.MAP)
                    .name("<#5ad1e8><bold>" + c + "</bold>").build());
        }
        inventory.setItem(ZERO, Items.of(Material.MAP).name("<#5ad1e8><bold>0</bold>").build());

        inventory.setItem(BACKSPACE, Items.of(Material.ARROW)
                .name("<yellow>Backspace").lore("<gray>Delete the last character.").build());
        inventory.setItem(CLEAR, Items.of(Material.WATER_BUCKET)
                .name("<yellow>Clear").lore("<gray>Erase everything.").build());
        inventory.setItem(CANCEL, Items.of(Material.BARRIER)
                .name("<red>Cancel").build());
        inventory.setItem(CONFIRM, Items.of(Material.LIME_DYE)
                .name("<green><bold>Confirm</bold>")
                .lore("<gray>Use what you've typed.").glow(true).hideAttributes().build());

        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot >= LETTER_START && slot <= LETTER_END) {
            append((char) ('A' + (slot - LETTER_START)));
            return;
        }
        if (slot >= DIGIT_START && slot <= DIGIT_END) {
            append((char) ('1' + (slot - DIGIT_START)));
            return;
        }
        switch (slot) {
            case UNDERSCORE -> append('_');
            case ZERO -> append('0');
            case BACKSPACE -> {
                if (text.length() > 0) text.deleteCharAt(text.length() - 1);
                redraw();
            }
            case CLEAR -> {
                text.setLength(0);
                redraw();
            }
            case CANCEL -> {
                if (onCancel != null) onCancel.run();
                else player.closeInventory();
            }
            case CONFIRM -> {
                String value = text.toString().trim();
                if (value.isEmpty()) {
                    plugin.msg().send(player, "<red>Type something first.");
                    return;
                }
                onConfirm.accept(value);
            }
            default -> { /* no-op */ }
        }
    }

    private void append(char c) {
        if (text.length() >= maxLength) return;
        text.append(c);
        redraw();
    }
}
