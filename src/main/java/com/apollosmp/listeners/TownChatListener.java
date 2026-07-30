package com.apollosmp.listeners;

import com.apollosmp.ApolloSMP;
import com.apollosmp.town.Town;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

/** Adds a [Town] tag in front of a resident's chat messages, plus the [item] showcase. */
public class TownChatListener implements Listener {

    private final ApolloSMP plugin;

    public TownChatListener(ApolloSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        // In incognito/disguise mode: no rank badge, no town tag, no channels -
        // just the random display name, so nothing gives the admin away.
        if (plugin.incognito().isIncognito(event.getPlayer().getUniqueId())) {
            event.renderer((source, sourceName, message, viewer) ->
                    sourceName.colorIfAbsent(NamedTextColor.WHITE)
                            .append(Component.text(": ", NamedTextColor.GRAY))
                            .append(message.colorIfAbsent(NamedTextColor.WHITE)));
            return;
        }

        Town town = plugin.towns().getTownOf(event.getPlayer().getUniqueId());

        // If they're in a town or ally channel, this never reaches public chat.
        var channel = plugin.channels().of(event.getPlayer());
        if (town != null && channel != com.apollosmp.town.ChatChannels.Channel.PUBLIC) {
            String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(event.message());
            event.setCancelled(true);
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> plugin.channels().send(event.getPlayer(), channel, text));
            return;
        }

        // Apollo+ can drop the item they're holding into chat by typing [item].
        final ItemStack showcase = showcaseFor(event);

        com.apollosmp.staff.StaffRank staff = plugin.ranks().of(event.getPlayer());
        if (town == null && staff == null) {
            if (showcase != null) {
                event.renderer((source, sourceName, message, viewer) ->
                        sourceName.colorIfAbsent(NamedTextColor.WHITE)
                                .append(Component.text(": ", NamedTextColor.GRAY))
                                .append(withShowcase(message, showcase).colorIfAbsent(NamedTextColor.WHITE)));
            }
            return; // nothing else to add
        }

        final Component badge = staff == null
                ? Component.empty()
                : Component.text("[", NamedTextColor.DARK_GRAY)
                        .append(Component.text(staff.display(),
                                TextColor.fromHexString(staff.colour())))
                        .append(Component.text("] ", NamedTextColor.DARK_GRAY));

        if (town == null) {
            // Staff with no town still get their badge.
            event.renderer((source, sourceName, message, viewer) ->
                    badge.append(sourceName.colorIfAbsent(NamedTextColor.WHITE))
                            .append(Component.text(": ", NamedTextColor.GRAY))
                            .append(withShowcase(message, showcase).colorIfAbsent(NamedTextColor.WHITE)));
            return;
        }

        final String tag = town.name();
        TextColor tagColour = TextColor.fromHexString(town.tagColour());
        final TextColor townColour = tagColour == null
                ? TextColor.fromHexString("#f9d423") : tagColour;

        // Just the town name in chat - no in-town role/title.
        event.renderer((source, sourceName, message, viewer) ->
                badge.append(Component.text("[", NamedTextColor.GRAY))
                        .append(Component.text(tag, townColour))
                        .append(Component.text("] ", NamedTextColor.GRAY))
                        .append(sourceName.colorIfAbsent(NamedTextColor.WHITE))
                        .append(Component.text(": ", NamedTextColor.GRAY))
                        .append(withShowcase(message, showcase).colorIfAbsent(NamedTextColor.WHITE)));
    }

    /** The held item if the player is Apollo+ and typed the [item] token, else null. */
    private ItemStack showcaseFor(AsyncChatEvent event) {
        if (!event.getPlayer().hasPermission("apollo.plus")
                && !event.getPlayer().hasPermission("apollo.admin")) {
            return null;
        }
        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(event.message());
        if (!text.contains("[item]")) return null;
        ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) return null;
        return hand.clone();
    }

    /** Replace the literal [item] token with a hoverable representation of the item. */
    private Component withShowcase(Component message, ItemStack item) {
        if (item == null) return message;
        Component itemComp = Component.text("[" + com.apollosmp.util.Items.pretty(item.getType()) + "]")
                .color(TextColor.fromHexString("#5ad1e8"))
                .hoverEvent(item.asHoverEvent());
        return message.replaceText(b -> b.matchLiteral("[item]").replacement(itemComp));
    }
}
