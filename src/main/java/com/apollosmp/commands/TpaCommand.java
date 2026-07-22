package com.apollosmp.commands;

import com.apollosmp.ApolloSMP;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Handles /tpa, /tpahere, /tpaccept, /tpdeny and /tpacancel. */
public class TpaCommand implements CommandExecutor, TabCompleter {

    private enum Type { TO, HERE }

    private record Request(UUID requester, String requesterName, UUID target, Type type, long expiresAt) {
        boolean expired() { return System.currentTimeMillis() > expiresAt; }
    }

    private static final long EXPIRY_MS = 60_000;

    private final ApolloSMP plugin;
    // key = target uuid -> list of incoming requests
    private final Map<UUID, List<Request>> incoming = new ConcurrentHashMap<>();

    public TpaCommand(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "<red>Only players can use teleport requests.");
            return true;
        }
        switch (command.getName().toLowerCase()) {
            case "tpa" -> request(player, args, Type.TO);
            case "tpahere" -> request(player, args, Type.HERE);
            case "tpaccept" -> accept(player, args);
            case "tpdeny" -> deny(player, args);
            case "tpacancel" -> cancel(player);
            default -> { return false; }
        }
        return true;
    }

    private void request(Player requester, String[] args, Type type) {
        if (args.length < 1) {
            plugin.msg().send(requester, "<red>Usage: /" + (type == Type.TO ? "tpa" : "tpahere") + " <player>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            plugin.msg().send(requester, "<red>That player isn't online.");
            return;
        }
        if (target.getUniqueId().equals(requester.getUniqueId())) {
            plugin.msg().send(requester, "<red>You can't teleport to yourself.");
            return;
        }
        List<Request> list = incoming.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>());
        list.removeIf(r -> r.requester().equals(requester.getUniqueId()));
        list.add(new Request(requester.getUniqueId(), requester.getName(), target.getUniqueId(),
                type, System.currentTimeMillis() + EXPIRY_MS));

        plugin.msg().send(requester, "<green>Request sent to <white>" + target.getName()
                + "</white>. <gray>It expires in 60s.");
        if (type == Type.TO) {
            plugin.msg().send(target, "<white>" + requester.getName()
                    + "</white> <gray>wants to teleport <green>to you</green>.");
        } else {
            plugin.msg().send(target, "<white>" + requester.getName()
                    + "</white> <gray>wants <green>you to teleport to them</green>.");
        }
        plugin.msg().send(target, "<gray>Type <#f9d423>/tpaccept " + requester.getName()
                + "</#f9d423> or <#ff4e50>/tpdeny " + requester.getName() + "</#ff4e50>.");
    }

    private void accept(Player target, String[] args) {
        Request req = pick(target.getUniqueId(), args);
        if (req == null) {
            plugin.msg().send(target, "<red>You have no pending teleport requests.");
            return;
        }
        Player requester = Bukkit.getPlayer(req.requester());
        remove(target.getUniqueId(), req);
        if (requester == null) {
            plugin.msg().send(target, "<red>That player is no longer online.");
            return;
        }
        if (req.type() == Type.TO) {
            requester.teleport(target.getLocation());
            plugin.msg().send(requester, "<green>Teleported to <white>" + target.getName() + "</white>.");
            plugin.msg().send(target, "<green>" + requester.getName() + " teleported to you.");
        } else {
            target.teleport(requester.getLocation());
            plugin.msg().send(target, "<green>Teleported to <white>" + requester.getName() + "</white>.");
            plugin.msg().send(requester, "<green>" + target.getName() + " teleported to you.");
        }
    }

    private void deny(Player target, String[] args) {
        Request req = pick(target.getUniqueId(), args);
        if (req == null) {
            plugin.msg().send(target, "<red>You have no pending teleport requests.");
            return;
        }
        remove(target.getUniqueId(), req);
        plugin.msg().send(target, "<yellow>Denied <white>" + req.requesterName() + "</white>'s request.");
        Player requester = Bukkit.getPlayer(req.requester());
        if (requester != null) {
            plugin.msg().send(requester, "<red><white>" + target.getName()
                    + "</white> denied your teleport request.");
        }
    }

    private void cancel(Player requester) {
        boolean removed = false;
        for (List<Request> list : incoming.values()) {
            removed |= list.removeIf(r -> r.requester().equals(requester.getUniqueId()));
        }
        plugin.msg().send(requester, removed ? "<yellow>Cancelled your outgoing request(s)."
                : "<gray>You have no outgoing requests.");
    }

    /** Choose a matching, non-expired request (by requester name, or the newest). */
    private Request pick(UUID target, String[] args) {
        List<Request> list = incoming.get(target);
        if (list == null) return null;
        list.removeIf(Request::expired);
        if (list.isEmpty()) return null;
        if (args.length >= 1) {
            for (int i = list.size() - 1; i >= 0; i--) {
                if (list.get(i).requesterName().equalsIgnoreCase(args[0])) return list.get(i);
            }
            return null;
        }
        return list.get(list.size() - 1);
    }

    private void remove(UUID target, Request req) {
        List<Request> list = incoming.get(target);
        if (list != null) list.remove(req);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length != 1 || !(sender instanceof Player player)) return out;
        String name = command.getName().toLowerCase();
        if (name.equals("tpa") || name.equals("tpahere")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(player) && p.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    out.add(p.getName());
                }
            }
        } else if (name.equals("tpaccept") || name.equals("tpdeny")) {
            List<Request> list = incoming.get(player.getUniqueId());
            if (list != null) {
                for (Request r : list) {
                    if (!r.expired() && r.requesterName().toLowerCase().startsWith(args[0].toLowerCase())) {
                        out.add(r.requesterName());
                    }
                }
            }
        }
        return out;
    }
}
