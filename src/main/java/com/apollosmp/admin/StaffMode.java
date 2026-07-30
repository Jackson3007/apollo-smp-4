package com.apollosmp.admin;

import com.apollosmp.ApolloSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
    /** Admins who "left" but are still here, hidden, in adventure mode. Value = saved gamemode. */
    private final Map<UUID, String> observers = new ConcurrentHashMap<>();

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

    // ---- hidden observer ("leave but stay") ----

    public boolean isObserver(UUID id) {
        return observers.containsKey(id);
    }

    /** Toggle "leave the server but stay, hidden, in adventure mode." */
    public boolean toggleObserver(Player player) {
        if (isObserver(player.getUniqueId())) {
            exitObserver(player);
            return false;
        }
        enterObserver(player);
        return true;
    }

    public void enterObserver(Player player) {
        if (isObserver(player.getUniqueId())) return;
        observers.put(player.getUniqueId(), player.getGameMode().name());
        save();
        setVanished(player, true);
        announceFakeLeave(player);
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        plugin.msg().send(player, "<green>You've \"left\" - <gray>hidden from tab, locator & the world, "
                + "in adventure mode. Open <white>/admin</white> and click again to return.");
    }

    public void exitObserver(Player player) {
        String gm = observers.remove(player.getUniqueId());
        save();
        setVanished(player, false);
        announceFakeJoin(player);
        restoreGameMode(player, gm);
        plugin.msg().send(player, "<yellow>You're back and visible again.");
    }

    /** On quit, put their real gamemode back so it saves, and clear the flag. */
    public void handleObserverQuit(Player player) {
        String gm = observers.remove(player.getUniqueId());
        if (gm == null) return;
        restoreGameMode(player, gm);
        save();
    }

    /** After a crash, recover an admin left in observer mode. */
    public void restoreObserverOnJoin(Player player) {
        String gm = observers.remove(player.getUniqueId());
        if (gm == null) return;
        restoreGameMode(player, gm);
        setVanished(player, false);
        save();
    }

    private void restoreGameMode(Player player, String gm) {
        if (gm == null) return;
        try {
            player.setGameMode(GameMode.valueOf(gm));
        } catch (IllegalArgumentException ignored) {
            player.setGameMode(GameMode.SURVIVAL);
        }
    }

    // ---- vanish ----

    /**
     * Toggle "disappear like you logged off." Hides the player from everyone (tab,
     * locator bar and world), fakes a leave message, and on toggle-off fakes a join.
     * Returns true if they're now hidden.
     */
    public boolean toggleVanish(Player player) {
        boolean hidden = !isVanished(player.getUniqueId());
        setVanished(player, hidden);
        if (hidden) {
            announceFakeLeave(player);
            plugin.msg().send(player, "<gray>You vanished - you now appear <white>offline</white> "
                    + "<gray>(hidden from tab, locator & the world). Run it again to \"rejoin\".");
        } else {
            announceFakeJoin(player);
            plugin.msg().send(player, "<gray>You're back - other players see you as having just joined.");
        }
        return hidden;
    }

    private boolean fakeMessages() {
        return plugin.getConfig().getBoolean("staff-mode.fake-messages", true);
    }

    /** Broadcast a vanilla-style "left the game" to everyone but the vanishing player. */
    public void announceFakeLeave(Player player) {
        if (!fakeMessages()) return;
        Component msg = Component.text(player.getName() + " left the game", NamedTextColor.YELLOW);
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.equals(player)) other.sendMessage(msg);
        }
    }

    /** Broadcast a vanilla-style "joined the game" to everyone but the returning player. */
    public void announceFakeJoin(Player player) {
        if (!fakeMessages()) return;
        Component msg = Component.text(player.getName() + " joined the game", NamedTextColor.YELLOW);
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (!other.equals(player)) other.sendMessage(msg);
        }
    }

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
        for (Map.Entry<UUID, String> e : observers.entrySet()) {
            cfg.set("observers." + e.getKey(), e.getValue());
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
        if (root != null) {
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

        ConfigurationSection obs = cfg.getConfigurationSection("observers");
        if (obs != null) {
            for (String key : obs.getKeys(false)) {
                try {
                    observers.put(UUID.fromString(key), cfg.getString("observers." + key, "SURVIVAL"));
                } catch (IllegalArgumentException ignored) {
                    // skip malformed
                }
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
