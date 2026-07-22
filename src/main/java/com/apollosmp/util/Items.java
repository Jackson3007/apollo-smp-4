package com.apollosmp.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Small helpers for building menu icons and (de)serializing item stacks. */
public final class Items {

    private Items() {}

    /** Fluent builder for GUI icons. */
    public static final class Builder {
        private final ItemStack stack;
        private final ItemMeta meta;

        private Builder(Material material, int amount) {
            this.stack = new ItemStack(material, amount);
            this.meta = stack.getItemMeta();
        }

        public Builder name(String miniMessage) {
            if (meta != null) meta.displayName(Msg.lore(miniMessage));
            return this;
        }

        public Builder name(Component component) {
            if (meta != null) meta.displayName(component);
            return this;
        }

        public Builder lore(String... miniMessageLines) {
            return lore(Arrays.asList(miniMessageLines));
        }

        public Builder lore(List<String> miniMessageLines) {
            if (meta != null) {
                List<Component> out = new ArrayList<>();
                for (String line : miniMessageLines) out.add(Msg.lore(line));
                meta.lore(out);
            }
            return this;
        }

        public Builder loreComponents(List<Component> lines) {
            if (meta != null) meta.lore(lines);
            return this;
        }

        public Builder glow(boolean glow) {
            if (meta != null && glow) {
                meta.setEnchantmentGlintOverride(true);
            }
            return this;
        }

        public Builder hideAttributes() {
            if (meta != null) {
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS,
                        ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_DYE);
            }
            return this;
        }

        public ItemStack build() {
            if (meta != null) stack.setItemMeta(meta);
            return stack;
        }
    }

    public static Builder of(Material material) {
        return new Builder(material, 1);
    }

    public static Builder of(Material material, int amount) {
        return new Builder(material, amount);
    }

    /** A blank "filler" pane used to pad menus. */
    public static ItemStack filler(Material material) {
        return of(material).name(" ").build();
    }

    /** Owner-skull icon for player-facing menus. */
    public static ItemStack playerHead(Player owner, String name, List<String> loreLines) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta m = head.getItemMeta();
        if (m instanceof SkullMeta skull) {
            skull.setOwningPlayer(owner);
            skull.displayName(Msg.lore(name));
            List<Component> out = new ArrayList<>();
            for (String line : loreLines) out.add(Msg.lore(line));
            skull.lore(out);
            head.setItemMeta(skull);
        }
        return head;
    }

    /** Give items to a player, dropping any overflow at their feet. */
    public static void give(Player player, ItemStack stack) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /** Count how many of a matching item a player holds. */
    public static int countMatching(Player player, ItemStack model) {
        int total = 0;
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content != null && content.isSimilar(model)) total += content.getAmount();
        }
        return total;
    }

    /** Remove up to {@code amount} items matching the model. Returns amount actually removed. */
    public static int removeMatching(Player player, ItemStack model, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack content = contents[i];
            if (content != null && content.isSimilar(model)) {
                int take = Math.min(content.getAmount(), remaining);
                content.setAmount(content.getAmount() - take);
                remaining -= take;
                if (content.getAmount() <= 0) contents[i] = null;
            }
        }
        player.getInventory().setStorageContents(contents);
        return amount - remaining;
    }

    // ---- Persistence: ItemStack <-> Base64 -------------------------------

    public static String toBase64(ItemStack item) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bytes)) {
            out.writeObject(item);
            out.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Could not serialize item", e);
        }
    }

    public static ItemStack fromBase64(String data) {
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             BukkitObjectInputStream in = new BukkitObjectInputStream(bytes)) {
            return (ItemStack) in.readObject();
        } catch (Exception e) {
            throw new IllegalStateException("Could not deserialize item", e);
        }
    }

    /** Human-readable material name: DIAMOND_SWORD -> Diamond Sword. */
    public static String pretty(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    /** Best-effort readable name for an ItemStack (custom name if present). */
    public static String displayName(ItemStack stack) {
        if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
            Component dn = stack.getItemMeta().displayName();
            if (dn != null) {
                return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                        .plainText().serialize(dn);
            }
        }
        return pretty(stack.getType());
    }

    public static Map<String, Object> emptyMap() {
        return new HashMap<>();
    }
}
