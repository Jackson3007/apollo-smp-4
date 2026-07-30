package com.apollosmp.admin;

import com.apollosmp.ApolloSMP;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Alt-character / disguise system. An admin can play as one of three preset
 * characters, each with its own saved inventory, position and progress (like a
 * separate account), then switch back to their real self with /adminmode.
 *
 * While playing a character they use normal player commands (admin commands are
 * blocked - see IncognitoListener) and their chat/tab name is the character's. The
 * skin and the name floating above their head change too IF LibsDisguises is
 * installed (see {@link Disguises}); without it, everything else still works.
 *
 * Slots: "self" is the real admin; "1", "2", "3" are the characters.
 */
public class IncognitoManager {

    private record Saved(ItemStack[] contents, ItemStack[] armor, ItemStack offHand,
                         String gameMode, boolean allowFlight, boolean flying,
                         double health, int food, float exp, int level,
                         String world, double x, double y, double z, float yaw, float pitch) {}

    public static final String SELF = "self";

    private final ApolloSMP plugin;
    private final File file;
    private final Map<UUID, Map<String, Saved>> slots = new ConcurrentHashMap<>();
    private final Map<UUID, String> current = new ConcurrentHashMap<>();

    public IncognitoManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "incognito.yml");
        load();
    }

    // ---- persona definitions ----
    private List<String> personaNames() {
        List<String> names = plugin.getConfig().getStringList("incognito.personas");
        if (names == null || names.isEmpty()) {
            return List.of("Milo_Craft", "PixelWren", "AshFox");
        }
        return names;
    }

    public int personaCount() {
        return Math.min(3, personaNames().size());
    }

    /** Character name for a slot index (0-based). */
    public String personaName(int index) {
        List<String> names = personaNames();
        return (index >= 0 && index < names.size()) ? names.get(index) : "Player";
    }

    private String slotName(String slot) {
        try {
            return personaName(Integer.parseInt(slot) - 1);
        } catch (NumberFormatException ex) {
            return "Player";
        }
    }

    // ---- state ----
    public String currentSlot(UUID id) {
        return current.getOrDefault(id, SELF);
    }

    public boolean isDisguised(UUID id) {
        return !currentSlot(id).equals(SELF);
    }

    /** Kept for the chat listener and other callers. */
    public boolean isIncognito(UUID id) {
        return isDisguised(id);
    }

    /** Switch the player to a slot ("self", "1", "2" or "3"). */
    public void switchTo(Player player, String targetSlot) {
        UUID id = player.getUniqueId();
        String cur = currentSlot(id);
        if (cur.equals(targetSlot)) {
            plugin.msg().send(player, "<yellow>You're already there.");
            return;
        }

        // Save where we are now into its slot.
        slots.computeIfAbsent(id, k -> new HashMap<>()).put(cur, capture(player));

        current.put(id, targetSlot);
        Saved target = slots.get(id).get(targetSlot);
        boolean fresh = target == null;
        if (target != null) {
            restoreState(player, target);
        } else if (!targetSlot.equals(SELF)) {
            freshStart(player);
        }

        if (targetSlot.equals(SELF)) {
            clearDisguise(player);
            plugin.msg().send(player, "<yellow><bold>Back to admin.</bold></yellow> <gray>Everything's restored.");
        } else {
            applyDisguiseVisuals(player, targetSlot);
            String name = slotName(targetSlot);
            plugin.msg().send(player, "<green><bold>You are now " + name + ".</bold></green> "
                    + "<gray>Normal commands work; admin commands are blocked. Use <white>/adminmode</white> to switch.");
            if (!Disguises.available()) {
                plugin.msg().send(player, "<dark_gray>(Install LibsDisguises to also change your skin and the name above your head.)");
            }
            if (fresh) {
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && targetSlot.equals(currentSlot(id))) {
                        plugin.rtp().attempt(player, true);
                    }
                }, 10L);
            }
        }
        save();
    }

    public void returnToSelf(Player player) {
        if (!isDisguised(player.getUniqueId())) {
            plugin.msg().send(player, "<yellow>You're already yourself.");
            return;
        }
        switchTo(player, SELF);
    }

    // ---- appearance ----
    private void applyDisguiseVisuals(Player player, String slot) {
        String name = slotName(slot);
        Component comp = Component.text(name);
        player.displayName(comp);
        player.playerListName(comp);
        Disguises.disguiseAs(player, name);
    }

    private void clearDisguise(Player player) {
        Disguises.undisguise(player);
        plugin.nicks().apply(player); // restore real name + rank badge
    }

    // ---- capture / restore ----
    private Saved capture(Player player) {
        Location loc = player.getLocation();
        return new Saved(
                player.getInventory().getContents().clone(),
                player.getInventory().getArmorContents().clone(),
                player.getInventory().getItemInOffHand().clone(),
                player.getGameMode().name(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getExp(),
                player.getLevel(),
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
    }

    private void restoreState(Player player, Saved state) {
        player.getInventory().clear();
        player.getInventory().setContents(state.contents());
        player.getInventory().setArmorContents(state.armor());
        player.getInventory().setItemInOffHand(state.offHand());
        try {
            player.setGameMode(GameMode.valueOf(state.gameMode()));
        } catch (IllegalArgumentException ignored) {
            player.setGameMode(GameMode.SURVIVAL);
        }
        player.setAllowFlight(state.allowFlight());
        player.setFlying(state.flying() && state.allowFlight());
        try {
            player.setHealth(Math.max(1, Math.min(state.health(), player.getMaxHealth())));
        } catch (Exception ignored) {
            // leave health
        }
        player.setFoodLevel(state.food());
        player.setExp(state.exp());
        player.setLevel(state.level());
        player.updateInventory();

        World world = plugin.getServer().getWorld(state.world());
        if (world != null) {
            player.teleport(new Location(world, state.x(), state.y(), state.z(), state.yaw(), state.pitch()));
        }
    }

    private void freshStart(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        try {
            player.setHealth(Math.min(20.0, player.getMaxHealth()));
        } catch (Exception ignored) {
            // leave health
        }
        player.setFoodLevel(20);
        player.setExp(0f);
        player.setLevel(0);
    }

    // ---- lifecycle hooks ----
    public void handleQuit(Player player) {
        UUID id = player.getUniqueId();
        if (!slots.containsKey(id) && !isDisguised(id)) return;
        slots.computeIfAbsent(id, k -> new HashMap<>()).put(currentSlot(id), capture(player));
        save();
    }

    /** On join, if they were mid-character, reapply the disguise visuals. */
    public void restoreOnJoin(Player player) {
        String cur = currentSlot(player.getUniqueId());
        if (cur.equals(SELF)) return;
        applyDisguiseVisuals(player, cur);
        plugin.msg().send(player, "<gray>You're still playing as <white>" + slotName(cur)
                + "</white>. Use <white>/adminmode</white> to switch back.");
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Saved>> e : slots.entrySet()) {
            String base = "personas." + e.getKey();
            cfg.set(base + ".current", current.getOrDefault(e.getKey(), SELF));
            for (Map.Entry<String, Saved> slot : e.getValue().entrySet()) {
                writeSaved(cfg, base + ".slots." + slot.getKey(), slot.getValue());
            }
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save incognito.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("personas");
        if (root == null) return;
        for (String idKey : root.getKeys(false)) {
            try {
                UUID id = UUID.fromString(idKey);
                String base = "personas." + idKey;
                current.put(id, cfg.getString(base + ".current", SELF));
                ConfigurationSection slotSec = cfg.getConfigurationSection(base + ".slots");
                if (slotSec != null) {
                    Map<String, Saved> map = new HashMap<>();
                    for (String slot : slotSec.getKeys(false)) {
                        Saved s = readSaved(cfg, base + ".slots." + slot);
                        if (s != null) map.put(slot, s);
                    }
                    slots.put(id, map);
                }
            } catch (Exception ignored) {
                plugin.getLogger().warning("Skipped a malformed persona entry: " + idKey);
            }
        }
    }

    private void writeSaved(FileConfiguration cfg, String base, Saved s) {
        cfg.set(base + ".contents", new ArrayList<>(Arrays.asList(s.contents())));
        cfg.set(base + ".armor", new ArrayList<>(Arrays.asList(s.armor())));
        cfg.set(base + ".offhand", s.offHand());
        cfg.set(base + ".gamemode", s.gameMode());
        cfg.set(base + ".allowFlight", s.allowFlight());
        cfg.set(base + ".flying", s.flying());
        cfg.set(base + ".health", s.health());
        cfg.set(base + ".food", s.food());
        cfg.set(base + ".exp", s.exp());
        cfg.set(base + ".level", s.level());
        cfg.set(base + ".world", s.world());
        cfg.set(base + ".x", s.x());
        cfg.set(base + ".y", s.y());
        cfg.set(base + ".z", s.z());
        cfg.set(base + ".yaw", s.yaw());
        cfg.set(base + ".pitch", s.pitch());
    }

    private Saved readSaved(FileConfiguration cfg, String base) {
        try {
            return new Saved(
                    readList(cfg, base + ".contents"),
                    readList(cfg, base + ".armor"),
                    cfg.getItemStack(base + ".offhand"),
                    cfg.getString(base + ".gamemode", "SURVIVAL"),
                    cfg.getBoolean(base + ".allowFlight"),
                    cfg.getBoolean(base + ".flying"),
                    cfg.getDouble(base + ".health", 20),
                    cfg.getInt(base + ".food", 20),
                    (float) cfg.getDouble(base + ".exp"),
                    cfg.getInt(base + ".level"),
                    cfg.getString(base + ".world", "world"),
                    cfg.getDouble(base + ".x"),
                    cfg.getDouble(base + ".y"),
                    cfg.getDouble(base + ".z"),
                    (float) cfg.getDouble(base + ".yaw"),
                    (float) cfg.getDouble(base + ".pitch"));
        } catch (Exception ex) {
            return null;
        }
    }

    private ItemStack[] readList(FileConfiguration cfg, String path) {
        List<?> raw = cfg.getList(path);
        if (raw == null) return new ItemStack[0];
        ItemStack[] out = new ItemStack[raw.size()];
        for (int i = 0; i < raw.size(); i++) {
            Object o = raw.get(i);
            out[i] = (o instanceof ItemStack stack) ? stack : null;
        }
        return out;
    }
}
