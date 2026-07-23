package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.util.Items;
import com.apollosmp.vote.VoteManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** The /vote hub: links to the server's vote sites. No rewards for now. */
public class VoteMenu extends Gui {

    private static final int[] SITE_SLOTS = {10, 12, 14, 16};
    private static final int CLOSE = 22;

    private final List<VoteManager.Service> services = new ArrayList<>();

    public VoteMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 3, "<gradient:#f9d423:#ff4e50><bold>Vote for Apollo</bold></gradient>");
    }

    @Override
    protected void build() {
        services.clear();
        services.addAll(plugin.voting().services());

        inventory.setItem(4, Items.of(Material.NETHER_STAR)
                .name("<gradient:#f9d423:#ff4e50><bold>Support the Server</bold></gradient>")
                .lore("<gray>Each confirmed vote pays you",
                        "<#f9d423>" + plugin.msg().money(plugin.voting().reward()) + "</#f9d423> <gray>and pushes Apollo up",
                        "<gray>the server lists.",
                        "",
                        plugin.voting().votifierActive()
                                ? "<green>\u2714 Votes are verified automatically."
                                : "<red>Vote verification isn't set up yet.",
                        "",
                        "<gray>Click a site below for its link.")
                .glow(true).hideAttributes().build());

        for (int i = 0; i < SITE_SLOTS.length; i++) {
            if (i < services.size()) {
                VoteManager.Service s = services.get(i);
                inventory.setItem(SITE_SLOTS[i], Items.of(Material.PAPER)
                        .name("<#f9d423><bold>" + s.name() + "</bold>")
                        .lore("<gray>" + s.link(),
                                "",
                                "<gray>Reward lands automatically once",
                                "<gray>the site confirms your vote.",
                                "<yellow>Click for a clickable link")
                        .glow(true).hideAttributes().build());
            } else {
                inventory.setItem(SITE_SLOTS[i], Items.of(Material.GRAY_DYE)
                        .name("<dark_gray>Vote site not set")
                        .lore("<dark_gray>Add it under voting.services",
                                "<dark_gray>in config.yml.").build());
            }
        }

        inventory.setItem(CLOSE, Items.of(Material.BARRIER).name("<red>Close").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot == CLOSE) { player.closeInventory(); return; }

        for (int i = 0; i < SITE_SLOTS.length; i++) {
            if (SITE_SLOTS[i] == slot && i < services.size()) {
                VoteManager.Service s = services.get(i);
                plugin.msg().send(player, "<gray>Vote here: <click:open_url:'" + s.link()
                        + "'><hover:show_text:'Click to open'><#5ad1e8><u>" + s.link()
                        + "</u></#5ad1e8></hover></click>");
                player.closeInventory();
                return;
            }
        }
    }
}
