package com.apollosmp.admin;

import com.apollosmp.ApolloSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * "Incognito" spy mode: an admin drops into the server as a random-named player
 * with a fresh survival start, no commands (except /adminmode) and an auto /rtp,
 * so they can experience the server exactly like a new player.
 *
 * NOTE: this changes the name shown in chat and the tab list, and gives a clean
 * survival start. It does NOT change the player's skin or the name floating above
 * their head - that needs a disguise plugin (e.g. LibsDisguises). Everything else
 * (gear, gamemode, position) is saved and fully restored when they run /adminmode.
 */
public class IncognitoManager {

    private record Saved(ItemStack[] contents, ItemStack[] armor, ItemStack offHand,
                         String gameMode, boolean allowFlight, boolean flying,
                         double health, int food, float exp, int level,
                         String world, double x, double y, double z, float yaw, float pitch,
                         String fakeName) {}

    private static final List<String> NAME_POOL = List.of(
            "Milo", "Pixel", "Birch", "Cobble", "Wren", "Fox", "Kai", "Ash", "Juno", "Pip",
            "Sable", "Reed", "Clay", "Vale", "Bramble", "Otter", "Finch", "Moss", "Rowan", "Skip");

    private final ApolloSMP plugin;
    private final File file;
    private final Map<UUID, Saved> saved = new ConcurrentHashMap<>();

    public IncognitoManager(ApolloSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "incognito.yml");
        load();
    }

    public boolean isIncognito(UUID id) {
        return saved.containsKey(id);
    }

    public String fakeNameOf(UUID id) {
        Saved s = saved.get(id);
        return s == null ? null : s.fakeName();
    }

    private String randomName() {
        String base = NAME_POOL.get(ThreadLocalRandom.current().nextInt(NAME_POOL.size()));
        return base + ThreadLocalRandom.current().nextInt(100, 9999);
    }

    /** Enter incognito: save real state, wipe to a fresh survival start, rename, rtp. */
    public void enter(Player player) {
        if (isIncognito(player.getUniqueId())) {
            plugin.msg().send(player, "<yellow>You're already incognito. Use <white>/adminmode</white> to return.");
            return;
        }
        Location loc = player.getLocation();
        String fake = randomName();
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
                player.getLevel(),
                loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch(),
                fake));
        save();

        // Fresh survival start.
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setHealth(Math.min(20.0, player.getMaxHealth()));
        player.setFoodLevel(20);
        player.setExp(0f);
        player.setLevel(0);

        // Random identity in chat and the tab list (no rank badge).
        Component name = Component.text(fake);
        player.displayName(name);
        player.playerListName(name);

        // Fake join so it looks like a new player logging in.
        if (plugin.getConfig().getBoolean("incognito.fake-messages", true)) {
            Component join = Component.text(fake + " joined the game", NamedTextColor.YELLOW);
            for (Player other : plugin.getServer().getOnlinePlayers()) {
                if (!other.equals(player)) other.sendMessage(join);
            }
        }

        plugin.msg().send(player, "<green><bold>Incognito on.</bold></green> <gray>You're now <white>"
                + fake + "</white> with a fresh start. Only <white>/adminmode</white> works - run it to return.");
        plugin.msg().send(player, "<dark_gray>(Your skin and the name above your head stay yours unless a disguise plugin is installed.)");

        // Send them somewhere fresh, like a new player.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && isIncognito(player.getUniqueId())) {
                plugin.rtp().attempt(player, true);
            }
        }, 10L);

        plugin.getLogger().info("[Incognito] " + player.getName() + " went incognito as " + fake + ".");
    }

    /** Leave incognito: restore everything and teleport back. */
    public void exit(Player player) {
        Saved state = saved.remove(player.getUniqueId());
        save();
        if (state == null) {
            plugin.msg().send(player, "<yellow>You aren't incognito.");
            return;
        }
        restoreState(player, state);

        // Restore real name/tag/badge.
        plugin.nicks().apply(player);

        Location back = backLocation(state);
        if (back != null) player.teleport(back);

        if (plugin.getConfig().getBoolean("incognito.fake-messages", true)) {
            Component leave = Component.text(state.fakeName() + " left the game", NamedTextColor.YELLOW);
            for (Player other : plugin.getServer().getOnlinePlayers()) {
                if (!other.equals(player)) other.sendMessage(leave);
            }
        }

        plugin.msg().send(player, "<yellow><bold>Incognito off.</bold></yellow> <gray>Welcome back - everything's restored.");
        plugin.getLogger().info("[Incognito] " + player.getName() + " left incognito.");
    }

    /**
     * If they disconnect while incognito, put their real gear back on the live
     * player so their save file keeps it, then clear the flag. Prevents item loss.
     */
    public void handleQuit(Player player) {
        Saved state = saved.remove(player.getUniqueId());
        if (state == null) return;
        restoreState(player, state);
        save();
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
            // leave health alone
        }
        player.setFoodLevel(state.food());
        player.setExp(state.exp());
        player.setLevel(state.level());
        player.updateInventory();
    }

    private Location backLocation(Saved state) {
        World world = plugin.getServer().getWorld(state.world());
        if (world == null) return null;
        return new Location(world, state.x(), state.y(), state.z(), state.yaw(), state.pitch());
    }

    /** After an unclean shutdown, recover a player who was left incognito. */
    public void restoreOnJoin(Player player) {
        Saved state = saved.remove(player.getUniqueId());
        if (state == null) return;
        save();
        restoreState(player, state);
        plugin.nicks().apply(player);
        Location back = backLocation(state);
        if (back != null) player.teleport(back);
        plugin.msg().send(player, "<yellow>Restored you from incognito mode.");
    }

    // ---- persistence ----
    public void save() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, Saved> e : saved.entrySet()) {
            String base = "incognito." + e.getKey();
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
            cfg.set(base + ".world", s.world());
            cfg.set(base + ".x", s.x());
            cfg.set(base + ".y", s.y());
            cfg.set(base + ".z", s.z());
            cfg.set(base + ".yaw", s.yaw());
            cfg.set(base + ".pitch", s.pitch());
            cfg.set(base + ".fakeName", s.fakeName());
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
        ConfigurationSection root = cfg.getConfigurationSection("incognito");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            try {
                String base = "incognito." + key;
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
                        cfg.getInt(base + ".level"),
                        cfg.getString(base + ".world", "world"),
                        cfg.getDouble(base + ".x"),
                        cfg.getDouble(base + ".y"),
                        cfg.getDouble(base + ".z"),
                        (float) cfg.getDouble(base + ".yaw"),
                        (float) cfg.getDouble(base + ".pitch"),
                        cfg.getString(base + ".fakeName", "Player")));
            } catch (Exception ignored) {
                plugin.getLogger().warning("Skipped a malformed incognito entry: " + key);
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
