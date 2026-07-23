package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.auction.AuctionManager;
import com.apollosmp.auction.Listing;
import com.apollosmp.gui.Gui;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class AuctionMenu extends Gui {

    private static final int PAGE_SIZE = 45;

    private final boolean mine;
    private final int page;
    private final String query;
    private final String category;
    private final String sort;
    private List<Listing> pageItems = new ArrayList<>();

    public AuctionMenu(ApolloSMP plugin, Player viewer, boolean mine, int page) {
        this(plugin, viewer, mine, page, null, null, null);
    }

    public AuctionMenu(ApolloSMP plugin, Player viewer, boolean mine, int page, String query) {
        this(plugin, viewer, mine, page, query, null, null);
    }

    public AuctionMenu(ApolloSMP plugin, Player viewer, boolean mine, int page,
                       String query, String category, String sort) {
        super(plugin, viewer, 6, mine
                ? "<#ff4e50><bold>My Listings</bold>"
                : "<gradient:#f9d423:#ff4e50><bold>Auction House</bold></gradient>");
        this.mine = mine;
        this.page = Math.max(0, page);
        this.query = (query == null || query.isBlank()) ? null : query;
        this.category = (category == null || category.equals("all")) ? null : category;
        this.sort = (sort == null || sort.isBlank()) ? "recent" : sort;
    }

    /** True when any filter is narrowing the list. */
    private boolean filtered() { return query != null || category != null; }

    @Override
    protected void build() {
        List<Listing> all = mine
                ? plugin.auctions().bySeller(viewer.getUniqueId())
                : plugin.auctions().active();

        if (!mine && filtered()) {
            List<Listing> kept = new ArrayList<>();
            for (Listing l : all) {
                if (query != null && !matches(l, query)) continue;
                if (category != null && !inCategory(l.item(), category)) continue;
                kept.add(l);
            }
            all = kept;
        }
        if (!mine) {
            all = new ArrayList<>(all);
            switch (sort) {
                case "price_low" -> all.sort(java.util.Comparator.comparingDouble(Listing::price));
                case "price_high" -> all.sort(java.util.Comparator.comparingDouble(Listing::price).reversed());
                case "ending" -> all.sort(java.util.Comparator.comparingLong(Listing::millisLeft));
                default -> all.sort(java.util.Comparator.comparingLong(Listing::createdAt).reversed());
            }
        }

        int from = page * PAGE_SIZE;
        int to = Math.min(all.size(), from + PAGE_SIZE);
        pageItems = (from >= all.size()) ? new ArrayList<>() : new ArrayList<>(all.subList(from, to));

        for (int i = 0; i < pageItems.size(); i++) {
            Listing listing = pageItems.get(i);
            ItemStack icon = listing.item();
            // Clear any leftover inventory price tag before we read the meta.
            if (plugin.worthTags() != null) plugin.worthTags().strip(icon);
            ItemMeta meta = icon.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(com.apollosmp.util.Msg.lore("<dark_gray>―――――――――――"));
            lore.add(com.apollosmp.util.Msg.lore("<gray>Seller: <white>" + listing.sellerName() + "</white>"));
            lore.add(com.apollosmp.util.Msg.lore("<gray>Price: <#f9d423>"
                    + plugin.msg().money(listing.price()) + "</#f9d423>"));
            lore.add(com.apollosmp.util.Msg.lore("<gray>Time left: <white>" + formatDuration(listing.millisLeft()) + "</white>"));
            lore.add(com.apollosmp.util.Msg.lore(""));
            if (mine) {
                lore.add(com.apollosmp.util.Msg.lore("<red>Click to cancel & reclaim"));
            } else if (listing.seller().equals(viewer.getUniqueId())) {
                lore.add(com.apollosmp.util.Msg.lore("<dark_gray>This is your listing"));
            } else {
                lore.add(com.apollosmp.util.Msg.lore("<yellow>Click to buy"));
            }
            if (meta != null) {
                // preserve existing lore then append info
                List<net.kyori.adventure.text.Component> existing = meta.lore();
                if (existing != null) {
                    existing.addAll(lore);
                    meta.lore(existing);
                } else {
                    meta.lore(lore);
                }
                icon.setItemMeta(meta);
            }
            inventory.setItem(i, icon);
        }

        for (int i = PAGE_SIZE; i < 54; i++) {
            inventory.setItem(i, Items.filler(Material.BLACK_STAINED_GLASS_PANE));
        }

        inventory.setItem(45, Items.of(Material.ARROW).name("<gray>Previous Page").build());
        inventory.setItem(46, Items.of(Material.BARRIER).name("<gray>Back to Menu").build());
        inventory.setItem(47, Items.of(mine ? Material.CHEST : Material.PLAYER_HEAD)
                .name(mine ? "<#f9d423>Browse All" : "<#f9d423>My Listings")
                .lore("<gray>Active listings: <white>"
                        + plugin.auctions().countBySeller(viewer.getUniqueId()) + "</white>")
                .build());

        if (!mine) {
            inventory.setItem(48, Items.of(Material.SPYGLASS)
                    .name("<#5ad1e8><bold>Search</bold>")
                    .lore("<gray>Browse by category, sort by price,",
                            "<gray>or search by name.",
                            "",
                            "<gray>Category: <white>" + (category == null ? "All" : pretty(category)) + "</white>",
                            "<gray>Sort: <white>" + sortName(sort) + "</white>",
                            query == null ? "" : "<gray>Name: <white>" + query + "</white>",
                            "",
                            "<yellow>Click to open search")
                    .glow(filtered()).hideAttributes().build());
            if (filtered()) {
                inventory.setItem(52, Items.of(Material.BARRIER)
                        .name("<red>Clear Filters")
                        .lore("<gray>Show all listings again.").build());
            }
        }

        if (filtered()) {
            inventory.setItem(49, Items.of(Material.PAPER)
                    .name("<#f9d423><bold>Page " + (page + 1) + "</bold>")
                    .lore("<gray>Matches: <#f9d423>" + all.size() + "</#f9d423>",
                            "<gray>Sell with <white>/ah sell <price></white>").build());
        } else {
            inventory.setItem(49, Items.of(Material.PAPER)
                    .name("<#f9d423><bold>Page " + (page + 1) + "</bold>")
                    .lore("<gray>Sell an item with <white>/ah sell <price></white>").build());
        }

        inventory.setItem(51, Items.of(Material.CHEST_MINECART)
                .name("<#f9d423>Collection Box")
                .lore("<gray>Waiting items: <white>"
                        + plugin.mailbox().size(viewer.getUniqueId()) + "</white>",
                        "<yellow>Click to collect").build());
        inventory.setItem(53, Items.of(Material.ARROW).name("<gray>Next Page").build());
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot >= 0 && slot < PAGE_SIZE) {
            if (slot >= pageItems.size()) return;
            Listing listing = pageItems.get(slot);
            if (mine) {
                if (plugin.auctions().cancel(player.getUniqueId(), listing.id())) {
                    plugin.msg().send(player, "<green>Listing cancelled. Item is in your collection box.");
                }
                redraw();
            } else {
                handleBuy(player, listing);
            }
            return;
        }
        switch (slot) {
            case 45 -> { if (page > 0) new AuctionMenu(plugin, player, mine, page - 1, query, category, sort).open(); }
            case 46 -> new MainMenu(plugin, player).open();
            case 47 -> new AuctionMenu(plugin, player, !mine, 0).open();
            case 48 -> {
                if (!mine) new AuctionSearchMenu(plugin, player, category, sort, query).open();
            }
            case 52 -> { if (filtered()) new AuctionMenu(plugin, player, false, 0).open(); }
            case 51 -> {
                int n = plugin.mailbox().collect(player);
                plugin.msg().send(player, n == 0 ? "<gray>Your collection box is empty."
                        : "<green>Collected <white>" + n + "</white> item stack(s).");
                redraw();
            }
            case 53 -> new AuctionMenu(plugin, player, mine, page + 1, query, category, sort).open();
            default -> { /* no-op */ }
        }
    }

    private void handleBuy(Player player, Listing listing) {
        AuctionManager.BuyResult result = plugin.auctions().buy(player, listing.id());
        switch (result) {
            case SUCCESS -> {
                plugin.msg().send(player, "<green>Purchased <white>" + Items.displayName(listing.item())
                        + "</white> for <#f9d423>" + plugin.msg().money(listing.price()) + "</#f9d423>.");
                redraw();
            }
            case NOT_FOUND -> {
                plugin.msg().send(player, "<red>That listing is no longer available.");
                redraw();
            }
            case OWN_LISTING -> plugin.msg().send(player, "<red>You can't buy your own listing.");
            case NO_FUNDS -> plugin.msg().send(player, "<red>You can't afford that.");
        }
    }

    private String sortName(String s) {
        return switch (s) {
            case "price_low" -> "Price (low)";
            case "price_high" -> "Price (high)";
            case "ending" -> "Ending soon";
            default -> "Newest";
        };
    }

    private String pretty(String id) {
        return switch (id) {
            case "weapons" -> "Weapons";
            case "tools" -> "Tools";
            case "armor" -> "Armor";
            case "ores" -> "Ores & Ingots";
            case "blocks" -> "Blocks";
            case "food" -> "Food";
            case "redstone" -> "Redstone";
            case "potions" -> "Potions";
            case "enchanted" -> "Enchanted";
            case "spawners" -> "Spawners & Eggs";
            case "apollo" -> "Apollo Gear";
            default -> "All";
        };
    }

    /** Category test shared with AuctionSearchMenu's category ids. */
    private boolean inCategory(ItemStack it, String cat) {
        Material m = it.getType();
        String n = m.name();
        return switch (cat) {
            case "weapons" -> n.endsWith("_SWORD") || n.endsWith("_AXE") || n.equals("BOW")
                    || n.equals("CROSSBOW") || n.equals("TRIDENT") || n.endsWith("ARROW")
                    || n.equals("SHIELD") || n.equals("MACE");
            case "tools" -> n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")
                    || n.equals("SHEARS") || n.equals("FISHING_ROD") || n.equals("FLINT_AND_STEEL")
                    || n.equals("SPYGLASS") || n.equals("BRUSH");
            case "armor" -> n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS")
                    || n.endsWith("_BOOTS") || n.equals("ELYTRA") || n.endsWith("_HORSE_ARMOR");
            case "ores" -> n.contains("DIAMOND") || n.contains("EMERALD") || n.contains("NETHERITE")
                    || n.contains("ANCIENT_DEBRIS") || n.contains("GOLD") || n.contains("IRON")
                    || n.contains("COPPER") || n.contains("COAL") || n.contains("LAPIS")
                    || n.contains("QUARTZ") || n.contains("AMETHYST");
            case "food" -> m.isEdible();
            case "redstone" -> n.contains("REDSTONE") || n.contains("REPEATER") || n.contains("COMPARATOR")
                    || n.contains("PISTON") || n.equals("OBSERVER") || n.equals("HOPPER")
                    || n.equals("DISPENSER") || n.equals("DROPPER") || n.contains("RAIL")
                    || n.equals("LEVER") || n.endsWith("_BUTTON") || n.contains("PRESSURE_PLATE")
                    || n.equals("TARGET") || n.equals("TNT");
            case "potions" -> n.contains("POTION") || n.equals("BREWING_STAND")
                    || n.equals("GLASS_BOTTLE") || n.equals("NETHER_WART") || n.equals("BLAZE_POWDER");
            case "enchanted" -> n.equals("ENCHANTED_BOOK")
                    || (it.getItemMeta() != null && !it.getItemMeta().getEnchants().isEmpty());
            case "spawners" -> n.equals("SPAWNER") || n.endsWith("_SPAWN_EGG");
            case "apollo" -> plugin.customItems().readId(it) != null;
            case "blocks" -> m.isBlock();
            default -> true;
        };
    }

    private boolean matches(Listing listing, String q) {
        String needle = q.toLowerCase();
        ItemStack it = listing.item();
        String name = Items.displayName(it).toLowerCase();
        String type = it.getType().name().toLowerCase().replace('_', ' ');
        return name.contains(needle) || type.contains(needle);
    }

    private String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return minutes + "m";
    }
}
