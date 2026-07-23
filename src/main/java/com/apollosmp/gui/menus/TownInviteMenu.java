package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Pick an online player to invite — no typing required. */
public class TownInviteMenu extends Gui {

    private static final int PAGE_SIZE = 45;

    private final int page;
    private final List<String> shown = new ArrayList<>();

    public TownInviteMenu(ApolloSMP plugin, Player viewer, int page) {
        super(plugin, viewer, 6, "<green><bold>Invite a Player</bold>");
        this.page = Math.max(0, page);
    }

    @Override
    protected void build() {
        shown.clear();
        List<Player> candidates = new ArrayList<>();
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            if (online.getUniqueId().equals(viewer.getUniqueId())) continue;
            if (plugin.towns().getTownOf(online.getUniqueId()) != null) continue;
            candidates.add(online);
        }

        int from = page * PAGE_SIZE;
        int to = Math.min(candidates.size(), from + PAGE_SIZE);
        List<Player> pageItems = from >= candidates.size()
                ? new ArrayList<>() : candidates.subList(from, to);

        if (pageItems.isEmpty()) {
            inventory.setItem(22, Items.of(Material.BARRIER)
                    .name("<gray>Nobody available")
                    .lore("<gray>Everyone online is already in a town,",
                            "<gray>or nobody else is online.").build());
        }

        for (int i = 0; i < pageItems.size(); i++) {
            Player target = pageItems.get(i);
            inventory.setItem(i, Items.playerHead(target, "<white>" + target.getName() + "</white>",
                    List.of("<gray>Not in a town", "", "<yellow>Click to invite")));
            shown.add(target.getName());
        }

        for (int i = PAGE_SIZE; i < 54; i++) {
            inventory.setItem(i, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
        inventory.setItem(45, Items.of(Material.ARROW).name("<gray>Previous Page").build());
        inventory.setItem(49, Items.of(Material.BARRIER).name("<gray>Back").build());
        inventory.setItem(53, Items.of(Material.ARROW).name("<gray>Next Page").build());
    }

    @Override
    public void onClick(Player player, int slot, ItemStack clicked, ClickType click) {
        if (slot >= 0 && slot < PAGE_SIZE) {
            if (slot >= shown.size()) return;
            plugin.towns().invite(player, shown.get(slot));
            redraw();
            return;
        }
        switch (slot) {
            case 45 -> { if (page > 0) new TownInviteMenu(plugin, player, page - 1).open(); }
            case 49 -> new TownMembersMenu(plugin, player).open();
            case 53 -> new TownInviteMenu(plugin, player, page + 1).open();
            default -> { /* no-op */ }
        }
    }
}
