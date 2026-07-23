package com.apollosmp.admin;

import com.apollosmp.ApolloSMP;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lets staff step out of survival and back in without losing anything.
 * The survival state is written to disk, so a restart mid-session is safe.
 */
public class StaffMode {

    /** Everything we need to put a player back exactly as they were. */
    private record Saved(ItemStack[] contents, ItemStack[] armor, ItemStack offHand,
                         String gameMode, boolean allowFlight, boolean flying,
                         double health, int food, float exp, int level) {}

    private final ApolloSMP plugin;
    private final File file;
    private final Map<UUID, Saved> saved = new ConcurrentHashMap<>();
    private final Set<UUID> vanished = ConcurrentHashMap.newKeySet();

    public StaffMode(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "staffmode.yml");
        load();
    }

    public boolean isStaff(Player player) {
        return saved.containsKey(player.getUniqueId());
    }

    public boolean isVanished(UUID id) {
        return vanished.contains(id);
    }

    /** Flip in or out of staff mode. Returns true if they're now in it. */
    public boolean toggle(Player player) {
        if (isStaff(player)) {
            exit(player);
            return false;
        }
        enter(player);
        return true;
    }

    public void enter(Player player) {
        if (isStaff(player)) return;

        saved.put(player.getUniqueId(), new Saved(
                player.getInventory().getContents().clone(),
                player.getInventory().getArmorContents().clone(),
                player.getInventory().getItemInOffHand().clone(),
                player.getGameMode().name(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getHealth(),
                player.getFoodLevel(),
                player.getExp(),
                player.getLevel()));
        save();

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);

        if (plugin.getConfig().getBoolean("staff-mode.vanish", true)) {
            setVanished(player, true);
        }

        plugin.msg().send(player, "<green><bold>Staff mode on.</bold></green> "
                + "<gray>Your survival gear is safely stored.");
        if (isVanished(player.getUniqueId())) {
            plugin.msg().send(player, "<gray>You're hidden from other players.");
        }
        plugin.getLogger().info("[StaffMode] " + player.getName() + " entered staff mode.");
    }

    public void exit(Player player) {
        Saved state = saved.remove(player.getUniqueId());
        setVanished(player, false);
        save();

        if (state == null) {
            plugin.msg().send(player, "<yellow>You weren't in staff mode.");
            return;
        }

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
            // health attribute may differ; leave it alone
        }
        player.setFoodLevel(state.food());
        player.setExp(state.exp());
        player.setLevel(state.level());
        player.updateInventory();

        plugin.msg().send(player, "<yellow><bold>Staff mode off.</bold></yellow> "
                + "<gray>Everything's back where you left it.");
        plugin.getLogger().info("[StaffMode] " + player.getName() + " left staff mode.");
    }

    // ---- vanish ----
    public void setVanished(Player player, boolean hidden) {
        if (hidden) vanished.add(player.getUniqueId());
        else vanished.remove(player.getUniqueId());

        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (other.equals(player)) continue;
            if (hidden && !other.hasPermission("apollo.admin")) other.hidePlayer(plugin, player);
            else other.showPlayer(plugin, player);
        }
    }

    /** Called when someone joins, so they can't see vanished staff. */
    public void applyVanishTo(Player joiner) {
        if (joiner.hasPermission("apollo.admin")) return;
        for (UUID id : vanished) {
            Player staff = plugin.getServer().getPlayer(id);
            if (staff != null) joiner.hidePlayer(plugin, staff);
        }
    }

    /** Called when a staff member joins while still flagged. */
    public void restoreOnJoin(Player player) {
        if (!isStaff(player)) return;
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        if (plugin.getConfig().getBoolean("staff-mode.vanish", true)) setVanished(player, true);
        plugin.msg().send(player, "<gray>You're still in <green>staff mode</green>. "
                + "Use <white>/staff</white> to drop back into survival.");
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Saved> e : saved.entrySet()) {
            String base = "staff." + e.getKey();
            Saved s = e.getValue();
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
        }
        try {
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Failed to save staffmode.yml: " + ex.getMessage());
        }
    }

    private void load() {
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("staff");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            try {
                String base = "staff." + key;
                saved.put(UUID.fromString(key), new Saved(
                        readList(cfg, base + ".contents"),
                        readList(cfg, base + ".armor"),
                        cfg.getItemStack(base + ".offhand"),
                        cfg.getString(base + ".gamemode", "SURVIVAL"),
                        cfg.getBoolean(base + ".allowFlight"),
                        cfg.getBoolean(base + ".flying"),
                        cfg.getDouble(base + ".health", 20),
                        cfg.getInt(base + ".food", 20),
                        (float) cfg.getDouble(base + ".exp"),
                        cfg.getInt(base + ".level")));
            } catch (Exception ignored) {
                plugin.getLogger().warning("Skipped a malformed staff-mode entry: " + key);
            }
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
