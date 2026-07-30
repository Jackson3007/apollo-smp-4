package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PlayerListener implements Listener {

    private final ApolloSMP plugin;

    public PlayerListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean firstJoin = !player.hasPlayedBefore();

        // Staff who were vanished across a restart come back silently.
        if (plugin.staffMode().isStaff(player)) {
            event.joinMessage(null);
        }

        plugin.economy().ensureAccount(player.getUniqueId(), player.getName());
        plugin.board().create(player);
        plugin.nameTags().invalidate();
        plugin.ranks().applyPermissions(player);
        plugin.nicks().apply(player);
        plugin.staffMode().applyVanishTo(player);
        plugin.staffMode().restoreOnJoin(player);
        sendWelcome(player);
        announceApolloPlusJoin(player);
        plugin.auctions().flushNotifications(player);
        plugin.voting().deliverPending(player);
        plugin.specialAuction().flushWins(player);
        plugin.incognito().restoreOnJoin(player);

        boolean wildEveryJoin = plugin.getConfig().getBoolean("rtp.wild-on-join", false);
        boolean wildFirstJoin = firstJoin
                && plugin.getConfig().getBoolean("rtp.random-spawn-on-first-join", true);

        if (wildEveryJoin || wildFirstJoin) {
            // Delay so the world is fully ready before we search for a spot.
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    boolean ok = plugin.rtp().randomSpawn(player);
                    if (ok && firstJoin) {
                        plugin.msg().send(player, "<green>Welcome to <#f9d423>Apollo SMP</#f9d423>! "
                                + "You've spawned in the wild.");
                    }
                }
            }, 20L);
        }

        int mail = plugin.mailbox().size(player.getUniqueId());
        if (mail > 0) {
            plugin.msg().send(player, "<gray>You have <white>" + mail
                    + "</white> item(s) waiting. Collect them with <white>/menu</white>.");
        }

        if (firstJoin) {
            giveStarterKit(player);
            if (plugin.onboarding().enabled()) {
                plugin.msg().send(player, "<#f9d423>\u2726</#f9d423> <gray>New here? Open <white>/guide</white> "
                        + "<gray>for starter tasks and rewards.");
                // Pop the guide open once so they see it.
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) new com.apollosmp.gui.menus.GuideMenu(plugin, player).open();
                }, 40L);
            }
        }
    }

    /** A small one-time kit handed to brand-new players. */
    private void giveStarterKit(Player player) {
        if (!plugin.getConfig().getBoolean("starter-kit.enabled", true)) return;

        List<ItemStack> kit = new ArrayList<>();
        List<String> configured = plugin.getConfig().getStringList("starter-kit.items");
        if (configured.isEmpty()) {
            kit.add(new ItemStack(Material.STONE_PICKAXE));
            kit.add(new ItemStack(Material.STONE_AXE));
            kit.add(new ItemStack(Material.STONE_SWORD));
            kit.add(new ItemStack(Material.BREAD, 16));
            kit.add(new ItemStack(Material.OAK_LOG, 8));
            kit.add(new ItemStack(Material.TORCH, 16));
        } else {
            for (String entry : configured) {
                String[] parts = entry.split(":");
                Material material = Material.matchMaterial(parts[0].trim());
                if (material == null || !material.isItem()) continue;
                int amount = 1;
                if (parts.length > 1) {
                    try {
                        amount = Math.max(1, Integer.parseInt(parts[1].trim()));
                    } catch (NumberFormatException ignored) {
                        // keep amount at 1
                    }
                }
                kit.add(new ItemStack(material, amount));
            }
        }
        if (kit.isEmpty()) return;

        for (ItemStack item : kit) {
            for (ItemStack overflow : player.getInventory().addItem(item).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), overflow);
            }
        }
        plugin.msg().send(player, "<green>Here's a little starter kit to get you going. Good luck!");
    }

    private void announceApolloPlusJoin(Player player) {
        if (!player.hasPermission("apollo.plus")) return;
        if (!plugin.getConfig().getBoolean("ranks.apollo-plus.join-flair", true)) return;
        String name = player.getName();
        plugin.getServer().broadcast(com.apollosmp.util.Msg.mm(
                "<#ffd54a>\u2726</#ffd54a> <gray>" + name
                        + " <#ffd54a>joined - welcome an Apollo+ member!</#ffd54a>"));
    }

    private void sendWelcome(Player player) {
        var msg = plugin.msg();
        // Only the first line carries the Apollo prefix; the rest are raw.
        msg.send(player, "<gradient:#f9d423:#ff4e50><bold>Welcome to Apollo SMP!</bold></gradient>");
        msg.sendRaw(player, "<#f9d423>\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501</#f9d423>");
        msg.sendRaw(player, "<gray>  Server IP: <#5ad1e8>" + plugin.serverIp() + "</#5ad1e8>");
        msg.sendRaw(player, "");
        msg.sendRaw(player, "<#f9d423>Handy commands:</#f9d423>");
        msg.sendRaw(player, "  <white>/guide</white> <dark_gray>-</dark_gray> <gray>new here? start here for rewards</gray>");
        msg.sendRaw(player, "  <white>/menu</white> <dark_gray>-</dark_gray> <gray>the main hub</gray>");
        msg.sendRaw(player, "  <white>/sell</white> <dark_gray>-</dark_gray> <gray>sell items for money</gray>");
        msg.sendRaw(player, "  <white>/ah</white> <dark_gray>-</dark_gray> <gray>browse the auction house</gray>");
        msg.sendRaw(player, "  <white>/invest</white> <dark_gray>-</dark_gray> <gray>buy & manage businesses</gray>");
        msg.sendRaw(player, "  <white>/town</white> <dark_gray>-</dark_gray> <gray>create & manage a town</gray>");
        msg.sendRaw(player, "  <white>/sethome</white> <dark_gray>&</dark_gray> <white>/home</white> <dark_gray>-</dark_gray> <gray>set & travel home</gray>");
        msg.sendRaw(player, "  <white>/rtp</white> <dark_gray>-</dark_gray> <gray>teleport into the wild</gray>");
        msg.sendRaw(player, "  <white>/vote</white> <dark_gray>-</dark_gray> <gray>earn <#f9d423>"
                + plugin.msg().money(plugin.voting().reward()) + "</#f9d423> <gray>per site</gray>");
        msg.sendRaw(player, "  <white>/discord</white> <dark_gray>-</dark_gray> <gray>join the community</gray>");
        msg.sendRaw(player, "<#f9d423>\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501</#f9d423>");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // A vanished admin already "left" - don't announce a second time.
        if (plugin.staffMode().isVanished(event.getPlayer().getUniqueId())) {
            event.quitMessage(null);
        }
        plugin.ranks().clearAttachment(event.getPlayer());
        plugin.snapshots().capture(event.getPlayer());
        plugin.board().remove(event.getPlayer());
        plugin.nameTags().remove(event.getPlayer());
    }
}
