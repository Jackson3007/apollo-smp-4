package com.apollosmp.sell;

import com.apollosmp.ApolloSMP;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.enchantments.Enchantment;

import java.util.EnumMap;
import java.util.Map;

/**
 * Server buy-back prices. A large built-in default table covers most items a
 * survival player collects; anything under sell.prices in config.yml overrides it.
 */
public class SellManager {

    private final ApolloSMP plugin;
    private final Map<Material, Double> prices = new EnumMap<>(Material.class);

    private static final String[] COLORS = {
            "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY",
            "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK"
    };
    private static final String[] WOODS = {
            "OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", "MANGROVE", "CHERRY",
            "PALE_OAK", "BAMBOO", "CRIMSON", "WARPED"
    };
    private static final String[] SAPLINGS = {
            "OAK_SAPLING", "SPRUCE_SAPLING", "BIRCH_SAPLING", "JUNGLE_SAPLING", "ACACIA_SAPLING",
            "DARK_OAK_SAPLING", "MANGROVE_PROPAGULE", "CHERRY_SAPLING", "PALE_OAK_SAPLING"
    };
    private static final String[] FLOWERS = {
            "DANDELION", "POPPY", "BLUE_ORCHID", "ALLIUM", "AZURE_BLUET", "RED_TULIP", "ORANGE_TULIP",
            "WHITE_TULIP", "PINK_TULIP", "OXEYE_DAISY", "CORNFLOWER", "LILY_OF_THE_VALLEY",
            "SUNFLOWER", "LILAC", "ROSE_BUSH", "PEONY", "WITHER_ROSE", "TORCHFLOWER", "PINK_PETALS"
    };

