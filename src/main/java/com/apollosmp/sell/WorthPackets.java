package com.apollosmp.sell;

import com.apollosmp.ApolloSMP;
import com.apollosmp.util.Msg;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows each item's sell price on its tooltip WITHOUT ever touching the real item.
 *
 * It works by rewriting items as they're sent to the client (the SET_SLOT and
 * WINDOW_ITEMS packets): the copy the player sees gets a price line, while the
 * item sitting on the server stays byte-for-byte vanilla. Because the server item
 * is unchanged, identical items always stack normally.
 *
 * This class touches ProtocolLib classes, so it's only ever loaded when ProtocolLib
 * is actually installed (see ApolloSMP#setupWorthPackets). It's active only while
 * sell.worth-display is "tooltip".
 */
public class WorthPackets {

    private final ApolloSMP plugin;
    private PacketAdapter adapter;

    public WorthPackets(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    /** Start listening. Returns true if the listener registered successfully. */
    public boolean register() {
        ProtocolManager pm = ProtocolLibrary.getProtocolManager();
        adapter = new PacketAdapter(plugin, ListenerPriority.NORMAL,
                PacketType.Play.Server.SET_SLOT,
                PacketType.Play.Server.WINDOW_ITEMS) {
            @Override
            public void onPacketSending(PacketEvent event) {
                try {
                    handle(event);
                } catch (Throwable ignored) {
                    // Never let a display tweak break the packet pipeline.
                }
            }
        };
        pm.addPacketListener(adapter);
        return true;
    }

    public void unregister() {
        if (adapter == null) return;
        try {
            ProtocolLibrary.getProtocolManager().removePacketListener(adapter);
        } catch (Throwable ignored) {
            // ProtocolLib may already be shutting down.
        }
        adapter = null;
    }

    private void handle(PacketEvent event) {
        // Only decorate while the tooltip mode is selected.
        if (!"tooltip".equals(plugin.worthDisplayMode())) return;

        PacketContainer packet = event.getPacket();
        if (event.getPacketType() == PacketType.Play.Server.SET_SLOT) {
            StructureModifier<ItemStack> items = packet.getItemModifier();
            ItemStack decorated = decorate(items.read(0));
            if (decorated != null) items.write(0, decorated);
            return;
        }

        // WINDOW_ITEMS carries the whole inventory at once.
        StructureModifier<List<ItemStack>> listMod = packet.getItemListModifier();
        List<ItemStack> list = listMod.read(0);
        if (list == null || list.isEmpty()) return;

        List<ItemStack> out = new ArrayList<>(list.size());
        boolean changed = false;
        for (ItemStack item : list) {
            ItemStack decorated = decorate(item);
            if (decorated != null) {
                out.add(decorated);
                changed = true;
            } else {
                out.add(item);
            }
        }
        if (changed) listMod.write(0, out);
    }

    /**
     * Return a CLIENT-ONLY copy of the item with the price appended, or null to
     * send the item unchanged. Never mutates the item passed in.
     */
    private ItemStack decorate(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        if (!plugin.sell().isSellable(item)) return null;
        double total = plugin.sell().valueOf(item);
        if (total <= 0) return null;

        int amount = Math.max(1, item.getAmount());
        double unit = total / amount;

        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        if (meta == null) return null;

        List<Component> lore = meta.lore();
        lore = (lore == null) ? new ArrayList<>() : new ArrayList<>(lore);
        if (plugin.getConfig().getBoolean("sell.worth-lore-total", false) && amount > 1) {
            lore.add(Msg.lore("<#f9d423>" + plugin.msg().money(total) + "</#f9d423>"));
            lore.add(Msg.lore("<dark_gray>" + plugin.msg().money(unit) + " each</dark_gray>"));
        } else {
            lore.add(Msg.lore("<#f9d423>" + plugin.msg().money(unit) + "</#f9d423>"));
        }
        meta.lore(lore);
        copy.setItemMeta(meta);
        return copy;
    }
}
