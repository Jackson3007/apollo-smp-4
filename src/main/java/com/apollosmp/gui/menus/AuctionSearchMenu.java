package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/** Browse-based auction search: pick a category and a sort order, no typing needed. */
public class AuctionSearchMenu extends Gui {

    /** id, display name, icon. Keep in step with AuctionMenu.inCategory(). */
    private static final String[][] CATEGORIES = {
            {"all", "Everything", "CHEST"},
            {"weapons", "Weapons", "DIAMOND_SWORD"},
            {"tools", "Tools", "DIAMOND_PICKAXE"},
            {"armor", "Armor", "DIAMOND_CHESTPLATE"},
            {"ores", "Ores & Ingots", "DIAMOND"},
            {"blocks", "Blocks", "GRASS_BLOCK"},
            {"food", "Food", "COOKED_BEEF"},
            {"redstone", "Redstone", "REDSTONE"},
            {"potions", "Potions", "BREWING_STAND"},
            {"enchanted", "Enchanted", "ENCHANTED_BOOK"},
            {"spawners", "Spawners & Eggs", "SPAWNER"},
            {"apollo", "Apollo Gear", "NETHER_STAR"},
    };
    private static final int[] CATEGORY_SLOTS = {19, 20, 21, 22, 23, 24, 28, 29, 30, 31, 32, 33};

    private static final String[][] SORTS = {
            {"recent", "Newest First", "CLOCK"},
            {"price_low", "Price: Low to High", "GOLD_NUGGET"},
            {"price_high", "Price: High to Low", "GOLD_BLOCK"},
            {"ending", "Ending Soon", "REDSTONE_TORCH"},
    };
    private static final int[] SORT_SLOTS = {38, 39, 41, 42};

    private static final int KEYBOARD = 45;
    private static final int VIEW = 49;
    private static final int BACK = 53;

    private String category;
    private String sort;
    private final String query;

    public AuctionSearchMenu(ApolloSMP plugin, Player viewer, String category, String sort, String query) {
        super(plugin, viewer, 6, "<#5ad1e8><bold>Search the Auction House</bold>");
        this.category = category == null ? "all" : category;
        this.sort = sort == null ? "recent" : sort;
        this.query = query;
    }

    @Override
    protected void build() {
        inventory.setItem(4, Items.of(Material.SPYGLASS)
                .name("<gradient:#f9d423:#ff4e50><bold>Find an Item</bold></gradient>")
                .lore("<gray>Pick a category and a sort order,",
                        "<gray>then hit <green>View Results</green>.",
                        "",
                        "<gray>Searching: <white>" + (query == null ? "everything" : query) + "</white>")
                .glow(true).hideAttributes().build());

        for (int i = 0; i < CATEGORIES.length && i < CATEGORY_SLOTS.length; i++) {
            String id = CATEGORIES[i][0];
            boolean active = id.equals(category);
            inventory.setItem(CATEGORY_SLOTS[i], Items.of(material(CATEGORIES[i][2]))
                    .name((active ? "<green>" : "<#f9d423>") + "<bold>" + CATEGORIES[i][1] + "</bold>")
                    .lore(active ? "<green>\u2714 Selected" : "<yellow>Click to select")
                    .glow(active).hideAttributes().build());
        }

        for (int i = 0; i < SORTS.length && i < SORT_SLOTS.length; i++) {
            String id = SORTS[i][0];
            boolean active = id.equals(sort);
            inventory.setItem(SORT_SLOTS[i], Items.of(material(SORTS[i][2]))
                    .name((active ? "<green>" : "<#5ad1e8>") + SORTS[i][1])
                    .lore(active ? "<green>\u2714 Selected" : "<yellow>Click to sort this way")
                    .glow(active).hideAttributes().build());
        }

        inventory.setItem(KEYBOARD, Items.of(Material.NAME_TAG)
                .name("<#5ad1e8>Search by Name")
                .lore("<gray>Type an item name on an",
                        "<gray>on-screen keyboard.",
                        query == null ? "" : "<gray>Current: <white>" + query + "</white>")
                .build());

        inventory.setItem(VIEW, Items.of(Material.LIME_DYE)
                .name("<green><bold>View Results</bold>")
                .glow(true).hideAttributes().build());

        inventory.setItem(BACK, Items.of(Material.ARROW).name("<gray>Back to Auction House").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        for (int i = 0; i < CATEGORIES.length && i < CATEGORY_SLOTS.length; i++) {
            if (CATEGORY_SLOTS[i] == slot) {
                category = CATEGORIES[i][0];
                redraw();
                return;
            }
        }
        for (int i = 0; i < SORTS.length && i < SORT_SLOTS.length; i++) {
            if (SORT_SLOTS[i] == slot) {
                sort = SORTS[i][0];
                redraw();
                return;
            }
        }
        switch (slot) {
            case KEYBOARD -> new TextEntryMenu(plugin, player,
                    "<#5ad1e8><bold>Search by Name</bold>",
                    "Item name to search for",
                    query, 24,
                    typed -> new AuctionMenu(plugin, player, false, 0, typed, category, sort).open(),
                    () -> new AuctionSearchMenu(plugin, player, category, sort, query).open()
            ).open();
            case VIEW -> new AuctionMenu(plugin, player, false, 0, query, category, sort).open();
            case BACK -> new AuctionMenu(plugin, player, false, 0).open();
            default -> { /* no-op */ }
        }
    }

    private Material material(String name) {
        Material m = Material.matchMaterial(name);
        return m == null ? Material.PAPER : m;
    }
}
