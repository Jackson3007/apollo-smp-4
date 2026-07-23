package com.apollosmp.gui.menus;

import com.apollosmp.ApolloSMP;
import com.apollosmp.gui.Gui;
import com.apollosmp.town.Town;
import com.apollosmp.town.TownPerm;
import com.apollosmp.town.TownRank;
import com.apollosmp.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Manage a town's residents: invite, promote/demote, and kick. */
public class TownMembersMenu extends Gui {

    private final List<UUID> slotMembers = new ArrayList<>();

    public TownMembersMenu(ApolloSMP plugin, Player viewer) {
        super(plugin, viewer, 6, "<#5ad1e8><bold>Town Members</bold>");
    }

    @Override
    protected void build() {
        Town town = plugin.towns().getTownOf(viewer.getUniqueId());
        slotMembers.clear();
        if (town == null) { player().closeInventory(); return; }

        inventory.setItem(4, Items.of(Material.WRITABLE_BOOK)
                .name("<green><bold>Invite a Player</bold>")
                .lore("<gray>Pick from the online players",
                        "<gray>who aren't in a town yet.").build());

        int slot = 9;
        for (Map.Entry<UUID, TownRank> e : town.members().entrySet()) {
            if (slot >= 45) break;
            UUID id = e.getKey();
            TownRank rank = e.getValue();
            String name = plugin.getServer().getOfflinePlayer(id).getName();
            if (name == null) name = "Unknown";
            boolean isMayor = id.equals(town.mayor());

            List<String> lore = new ArrayList<>();
            lore.add("<gray>Rank: <#5ad1e8>" + rank.display() + "</#5ad1e8>");
            if (!isMayor) {
                lore.add("");
                lore.add("<green>Left-click:</green> <gray>promote");
                lore.add("<yellow>Right-click:</yellow> <gray>demote");
                lore.add("<red>Shift-click:</red> <gray>remove");
            } else {
                lore.add("<#f9d423>Town founder</#f9d423>");
            }

            Player online = plugin.getServer().getPlayer(id);
            ItemStack head = online != null
                    ? Items.playerHead(online, "<white>" + name + "</white>", lore)
                    : Items.of(Material.PLAYER_HEAD).name("<white>" + name + "</white>")
                        .lore(lore.toArray(new String[0])).build();
            inventory.setItem(slot, head);
            slotMembers.add(id);
            slot++;
        }

        inventory.setItem(49, Items.of(Material.ARROW).name("<gray>Back").build());
        fillEmpty(Items.filler(Material.GRAY_STAINED_GLASS_PANE));
    }

    @Override
    public void onClick(Player clicker, int slot, ItemStack clicked, ClickType click) {
        if (slot == 49) { new TownMenu(plugin, clicker).open(); return; }
        if (slot == 4) {
            new TownInviteMenu(plugin, clicker, 0).open();
            return;
        }
        int index = slot - 9;
        if (index < 0 || index >= slotMembers.size()) return;
        UUID target = slotMembers.get(index);
        Town town = plugin.towns().getTownOf(clicker.getUniqueId());
        if (town == null || target.equals(town.mayor())) return;

        if (click.isShiftClick()) {
            plugin.towns().kick(clicker, target);
            redraw();
            return;
        }
        TownRank current = town.rankOf(target);
        if (current == null) return;
        TownRank next;
        if (click.isRightClick()) {
            next = TownRank.values()[Math.min(TownRank.RESIDENT.ordinal(), current.ordinal() + 1)];
        } else {
            next = TownRank.values()[Math.max(TownRank.ASSISTANT.ordinal(), current.ordinal() - 1)];
        }
        if (next != current && town.hasPerm(clicker.getUniqueId(), TownPerm.SET_RANK)) {
            plugin.towns().setRank(clicker, target, next);
        }
        redraw();
    }

    private Player player() { return viewer; }
}
