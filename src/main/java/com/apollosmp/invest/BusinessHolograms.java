package com.apollosmp.invest;

import com.apollosmp.ApolloSMP;
import com.apollosmp.util.Items;
import com.apollosmp.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Floating labels above business blocks showing income and the next payout. */
public class BusinessHolograms {

    private static final int VIEW_DISTANCE = 24;

    private final ApolloSMP plugin;
    private final NamespacedKey tagKey;
    private final Map<String, TextDisplay> labels = new ConcurrentHashMap<>();

    public BusinessHolograms(ApolloSMP plugin) {
        this.plugin = plugin;
        this.tagKey = new NamespacedKey(plugin, "apollo_business_label");
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("invest.holograms", true);
    }

    /** Refresh labels near players; remove the rest. */
    public void tick() {
        if (!enabled()) {
            removeAll();
            return;
        }

        Set<String> wanted = new HashSet<>();
        for (BusinessBlock block : new ArrayList<>(plugin.businesses().all())) {
            World world = plugin.getServer().getWorld(block.worldName());
            if (world == null) continue;
            Location loc = new Location(world, block.x() + 0.5, block.y() + 1.35, block.z() + 0.5);
            if (!world.isChunkLoaded(block.x() >> 4, block.z() >> 4)) continue;
            if (!anyoneNear(loc)) continue;

            wanted.add(block.key());
            TextDisplay label = labels.get(block.key());
            if (label == null || !label.isValid()) {
                label = spawn(loc);
                if (label == null) continue;
                labels.put(block.key(), label);
            }
            label.text(buildText(block));
        }

        // Special businesses get the same treatment.
        for (com.apollosmp.special.SpecialBusiness sb :
                new ArrayList<>(plugin.specialBusinesses().all())) {
            World world = plugin.getServer().getWorld(sb.worldName());
            if (world == null) continue;
            if (!world.isChunkLoaded(sb.x() >> 4, sb.z() >> 4)) continue;
            Location loc = new Location(world, sb.x() + 0.5, sb.y() + 1.35, sb.z() + 0.5);
            if (!anyoneNear(loc)) continue;

            String key = "special:" + sb.locationKey();
            wanted.add(key);
            TextDisplay label = labels.get(key);
            if (label == null || !label.isValid()) {
                label = spawn(loc);
                if (label == null) continue;
                labels.put(key, label);
            }
            label.text(specialText(sb));
        }

        // Logistics blocks get a simpler label in the same style.
        for (com.apollosmp.logistics.LogisticsManager.Node n :
                plugin.logistics().allNodes()) {
            World world = plugin.getServer().getWorld(n.world);
            if (world == null) continue;
            if (!world.isChunkLoaded(n.x >> 4, n.z >> 4)) continue;
            Location loc = new Location(world, n.x + 0.5, n.y + 1.3, n.z + 0.5);
            if (!anyoneNear(loc)) continue;

            String key = "logi:" + n.key();
            wanted.add(key);
            TextDisplay label = labels.get(key);
            if (label == null || !label.isValid()) {
                label = spawn(loc);
                if (label == null) continue;
                labels.put(key, label);
            }
            label.text(logisticsText(n));
        }

        for (Map.Entry<String, TextDisplay> e : new ArrayList<>(labels.entrySet())) {
            if (wanted.contains(e.getKey())) continue;
            TextDisplay label = e.getValue();
            if (label != null && label.isValid()) label.remove();
            labels.remove(e.getKey());
        }
    }