    public SellManager(ApolloSMP plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        prices.clear();
        loadDefaults();

        // Config overrides / extends the defaults.
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("sell.prices");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat == null) {
                    plugin.getLogger().warning("Unknown sell material in config: " + key);
                    continue;
                }
                prices.put(mat, sec.getDouble(key));
            }
        }
    }

    private void put(String name, double price) {
        Material mat = Material.matchMaterial(name);
        if (mat != null) prices.put(mat, price);
    }

    private void loadDefaults() {
        // ---- Ores, minerals, ingots, gems ----
        put("COAL", 2); put("CHARCOAL", 2); put("COAL_BLOCK", 18);
        put("RAW_IRON", 4); put("IRON_INGOT", 6); put("IRON_NUGGET", 0.6);
        put("IRON_BLOCK", 54); put("RAW_IRON_BLOCK", 36);
        put("RAW_COPPER", 2.5); put("COPPER_INGOT", 3); put("COPPER_BLOCK", 27);
        put("RAW_COPPER_BLOCK", 22.5);
        put("RAW_GOLD", 8); put("GOLD_INGOT", 12); put("GOLD_NUGGET", 1.3);
        put("GOLD_BLOCK", 108); put("RAW_GOLD_BLOCK", 72);
        put("DIAMOND", 45); put("DIAMOND_BLOCK", 405);
        put("EMERALD", 30); put("EMERALD_BLOCK", 270);
        put("LAPIS_LAZULI", 3); put("LAPIS_BLOCK", 27);
        put("REDSTONE", 2); put("REDSTONE_BLOCK", 18);
        put("QUARTZ", 2.5); put("QUARTZ_BLOCK", 10);
        put("AMETHYST_SHARD", 3); put("AMETHYST_BLOCK", 12);
        put("NETHERITE_SCRAP", 120); put("NETHERITE_INGOT", 500); put("NETHERITE_BLOCK", 4500);
        put("ANCIENT_DEBRIS", 150);

        // ---- Crops & farm produce ----
        put("WHEAT", 1); put("WHEAT_SEEDS", 0.2); put("HAY_BLOCK", 9);
        put("CARROT", 1); put("GOLDEN_CARROT", 3);
        put("POTATO", 1); put("BAKED_POTATO", 1.5); put("POISONOUS_POTATO", 0.2);
        put("BEETROOT", 1); put("BEETROOT_SEEDS", 0.2);
        put("SUGAR_CANE", 1); put("SUGAR", 0.5);
        put("PUMPKIN", 2); put("CARVED_PUMPKIN", 2); put("PUMPKIN_SEEDS", 0.2);
        put("MELON_SLICE", 0.5); put("MELON", 4); put("MELON_SEEDS", 0.2);
        put("NETHER_WART", 2); put("NETHER_WART_BLOCK", 18);
        put("COCOA_BEANS", 1); put("SWEET_BERRIES", 0.5); put("GLOW_BERRIES", 0.5);
        put("KELP", 0.2); put("DRIED_KELP", 0.3); put("DRIED_KELP_BLOCK", 2.7);
        put("BAMBOO", 0.2); put("CACTUS", 0.5); put("CHORUS_FRUIT", 1); put("POPPED_CHORUS_FRUIT", 1);
        put("APPLE", 2); put("GOLDEN_APPLE", 40); put("ENCHANTED_GOLDEN_APPLE", 400);
        put("BREAD", 2); put("COOKIE", 0.5); put("PUMPKIN_PIE", 3); put("CAKE", 5);
        put("MUSHROOM_STEW", 2); put("BEETROOT_SOUP", 2); put("RABBIT_STEW", 3);
        put("BROWN_MUSHROOM", 0.5); put("RED_MUSHROOM", 0.5);
        put("EGG", 0.5); put("MILK_BUCKET", 3); put("HONEY_BOTTLE", 2); put("HONEYCOMB", 2);
        put("HONEY_BLOCK", 8); put("HONEYCOMB_BLOCK", 8);

        // ---- Meat & fish ----
        put("BEEF", 2); put("COOKED_BEEF", 3); put("PORKCHOP", 2); put("COOKED_PORKCHOP", 3);
        put("CHICKEN", 2); put("COOKED_CHICKEN", 3); put("MUTTON", 2); put("COOKED_MUTTON", 3);
        put("RABBIT", 2); put("COOKED_RABBIT", 3); put("RABBIT_FOOT", 5); put("RABBIT_HIDE", 1);
        put("COD", 2); put("COOKED_COD", 3); put("SALMON", 2); put("COOKED_SALMON", 3);
        put("TROPICAL_FISH", 3); put("PUFFERFISH", 2);

        // ---- Mob drops ----
        put("ROTTEN_FLESH", 0.5); put("BONE", 1); put("BONE_MEAL", 0.3); put("BONE_BLOCK", 9);
        put("STRING", 1); put("GUNPOWDER", 3); put("SPIDER_EYE", 1.5); put("FERMENTED_SPIDER_EYE", 2);
        put("ENDER_PEARL", 12); put("ENDER_EYE", 15);
        put("BLAZE_ROD", 10); put("BLAZE_POWDER", 5);
        put("GHAST_TEAR", 25); put("SLIME_BALL", 3); put("SLIME_BLOCK", 27);
        put("MAGMA_CREAM", 4); put("PHANTOM_MEMBRANE", 8);
        put("SHULKER_SHELL", 20); put("DRAGON_BREATH", 10);
        put("PRISMARINE_SHARD", 2); put("PRISMARINE_CRYSTALS", 3);
        put("NAUTILUS_SHELL", 15); put("HEART_OF_THE_SEA", 100);
        put("TURTLE_SCUTE", 10); put("ARMADILLO_SCUTE", 8);
        put("INK_SAC", 1); put("GLOW_INK_SAC", 2);
        put("FEATHER", 0.5); put("LEATHER", 2);
        put("NETHER_STAR", 500); put("WITHER_SKELETON_SKULL", 75);
        put("ECHO_SHARD", 15); put("SCULK", 0.5); put("SCULK_VEIN", 0.5); put("SCULK_CATALYST", 5);
        put("TOTEM_OF_UNDYING", 50); put("EXPERIENCE_BOTTLE", 5);
        put("SPIDER_WEB", 1); put("COBWEB", 1);

        // ---- Stone & natural blocks ----
        put("STONE", 0.3); put("COBBLESTONE", 0.2); put("MOSSY_COBBLESTONE", 0.3);
        put("DEEPSLATE", 0.3); put("COBBLED_DEEPSLATE", 0.2);
        put("GRANITE", 0.3); put("DIORITE", 0.3); put("ANDESITE", 0.3);
        put("CALCITE", 0.5); put("TUFF", 0.3);
        put("DIRT", 0.1); put("COARSE_DIRT", 0.1); put("ROOTED_DIRT", 0.2);
        put("GRASS_BLOCK", 0.3); put("PODZOL", 0.3); put("MYCELIUM", 0.5); put("MUD", 0.2);
        put("SAND", 0.3); put("RED_SAND", 0.3); put("GRAVEL", 0.2); put("FLINT", 0.5);
        put("CLAY", 0.5); put("CLAY_BALL", 0.1); put("SANDSTONE", 0.4); put("RED_SANDSTONE", 0.4);
        put("NETHERRACK", 0.1); put("SOUL_SAND", 0.5); put("SOUL_SOIL", 0.5);
        put("MAGMA_BLOCK", 1); put("GLOWSTONE", 3); put("GLOWSTONE_DUST", 0.75);
        put("OBSIDIAN", 5); put("CRYING_OBSIDIAN", 8);
        put("BASALT", 0.3); put("SMOOTH_BASALT", 0.3); put("BLACKSTONE", 0.3); put("GILDED_BLACKSTONE", 5);
        put("END_STONE", 0.5); put("PURPUR_BLOCK", 1); put("SHROOMLIGHT", 3);
        put("ICE", 0.5); put("PACKED_ICE", 1); put("BLUE_ICE", 2);
        put("SNOWBALL", 0.1); put("SNOW_BLOCK", 0.3);
        put("MOSS_BLOCK", 1); put("MOSS_CARPET", 0.5);
        put("DRIPSTONE_BLOCK", 0.5); put("POINTED_DRIPSTONE", 0.5);
        put("GLASS", 0.3); put("SEA_LANTERN", 5); put("SPONGE", 5); put("WET_SPONGE", 3);
        put("NETHER_BRICK", 0.4); put("QUARTZ_BRICKS", 0.5);
        put("CRIMSON_NYLIUM", 0.5); put("WARPED_NYLIUM", 0.5);
        put("PRISMARINE", 0.5); put("PRISMARINE_BRICKS", 0.6); put("DARK_PRISMARINE", 0.6);
        put("TERRACOTTA", 0.4); put("STICK", 0.1); put("VINE", 0.3); put("LILY_PAD", 0.5);
        put("SEAGRASS", 0.2); put("GLOW_LICHEN", 0.5); put("BIG_DRIPLEAF", 0.5); put("SMALL_DRIPLEAF", 0.5);
        put("SUGAR_CANE", 1); put("TWISTING_VINES", 0.3); put("WEEPING_VINES", 0.3);

        // ---- Wood: logs, wood, stems, planks (null-skips combos that don't exist) ----
        for (String w : WOODS) {
            put(w + "_LOG", 1.5);
            put(w + "_WOOD", 1.5);
            put("STRIPPED_" + w + "_LOG", 1.5);
            put("STRIPPED_" + w + "_WOOD", 1.5);
            put(w + "_STEM", 1.5);
            put(w + "_HYPHAE", 1.5);
            put("STRIPPED_" + w + "_STEM", 1.5);
            put("STRIPPED_" + w + "_HYPHAE", 1.5);
            put(w + "_PLANKS", 0.4);
        }
        put("BAMBOO_BLOCK", 1.5); put("BAMBOO_MOSAIC", 0.5);

        // ---- Saplings & flowers ----
        for (String s : SAPLINGS) put(s, 0.5);
        for (String f : FLOWERS) put(f, 0.5);

        // ---- Coloured blocks (wool, carpet, terracotta, concrete, dye, glass) ----
        for (String c : COLORS) {
            put(c + "_WOOL", 1);
            put(c + "_CARPET", 0.5);
            put(c + "_TERRACOTTA", 0.5);
            put(c + "_CONCRETE", 0.6);
            put(c + "_CONCRETE_POWDER", 0.4);
            put(c + "_DYE", 0.5);
            put(c + "_STAINED_GLASS", 0.4);
            put(c + "_GLAZED_TERRACOTTA", 1);
        }
    }

    /** Fallback price for anything not in the table. 0 disables the fallback. */
    public double defaultPrice() {
        return Math.max(0, plugin.getConfig().getDouble("sell.default-price", 0.25));
    }

    /** Materials that must never be sellable, whatever else is configured. */
    private boolean blocked(Material material) {
        if (material.isAir() || !material.isItem()) return true;
        String name = material.name();
        if (name.equals("SPAWNER") || name.equals("BEDROCK") || name.equals("BARRIER")
                || name.equals("STRUCTURE_BLOCK") || name.equals("STRUCTURE_VOID")
                || name.equals("JIGSAW") || name.equals("LIGHT") || name.equals("DEBUG_STICK")
                || name.equals("END_PORTAL_FRAME") || name.equals("COMMAND_BLOCK")
                || name.equals("CHAIN_COMMAND_BLOCK") || name.equals("REPEATING_COMMAND_BLOCK")
                || name.equals("BUNDLE") || name.endsWith("SHULKER_BOX")) {
            return true;
        }
        for (String extra : plugin.getConfig().getStringList("sell.never-sell")) {
            if (name.equalsIgnoreCase(extra)) return true;
        }
        return false;
    }

    public boolean isSellable(Material material) {
        if (blocked(material)) return false;
        if (prices.containsKey(material)) return true;
        return defaultPrice() > 0;
    }

    /**
     * Plugin-made items (businesses, merchant tools, tagged spawners and so on)
     * are never sellable - they'd be worth far more than any price we'd give.
     */
    private boolean isPluginItem(ItemStack stack) {
        try {
            if (plugin.businesses() != null && plugin.businesses().readBusinessId(stack) != null) return true;
            if (plugin.specialBusinesses() != null && plugin.specialBusinesses().readId(stack) != null) return true;
            if (plugin.merchant() != null && plugin.merchant().toolType(stack) != null) return true;
            if (plugin.logistics() != null && plugin.logistics().readType(stack) != null) return true;
            if (plugin.spawners() != null && plugin.spawners().readType(stack) != null) return true;
            if (plugin.customItems() != null && plugin.customItems().readId(stack) != null) return true;
        } catch (Exception ignored) {
            // if anything isn't ready yet, fall through and treat it as normal
        }
        return false;
    }

    public boolean isSellable(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        if (isPluginItem(stack)) return false;
        return isSellable(stack.getType());
    }

    public double priceOf(Material material) {
        Double listed = prices.get(material);
        if (listed != null) return listed;
        return blocked(material) ? 0.0 : defaultPrice();
    }

    /** Total value of a stack, accounting for gear, enchantments and wear. */
    public double valueOf(ItemStack stack) {
        if (!isSellable(stack)) return 0;

        double each = priceOf(stack.getType());
        if (plugin.getConfig().getBoolean("sell.gear-pricing", true)) {
            double crafted = craftValue(stack.getType());
            if (crafted > 0) each = crafted;
            each += enchantValue(stack);
            each *= condition(stack);
        }
        return each * stack.getAmount();
    }

    /** What the raw materials for a tool, weapon or armour piece are worth. */
    public double craftValue(Material material) {
        String name = material.name();
        Material base = tierMaterial(name);
        if (base != null) {
            int units = pieceCount(name);
            if (units > 0) {
                double stickCost = priceOf(Material.STICK) * stickCount(name);
                return priceOf(base) * units + stickCost;
            }
        }
        // A few things aren't made from a simple tier material.
        return switch (name) {
            case "BOW" -> priceOf(Material.STICK) * 3 + priceOf(Material.STRING) * 3;
            case "CROSSBOW" -> priceOf(Material.STICK) * 3 + priceOf(Material.STRING) * 2
                    + priceOf(Material.IRON_INGOT) + priceOf(Material.TRIPWIRE_HOOK);
            case "SHIELD" -> priceOf(Material.IRON_INGOT) + priceOf(Material.OAK_PLANKS) * 6;
            case "SHEARS" -> priceOf(Material.IRON_INGOT) * 2;
            case "FISHING_ROD" -> priceOf(Material.STICK) * 3 + priceOf(Material.STRING) * 2;
            case "FLINT_AND_STEEL" -> priceOf(Material.IRON_INGOT) + priceOf(Material.FLINT);
            case "TRIDENT" -> priceOf(Material.DIAMOND) * 6;
            case "ELYTRA" -> priceOf(Material.DIAMOND) * 12;
            default -> 0;
        };
    }

    /** The metal or mineral a piece of gear is made from. */
    private Material tierMaterial(String name) {
        if (name.startsWith("WOODEN_")) return Material.OAK_PLANKS;
        if (name.startsWith("STONE_")) return Material.COBBLESTONE;
        if (name.startsWith("IRON_") || name.startsWith("CHAINMAIL_")) return Material.IRON_INGOT;
        if (name.startsWith("GOLDEN_")) return Material.GOLD_INGOT;
        if (name.startsWith("DIAMOND_")) return Material.DIAMOND;
        if (name.startsWith("NETHERITE_")) return Material.NETHERITE_INGOT;
        if (name.startsWith("LEATHER_")) return Material.LEATHER;
        if (name.startsWith("TURTLE_")) return Material.SCUTE;
        return null;
    }

    /** How many units of the tier material the recipe uses. */
    private int pieceCount(String name) {
        if (name.endsWith("_SWORD")) return 2;
        if (name.endsWith("_PICKAXE") || name.endsWith("_AXE")) return 3;
        if (name.endsWith("_SHOVEL")) return 1;
        if (name.endsWith("_HOE")) return 2;
        if (name.endsWith("_HELMET")) return 5;
        if (name.endsWith("_CHESTPLATE")) return 8;
        if (name.endsWith("_LEGGINGS")) return 7;
        if (name.endsWith("_BOOTS")) return 4;
        return 0;
    }

    private int stickCount(String name) {
        if (name.endsWith("_SWORD") || name.endsWith("_SHOVEL")) return 1;
        if (name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_HOE")) return 2;
        return 0;
    }

    /** Enchantments are where the real money is. */
    public double enchantValue(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return 0;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0;

        double perLevel = plugin.getConfig().getDouble("sell.enchant-value", 250.0);
        double total = 0;

        for (Map.Entry<Enchantment, Integer> e : meta.getEnchants().entrySet()) {
            total += perLevel * e.getValue() * enchantMultiplier(e.getKey());
        }
        // Enchanted books keep their value in a separate place.
        if (meta instanceof EnchantmentStorageMeta book) {
            for (Map.Entry<Enchantment, Integer> e : book.getStoredEnchants().entrySet()) {
                total += perLevel * e.getValue() * enchantMultiplier(e.getKey());
            }
        }
        return total;
    }

    /** The sought-after enchantments are worth a good deal more. */
    private double enchantMultiplier(Enchantment enchantment) {
        String key;
        try {
            key = enchantment.getKey().getKey().toLowerCase();
        } catch (Throwable ex) {
            return 1.0;
        }
        return switch (key) {
            case "mending" -> 4.0;
            case "silk_touch", "infinity" -> 3.0;
            case "fortune", "looting" -> 2.5;
            case "sharpness", "efficiency", "protection", "unbreaking", "power" -> 1.5;
            case "feather_falling", "depth_strider", "thorns", "riptide", "channeling" -> 1.4;
            case "fire_aspect", "flame", "knockback", "punch", "smite", "bane_of_arthropods" -> 0.8;
            default -> 1.0;
        };
    }

    /** Worn gear is worth less, but never worthless. */
    private double condition(ItemStack stack) {
        short max = stack.getType().getMaxDurability();
        if (max <= 0) return 1.0;
        if (!(stack.getItemMeta() instanceof Damageable damageable)) return 1.0;
        if (!damageable.hasDamage()) return 1.0;
        double remaining = Math.max(0, 1.0 - ((double) damageable.getDamage() / max));
        // Keep a floor so a battered but enchanted tool still sells.
        return 0.4 + (0.6 * remaining);
    }

    public Map<Material, Double> allPrices() {
        return new EnumMap<>(prices);
    }

    public int sellableCount() {
        return prices.size();
    }

    /** Result of a sell operation. */
    public record Result(int quantity, double earned) {
        public boolean soldAnything() { return quantity > 0; }
    }

    /** Sell the item currently in the main hand (whole stack). */
    public Result sellHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isSellable(hand)) return new Result(0, 0);
        int qty = hand.getAmount();
        double earned = qty * priceOf(hand.getType());
        player.getInventory().setItemInMainHand(null);
        plugin.economy().deposit(player.getUniqueId(), earned);
        return new Result(qty, earned);
    }

    /** Sell every sellable item in the player's inventory (skips named/enchanted gear). */
    public Result sellAll(Player player) {
        int totalQty = 0;
        double totalEarned = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (!isSellable(stack)) continue;
            if (stack.hasItemMeta() && (stack.getItemMeta().hasDisplayName()
                    || stack.getItemMeta().hasEnchants())) {
                continue;
            }
            int qty = stack.getAmount();
            totalQty += qty;
            totalEarned += qty * priceOf(stack.getType());
            contents[i] = null;
        }
        if (totalQty > 0) {
            player.getInventory().setStorageContents(contents);
            plugin.economy().deposit(player.getUniqueId(), totalEarned);
        }
        return new Result(totalQty, totalEarned);
    }
}
