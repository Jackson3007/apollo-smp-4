package com.apollosmp.items;

import com.apollosmp.ApolloSMP;
import com.apollosmp.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Builds the exclusive coin-shop gear and the vote key, all tagged for identification. */
public class CustomItems {

    public static final String DRILL = "drill";
    public static final String BLADE = "blade";
    public static final String CLEAVER = "cleaver";
    public static final String BOW = "bow";
    public static final String BOOTS = "boots";
    public static final String VOTE_KEY = "votekey";

    private final ApolloSMP plugin;
    private final NamespacedKey idKey;

    public CustomItems(ApolloSMP plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "apollo_item");
    }

    public String readId(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    public ItemStack build(String id) {
        return switch (id) {
            case DRILL -> drill();
            case BLADE -> blade();
            case CLEAVER -> cleaver();
            case BOW -> bow();
            case BOOTS -> boots();
            case VOTE_KEY -> voteKey();
            default -> null;
        };
    }

    public ItemStack drill() {
        ItemStack item = base(Material.NETHERITE_PICKAXE, DRILL,
                "<gradient:#5ad1e8:#7d3cff><bold>Sky Drill</bold></gradient>",
                List.of("<gray>Mines a <white>3x3</white> area at once.", "<gray>Fortune-boosted."));
        ItemMeta meta = item.getItemMeta();
        enchant(meta, "efficiency", 5);
        enchant(meta, "fortune", 3);
        enchant(meta, "unbreaking", 3);
        enchant(meta, "mending", 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack blade() {
        ItemStack item = base(Material.NETHERITE_SWORD, BLADE,
                "<gradient:#ff4e50:#f9d423><bold>Sky Blade</bold></gradient>",
                List.of("<gray>A blade forged in sunfire."));
        ItemMeta meta = item.getItemMeta();
        enchant(meta, "sharpness", 5);
        enchant(meta, "fire_aspect", 2);
        enchant(meta, "looting", 3);
        enchant(meta, "unbreaking", 3);
        enchant(meta, "mending", 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack cleaver() {
        ItemStack item = base(Material.NETHERITE_AXE, CLEAVER,
                "<gradient:#c9a36a:#ff8a3d><bold>Sky Cleaver</bold></gradient>",
                List.of("<gray>Fells foes and forests alike."));
        ItemMeta meta = item.getItemMeta();
        enchant(meta, "sharpness", 4);
        enchant(meta, "efficiency", 4);
        enchant(meta, "unbreaking", 3);
        enchant(meta, "mending", 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack bow() {
        ItemStack item = base(Material.BOW, BOW,
                "<gradient:#b7f542:#3dbb2f><bold>Sky Bow</bold></gradient>",
                List.of("<gray>Never runs out of arrows."));
        ItemMeta meta = item.getItemMeta();
        enchant(meta, "power", 5);
        enchant(meta, "flame", 1);
        enchant(meta, "infinity", 1);
        enchant(meta, "unbreaking", 3);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack boots() {
        ItemStack item = base(Material.NETHERITE_BOOTS, BOOTS,
                "<gradient:#5ad1e8:#1f6fb0><bold>Sky Striders</bold></gradient>",
                List.of("<gray>Walk on water, fall like a feather."));
        ItemMeta meta = item.getItemMeta();
        enchant(meta, "protection", 4);
        enchant(meta, "feather_falling", 4);
        enchant(meta, "depth_strider", 3);
        enchant(meta, "unbreaking", 3);
        enchant(meta, "mending", 1);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack voteKey() {
        return base(Material.TRIPWIRE_HOOK, VOTE_KEY,
                "<gradient:#f9d423:#ff4e50><bold>Vote Key</bold></gradient>",
                List.of("<gray>Right-click anywhere to spin", "<gray>the <#f9d423>Vote Crate</#f9d423> for a prize!"));
    }

    // ---- helpers ----

    private ItemStack base(Material material, String id, String nameMini, List<String> loreMini) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Msg.lore(nameMini));
            List<Component> lore = new ArrayList<>();
            for (String l : loreMini) lore.add(Msg.lore(l));
            meta.lore(lore);
            meta.setEnchantmentGlintOverride(true);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void enchant(ItemMeta meta, String key, int level) {
        if (meta == null) return;
        try {
            Enchantment e = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key));
            if (e != null) meta.addEnchant(e, level, true);
        } catch (Throwable ignored) {
            // Enchantment not present on this version; skip it safely.
        }
    }
}