    private boolean anyoneNear(Location loc) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.getWorld().equals(loc.getWorld())) continue;
            if (player.getLocation().distanceSquared(loc) <= VIEW_DISTANCE * VIEW_DISTANCE) return true;
        }
        return false;
    }

    private TextDisplay spawn(Location loc) {
        try {
            return loc.getWorld().spawn(loc, TextDisplay.class, display -> {
                display.setBillboard(Display.Billboard.CENTER);
                display.setSeeThrough(false);
                display.setShadowed(true);
                display.setPersistent(false);
                display.setViewRange(0.6f);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setLineWidth(400);
                applyScale(display);
                display.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
            });
        } catch (Exception ex) {
            return null;
        }
    }

    /** Join lines with real newline components - safer than embedding \n in MiniMessage. */
    private Component lines(String... miniLines) {
        Component out = Component.empty();
        for (int i = 0; i < miniLines.length; i++) {
            if (i > 0) out = out.append(Component.newline());
            out = out.append(Msg.mm(miniLines[i]));
        }
        return out;
    }

    /** Shrink the floating text so it doesn't tower over the block. */
    private void applyScale(TextDisplay display) {
        float scale = (float) plugin.getConfig().getDouble("invest.hologram-scale", 0.6);
        if (scale <= 0) scale = 0.6f;
        try {
            display.setTransformation(new org.bukkit.util.Transformation(
                    new org.joml.Vector3f(0f, 0f, 0f),
                    new org.joml.Quaternionf(),
                    new org.joml.Vector3f(scale, scale, scale),
                    new org.joml.Quaternionf()));
        } catch (Throwable ignored) {
            // if the server build lacks transformations, the default size is fine
        }
    }

    private Component buildText(BusinessBlock block) {
        Business def = Businesses.get(block.businessId());
        if (def == null) return Component.text("");

        int level = block.level();
        double boost = townBoost(block);
        double hourly = def.hourlyValueAtLevel(plugin.sell(), level) * boost;

        String header = icon(def) + " <gradient:#f9d423:#ff4e50><bold>"
                + plainName(def).toUpperCase() + "</bold></gradient> " + stars(level);

        String owner = "<gray>Owner:</gray> <white>" + block.ownerName() + "</white>";
        String lvl = "<gray>Level:</gray> <#f9d423>" + roman(level) + "</#f9d423>";
        String income = "<gray>Income:</gray> <green>" + plugin.msg().money(hourly) + "/hr</green>"
                + (boost > 1.0 ? " <#5ad1e8>+town</#5ad1e8>" : "");

        // Whichever product is closest to filling up is the one that matters.
        Business.Product tightest = null;
        double worstRatio = -1;
        for (Business.Product p : def.products()) {
            int cap = def.capacityForAtLevel(p, level);
            if (cap <= 0) continue;
            int have = block.storage().getOrDefault(p.material(), 0);
            double ratio = (double) have / cap;
            if (ratio > worstRatio) {
                worstRatio = ratio;
                tightest = p;
            }
        }

        String bar;
        String counts;
        if (tightest == null) {
            bar = progressBar(0);
            counts = "<dark_gray>nothing stored</dark_gray>";
        } else {
            int cap = def.capacityForAtLevel(tightest, level);
            int have = block.storage().getOrDefault(tightest.material(), 0);
            bar = progressBar(worstRatio);
            counts = "<white>" + have + "</white> <dark_gray>/</dark_gray> <white>" + cap
                    + "</white> <gray>" + Items.pretty(tightest.material()) + "</gray>";
        }

        return lines(header, "", owner, lvl, income, "",
                "<gray>Storage</gray>", bar, counts, "", statusLine(block, def));
    }

    /** A little symbol per industry so businesses read at a glance. */
    private String icon(Business def) {
        return switch (def.id()) {
            case "quarry", "goldmine" -> "<#f9d423>\u26cf</#f9d423>";
            case "gemmine" -> "<#5ad1e8>\u25c6</#5ad1e8>";
            case "farm" -> "<green>\u273f</green>";
            case "lumber" -> "<#c8873c>\u2663</#c8873c>";
            case "fishery" -> "<#5ad1e8>\u2248</#5ad1e8>";
            default -> "<#ffcf7a>\u2726</#ffcf7a>";
        };
    }

    /** Strip MiniMessage tags so the name can safely be upper-cased. */
    private String plainName(Business def) {
        return def.displayName().replaceAll("<[^>]*>", "").trim();
    }

    /** Five stars showing progress toward max level. */
    private String stars(int level) {
        int filled = Math.max(1, (int) Math.round(level * 5.0 / Business.MAX_LEVEL));
        StringBuilder sb = new StringBuilder("<#f9d423>");
        for (int i = 0; i < filled; i++) sb.append("\u2605");
        sb.append("</#f9d423><dark_gray>");
        for (int i = filled; i < 5; i++) sb.append("\u2606");
        sb.append("</dark_gray>");
        return sb.toString();
    }

    private String roman(int value) {
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return value >= 0 && value < numerals.length ? numerals[value] : String.valueOf(value);
    }

    /** Ten-segment fill bar, green through gold to red as it fills. */
    private String progressBar(double ratio) {
        double clamped = Math.max(0, Math.min(1, ratio));
        int filled = (int) Math.round(clamped * 10);
        String colour = clamped >= 1.0 ? "red" : clamped >= 0.75 ? "#f9d423" : "green";
        StringBuilder sb = new StringBuilder("<" + colour + ">");
        for (int i = 0; i < filled; i++) sb.append("\u2588");
        sb.append("</").append(colour).append("><dark_gray>");
        for (int i = filled; i < 10; i++) sb.append("\u2591");
        sb.append("</dark_gray>");
        return sb.toString();
    }

    /** Bottom line: what it's doing right now. */
    private String statusLine(BusinessBlock block, Business def) {
        boolean roomLeft = false;
        for (Business.Product p : def.products()) {
            int cap = def.capacityForAtLevel(p, block.level());
            if (block.storage().getOrDefault(p.material(), 0) < cap) { roomLeft = true; break; }
        }
        if (!roomLeft) return "<red>\u26a0 Storage full - collect it!</red>";

        String main = def.products().isEmpty()
                ? "goods" : Items.pretty(def.products().get(0).material());
        long remaining = (block.lastGen() + def.intervalMillis()) - System.currentTimeMillis();
        if (remaining <= 0) return "<green>\u23f3 Producing " + main + "</green>";

        long seconds = remaining / 1000;
        String time = seconds >= 60 ? (seconds / 60) + "m " + (seconds % 60) + "s" : seconds + "s";
        return "<gray>\u23f3 Next " + main + " in <white>" + time + "</white></gray>";
    }

    private Component specialText(com.apollosmp.special.SpecialBusiness b) {
        String header = specialIcon(b.industry()) + " <gradient:#f9d423:#ff4e50><bold>"
                + b.name().toUpperCase() + "</bold></gradient> " + rarityStars(b.rarity());

        String owner = "<gray>Owner:</gray> <white>"
                + (b.ownerName() == null ? "Unknown" : b.ownerName()) + "</white>";
        String rarity = "<gray>Rarity:</gray> <#e94fd0>" + b.rarity() + "</#e94fd0>";
        String income = "<gray>Income:</gray> <green>"
                + plugin.msg().money(b.exactProfit()) + "/day</green>";

        // Same idea as normal businesses: show whichever product is closest to full.
        int cap = b.effectiveStorage();
        int knownHave = b.storage().getOrDefault(b.knownItem(), 0);
        int hiddenHave = b.storage().getOrDefault(b.hiddenItem(), 0);
        boolean useKnown = knownHave >= hiddenHave;
        int have = useKnown ? knownHave : hiddenHave;
        org.bukkit.Material shown = useKnown ? b.knownItem() : b.hiddenItem();

        String bar = progressBar(cap <= 0 ? 0 : (double) have / cap);
        String counts = "<white>" + have + "</white> <dark_gray>/</dark_gray> <white>" + cap
                + "</white> <gray>" + Items.pretty(shown) + "</gray>";

        return lines(header, "", owner, rarity, income, "",
                "<gray>Storage</gray>", bar, counts, "", specialStatus(b));
    }

    /** Short two-line label for a distribution or wholesale block. */
    private Component logisticsText(com.apollosmp.logistics.LogisticsManager.Node n) {
        var logistics = plugin.logistics();
        boolean distributor = logistics.isDistributorNode(n);

        if (distributor) {
            int count = logistics.linkedBusinesses(n).size() + logistics.linkedSpecials(n).size();
            boolean attached = logistics.wholesalerFor(n) != null;
            String header = "<#5ad1e8>\u25c6</#5ad1e8> <gradient:#5ad1e8:#e94fd0><bold>DISTRIBUTION</bold></gradient>";
            String body = "<gray>" + count + " business" + (count == 1 ? "" : "es") + " linked</gray>";
            String status = attached
                    ? "<green>\u23f3 Feeding a wholesaler</green>"
                    : "<red>\u26a0 Not attached</red>";
            return lines(header, body, status);
        }

        int sources = logistics.linkedDistributors(n).size();
        int stored = logistics.stored(n);
        int cap = logistics.storageCap();
        String header = "<#f9d423>\u2726</#f9d423> <gradient:#f9d423:#ff4e50><bold>WHOLESALE</bold></gradient>";
        String body = "<gray>" + stored + " / " + cap + " stored</gray>";
        String status;
        if (sources == 0) status = "<red>\u26a0 Nothing attached</red>";
        else if (stored >= cap) status = "<red>\u26a0 Full - collect it!</red>";
        else if (!n.autoSell) status = "<gray>\u23f3 " + plugin.msg().money(logistics.storedValue(n))
                + " held</gray>";
        else status = "<gray>\u23f3 Selling in "
                + formatShort(logistics.secondsUntilSale(n)) + "</gray>";
        return lines(header, body, status);
    }

    private String formatShort(long seconds) {
        if (seconds >= 60) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    private String specialIcon(String industry) {
        return switch (industry == null ? "" : industry) {
            case "Mining" -> "<#f9d423>\u26cf</#f9d423>";
            case "Farming" -> "<green>\u273f</green>";
            case "Forestry" -> "<#c8873c>\u2663</#c8873c>";
            case "Fishing" -> "<#5ad1e8>\u2248</#5ad1e8>";
            case "Alchemy" -> "<#e94fd0>\u25c6</#e94fd0>";
            default -> "<#ff4e50>\u2726</#ff4e50>";
        };
    }

    /** Specials have no levels, so the stars show rarity instead. */
    private String rarityStars(String rarity) {
        int filled = switch (rarity == null ? "" : rarity) {
            case "Legendary" -> 5;
            case "Epic" -> 4;
            case "Rare" -> 3;
            default -> 2;
        };
        StringBuilder sb = new StringBuilder("<#e94fd0>");
        for (int i = 0; i < filled; i++) sb.append("\u2605");
        sb.append("</#e94fd0><dark_gray>");
        for (int i = filled; i < 5; i++) sb.append("\u2606");
        sb.append("</dark_gray>");
        return sb.toString();
    }

    private String specialStatus(com.apollosmp.special.SpecialBusiness b) {
        int stored = plugin.specialBusinesses().stored(b);
        if (stored >= b.effectiveStorage()) return "<red>\u26a0 Storage full - collect it!</red>";

        String main = Items.pretty(b.knownItem());
        long seconds = plugin.specialBusinesses().secondsUntilNext(b);
        if (seconds <= 0) return "<green>\u23f3 Producing " + main + "</green>";
        String time = seconds >= 60 ? (seconds / 60) + "m " + (seconds % 60) + "s" : seconds + "s";
        return "<gray>\u23f3 Next " + main + " in <white>" + time + "</white></gray>";
    }

    private double townBoost(BusinessBlock block) {
        try {
            World world = plugin.getServer().getWorld(block.worldName());
            if (world == null) return 1.0;
            String key = com.apollosmp.town.TownManager.chunkKey(world, block.x() >> 4, block.z() >> 4);
            com.apollosmp.town.Town town = plugin.towns().getTownAt(key);
            return town == null ? 1.0 : town.productionMultiplier();
        } catch (Exception ex) {
            return 1.0;
        }
    }


    /** Called on shutdown, and to clear leftovers on startup. */
    public void removeAll() {
        for (TextDisplay label : labels.values()) {
            if (label != null && label.isValid()) label.remove();
        }
        labels.clear();
    }

    /** Sweep up any labels left behind by a crash or reload. */
    public void cleanupOrphans() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof TextDisplay)) continue;
                if (entity.getPersistentDataContainer().has(tagKey, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    /** Drop the label for a business that was just removed. */
    public void forget(String blockKey) {
        TextDisplay label = labels.remove(blockKey);
        if (label != null && label.isValid()) label.remove();
    }

    /** Unused, kept for symmetry with other managers. */
    public Material marker() { return Material.AIR; }

    public List<String> tracked() { return new ArrayList<>(labels.keySet()); }
}
