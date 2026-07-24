package com.apollosmp.spawner;

import com.apollosmp.ApolloSMP;
import com.apollosmp.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Player-placed spawners: stacking, pickup, and the label floating above them. */
public class SpawnerManager {

    private int viewDistance() {
        return Math.max(4, plugin.getConfig().getInt("holograms.view-distance", 16));
    }

    /** One placed spawner block. */
    public static class Placed {
        public final String world;
        public final int x, y, z;
        public EntityType type;
        public int stack;

        Placed(String world, int x, int y, int z, EntityType type, int stack) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.stack = Math.max(1, stack);
        }

        public String key() { return SpawnerManager.key(world, x, y, z); }
        public Location toLocation(World w) { return new Location(w, x, y, z); }
    }

    private final ApolloSMP plugin;
    private final File file;
    private final NamespacedKey typeKey;
    private final NamespacedKey labelKey;

    private final Map<String, Placed> placed = new ConcurrentHashMap<>();
    private final Map<String, TextDisplay> labels = new ConcurrentHashMap<>();

    public SpawnerManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "spawners.yml");
        this.typeKey = new NamespacedKey(plugin, "apollo_spawner_type");
        this.labelKey = new NamespacedKey(plugin, "apollo_spawner_label");
        load();
    }

    public NamespacedKey typeKey() { return typeKey; }

    public static String key(String world, int x, int y, int z) {
        return world + "," + x + "," + y + "," + z;
    }

    public static String key(Location loc) {
        return key(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public int maxStack() {
        return Math.max(1, plugin.getConfig().getInt("spawners.max-stack", 64));
    }

    // ---- items ----
    public ItemStack createItem(EntityType type, int amount) {
        ItemStack item = new ItemStack(Material.SPAWNER, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String label = pretty(type);
            meta.displayName(Msg.lore("<#e94fd0>" + label + " Spawner</#e94fd0>"));
            List<Component> lore = new ArrayList<>();
            lore.add(Msg.lore("<gray>Spawns: <white>" + label + "</white>"));
            lore.add(Msg.lore("<dark_gray>Place it down, then right-click with"));
            lore.add(Msg.lore("<dark_gray>another to stack them together."));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    public EntityType readType(ItemStack item) {
        if (item == null || item.getType() != Material.SPAWNER || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer()
                .get(typeKey, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return EntityType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ---- placed spawners ----
    public Placed at(Location loc) {
        return placed.get(key(loc));
    }

    public Placed register(Location loc, EntityType type, int stack) {
        Placed p = new Placed(loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(),
                loc.getBlockZ(), type, stack);
        placed.put(p.key(), p);
        applyToBlock(loc.getBlock(), type);
        save();
        return p;
    }

    public void unregister(Placed p) {
        placed.remove(p.key());
        TextDisplay label = labels.remove(p.key());
        if (label != null && label.isValid()) label.remove();
        save();
    }

    /** Keep the physical block's mob in step with our record. */
    public void applyToBlock(Block block, EntityType type) {
        if (block.getType() != Material.SPAWNER) return;
        if (block.getState() instanceof CreatureSpawner spawner) {
            try {
                spawner.setSpawnedType(type);
                spawner.update(true, false);
            } catch (Exception ignored) {
                // some types can't be set; leave it alone
            }
        }
    }

    public void setStack(Placed p, int stack) {
        p.stack = Math.max(0, Math.min(maxStack(), stack));
        save();
    }

    // ---- floating labels ----
    private boolean labelsEnabled() {
        return plugin.getConfig().getBoolean("holograms.enabled", true)
                && plugin.getConfig().getBoolean("spawners.holograms", true);
    }

    public void tick() {
        if (!labelsEnabled()) {
            removeAllLabels();
            return;
        }
        Set<String> wanted = new HashSet<>();
        for (Placed p : new ArrayList<>(placed.values())) {
            World world = plugin.getServer().getWorld(p.world);
            if (world == null) continue;
            if (!world.isChunkLoaded(p.x >> 4, p.z >> 4)) continue;

            Location base = new Location(world, p.x + 0.5, p.y + 1.15, p.z + 0.5);
            if (!anyoneNear(base)) continue;

            wanted.add(p.key());
            TextDisplay label = labels.get(p.key());
            if (label == null || !label.isValid()) {
                label = spawnLabel(base);
                if (label == null) continue;
                labels.put(p.key(), label);
            }
            label.text(labelText(p));
        }

        for (Map.Entry<String, TextDisplay> e : new ArrayList<>(labels.entrySet())) {
            if (wanted.contains(e.getKey())) continue;
            TextDisplay label = e.getValue();
            if (label != null && label.isValid()) label.remove();
            labels.remove(e.getKey());
        }
    }

    private Component labelText(Placed p) {
        Component text = Msg.mm("<#e94fd0><bold>" + pretty(p.type) + " Spawner</bold></#e94fd0>");
        if (p.stack > 1) {
            text = text.append(Component.newline())
                    .append(Msg.mm("<#f9d423>x" + p.stack + "</#f9d423>"));
        }
        return text;
    }

    private boolean anyoneNear(Location loc) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.getWorld().equals(loc.getWorld())) continue;
            int range = viewDistance();
            if (player.getLocation().distanceSquared(loc) <= (double) range * range) return true;
        }
        return false;
    }

    private TextDisplay spawnLabel(Location loc) {
        try {
            return loc.getWorld().spawn(loc, TextDisplay.class, display -> {
                display.setBillboard(Display.Billboard.CENTER);
                display.setSeeThrough(false);
                display.setShadowed(true);
                display.setPersistent(false);
                display.setViewRange(0.6f);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setLineWidth(400);
                float scale = (float) plugin.getConfig().getDouble("invest.hologram-scale", 0.6);
                if (scale <= 0) scale = 0.6f;
                try {
                    display.setTransformation(new org.bukkit.util.Transformation(
                            new org.joml.Vector3f(0f, 0f, 0f),
                            new org.joml.Quaternionf(),
                            new org.joml.Vector3f(scale, scale, scale),
                            new org.joml.Quaternionf()));
                } catch (Throwable ignored) {
                    // default size is fine if transformations aren't available
                }
                display.getPersistentDataContainer().set(labelKey, PersistentDataType.BYTE, (byte) 1);
            });
        } catch (Exception ex) {
            return null;
        }
    }

    public void removeAllLabels() {
        for (TextDisplay label : labels.values()) {
            if (label != null && label.isValid()) label.remove();
        }
        labels.clear();
    }

    public void cleanupOrphans() {
        for (World world : plugin.getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof TextDisplay)) continue;
                if (entity.getPersistentDataContainer().has(labelKey, PersistentDataType.BYTE)) {
                    entity.remove();
                }
            }
        }
    }

    public static String pretty(EntityType type) {
        if (type == null) return "Empty";
        String[] words = type.name().toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (w.isEmpty()) continue;
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        int i = 0;
        for (Placed p : placed.values()) {
            cfg.set("spawners." + i + ".world", p.world);
            cfg.set("spawners." + i + ".x", p.x);
            cfg.set("spawners." + i + ".y", p.y);
            cfg.set("spawners." + i + ".z", p.z);
            cfg.set("spawners." + i + ".type", p.type == null ? null : p.type.name());
            cfg.set("spawners." + i + ".stack", p.stack);
            i++;
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save spawners.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("spawners");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                String world = cfg.getString("spawners." + key + ".world");
                if (world == null) continue;
                EntityType type = EntityType.valueOf(cfg.getString("spawners." + key + ".type", "ZOMBIE"));
                Placed p = new Placed(world,
                        cfg.getInt("spawners." + key + ".x"),
                        cfg.getInt("spawners." + key + ".y"),
                        cfg.getInt("spawners." + key + ".z"),
                        type, cfg.getInt("spawners." + key + ".stack", 1));
                placed.put(p.key(), p);
            } catch (Exception ignored) {
                // skip bad rows
            }
        }
    }
}
