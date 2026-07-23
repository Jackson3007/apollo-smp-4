package com.apollosmp.invest;

import com.apollosmp.ApolloSMP;
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
                display.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
            });
        } catch (Exception ex) {
            return null;
        }
    }

    private Component buildText(BusinessBlock block) {
        Business def = Businesses.get(block.businessId());
        if (def == null) return Component.text("");

        StringBuilder sb = new StringBuilder();
        sb.append("<#f9d423><bold>").append(def.displayName())
                .append("</bold></#f9d423> <gray>[L").append(block.level()).append("]</gray>");

        double hourly = def.hourlyValueAtLevel(plugin.sell(), block.level());
        double boosted = hourly * townBoost(block);
        sb.append("\n<gray>Income:</gray> <green>").append(plugin.msg().money(boosted)).append("/hr</green>");
        if (boosted > hourly) {
            sb.append(" <#5ad1e8>(town bonus)</#5ad1e8>");
        }

        sb.append("\n").append(nextLine(block, def));
        return Msg.mm(sb.toString());
    }

    private Component specialText(com.apollosmp.special.SpecialBusiness b) {
        StringBuilder sb = new StringBuilder();
        sb.append("<gradient:#f9d423:#ff4e50><bold>").append(b.name())
                .append("</bold></gradient> <#e94fd0>").append(b.rarity()).append("</#e94fd0>");
        sb.append("\n<gray>Income:</gray> <green>")
                .append(plugin.msg().money(b.exactProfit())).append("/day</green>");

        int stored = plugin.specialBusinesses().stored(b);
        if (stored >= b.effectiveStorage()) {
            sb.append("\n<red>Storage full - collect it!</red>");
        } else {
            long seconds = plugin.specialBusinesses().secondsUntilNext(b);
            if (seconds <= 0) sb.append("\n<green>Producing now...</green>");
            else if (seconds >= 60) sb.append("\n<gray>Next batch in</gray> <white>")
                    .append(seconds / 60).append("m ").append(seconds % 60).append("s</white>");
            else sb.append("\n<gray>Next batch in</gray> <white>").append(seconds).append("s</white>");
        }
        return Msg.mm(sb.toString());
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

    /** "Next batch in 42s", or a warning when storage is full. */
    private String nextLine(BusinessBlock block, Business def) {
        boolean roomLeft = false;
        for (Business.Product p : def.products()) {
            int cap = def.capacityForAtLevel(p, block.level());
            int current = block.storage().getOrDefault(p.material(), 0);
            if (current < cap) { roomLeft = true; break; }
        }
        if (!roomLeft) return "<red>Storage full - collect it!</red>";

        long remaining = (block.lastGen() + def.intervalMillis()) - System.currentTimeMillis();
        if (remaining <= 0) return "<green>Producing now...</green>";
        long seconds = remaining / 1000;
        if (seconds >= 60) return "<gray>Next batch in</gray> <white>" + (seconds / 60) + "m "
                + (seconds % 60) + "s</white>";
        return "<gray>Next batch in</gray> <white>" + seconds + "s</white>";
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
